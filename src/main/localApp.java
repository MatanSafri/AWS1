import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import services.ec2Service;

public class localApp {

	public static void main(String[] args)  {
		//s3Service s3 = new s3Service();
		ec2Service ec2 = new ec2Service();
		//ec2.createTagsToInstance("i-02ce5424d8433ea75", "type", "manager");
		//ec2.stopInstance("i-02ce5424d8433ea75");
		
		System.out.println(ec2.getInstances());
		//ec2.createInstance();
		//s3.saveFile("C:\\Users\\Matan Safri\\Documents\\University\\Semester8\\AWS\\1\\AWS1\\README.md");
//		try {
//			displayTextInputStream(s3.getFile("README.md"));
//		} catch (IOException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
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


