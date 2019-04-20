package services;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.CreateTagsRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.IamInstanceProfileSpecification;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.InstanceType;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.StartInstancesRequest;
import com.amazonaws.services.ec2.model.StopInstancesRequest;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;
import com.amazonaws.util.Base64;

public class ec2Service {
	
	private AmazonEC2 amazonEc2;
	
	public ec2Service()
	{
		AWSCredentials cred =  new ProfileCredentialsProvider().getCredentials();
		if (cred == null)
			System.out.println("Error getting credentials");
		else
			System.out.println(cred.getAWSAccessKeyId());
		
		AWSCredentialsProvider credentialsProvider = new AWSStaticCredentialsProvider(cred);
		
		
		amazonEc2 = AmazonEC2ClientBuilder.standard()
                .withCredentials(credentialsProvider)
                .withRegion("us-east-1")
                .build();
	}
	
	public String runJarOnEc2Script(String jarName)
	{
		 String script =  String.format("#!/bin/bash -xe" + "\n"
				 	+ "exec > >(tee /var/log/user-data.log|logger -t user-data -s 2>/dev/console) 2>&1" + "\n" 
					+ "yum install java-1.8.0-openjdk-devel -y" + "\n"
					+ "yum remove java-1.6.0-openjdk -y" + "\n"
					+ "cd ~" + "\n"
					+ "mkdir .aws" + "\n"
					+ "cd .aws" + "\n"
					+ "aws s3 cp s3://ass1jarsbucket/credentials credentials --region us-east-1" + "\n"
					+ "cd ../../.." + "\n"	
					+ "JAVA_VER=$(java -version 2>&1 | sed -n ';s/.* version \"\\(.*\\)\\.\\(.*\\)\\..*\"/\\1\\2/p;')" + "\n"
					+ "echo $JAVA_VER" + "\n"
					+ "aws s3 cp s3://ass1jarsbucket/%s.jar %s.jar --region us-east-1" + "\n"	               
	                + "java -jar %s.jar\n",jarName,jarName,jarName);
		  System.out.println(script);
        String str;
		try {
			str = new String( Base64.encode( script.getBytes( "UTF-8" )), "UTF-8" );
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return "";
		}
        return str;
	}
	
	public String createAndRunInstance(String iamRoleName,String script)
	{
		try {
            // Basic 32-bit Amazon Linux AMI 1.0 (AMI Id:ami-51792c38.)
            RunInstancesRequest request = new RunInstancesRequest("ami-51792c38", 1, 1).withKeyName("ass1Key");
            request.setInstanceType(InstanceType.T1Micro.toString());
            
            
            // set the iam role
            if (iamRoleName != null)
            {
	            IamInstanceProfileSpecification instanceRoles = new IamInstanceProfileSpecification();
	            instanceRoles.setName(iamRoleName);
	            request.setIamInstanceProfile(instanceRoles);
            }
            
            // set the script
            if (script != null)
            {
            	 request.setUserData(script);
            }
            
            List<Instance> instances = amazonEc2.runInstances(request).getReservation().getInstances();
            System.out.println("Launch instances: " + instances);
            return instances.get(0).getInstanceId();
 
        } catch (AmazonServiceException ase) {
            System.out.println("Caught Exception: " + ase.getMessage());
            System.out.println("Reponse Status Code: " + ase.getStatusCode());
            System.out.println("Error Code: " + ase.getErrorCode());
            System.out.println("Request ID: " + ase.getRequestId());
            return "";
        }
	}
	
	public void runInstance(String instanceId)
	{
		StartInstancesRequest request = new StartInstancesRequest()
		    .withInstanceIds(instanceId);
		
		amazonEc2.startInstances(request);
	}
	
	public void stopInstance(String instanceId)
	{
		StopInstancesRequest request = new StopInstancesRequest()
			    .withInstanceIds(instanceId);
		amazonEc2.stopInstances(request);
	}
	
	public void terminateInstance(String instanceId)
	{
		TerminateInstancesRequest request = new TerminateInstancesRequest().withInstanceIds(instanceId);
		amazonEc2.terminateInstances(request);
	}

	
	public void createTagsToInstance(String instanceId,String key,String value)
	{
		Collection<Tag> tags = new ArrayList<Tag>();
		Tag t = new Tag();
        t.setKey(key);
        t.setValue(value);
        tags.add(t);
        CreateTagsRequest createTagsRequest = new CreateTagsRequest();  
        createTagsRequest.withTags(tags);
        createTagsRequest.withResources(instanceId);
        amazonEc2.createTags(createTagsRequest);
	}
	
	public Collection<Instance> getInstancesByTag(String key,String value)
	{
		Collection<Instance> instances = new ArrayList<Instance>();
		boolean done = false;
		
		DescribeInstancesRequest request = new DescribeInstancesRequest();
		while(!done) 
		{
		    DescribeInstancesResult response = amazonEc2.describeInstances(request);
	
		    for(Reservation reservation : response.getReservations())
		    {
		        for(Instance instance : reservation.getInstances()) {
		        	for (Tag tag : instance.getTags())
		        	{
		        		if(tag.getKey().equals(key) && tag.getValue().equals(value))
		        			instances.add(instance);
		        	}
		        }
		    }
	
		    request.setNextToken(response.getNextToken());
	
		    if(response.getNextToken() == null) {
		        done = true;
		    }
		}
		return instances;
	}
	
	public Instance getInstanceById(String instanceId)
	{
		boolean done = false;
		
		DescribeInstancesRequest request = new DescribeInstancesRequest();
		while(!done) 
		{
		    DescribeInstancesResult response = amazonEc2.describeInstances(request);
	
		    for(Reservation reservation : response.getReservations())
		    {
		        for(Instance instance : reservation.getInstances()) {
		           if(instance.getInstanceId().equals(instanceId))   	 
		        	   return instance;
		        }
		    }
	
		    request.setNextToken(response.getNextToken());
	
		    if(response.getNextToken() == null) {
		        done = true;
		    }
		}
		return null;
	}
	
	public Collection<Instance> getAllInstances()
	{
		Collection<Instance> instances = new ArrayList<Instance>();
		boolean done = false;
		
		DescribeInstancesRequest request = new DescribeInstancesRequest();
		while(!done) 
		{
		    DescribeInstancesResult response = amazonEc2.describeInstances(request);
	
		    for(Reservation reservation : response.getReservations())
		    {
		        for(Instance instance : reservation.getInstances()) {
		           instances.add(instance);
		        }
		    }
	
		    request.setNextToken(response.getNextToken());
	
		    if(response.getNextToken() == null) {
		        done = true;
		    }
		}
		return instances;
	}
	
	public boolean isRunning(Instance instance)
	{
		int stateCode = instance.getState().getCode();
		
		if (stateCode == 0 || stateCode == 16) // 16 running or 0  pending codes
			return true;
		return false;
//		return (!instance.getState().getName().toLowerCase().equals("running")) && 
//				(!instance.getState().getName().toLowerCase().equals("pending"));
	}
	
	public boolean isTerminate(Instance instance)
	{
		int stateCode = instance.getState().getCode();
		
		if (stateCode == 32 || stateCode == 48) // 32 shutting-down  or 48 terminated
			return true;
		return false;	
	}	
}
