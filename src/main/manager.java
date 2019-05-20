import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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
import com.amazonaws.services.sqs.model.MessageAttributeValue;

import javafx.util.Pair;
import services.ec2Service;
import services.s3Service;
import services.sqsJmsService;
import services.sqsService;

public class manager {
	
	static ConcurrentHashMap<String,AtomicInteger> applicationTasks = 
			new ConcurrentHashMap<String, AtomicInteger>();
	//static ConcurrentHashMap<String,Pair<Pair<File,FileWriter>,BufferedWriter>> applicationFiles = 
		//	new ConcurrentHashMap<String,Pair<Pair<File,FileWriter>,BufferedWriter>>();
	static ConcurrentHashMap<String,fileProp> applicationFiles = 
				new ConcurrentHashMap<String,fileProp>();
	
	static Object lock = new Object();
	static int activeWorkersNum = 0;
	static  boolean terminate = false;
	static final int maxWorkers = 19;
	
	public static void main(String[] args)  {
		//applicationTasks.put("12345",new AtomicInteger(1));
		try {
			ExecutorService threadpool = Executors.newFixedThreadPool(8);
			
			// get active workers //- we do it every new task to check node down
			activeWorkersNum = getActiveWorkers().size();
					
			// create the workers queue if not exists 
			sqsJmsService.getInstance().createQueue(constants.workersQueueName);
			
			sqsJmsService.getInstance().getMessagesAsync(constants.managerQueueName, (message)->{

			// handle message in a different thread 
				threadpool.execute(() ->{	
				// read the file key in s3 from message and handle the file
				try {
					handleMessage(message);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			);
			});
			
			// create thread which check if no worker has been down 
			new Thread(() -> {
				//while (true )
				while(!terminate || (applicationTasks.size() != 0 && terminate))
				{
					for(int i= 0; i< activeWorkersNum - getActiveWorkers().size();i++)
						// create and start worker instance
		            	ec2Service.getInstance().createTagsToInstance(ec2Service.getInstance().createAndRunInstance(constants.adminRoleName,
		            			ec2Service.getInstance().runJarOnEc2Script("worker")), "type", "worker");
							
					try {
						Thread.sleep(5000);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
			).run();
		
			
			
			// for the first time as long as there are messages get them sync
			do {
				// first time get the messages sync
				final Message message = (sqsJmsService.getInstance().getMessagesSync(constants.managerQueueName));
				
				if (message != null)
				{
					threadpool.execute(() ->{
					try {
						handleMessage(message);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}});
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
	
	private static void handleMessage(Message message) throws InterruptedException
	{
		try {
			TextMessage textMessage = ((TextMessage)message);
			String data =textMessage.getText();
			String localAppId = textMessage.getStringProperty("localAppId");
			//System.out.format("Got message: type: %s, localAppId: %s, data: %s \n",((TextMessage)message).getStringProperty("header"),localAppId,data);
			switch(((TextMessage)message).getStringProperty("header")) {
			  case "new task":
				  synchronized (lock) {
					// only if terminate not requested 
					  	if (!terminate)
							// download the file from s3 and handle 
							handleNewFile(s3Service.getInstance().getFile(data),
									localAppId,Integer.parseInt(textMessage.getStringProperty("n")));
				}
				  	
			    break;
			  case "done PDF task": 
				  // first wait for all the tasks to be added before handling the task
				  synchronized (applicationTasks.get(localAppId)) {
					  handleEndTask(data, localAppId);   
				}
			   	
			    
			    
			    // terminate the manager if terminate requested and no more tasks left
			    if (terminate && applicationTasks.size() == 0)
			    {
			    	// sync to prevent creation of more workers while terminating
			    	synchronized (lock) {
				    	sqsJmsService.getInstance().closeConnection();
				    	String managerInstanceId = getManagerInstanceId();
				    	
				    	// terminate the workers 
				    	ec2Service.getInstance().terminateInstances(getActiveWorkers().stream().map(instance -> 
				    		instance.getInstanceId()
				    	).collect(Collectors.toList()));
				    	
				    	
				    	activeWorkersNum = 0;
				    	
				    	if (managerInstanceId != null)
				    		ec2Service.getInstance().terminateInstance(managerInstanceId);
				    	
				    	// sleep no need for any other action wait about 10 sec until the instance is closed 
				    	Thread.sleep(10000);
				    	return;
			    	}
			    }
			    break;
			  case "terminate":
				  synchronized (lock) {
					  terminate = true;
					  //in case manager got termination and has no more tasks
					  if (applicationTasks.size() == 0)
					    {
						  	// sync to prevent creation of more workers while terminating
					    	sqsJmsService.getInstance().closeConnection();
					    	String managerInstanceId = getManagerInstanceId();
					    	
					    	// terminate the workers 
					    	try
					    	{
					    	ec2Service.getInstance().terminateInstances(getActiveWorkers().stream().map(instance -> 
					    		instance.getInstanceId()
					    	).collect(Collectors.toList()));
					    	}
					    	catch(Exception e)
					    	{
					    		e.printStackTrace();
					    	}
					    	
					    	
					    	activeWorkersNum = 0;
					    	

					    	if (managerInstanceId != null)
					    	{
					    		try
					    		{
					    			ec2Service.getInstance().terminateInstance(managerInstanceId);
					    		}
					    		catch(Exception e)
					    		{
					    			e.printStackTrace();
					    		}
					    	}
					    	else
					    	{
					    		System.out.println("no manager was found");
					    	}
					    	
					    	// sleep no need for any other action wait about 10 sec until the instance is closed 
					    	Thread.sleep(10000);
					    	return;
				    	}
				}
				       
				 
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
		Iterable<Instance> instances = ec2Service.getInstance().getInstancesByTag("type", "manager");
		if (instances.iterator().hasNext())
			return instances.iterator().next().getInstanceId();
		return null;
	}
	
	private static void updateOutputFile(String localAppId,String finishedTask) throws IOException
	{    
		//BufferedWriter bw = applicationFiles.get(localAppId).getValue();
		BufferedWriter bw = applicationFiles.get(localAppId).getBufferedWriter();
		
	    // only one thread of the application can write to the output file in the same time 
	    synchronized (bw) {
//		    try(FileWriter fw = new FileWriter(outputFile);
//		    	    BufferedWriter bw = new BufferedWriter(fw);
//		    	    PrintWriter out = new PrintWriter(bw))
//	    	{
//		    
//	    	    out.println(finishedTask);
//	    	} 
	    	
	    	bw.write(finishedTask+"\n");
	    }
	}
	    //outputFile.createNewFile(); // if file already exists will do nothing 

	
	private static void handleEndTask(String input,String localAppId) throws IOException, JMSException {
		// create the output file if not exists  
		synchronized (applicationFiles) {
			 if (!applicationFiles.containsKey(localAppId))
			    {
			    	System.out.println("enter applicationFile");
			    	 // create the buffer writer - it is more efficient to keep one writer open because 
			    	 // the writing is done frequently 
			    	 String fileName =  UUID.randomUUID().toString()+".txt";
			    	 //System.out.println("the output file name is"+ fileName);
			    	 File outputFile = new File(fileName);
			    	 FileWriter fw = new FileWriter(outputFile);
			    	 BufferedWriter bw = new BufferedWriter(fw);  
			    	 	     	 
			    	/* applicationFiles.put(localAppId, 
			    			 new Pair<Pair<File, FileWriter>, BufferedWriter>
			    	 (new Pair<File, FileWriter>(outputFile, fw), bw));
			    	 	    
			    	 	    */
			    	 applicationFiles.put(localAppId, new fileProp(outputFile, fw, bw));
			    }
		}
	   
	    updateOutputFile(localAppId,input);
	    //applicationTasks.put(localAppId, new AtomicInteger(1));//TODO:remove - just for debugging   
	    
	    // finish local app mission
	    //System.out.println(applicationTasks.get(localAppId));
	    

	    if (applicationTasks.get(localAppId).decrementAndGet() == 0)
	    {
	    	applicationTasks.remove(localAppId);
	    	
	    	 Map<String,String> properties = new HashMap<String,String>();
			properties.put("header", "done task");
			properties.put("localAppId",localAppId);
			
			
			
			//Pair<Pair<File, FileWriter>,BufferedWriter> fileUtils = applicationFiles.get(localAppId);
			fileProp fileUtils = applicationFiles.get(localAppId);
			
			// close the buffer writer before uploading it to S3
	    	//fileUtils.getValue().close();   
	    	//fileUtils.getKey().getValue().close();
			
	    	fileUtils.getBufferedWriter().close();
	    	fileUtils.getFileWriter().close();

	    	
	    	
	    	
			// Save the output file in s3 
			//String fileLocation = s3Service.getInstance().saveFile(fileUtils.getKey().getKey().getAbsolutePath());
			s3Service.getInstance().saveFile(fileUtils.getFile().getAbsolutePath());
			String fileLocation = "ass1/"+ fileUtils.getFile().getName();
			System.out.println("saved");
	    	// Sending message notifying the local app that task is finished
			sqsJmsService.getInstance().sendMessage(localAppId, fileLocation, properties);
	    	
	    	// close the buffer writer
	    	//fileUtils.getValue().close();   
	    	//fileUtils.getKey().getValue().close();
	    	
	    	applicationFiles.remove(localAppId);
	    }
	}
	
	private static void handleNewFile(InputStream input,String localAppId,int n) throws IOException, JMSException {
		
		//int activeWorkersNum = getActiveWorkers().size();
		applicationTasks.put(localAppId, new AtomicInteger(0));
		
		synchronized (applicationTasks.get(localAppId))
		{		
			int lines = 0;
			
	        BufferedReader reader = new BufferedReader(new InputStreamReader(input));
	        while (true) {
	            String line = reader.readLine();
	            if (line == null) break;
	            
	            lines++;

		            if (lines - (activeWorkersNum * n) > 0 && activeWorkersNum <= maxWorkers)
		            {
		            	 //create and start worker instance
		            	String instanceId = ec2Service.getInstance().createAndRunInstance(constants.adminRoleName,
		            			ec2Service.getInstance().runJarOnEc2Script("worker")); 
		            	// wait for the worker to create - if not waiting could not be create yet
		            	try {
							Thread.sleep(1000);
						} catch (InterruptedException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
		            	 ec2Service.getInstance().createTagsToInstance(instanceId, "type", "worker");
		    			activeWorkersNum++;
		            }
	            
	            
	            // for each line send to workers queue a message
	            HashMap<String,MessageAttributeValue> properties = new HashMap<String,MessageAttributeValue>();
				properties.put("header",new MessageAttributeValue()
				        .withDataType("String")
				        .withStringValue("new PDF task") );
				properties.put("localAppId", new MessageAttributeValue()
				        .withDataType("String")
				        .withStringValue(localAppId));
				
				sqsService.getInstance().sendMessage(constants.workersQueueName, line,properties);
	
				
	            /*
	            HashMap<String,String> properties = new HashMap<String,String>();
				properties.put("header","new PDF task");
				properties.put("localAppId",localAppId);
	            
	            
				sqsJmsService.getInstance().sendMessage(constants.workersQueueName, line,properties);
				*/
	        }
	        
	        final int numberOfTasks = lines;
	        
	        applicationTasks.get(localAppId).updateAndGet(value -> numberOfTasks);
		}
        
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
		return ec2Service.getInstance().getInstancesByTag("type", "worker")
				.stream().filter(worker -> ec2Service.getInstance().isRunning(worker)).collect(Collectors.toList());
	}

}
