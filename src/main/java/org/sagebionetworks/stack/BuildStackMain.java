package org.sagebionetworks.stack;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.sagebionetworks.stack.config.InputConfiguration;

import com.amazonaws.services.cloudwatch.AmazonCloudWatchClient;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.SecurityGroup;
import com.amazonaws.services.rds.AmazonRDSClient;
import com.amazonaws.services.rds.model.DBParameterGroup;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.sns.AmazonSNSClient;
import com.amazonaws.services.sns.model.CreateTopicResult;

/**
 * The main class to start the stack builder
 * @author John
 *
 */
public class BuildStackMain {

	private static Logger log = Logger.getLogger(BuildStackMain.class.getName());
	/**
	 * This is the main script that builds the configuration.
	 * @param args
	 */
	public static void main(String[] args) {
		try{
			// Log the args.
			logArgs(args);
			// The path to the configuration property file must be provided
			if(args == null || args.length != 1) throw new IllegalArgumentException("The first argument must be the path configuration property file to be used to build this stack.");
			String pathConfig = args[0];
			// Load the configuration
			InputConfiguration config = loadConfiguration(pathConfig);
			// Load the default properties used for this stack
			Properties defaultStackProperties = StackDefaults.loadStackDefaultsFromS3(config, new AmazonS3Client(config.getAWSCredentials()));
			// Add the default properties to the config
			// Note: This is where all encrypted properties are also added.
			config.addPropertiesWithPlaintext(defaultStackProperties);
			
			// Collect all of the resources generated by this build
			GeneratedResources resources = new GeneratedResources();
			
			// The first step is to setup the stack security
			SecurityGroup elasticSecurityGroup = new EC2SecuritySetup(new AmazonEC2Client(config.getAWSCredentials()), config, resources).setupElasticBeanstalkEC2SecutiryGroup();
			
			// Setup the notification topic.
			CreateTopicResult topic = new NotificationSetup(new AmazonSNSClient(config.getAWSCredentials()), config, resources).setupNotificationTopics();
			
			// Setup the Database Parameter group
			DBParameterGroup dbParamGroup = new DatabaseParameterGroup(new AmazonRDSClient(config.getAWSCredentials()), config).setupDBParameterGroup();
			
			// Setup all of the database security groups
			new DatabaseSecuritySetup(new AmazonRDSClient(config.getAWSCredentials()), config, resources).setupDatabaseAllSecuityGroups();
			
			// We are read to create the database instances
			new MySqlDatabaseSetup(new AmazonRDSClient(config.getAWSCredentials()), config, resources).setupAllDatabaseInstances();
			
			// Add all of the the alarms
			new AlarmSetup(new AmazonCloudWatchClient(config.getAWSCredentials()), config, resources).setupAllAlarms();
			

		}catch(Throwable e){
			log.error("Terminating: ",e);
		}finally{
			log.info("Terminating stack builder\n\n\n");
		}
	}
	
	/**
	 * Load the configuration from the properties file
	 * 
	 * @param pathConfig
	 * @return
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	private static InputConfiguration loadConfiguration(String pathConfig)
			throws FileNotFoundException, IOException {
		// Load the config file
		File configFile = new File(pathConfig);
		if(!configFile.exists()) throw new IllegalArgumentException("Passed configuration file does not exist: "+configFile.getAbsolutePath());
		FileInputStream in =  new FileInputStream(configFile);
		try{
			Properties props = new Properties();
			props.load(in);
			return new InputConfiguration(props);
		}finally{
			in.close();
		}
	}
	
	/**
	 * Write the arguments to the log.
	 * @param args
	 */
	private static void logArgs(String[] args) {
		log.info("Starting Stack Builder...");
		if(args !=null){
			log.info("args[] length="+args.length);
			for(String arg: args){
				log.info(arg);
			}
		}
	}

}
