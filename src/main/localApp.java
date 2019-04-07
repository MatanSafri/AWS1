import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

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
	public static void main(String[] args)  {
	
		//System.out.println(sqs.createQueue("matanbala"));
		//sqs.sendMessage(sqs.getUrlByName("MatanAndShirQueue"), "balaaa");
		//System.out.println(sqs.getMessages(sqs.getUrlByName("MatanAndShirQueue")));
		
		//s3Service s3 = new s3Service();
		//s3.deleteFile("README.md");
		//ec2.createTagsToInstance("i-02ce5424d8433ea75", "type", "manager");
		//ec2.instanceIsRunning(ec2.getInstance("i-02ce5424d8433ea75"));
		
		if (!isManagerActive())
			ec2.runInstance("i-02ce5424d8433ea75");
		
		// TODO : change the path of the file to user input 
		String path = s3.saveFile("C:\\Users\\Matan Safri\\Documents\\University\\Semester8\\AWS\\1\\AWS1\\README.md");
		sqs.sendMessage(sqs.getUrlByName("MatanAndShirQueue"), path);
//		
//		
//		System.out.println(isManagerActive());
//		
//		ec2.stopInstance("i-02ce5424d8433ea75");
		
		//System.out.println(ec2.getInstances());
		//ec2.createInstance();
		//s3.saveFile("C:\\Users\\Matan Safri\\Documents\\University\\Semester8\\AWS\\1\\AWS1\\README.md");
//		try {
//			displayTextInputStream(s3.getFile("README.md"));
//		} catch (IOException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
	}
	
	static private boolean isManagerActive()
	{
		Iterable<Instance> instances = ec2.getInstancesByTag("type", "manager");
		if (instances.iterator().hasNext())
			return ec2.isInstanceRunningOrPending(instances.iterator().next());
		return false;
	}

	
	 /**
     * Displays the contents of the specified input stream as text.
     *
     * @param input
     *            The input stream to display as text.
     *
     * @throws IOException
     */
	/*
	private static void displayTextInputStream(InputStream input) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(input));
        while (true) {
            String line = reader.readLine();
            if (line == null) break;
 
            System.out.println("    " + line);
        }
        System.out.println();
    }
    */
}


