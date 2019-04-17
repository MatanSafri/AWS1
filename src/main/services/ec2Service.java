package services;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import com.amazonaws.AmazonServiceException;
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
		AWSCredentialsProvider credentialsProvider = new AWSStaticCredentialsProvider(new ProfileCredentialsProvider().getCredentials());
		amazonEc2 = AmazonEC2ClientBuilder.standard()
                .withCredentials(credentialsProvider)
                .withRegion("us-east-1")
                .build();
	}
	
	public String runJarOnEc2Script(String jarName)
	{
//		  String script =String.format( "#!/bin/bash" + "\n" 
//				  	+ "wget --no-check-certificate --no-cookies --header \"Cookie: oraclelicense=accept-securebackup-cookie\" http://download.oracle.com/otn-pub/java/jdk/8u141-b15/336fa29ff2bb4ef291e347e091f7f4a7/jdk-8u141-linux-x64.rp" + "\n"
//				  	+ "sudo yum install -y jdk-8u141-linux-x64.rpm" + "\n"
//				  	+ "sudo yum install java-1.8.0" + "\n" 
//				  	+ "sudo alternatives --config java" + "\n"
//	                + "aws s3 cp s3://matanandshirjarsbucket/%s.jar %s.jar" + "\n"
//	                + "java -jar %s.jar\n",jarName,jarName,jarName);
		
		 String script =  "#!/bin/bash -ex" + "\n"
				 	+ "$ cd /opt" + "\n"
				 	+ "wget --no-cookies --no-check-certificate --header \"Cookie: gpw_e24=http%3a%2F%2Fwww.oracle.com%2Ftechnetwork%2Fjava%2Fjavase%2Fdownloads%2Fjdk8-downloads-2133151.html; oraclelicense=accept-securebackup-cookie;\" \"https://download.oracle.com/otn-pub/java/jdk/8u191-b12/2787e4a523244c269598db4e85c51e0c/jdk-8u191-linux-x64.tar.gz\"" + "\n" 
				 	+ "sudo tar xzf jdk-8u191-linux-x64.tar.gz" + "\n"
				 	+ "cd jdk1.8.0_191/" + "\n"
				 	+ "sudo alternatives --install /usr/bin/java java /opt/jdk1.8.0_191/bin/java 2" + "\n"
				 	+ "sudo alternatives --config java" + "\n" + "\n" 
				 	+ "sudo alternatives --install /usr/bin/jar jar /opt/jdk1.8.0_191/bin/jar 2" + "\n"
				 	+ "sudo alternatives --set jar /opt/jdk1.8.0_191/bin/jar" + "\n"
				 	+ "sudo alternatives --set javac /opt/jdk1.8.0_191/bin/javac" + "\n" 
				 	+ "export JAVA_HOME=/opt/jdk1.8.0_191" + "\n"
				 	+ "export JRE_HOME=/opt/jdk1.8.0_191/jre" + "\n"
				 	+ "export PATH=$PATH:$JAVA_HOME/bin:$JRE_HOME/bin"
	                + "aws s3 cp s3://matanandshirjarsbucket/manager.jar manager.jar" + "\n"
	                + "java -jar manager.jar\n";
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
            RunInstancesRequest request = new RunInstancesRequest("ami-51792c38", 1, 1);
            request.setInstanceType(InstanceType.T1Micro.toString());
            
            
            // set the iam role
            if (iamRoleName != null)
            {
	            IamInstanceProfileSpecification instanceRoles = new IamInstanceProfileSpecification();
	            instanceRoles.setName(iamRoleName);
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
