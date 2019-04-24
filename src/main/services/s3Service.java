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
	
	final String bucketName = "ass1filesbucket";
	
	private static class s3ServiceHelper{
		 private static final s3Service INSTANCE = new s3Service();
	}
	
	public static s3Service getInstance(){
        return s3ServiceHelper.INSTANCE;
    }
	
	private s3Service()
	{
		 AWSCredentialsProvider credentialsProvider = new AWSStaticCredentialsProvider(new ProfileCredentialsProvider().getCredentials());
    	 amazonS3 = AmazonS3Client.builder()
				    .withRegion("us-east-1")
				    .withCredentials(credentialsProvider)
				    .build();
	}
	
	
	public void deleteFile(String fileKey)
	{
		amazonS3.deleteObject(bucketName,fileKey);
	}

	
	public InputStream getFile(String fileKey) {
		 S3Object object = amazonS3.getObject(new GetObjectRequest(bucketName, fileKey));
		return object.getObjectContent();
	}

	
	public String saveFile(String path) {
		 File file = new File(path);
		 return save(file);
	}
	
	public String saveFile(File file) {
		
		 return save(file);
	}
	
	private String save(File file)
	{
		 String key = "ass1/" +  file.getName().replace('\\', '_').replace('/','_').replace(':', '_');
		 PutObjectRequest req = new PutObjectRequest(bucketName, key, file);
		 amazonS3.putObject(req);
		 return key;
	}
	
	
}
