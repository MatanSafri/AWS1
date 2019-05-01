package services;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
 
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.CreateQueueRequest;
import com.amazonaws.services.sqs.model.DeleteMessageRequest;
import com.amazonaws.services.sqs.model.DeleteQueueRequest;
import com.amazonaws.services.sqs.model.GetQueueAttributesRequest;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.MessageAttributeValue;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.SendMessageRequest;


public class sqsService {
	
	private AmazonSQS sqs;
	
	private static class sqsServiceHelper{
		 private static final sqsService INSTANCE = new sqsService();
	}
	
	public static sqsService getInstance(){
      return sqsServiceHelper.INSTANCE;
  }
	
	private sqsService()
	{
		AWSCredentialsProvider credentialsProvider = new AWSStaticCredentialsProvider(new ProfileCredentialsProvider().getCredentials());
        sqs = AmazonSQSClientBuilder.standard()
                .withCredentials(credentialsProvider)
                .withRegion("us-east-1")
                .build();        
	}
	
	public String createQueue(String queueName)
	{
	   CreateQueueRequest createQueueRequest = new CreateQueueRequest(queueName);
       return  sqs.createQueue(createQueueRequest).getQueueUrl();
	}
	
	public void sendMessage(String queueName,String message,HashMap<String,MessageAttributeValue> properties)
	{
		final SendMessageRequest sendMessageRequest = new SendMessageRequest();
		sendMessageRequest.withMessageBody(message);
		sendMessageRequest.withQueueUrl(getUrlByName(queueName));
		sendMessageRequest.withMessageAttributes(properties);
		sqs.sendMessage(sendMessageRequest);
	}
	
	public List<Message> getMessages(String queueName)
	{
		ReceiveMessageRequest receiveMessageRequest = new ReceiveMessageRequest(getUrlByName(queueName));
        return sqs.receiveMessage(receiveMessageRequest.withMessageAttributeNames("localAppId")).getMessages();
	}
	
	public void deleteMessage(String queueName,Message message)
	{
		 String messageRecieptHandle = message.getReceiptHandle();
         sqs.deleteMessage(new DeleteMessageRequest(getUrlByName(queueName), messageRecieptHandle));
	}
	
	public String getUrlByName(String queueName)
	{
		return sqs.getQueueUrl(queueName).getQueueUrl();
	}
	
	public void deleteQueue(String queueUrl)
	{
		sqs.deleteQueue(new DeleteQueueRequest(queueUrl));
	}
	
	public int getQueueMessageCount(String queueName)
	{
		String attr = "ApproximateNumberOfMessages";
		Map<String, String> attributes = sqs.getQueueAttributes(
				new GetQueueAttributesRequest(getUrlByName(queueName)).withAttributeNames(attr)).getAttributes();
		return Integer.parseInt(attributes.get(attr));

	}
	
	public void changeVisibility (String queueName, Message msg, int time){
		sqs.changeMessageVisibility(getUrlByName(queueName), msg.getReceiptHandle(), time);
		
	}

}
