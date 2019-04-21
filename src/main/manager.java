import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.TextMessage;

import com.amazonaws.services.ec2.model.Instance;

import services.ec2Service;
import services.s3Service;
import services.sqsJmsService;
import services.sqsService;

public class manager {
	
	static sqsJmsService sqsJms;
	static ec2Service ec2 = new ec2Service();
	static s3Service s3 = new s3Service();
	static sqsService sqs = new sqsService();
	static final String workersQueueName = "MatanAndShirQueueWorkers";
	static final String managerQueueName = "MatanAndShirQueueManager";
	
	static ConcurrentHashMap<String,AtomicInteger> applicationTasks = new ConcurrentHashMap<String, AtomicInteger>();
	static ConcurrentHashMap<String,File> applicationFiles = new ConcurrentHashMap<String, File>();
	static int activeWorkersNum;
	static  boolean terminate = false;
	static final int maxWorkers = 19;
	
	public static void main(String[] args)  {
		
		try {
			
			ExecutorService threadpool = Executors.newFixedThreadPool(8);
			sqsJms = new sqsJmsService();
			// get active workers
			activeWorkersNum = getActiveWorkers().size();
			
			// create the workers queue if not exists 
			sqsJms.createQueue(workersQueueName);
			
			sqsJms = new sqsJmsService();	
			sqsJms.getMessagesAsync(managerQueueName, (message)->{
			// handle message in a different thread 
				threadpool.execute(() ->{	
				// read the file key in s3 from message and handle the file
				handleMessage(message);
			}
			);
			});
			
			
			// for the first time as long as there are messages get them sync
			do {
				// first time get the messages sync
				final Message message = (sqsJms.getMessagesSync(managerQueueName));
				
				if (message != null)
				{
					threadpool.execute(() ->{
					handleMessage(message);});
				}
				else 
				{
					break;
				}
				
			}while(!terminate);
			
			
			
			
		} catch (JMSException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	
	private static void handleMessage(Message message)
	{
		try {
			TextMessage textMessage = ((TextMessage)message);
			String data =textMessage.getText();
			String localAppId = textMessage.getStringProperty("localAppId");
			
			switch(((TextMessage)message).getStringProperty("header")) {
			  case "new task":
				  	// only if terminate not requested 
				  	if (!terminate)
						// download the file from s3 and handle 
						handleNewFile(s3.getFile(data),localAppId,Integer.parseInt(textMessage.getStringProperty("n")));
			    break;
			  case "done PDF task": 
			    handleEndTask(data, localAppId);   	
			    
			    // terminate the manager if terminate requested and no more tasks left
			    if (terminate && applicationTasks.size() == 0)
			    {
			    	sqsJms.closeConnection();
			    	String managerInstanceId = getManagerInstanceId();
			    	
			    	// terminate the workers 
			    	ec2.terminateInstances(getActiveWorkers().stream().map(instance -> 
			    		instance.getInstanceId()
			    	).collect(Collectors.toList()));
			    	
			    	if (managerInstanceId != null)
			    		ec2.terminateInstance(managerInstanceId);
			    	return;
			    }
			    break;
			  case "terminate":
				  terminate = true;
				  break;
			  default:
			    // code block
			}		
			
		} catch (JMSException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		catch (IOException e)
		{
			e.printStackTrace();
		}
	}
	
	private static String getManagerInstanceId()
	{
		Iterable<Instance> instances = ec2.getInstancesByTag("type", "manager");
		if (instances.iterator().hasNext())
			return instances.iterator().next().getInstanceId();
		return null;
	}
	
	private static void updateOutputFile(String localAppId,String finishedTask) throws IOException
	{    
	    File outputFile = applicationFiles.get(localAppId);
	    
	    // only one thread of the application can write to the output file in the same time 
	    synchronized (outputFile) {
		    try(FileWriter fw = new FileWriter(outputFile);
		    	    BufferedWriter bw = new BufferedWriter(fw);
		    	    PrintWriter out = new PrintWriter(bw))
	    	{
	    	    out.println(finishedTask);
	    	} 
	    }
	}
	    //outputFile.createNewFile(); // if file already exists will do nothing 

	
	private static void handleEndTask(String input,String localAppId) throws IOException, JMSException {
		// create the output file if not exists  
	    if (!applicationFiles.containsKey(localAppId))
	    {
	    	 String fileName =  UUID.randomUUID().toString();
	    	 File outputFile = new File(fileName);
	    	 applicationFiles.put(localAppId, outputFile);
	    	 
	    }
	    updateOutputFile(localAppId,input);
	    
	    // finish local app mission
	    if (applicationTasks.get(localAppId).decrementAndGet() == 0)
	    {
	    	applicationTasks.remove(localAppId);
	    	
	    	 Map<String,String> properties = new HashMap<String,String>();
			properties.put("header", "done task");
			properties.put("localAppId",localAppId);
			
			// Save the output file in s3 
			String fileLocation = s3.saveFile(applicationFiles.get(localAppId));
			
	    	// Sending message notifying the local app that task is finished
	    	sqsJms.sendMessage(localAppId, fileLocation, properties);
	    	
	    	applicationFiles.remove(localAppId);
	    }
	}
	
	private static void handleNewFile(InputStream input,String localAppId,int n) throws IOException, JMSException {
		
		//int activeWorkersNum = getActiveWorkers().size();
		
		int lines = 0;
		
        BufferedReader reader = new BufferedReader(new InputStreamReader(input));
        while (true) {
            String line = reader.readLine();
            if (line == null) break;
            
            lines++;
            synchronized (applicationTasks) 
            {
	            if (lines - (activeWorkersNum * n) > 0 && activeWorkersNum <= maxWorkers)
	            {
	            	// create and start worker instance
	    			ec2.createTagsToInstance(ec2.createAndRunInstance("ec2AdminRole",ec2.runJarOnEc2Script("worker")), "type", "worker");
	    			activeWorkersNum++;
	            }
            }
            
            // for each line send to workers queue a message
            Map<String,String> properties = new HashMap<String,String>();
			properties.put("header", "new PDF task");
			properties.put("localAppId", localAppId);
            sqsJms.sendMessage(workersQueueName, line,properties);
        }
        
        applicationTasks.put(localAppId, new AtomicInteger(lines));
        
    }
	
//	private static void startWorkers()
//	{
//		int workersQueueMessageCount = sqs.getQueueMessageCount(workersQueueName);
//		int activeWorkersNum = getActiveWorkers().size();
//		
//		// Divide  rounding up
//		int newWorkersNum = ((workersQueueMessageCount+n-1)/n) - activeWorkersNum;
//
//		for (int i = 0;i < newWorkersNum; i++)
//		{
//			// create and start worker instance
//			ec2.createTagsToInstance(ec2.createAndRunInstance(), "type", "worker");
//		}
//	}
	
	private static Collection<Instance> getActiveWorkers()
	{
		return ec2.getInstancesByTag("type", "worker");
	}

}
