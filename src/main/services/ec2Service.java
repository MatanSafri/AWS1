package services;
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
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.InstanceState;
import com.amazonaws.services.ec2.model.InstanceType;
import com.amazonaws.services.ec2.model.Reservation;
import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.StartInstancesRequest;
import com.amazonaws.services.ec2.model.StopInstancesRequest;
import com.amazonaws.services.ec2.model.Tag;
import com.amazonaws.services.ec2.model.TerminateInstancesRequest;

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
	public String createAndRunInstance()
	{
		try {
            // Basic 32-bit Amazon Linux AMI 1.0 (AMI Id:ami-51792c38.)
            RunInstancesRequest request = new RunInstancesRequest("ami-51792c38", 1, 1);
            request.setInstanceType(InstanceType.T1Micro.toString());
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
	
	public boolean isInstanceRunningOrPending(Instance instance)
	{
		return instance.getState().getName().toLowerCase().equals("running") || 
				instance.getState().getName().toLowerCase().equals("pending");
	}
		
}
