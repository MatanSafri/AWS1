import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.jms.JMSException;
import javax.jms.TextMessage;

import com.amazonaws.services.ec2.model.Instance;

import services.ec2Service;
import services.s3Service;
import services.sqsJmsService;
import services.sqsService;

public class localApp {
	
	static ec2Service ec2 = new ec2Service();
	static sqsService sqs = new sqsService();
	static s3Service s3 = new s3Service();
	static sqsJmsService sqsJms;
	static final String managerQueueName = "MatanAndShirQueueManager";
	//static final String localAppQueueName = "MatanAndShirQueueLocalApp";
	static final Integer n = 10;
	public static void main(String[] args)  {
		
		// get the args
		String inputFileName = "README.md"; //= args[0];
		String outputFileName ="bala.txt"; //= args[1];
		int n = 10; //= Integer.parseInt(args[2]);				
		
		// Running the manager if not active 
		try {
			
			sqsJms = new sqsJmsService();
			// create the manager queue 
			sqsJms.createQueue(managerQueueName);
			
			activateManager();
			// create a queue and a listener for the current localApplication
			String localAppId = UUID.randomUUID().toString();
			sqsJms = new sqsJmsService();
			sqsJms.createQueue(localAppId);
			sqsJms.getMessagesAsync(localAppId, (message) -> {
				// getting the path to the ready file on s3
				try {
					String path = ((TextMessage)message).getText();
					
					if (((TextMessage)message).getStringProperty("localAppId") != localAppId)
						return;
						
					InputStream fileStream = s3.getFile(path);
					saveFile(fileStream,outputFileName);
					
					// delete the queue 
					sqs.deleteQueue(sqs.getUrlByName(localAppId));
					sqsJms.closeConnection();
					return;
					
				} catch (JMSException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			});

			// upload the file to s3 
			String path = s3.saveFile(inputFileName);
			Map<String,String> properties = new HashMap<String,String>();
			properties.put("header", "new task");
			properties.put("localAppId", localAppId);
			properties.put("n", Integer.toString(n) );
			sqsJms.sendMessage(managerQueueName, path,properties);
			
			// TODO: Sends a termination message to the Manager if it was supplied as one of its input arguments.
			if ((args.length == 4 && args[4] == "terminate"))
			{
				properties.put("header", "terminate");
				sqsJms.sendMessage(managerQueueName, "",properties);
			}
		} catch (JMSException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
	}
	
	private static void activateManager() throws JMSException
	{
		
		Iterable<Instance> instances = ec2.getInstancesByTag("type", "manager");
		// run the manager if needed
		if (instances.iterator().hasNext())
		{
			Instance instance= instances.iterator().next();
			if(!ec2.isRunning(instance))
			{
				if (ec2.isTerminate(instance))
					createManagerInstance();
				else
					ec2.runInstance(instance.getInstanceId());
			}
		}
		// create the manager instance
		else
		{
			createManagerInstance();
		}
	}

	private static void saveFile(InputStream inputStream,String fileName) throws IOException {
		//Path path = Paths.get(System.getProperty("user.dir"));
		Path path = Paths.get(fileName);
		Files.copy(inputStream, path, StandardCopyOption.REPLACE_EXISTING);
	}
	
	private static void createManagerInstance()
	{
		String managerInstanceId = ec2.createAndRunInstance("ec2AdminRole",ec2.runJarOnEc2Script("manager"));
		ec2.createTagsToInstance(managerInstanceId, "type", "manager");
	}
}


