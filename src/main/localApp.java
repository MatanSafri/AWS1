import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
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
	//static sqsService sqs = new sqsService();
	static s3Service s3 = new s3Service();
	static sqsJmsService sqsJms;
	static final String managerQueueName = "MatanAndShirQueueManager";
	public static void main(String[] args)  {
		
		// Running the manager if not active 
		try {
			activateManager();
			// create a queue and a listener for the current localApplication
			String localQueueName = UUID.randomUUID().toString();
			sqsJms = new sqsJmsService();
			sqsJms.createQueue(localQueueName);
			sqsJms.getMessagesAsync(localQueueName, (message) -> {
				// getting the path to the ready file on s3
				try {
					String path = ((TextMessage)message).getText();
					InputStream fileStream = s3.getFile(path);
					saveFile(fileStream);
					
				} catch (JMSException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			});

			// upload the file to s3 
			// TODO : change the path of the file to user input 
			//String path = s3.saveFile("C:\\Users\\Matan Safri\\Documents\\University\\Semester8\\AWS\\1\\AWS1\\README.md");
			String path = s3.saveFile("README.md");
			sqsJms.sendMessage("MatanAndShirQueue", path);
			
			// TODO: Sends a termination message to the Manager if it was supplied as one of its input arguments.
		} catch (JMSException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
	}
	
	static private void activateManager() throws JMSException
	{
		// create the manager instance if not exists already 
		if (sqsJms.createQueue(managerQueueName))
			return;
		// run the manager if needed
		Iterable<Instance> instances = ec2.getInstancesByTag("type", "manager");
		if (instances.iterator().hasNext())
		{
			Instance instance= instances.iterator().next();
			if(!ec2.isInstanceRunningOrPending(instance))
				ec2.runInstance(instance.getInstanceId());
		}
	}

	private static void saveFile(InputStream inputStream) throws IOException {	
		Path path = Paths.get(System.getProperty("user.dir"));
		Files.copy(inputStream, path, StandardCopyOption.REPLACE_EXISTING);
	}
}


