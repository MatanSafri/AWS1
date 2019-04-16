import javax.jms.JMSException;

import services.ec2Service;
import services.s3Service;
import services.sqsJmsService;
import services.sqsService;

public class worker {
	
	static sqsJmsService sqsJms;
	static ec2Service ec2 = new ec2Service();
	static s3Service s3 = new s3Service();
	static sqsService sqs = new sqsService();
	static final String workersQueueName = "MatanAndShirQueueWorkers";
	static final String managerQueueName = "MatanAndShirQueueManager";
	static final int n = 10;
	
	public static void main(String[] args)  {
		try {
			sqsJms = new sqsJmsService();
			sqsJms.sendMessage(managerQueueName, "bala", null);
		} catch (JMSException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
}
