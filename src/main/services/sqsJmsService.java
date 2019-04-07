package services;

import java.util.function.Consumer;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.MessageListener;
import javax.jms.MessageProducer;
import javax.jms.Queue;
import javax.jms.Session;
import javax.jms.TextMessage;

import com.amazon.sqs.javamessaging.AmazonSQSMessagingClientWrapper;
import com.amazon.sqs.javamessaging.ProviderConfiguration;
import com.amazon.sqs.javamessaging.SQSConnection;
import com.amazon.sqs.javamessaging.SQSConnectionFactory;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;

public class sqsJmsService {
	
	private SQSConnection connection;
	
	public sqsJmsService() throws JMSException
	{

		AWSCredentialsProvider credentialsProvider = new AWSStaticCredentialsProvider(new ProfileCredentialsProvider().getCredentials());
		
		SQSConnectionFactory connectionFactory = new SQSConnectionFactory(
		        new ProviderConfiguration(),
		        AmazonSQSClientBuilder.standard().withRegion("us-east-2").withCredentials(credentialsProvider)
		        );
		 

		connection = connectionFactory.createConnection();
	
	}
	
	public void createQueue(String queueName) throws JMSException
	{
		// Get the wrapped client
		AmazonSQSMessagingClientWrapper client = connection.getWrappedAmazonSQSClient();
		 
		// Create an SQS queue named MyQueue, if it doesn't already exist
		if (!client.queueExists(queueName)) {
		    client.createQueue(queueName);
		}
	}
	
	
	public void sendMessage(String queueName,String message) throws JMSException
	{
		// Create the nontransacted session with AUTO_ACKNOWLEDGE mode
		Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
		
		// Create a queue identity and specify the queue name to the session
		Queue queue = session.createQueue(queueName);
		 
		// Create a producer for the 'MyQueue'
		MessageProducer producer = session.createProducer(queue);
		
		// Create the text message
		TextMessage textMessage = session.createTextMessage(message);
		 
		// Send the message
		producer.send(textMessage);
		System.out.println("JMS Message " + textMessage.getJMSMessageID());
	}
	
	public Message getMessagesSync(String queueName) throws JMSException
	{
		// Create the nontransacted session with AUTO_ACKNOWLEDGE mode
		Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
		
		// Create a queue identity and specify the queue name to the session
		Queue queue = session.createQueue(queueName);
				
		// Create a consumer for the 'MyQueue'
		MessageConsumer consumer = session.createConsumer(queue);
		// Start receiving incoming messages
		connection.start();
		
		// Receive a message from queue and wait up to 1 second
		Message receivedMessage = consumer.receive(1000);
		
		connection.close();
		
		return receivedMessage;
	}
	
	
	public void getMessagesAsync(String queueName,Consumer<Message> onMessage) throws JMSException
	{
		// Create the nontransacted session with AUTO_ACKNOWLEDGE mode
		Session session = connection.createSession(false, Session.AUTO_ACKNOWLEDGE);
		
		// Create a queue identity and specify the queue name to the session
		Queue queue = session.createQueue(queueName);
		
		// Create a consumer for the 'MyQueue'.
		MessageConsumer consumer = session.createConsumer(queue);
		 
		// Instantiate and set the message listener for the consumer.
		consumer.setMessageListener(new MessageListener () {

			@Override
			public void onMessage(Message message) {
				onMessage.accept(message);
			}
			
		});
		
		// Start receiving incoming messages.
		connection.start();
	}

}
