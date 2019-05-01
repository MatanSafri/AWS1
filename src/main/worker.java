import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.jms.JMSException;
import javax.jms.TextMessage;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.MessageAttributeValue;

import services.s3Service;
import services.sqsJmsService;
import services.sqsService;

public class worker {
	
	static PDFfunc pdf_handler = new PDFfunc();
	static boolean allowTimer;
	public static void main(String[] args)  {
		allowTimer=true;
		while (true){
			List<Message> messages = sqsService.getInstance().getMessages(constants.workersQueueName);
			if(!messages.isEmpty()) //in case message recieved
			{
				Message message = messages.get(0);
				String localAppId =  message.getMessageAttributes().get("localAppId").getStringValue();
				
				//every 25 seconds extend the visibility time by more 30 seconds
				ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor();
				//((ScheduledThreadPoolExecutor) exec).setRemoveOnCancelPolicy(true); //when cancellation there will be also removale
				
				ScheduledFuture<?> scheduledFuture = exec.scheduleAtFixedRate(new Runnable() {
				  @Override
				  public void run() {
				  		if(allowTimer)
				  			sqsService.getInstance().changeVisibility(constants.workersQueueName, message, 30);
				  }
				}, 0, 25, TimeUnit.SECONDS); // extend the visibility time
				
				

				String[] msg_prop;
				try {
					msg_prop = message.getBody().split("\t");
					 //the operation and url are splitted by tab
					try{
						sqsService.getInstance().changeVisibility(constants.workersQueueName, message, 60);

						File new_file = pdf_handler.handle(msg_prop[0], msg_prop[1]); //o-operation, 1-input URL
						
						s3Service.getInstance().saveFile(new_file.getAbsolutePath()); 
							
						Map<String,String> properties = new HashMap<String,String>();
						properties.put("header", "done PDF task");
						properties.put("localAppId", localAppId);
						// send message back to the manager
						//TODO: ensure the output URL
						sqsJmsService.getInstance().sendMessage(constants.managerQueueName, msg_prop[0]+": "+ msg_prop[1] + " https://s3.us-east-2.amazonaws.com/matanandshirbucket/ass1/"+ new_file.getName() ,properties); 
						//finished handle this msg, delete it from queue
						sqsService.getInstance().deleteMessage(constants.workersQueueName, message);
						System.out.print("msg deleted");
						scheduledFuture.cancel(true);
						allowTimer = false;
							
					} catch (MalformedURLException e ){
						Map<String,String> properties = new HashMap<String,String>();
						properties.put("header", "done PDF task");
						properties.put("localAppId", localAppId);
						sqsJmsService.getInstance().sendMessage(constants.managerQueueName, msg_prop[0]+": "+ msg_prop[1] + " MalformedURLExeption occured" ,properties); 
						sqsService.getInstance().deleteMessage(constants.workersQueueName, message); //this is awrong URL, no need to check again
						scheduledFuture.cancel(true);
						allowTimer = false;
					}
					catch (IOException e){ 
						Map<String,String> properties = new HashMap<String,String>();
						properties.put("header", "done PDF task");
						properties.put("localAppId", localAppId);
						sqsJmsService.getInstance().sendMessage(constants.managerQueueName, msg_prop[0]+": "+ msg_prop[1] + " IOException occured" ,properties); 
						sqsService.getInstance().deleteMessage(constants.workersQueueName, message); 
						allowTimer = false;
						scheduledFuture.cancel(true);
						//sqs.changeVisibility(workersQueueName, message, 0); //if we want the msg to stay in queue and to be visible again
					}
						
				} catch (JMSException e1) {
					//TODO: check what to do here			
				}
			}
		}

	}
	
}
