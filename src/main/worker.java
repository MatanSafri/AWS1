import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.jms.JMSException;
import javax.jms.TextMessage;

import com.amazonaws.services.sqs.model.Message;

import services.ec2Service;
import services.s3Service;
import services.sqsJmsService;
import services.sqsService;

public class worker {
	
	static sqsJmsService sqsJms ;
	//static ec2Service ec2 = new ec2Service();
	static s3Service s3 = new s3Service();
	static sqsService sqs = new sqsService();
	static PDFfunc pdf_handler = new PDFfunc();
	static final String workersQueueName = "MatanAndShirQueueWorkers";
	static final String managerQueueName = "MatanAndShirQueueManager";
	static final int n = 10;
	

	public static void main(String[] args)  {
		try {
			sqsJms = new sqsJmsService();
		} catch (JMSException e2) {
			// TODO Auto-generated catch block
			e2.printStackTrace();
		}
		while (true){
			List<Message> messages = sqs.getMessages(workersQueueName);
			if(!messages.isEmpty()) //in case message recieved
			{
				Message message = messages.get(0);
				System.out.print(message.getBody());
				//every 25 seconds extend the visibility time by more 30 seconds
				ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor();
				//((ScheduledThreadPoolExecutor) exec).setRemoveOnCancelPolicy(true); //when cancellation there will be also removale
				
				ScheduledFuture<?> scheduledFuture = exec.scheduleAtFixedRate(new Runnable() {
				  @Override
				  public void run() {
				    sqs.changeVisibility(workersQueueName, message, 30);
				  }
				}, 0, 25, TimeUnit.SECONDS); // extend the visibility time
				
				String[] msg_prop;
				try {
					msg_prop = message.getBody().split("\t");
					 //the operation and url are splitted by tab
					try{
						File new_file = pdf_handler.handle(msg_prop[0], msg_prop[1]); //o-operation, 1-input URL
						
						s3.saveFile(new_file.getAbsolutePath()); 
							
						Map<String,String> properties = new HashMap<String,String>();
						properties.put("header", "done PDF task");
						// send message back to the manager
						//TODO: ensure the output URL
						sqsJms.sendMessage(managerQueueName, msg_prop[0]+": "+ msg_prop[1] + " https://s3.us-east-2.amazonaws.com/matanandshirbucket/ass1/"+ new_file.getName() ,properties); 
						//finished handle this msg, delete it from queue
						sqs.deleteMessage(workersQueueName, message);
						System.out.print("msg deleted");
						scheduledFuture.cancel(false);
							
					} catch (MalformedURLException e){
						Map<String,String> properties = new HashMap<String,String>();
						properties.put("header", "done PDF task");
						sqsJms.sendMessage(managerQueueName, msg_prop[0]+": "+ msg_prop[1] + " MalformedURLExeption occured" ,properties); 
						sqs.deleteMessage(workersQueueName, message); //this is awrong URL, no need to check again
						scheduledFuture.cancel(false);
					}
					catch (IOException e){ 
						Map<String,String> properties = new HashMap<String,String>();
						properties.put("header", "done PDF task");
						sqsJms.sendMessage(managerQueueName, msg_prop[0]+": "+ msg_prop[1] + " IOException occured" ,properties); 
						sqs.deleteMessage(workersQueueName, message); 
						scheduledFuture.cancel(false);
						//sqs.changeVisibility(workersQueueName, message, 0); //if we want the msg to stay in queue and to be visible again
					}
						
				} catch (JMSException e1) {
					//TODO: check what to do here			
				}
			}
		}
		
	
	//TODO: when I send back the msg to the manneger, add localAppID, the manager sent it in the first msg	

	}
}
