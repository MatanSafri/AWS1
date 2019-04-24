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
	
	static final Integer n = 10;
	public static void main(String[] args)  {
		
		// get the args
		String inputFileName = "README.md"; //= args[0];
		String outputFileName ="bala.txt"; //= args[1];
		int n = 10; //= Integer.parseInt(args[2]);				
		
		// Running the manager if not active 
		try {
				
			// create the manager queue 
			sqsJmsService.getInstance().createQueue(constants.managerQueueName);
			
			activateManager();
			// create a queue and a listener for the current localApplication
			String localAppId = UUID.randomUUID().toString();
			sqsJmsService.getInstance().createQueue(localAppId);
			sqsJmsService.getInstance().getMessagesAsync(localAppId, (message) -> {
				// getting the path to the ready file on s3
				try {
					String path = ((TextMessage)message).getText();
					
					if (((TextMessage)message).getStringProperty("localAppId") != localAppId)
						return;
						
					InputStream fileStream = s3Service.getInstance().getFile(path);
					saveFile(fileStream,outputFileName);
					
					// TODO: Sends a termination message to the Manager if it was supplied as one of its input arguments.
					if ((args.length == 4 && args[4] == "terminate"))
					{
						Map<String,String> properties = new HashMap<String,String>();
						properties.put("header", "terminate");
						sqsJmsService.getInstance().sendMessage(constants.managerQueueName, "",properties);
					}
					
					// delete the queue 
					sqsService.getInstance().deleteQueue(sqsService.getInstance().getUrlByName(localAppId));
					sqsJmsService.getInstance().closeConnection();
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
			String path = s3Service.getInstance().saveFile(inputFileName);
			Map<String,String> properties = new HashMap<String,String>();
			properties.put("header", "new task");
			properties.put("localAppId", localAppId);
			properties.put("n", Integer.toString(n) );
			sqsJmsService.getInstance().sendMessage(constants.managerQueueName, path,properties);
			
			
		} catch (JMSException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
	}
	
	private static void activateManager() throws JMSException
	{
		
		Iterable<Instance> instances = ec2Service.getInstance().getInstancesByTag("type", "manager");
		// run the manager if needed
		if (instances.iterator().hasNext())
		{
			Instance instance= instances.iterator().next();
			if(!ec2Service.getInstance().isRunning(instance))
			{
				if (ec2Service.getInstance().isTerminate(instance))
					createManagerInstance();
				else
					ec2Service.getInstance().runInstance(instance.getInstanceId());
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
		String managerInstanceId = ec2Service.getInstance().createAndRunInstance(constants.adminRoleName,
				ec2Service.getInstance().runJarOnEc2Script("manager"));
		ec2Service.getInstance().createTagsToInstance(managerInstanceId, "type", "manager");
	}
}