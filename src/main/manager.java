import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import javax.jms.JMSException;
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
	static final int n = 10;
	static Map<String,Integer> applicationTask = new HashMap<String, Integer>();
	
	public static void main(String[] args)  {
		try {
			sqsJms = new sqsJmsService();
			sqsJms.getMessagesAsync(managerQueueName, (message)->{
			// read the file key in s3 from message and handle the file
			try {
				String fileKey = ((TextMessage)message).getText();
				// download the file from s3 and handle 
				handleFile(s3.getFile(fileKey));	
				
				// TODO: handle terminate command
				
			} catch (JMSException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
				
			});
		} catch (JMSException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	
	private static void handleFile(InputStream input) throws IOException, JMSException {
		
		// create the workers queue if not exists 
		sqsJms.createQueue(workersQueueName);
		
		int activeWorkersNum = getActiveWorkers().size();
		
		int lines = 0;
		
        BufferedReader reader = new BufferedReader(new InputStreamReader(input));
        while (true) {
            String line = reader.readLine();
            if (line == null) break;
            
            lines++;
            if (lines - (activeWorkersNum * n) > 0)
            {
            	// create and start worker instance
    			ec2.createTagsToInstance(ec2.createAndRunInstance(), "type", "worker");
    			activeWorkersNum ++;
            }
            
            // for each line send to workers queue a message
            Map<String,String> properties = new HashMap<String,String>();
			properties.put("header", "new PDF task");
            sqsJms.sendMessage(workersQueueName, line,properties);
            
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
		return ec2.getInstancesByTag("type", "worker");
	}

}
