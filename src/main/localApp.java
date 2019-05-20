import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;

import javax.jms.JMSException;
import javax.jms.TextMessage;

import com.amazonaws.services.ec2.model.Instance;

import services.ec2Service;
import services.s3Service;
import services.sqsJmsService;
import services.sqsService;

public class localApp {
	
	public static void main(String[] args)  {
		
		// get the args
		String inputFileName = args[0];
		String outputFileName = args[1];
		int n = Integer.parseInt(args[2]);	
		//closeConnection = false;
		CountDownLatch latch = new CountDownLatch(1);
		
		
		// Running the manager if not active 
		try {
				
			// create the manager queue 
			sqsJmsService.getInstance().createQueue(constants.managerQueueName);
			
			activateManager();
			// create a queue and a listener for the current localApplication
			String localAppId = UUID.randomUUID().toString();
			//String localAppId = "12345";
			sqsJmsService.getInstance().createQueue(localAppId);
			
			sqsJmsService.getInstance().getMessagesAsync(localAppId, (message) -> {
				System.out.println("localapp got msg");
				// getting the path to the ready file on s3
				try {
					String path = ((TextMessage)message).getText();
										
					if ( !(((TextMessage)message).getStringProperty("localAppId").equals(localAppId)) )
						return;
						
					InputStream fileStream = s3Service.getInstance().getFile(path);
					saveFile(fileStream,outputFileName);
					
					// TODO: Sends a termination message to the Manager if it was supplied as one of its input arguments.
					if ((args.length == 4 && args[3].equals("terminate")))
					{
						Map<String,String> properties = new HashMap<String,String>();
						properties.put("header", "terminate");
						properties.put("localAppId", localAppId);	
						sqsJmsService.getInstance().sendMessage(constants.managerQueueName, "terminate message",properties);
					}
					
					latch.countDown();
					
					// delete the queue 
					//sqsService.getInstance().deleteQueue(sqsService.getInstance().getUrlByName(localAppId));
					//closeConnection = true;  //tell the thread to close the connection, it is not possible to close the connection from itself
					//return;
					
				} catch (JMSException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			});

//			new Thread(() -> {
//				if(closeConnection)
//					try {
//						sqsJmsService.getInstance().closeConnection();
//						closeConnection =false;
//					} catch (Exception e) {
//						// TODO Auto-generated catch block
//						e.printStackTrace();
//					}
//			}
//			).run();
			
			
		
			
			// upload the file to s3 
			String path = s3Service.getInstance().saveFile(inputFileName+".txt");
			Map<String,String> properties = new HashMap<String,String>();
			properties.put("header", "new task");
			properties.put("localAppId", localAppId);
			properties.put("n", Integer.toString(n) );
			sqsJmsService.getInstance().sendMessage(constants.managerQueueName, path,properties);
			
			// wait for signal from the message
			latch.await();
			sqsJmsService.getInstance().closeConnection();
			sqsService.getInstance().deleteQueue(sqsService.getInstance().getUrlByName(localAppId));
			
		} catch (JMSException e1) {
			// TODO Auto-generated catch block
			System.out.println("exception");
			e1.printStackTrace();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private static void activateManager() throws JMSException
	{
		
		Iterable<Instance> instances = ec2Service.getInstance().getInstancesByTag("type", "manager");
		
		
		List<Instance> list = new ArrayList<Instance>();
		instances.iterator().forEachRemaining(list::add);
		if (list.stream().allMatch(instance -> !ec2Service.getInstance().isRunning(instance)) || list.size() == 0)
			createManagerInstance();
			
		
//		// run the manager if needed
//		if (instances.iterator().hasNext())
//		{
//			Instance instance= instances.iterator().next();
//			if(!ec2Service.getInstance().isRunning(instance))
//			{
//				if (ec2Service.getInstance().isTerminate(instance))
//					createManagerInstance();
//				else
//					ec2Service.getInstance().runInstance(instance.getInstanceId());
//			}
//		}
//		// create the manager instance
//		else
//		{
//			createManagerInstance();
//		}
	}

	private static void saveFile(InputStream inputStream,String outputfileName) throws IOException {
		//Path path = Paths.get(System.getProperty("user.dir"));
		Path path = Paths.get(outputfileName+".html");
		Files.copy(inputStream, path, StandardCopyOption.REPLACE_EXISTING);
	}
	
	private static void createManagerInstance()
	{
		String managerInstanceId = ec2Service.getInstance().createAndRunInstance(constants.adminRoleName,
				ec2Service.getInstance().runJarOnEc2Script("manager"));
		ec2Service.getInstance().createTagsToInstance(managerInstanceId, "type", "manager");
	}
}