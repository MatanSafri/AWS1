package services;
import java.io.File;
import java.io.InputStream;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.services.s3.model.S3Object;

import myInterfaces.IfileSystem;


public class s3Service implements IfileSystem {
	
	private AmazonS3  amazonS3; 
	
	public s3Service()
	{
		 AWSCredentialsProvider credentialsProvider = new AWSStaticCredentialsProvider(new ProfileCredentialsProvider().getCredentials());
    	 amazonS3 = AmazonS3Client.builder()
				    .withRegion("us-east-2")
				    .withCredentials(credentialsProvider)
				    .build();
	}

	
	public InputStream getFile(String fileKey) {
		 S3Object object = amazonS3.getObject(new GetObjectRequest("matanandshirbucket", fileKey));
		return object.getObjectContent();
	}

	
	public void saveFile(String path) {
		 File file = new File(path);
		 String key = file.getName().replace('\\', '_').replace('/','_').replace(':', '_');
		 PutObjectRequest req = new PutObjectRequest("matanandshirbucket", key, file);
		 amazonS3.putObject(req);
	}
	
	
}
