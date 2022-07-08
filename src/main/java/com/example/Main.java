package com.example;
/*
 * All the required Classes from AWS and elastic search
 * */
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import co.elastic.clients.elasticsearch.indices.CreateIndexResponse;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.backup.AWSBackup;
import com.amazonaws.services.backup.AWSBackupClient;
import com.amazonaws.services.backup.model.*;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.*;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancing;
import com.amazonaws.services.elasticloadbalancingv2.AmazonElasticLoadBalancingClient;
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeLoadBalancersRequest;
import com.amazonaws.services.elasticloadbalancingv2.model.DescribeLoadBalancersResult;
import com.amazonaws.services.elasticloadbalancingv2.model.LoadBalancer;
import com.amazonaws.services.pricing.AWSPricing;
import com.amazonaws.services.pricing.AWSPricingClient;
import com.amazonaws.services.pricing.model.Filter;
import com.amazonaws.services.pricing.model.GetProductsRequest;
import com.amazonaws.services.pricing.model.GetProductsResult;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3Client;
import com.amazonaws.services.s3.model.Bucket;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.*;

/*
 * General Class Imports
 * */
import java.io.*;
import java.time.Instant;
import java.util.*;
import org.json.JSONObject;

/**
 * AWSCostOptimizerAndReportGenerator is the primary class which takes care of all the actions.
 * As of now, it retrieves the data from the AWS account using the credentials
 * provided, processes them and prepares a report of the resources which might be considered as unused.
 * */
class AWSCostOptimizerAndReportGenerator implements AwsCredentialsProvider {
    private static boolean DEBUG = true; // If true then Additional information is printed to assist in debugging

    /**
     * Major bottleneck of the code if this value is true
     * One point to note down is even if this is true, this version of code only supports price comparison for linux based instances
     * If it is true then a call to an API is made which takes about 10-12 minutes to run alone.
     */
    private static final boolean PRICE_COMPARISON = false;
    private static final boolean ADD_S3_DATA_TO_ELASTIC_SEARCH = false;
    private static final boolean SAVETIME = true; // If true then clubbing of getMetricData API calls happen that is one call for multiple resources else one call for each resource
    private static final boolean SUGGESTION_MODE = true; // If true then we make some suggestions based on pure simple logic for On Demand EC2 Instances (Number of VCPUs, Memory Required, and Which Family's instance should we use)
    private static final int DAYS_OF_DATA = 7; // The number of Days of Data which we should fetch from cloudwatch
    private static final int EC2_CPU_THRESHOLD_IN_PERCENT_UNDER = 10; // Threshold CPU Utilization for identifying Under Utilized EC2 Instances. All instances having CPU Utilization below this threshold are marked as under utilized
    private static final int EC2_MEMORY_THRESHOLD_IN_PERCENT_UNDER = 10; // Threshold Memory Utilization for identifying Under Utilized EC2 Instances. All instances having Memory Utilization below this threshold are marked as under utilized
    private static final int EC2_CPU_THRESHOLD_IN_PERCENT_OVER = 90; // Threshold CPU Utilization for identifying Over Utilized EC2 Instances. All instances having CPU Utilization above this threshold are marked as over utilized
    private static final int EC2_MEMORY_THRESHOLD_IN_PERCENT_OVER = 90; // Threshold Memory Utilization for identifying Over Utilized EC2 Instances. All instances having Memory Utilization above this threshold are marked as over utilized
    private static final String EC2_CPU_STATISTIC_UNDER = "maximum"; // Statistic which is to be used while identifying under utilized resources (CPU). Possible values are "average", "minimum" and "maximum"
    private static final String EC2_CPU_STATISTIC_OVER = "maximum"; // Statistic which is to be used while identifying over utilized resources (CPU). Possible values are "average", "minimum" and "maximum"
    private static final String EC2_MEMORY_STATISTIC_UNDER = "maximum"; // Statistic which is to be used while identifying under utilized resources (Memory). Only Possible value is "maximum" in this current release
    private static final String EC2_MEMORY_STATISTIC_OVER = "maximum"; // Statistic which is to be used while identifying Over utilized resources (Memory). Only Possible value is "maximum" in this current release
    private static final double EC2_RESERVED_CAPACITY_THRESHOLD_IN_PERCENT = 50.0; // Threshold to be used for identifying Reserved Instances which are not utilized as per the bought capacity
    private static final int EC2_RESERVED_EXPIRING_IN_NEXT_N_DAYS_THRESHOLD = 14; // Threshold to be used for identifying Reserved Instance which are Expiring in near future

    private static final String LOAD_BALANCER_STATISTIC = "sum"; // sum makes the most sense out of all the metrics. Statistic to be used for identifying underutilized load balancers. Possible values are "maximum", "minimum", "average" and "sum"
    private static final int LOAD_BALANCER_THRESHOLD_COUNT = 15; // this value should be 100 if granularity is 24*7, So scale accordingly. If the values for the statistic in concern are below this threshold then the load balancer is underutilized else over utilized

    private static final int BACKUPS_THRESHOLD_DAYS = 30*4; // The Threshold value for backups, if any backup is there which is lying for more than this threshold number of days
    private static final int S3_OBJECTS_THRESHOLD_DAYS = 30*4; // The Threshold value for S3 Objects/Files, if any S3 Object is there which hasn't been modified in this many days will be listed down in the report

    private static final double EBS_THRESHOLD_READ_OPS_PER_SECOND = 1.0/(24.0*3600.0); // The threshold value for Identifying under utilized EBS Volumes, if the read ops value is below this threshold then it will be considered under utilized
    private static final double EBS_THRESHOLD_WRITE_OPS_PER_SECOND = 1.0/(24.0*3600.0); // The threshold value for Identifying under utilized EBS Volumes, if write ops value is below this threshold then it will be considered under utilized
    /**
     * Below defined variable is the number of hours over which we want aggregated results
     * So, if the value is 5 then that means we want one record for every 5 hours passed
     * So if I am getting data of total 5 days then we have 5*24 hours and for each 5 hours we will
     * get one data point, so we will get a total of (24*5)/5 = 24 data points
     *
     * For every 5 hours interval we will either get sum of all the values in that interval, minimum
     * of all the values in that interval, maximum of all the values in that interval or average of
     * all the values in that interval
     */
    private static final int GRANULARITY_IN_HOURS = 24;
    private static final String REPORT_FILENAME_WITH_PATH = "/Users/kartik-mdcharw/kartik/codes/testFile.xlsx";

    private String KEY_ID; // AWS Credentials Key ID
    private String SECRET_KEY; // AWS Credentials Secret Key
    private AWSStaticCredentialsProvider CREDENTIALS;
    private Region REGION = Region.US_EAST_1; // Which AWS region we are talking about, this is the default value although the code supports multiple regions by default
    private AwsBasicCredentials CREDS;

    private ArrayList<Ec2InstanceData> ec2InstancesData = new ArrayList<>(); // ArrayList storing data of all EC2 Instances
    private ArrayList<Ec2InstanceData> ec2SpotInstancesData = new ArrayList<>(); // ArrayList storing data of all EC2 Spot Instances
    private ArrayList<Ec2InstanceData> ec2OnDemandInstancesData = new ArrayList<>(); // ArrayList Storing data of all EC2 On Demand Instances
    private ArrayList<Ec2InstanceData> ec2ReservedInstancesData = new ArrayList<>(); // ArrayList Soring data of all EC2 Reserved Instances
    private ArrayList<ReservedInstanceData> reservedInstancesData = new ArrayList<>(); // ArrayList Storing information of the purchased Reserved Instances Configuration
    private ArrayList<SpotRequestData> spotRequestsData = new ArrayList<>(); // ArrayList Storing information about the Requests made for Spot Instances

    private ArrayList<ElasticLoadBalancerData> elasticLoadBalancersData = new ArrayList<>(); // ArrayList Storing data of all Load Balancers
    private ArrayList<ElasticLoadBalancerData> applicationLoadBalancersData = new ArrayList<>(); // ArrayList Storing data of Application Load Balancers Only
    private ArrayList<ElasticLoadBalancerData> networkLoadBalancersData = new ArrayList<>(); // ArrayList Storing data of Network Load Balancers Only
    private ArrayList<ElasticLoadBalancerData> gatewayLoadBalancersData = new ArrayList<>(); // ArrayList Storing data of Gateway Load Balancers Only

    private ArrayList<S3BucketData> s3bucketsData = new ArrayList<>(); // ArrayList Storing data of S3 Buckets
    private ArrayList<ElasticIpData> elasticIpsData = new ArrayList<>(); // ArrayList Storing data of Elastic IP Addresses
    private ArrayList<BackupData> backupsData = new ArrayList<>(); // ArrayList Storing data of the backups/snapshots

    private ArrayList<EbsVolumeData> ebsVolumesData = new ArrayList<>(); // Arraylist Storing data of the EBS volumes

    private HashMap<String, Long> ec2InstanceTypeToMemorySizeInMB = new HashMap<String, Long>(); // Hashmap storing the mapping between the instance type and the size of the memory associated with it
    private HashMap<String, Integer> ec2InstanceTypeToVcpuCount = new HashMap<String, Integer>(); // Hashmap storing the mapping between the instance type and the number of vcpus associated with it
    private HashMap<String, Double> ec2InstanceTypeToPrice = new HashMap<String, Double>(); // hashmap storing the mapping between the instance type and the on demand price of it for a specific region

    /*
     * Availability Zone, Tenancy, Instance Type, Product Description*/
    private HashMap<String, ArrayList<Integer>> reservedInstanceMatcher = new HashMap<>();
    /**
     * Default Constructor
     * @param debugStatus If this is true then the code is run in DEBUG mode which will lead to printing of some extra information
     */
    public AWSCostOptimizerAndReportGenerator(boolean debugStatus) {
        /*
         * Reading Credentials from the properties file
         * */
        readProperties();

        AWSCostOptimizerAndReportGenerator.DEBUG = debugStatus;
        CREDENTIALS = new AWSStaticCredentialsProvider(new BasicAWSCredentials(KEY_ID, SECRET_KEY));
        CREDS = AwsBasicCredentials.create(KEY_ID, SECRET_KEY);
    }

    /**
     * Method to read the credentials properties from the project.properties file
     * Here properties are the AWS credentials that is ACCESS KEY ID and SECRET ACCESS KEY
     */
    private void readProperties() {
        Properties property = new Properties();
        try {
            InputStream input = new FileInputStream("project.properties");
            property.load(input);
            KEY_ID = property.getProperty("ACCESS_KEY_ID");
            SECRET_KEY = property.getProperty("SECRET_ACCESS_KEY");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Function to set the Region to be considered for AWS region specific services, for example
     * EC2 Instances have region level visibility that is an instance created at region A will not
     * be visible at region B
     *
     * @param region Region class instance corresponding to the AWS region
     */
    public void setRegion(Region region) {
        REGION = region;
    }

    /**
     * Public Wrapper Function to hide the actual functionality (Abstraction). This method calls another
     * private method to fetch details of the S3 Buckets associated to our AWS account
     *
     * @param daysOfData                  Number of Days for which we want the data, for example last 7 days or last 10 days etc
     * @param hoursOverWhichStatsRequired the number of hours over which you want to take average, sum, minimum and maximum
     */
    public void getS3Info(int daysOfData, int hoursOverWhichStatsRequired) {
        S3BasicInfo(REGION, daysOfData, hoursOverWhichStatsRequired);
    }

    /**
     * Public Wrapper Function to hide the actual functionality (Abstraction). This method calls another
     * private method to fetch details of the EC2 Instances associated to our AWS account and the earlier
     * specified region
     *
     * @param daysOfData                  Number of Days for which we want the data, for example last 7 days or last 10 days etc
     * @param hoursOverWhichStatsRequired the number of hours over which you want to take average, sum, minimum and maximum
     */
    public void getEc2InstancesInfo(int daysOfData, int hoursOverWhichStatsRequired) {
        ec2BasicInfo(REGION, daysOfData, hoursOverWhichStatsRequired);
    }

    /**
     * Public Wrapper Function to hide the actual functionality (Abstraction). This method calls another
     * private method to fetch details of the Load Balancers associated to our AWS account
     *
     * @param daysOfData                  Number of Days for which we want the data, for example last 7 days or last 10 days etc
     * @param hoursOverWhichStatsRequired the number of hours over which you want to take average, sum, minimum and maximum
     */
    public void getLoadBalancerInfo(int daysOfData, int hoursOverWhichStatsRequired) {
        elbBasicInfo(REGION, daysOfData, hoursOverWhichStatsRequired);
    }

    /**
     * Public Wrapper Function to hide the actual functionality (Abstraction). This method calls another
     * private method to fetch details of the Elastic IP Addresses allocated to our AWS account (for the region specified)
     */
    public void getElasticIpInfo() {
        eipBasicInfo(REGION);
    }

    /**
     * Public Wrapper Function to hide the actual functionality (Abstraction). This method calls another
     * private method to fetch details of the backups created by our AWS account
     */
    public void getBackupsInfo() {
        backupsBasicInfo(REGION);
    }

    /**
     * Wrapper method which in turns calls other private methods to retrieve all the data from amazon and for multiple regions and then
     * generate a report based on that data
     */
    public void getDataAndGenerateReport() {
        S3BasicInfo(REGION,DAYS_OF_DATA,GRANULARITY_IN_HOURS);  // exceptions handled 2

        ArrayList<Region> regions = new ArrayList<>();
        regions.add(Region.US_EAST_1);
        regions.add(Region.US_EAST_2);
        regions.add(Region.US_WEST_1);
        regions.add(Region.US_WEST_2);

        regions.add(Region.AP_SOUTHEAST_1);
        regions.add(Region.AP_SOUTH_1);
        regions.add(Region.AP_NORTHEAST_1);
        regions.add(Region.AP_NORTHEAST_2);
        regions.add(Region.AP_SOUTHEAST_2);
        regions.add(Region.AP_SOUTHEAST_2);

        regions.add(Region.CA_CENTRAL_1);

        regions.add(Region.EU_NORTH_1);
        regions.add(Region.EU_CENTRAL_1);
        regions.add(Region.EU_WEST_1);
        regions.add(Region.EU_WEST_2);
        regions.add(Region.EU_WEST_3);

        regions.add(Region.SA_EAST_1);

//        regions.clear();
//        regions.add(Region.US_EAST_1);

        for(Region region : regions) {
            if(DEBUG)
                System.out.println("FETCHING DATA FOR REGION "+region.toString()+": ");
            this.setRegion(region);
            ec2BasicInfo(REGION, DAYS_OF_DATA, GRANULARITY_IN_HOURS); // exceptions handled 5
            elbBasicInfo(REGION, DAYS_OF_DATA, GRANULARITY_IN_HOURS); // exceptions handled 4
            ebsBasicInfo(REGION, DAYS_OF_DATA, GRANULARITY_IN_HOURS); // exceptions handles 2
            eipBasicInfo(REGION); // exceptions handled
            backupsBasicInfo(REGION); // exceptions handled
        }

        if(SUGGESTION_MODE)
            getAllInstanceTypesInfo(Region.US_EAST_1,true);

        if(PRICE_COMPARISON)
            getOneTimeEc2Info(Region.US_EAST_1, false);
        makeExcelReportFile(REPORT_FILENAME_WITH_PATH); // exceptions handled
    }

    /**
     * Method for generating the report as an Excel file. It makes use of Report class
     * to generate the report in the desired format.
     *
     * @param filename Name of the report file along with the path (absolute) details
     */
    public void makeExcelReportFile(String filename) {
        try {
            Report report = new Report(filename, SUGGESTION_MODE, ec2InstanceTypeToVcpuCount, ec2InstanceTypeToMemorySizeInMB, ec2InstanceTypeToPrice);

            report.createSheetAndLoad("EC2 Utilization");
            report.addHeading("UNDER-UTILIZED EC2 INSTANCES / SERVERS", 2);
//            report.addEc2InstanceData(ec2InstancesData, "UNDER-UTILIZED EC2 INSTANCES", EC2_CPU_STATISTIC_UNDER, EC2_CPU_THRESHOLD_IN_PERCENT_UNDER, EC2_MEMORY_STATISTIC_UNDER, EC2_MEMORY_THRESHOLD_IN_PERCENT_UNDER, true, false);
            report.addEc2InstanceData(ec2ReservedInstancesData, "UNDER-UTILIZED RESERVED EC2 INSTANCES", EC2_CPU_STATISTIC_UNDER, EC2_CPU_THRESHOLD_IN_PERCENT_UNDER, EC2_MEMORY_STATISTIC_UNDER, EC2_MEMORY_THRESHOLD_IN_PERCENT_UNDER, true, false);
            report.addEc2InstanceData(ec2SpotInstancesData, "UNDER-UTILIZED SPOT EC2 INSTANCES", EC2_CPU_STATISTIC_UNDER, EC2_CPU_THRESHOLD_IN_PERCENT_UNDER, EC2_MEMORY_STATISTIC_UNDER, EC2_MEMORY_THRESHOLD_IN_PERCENT_UNDER, true, false);
            report.addEc2InstanceData(ec2OnDemandInstancesData, "UNDER-UTILIZED ON DEMAND EC2 INSTANCES", EC2_CPU_STATISTIC_UNDER, EC2_CPU_THRESHOLD_IN_PERCENT_UNDER, EC2_MEMORY_STATISTIC_UNDER, EC2_MEMORY_THRESHOLD_IN_PERCENT_UNDER, true, true);
            report.addRowGaps(2);

            report.addHeading("OVER-UTILIZED EC2 INSTANCES / SERVERS", 2);
//            report.addEc2InstanceData(ec2InstancesData, "OVER-UTILIZED EC2 INSTANCES", EC2_CPU_STATISTIC_OVER, EC2_CPU_THRESHOLD_IN_PERCENT_OVER, EC2_MEMORY_STATISTIC_OVER, EC2_MEMORY_THRESHOLD_IN_PERCENT_OVER, false, false);
            report.addEc2InstanceData(ec2ReservedInstancesData, "OVER-UTILIZED RESERVED EC2 INSTANCES", EC2_CPU_STATISTIC_OVER, EC2_CPU_THRESHOLD_IN_PERCENT_OVER, EC2_MEMORY_STATISTIC_OVER, EC2_MEMORY_THRESHOLD_IN_PERCENT_OVER, false, false);
            report.addEc2InstanceData(ec2SpotInstancesData, "OVER-UTILIZED SPOT EC2 INSTANCES", EC2_CPU_STATISTIC_OVER, EC2_CPU_THRESHOLD_IN_PERCENT_OVER, EC2_MEMORY_STATISTIC_OVER, EC2_MEMORY_THRESHOLD_IN_PERCENT_OVER, false, false);
            report.addEc2InstanceData(ec2OnDemandInstancesData, "OVER-UTILIZED ON DEMAND EC2 INSTANCES", EC2_CPU_STATISTIC_OVER, EC2_CPU_THRESHOLD_IN_PERCENT_OVER, EC2_MEMORY_STATISTIC_OVER, EC2_MEMORY_THRESHOLD_IN_PERCENT_OVER, false, true);
            report.addRowGaps(2);

            report.createSheetAndLoad("Reserved Instances");
            report.addHeading("RESERVED INSTANCES ANALYSIS", 2);
            report.addReservedInstancesAtLowCapacity(reservedInstancesData, "RESERVED INSTANCES RUNNING AT LOW CAPACITY THAN BOUGHT", EC2_RESERVED_CAPACITY_THRESHOLD_IN_PERCENT);
            report.addExpiringReservedInstances(reservedInstancesData, "RESERVED INSTANCES EXPIRING IN NEXT", EC2_RESERVED_EXPIRING_IN_NEXT_N_DAYS_THRESHOLD);
            report.addRowGaps(2);

            if(PRICE_COMPARISON) {
                report.createSheetAndLoad("EC2 Cost Implication");
                report.addHeading("ON DEMAND INSTANCE COST IMPLICATION", 2);
                report.addOnDemandCostImplication(ec2OnDemandInstancesData, "COST IMPLICATIONS");
                report.addRowGaps(2);
            }

            report.createSheetAndLoad("ELB Utilization");
            report.addHeading("LOAD BALANCERS", 3);
//            report.addLoadBalancerData(elasticLoadBalancersData, "UNDERUTILIZED LOAD BALANCERS", LOAD_BALANCER_STATISTIC, LOAD_BALANCER_THRESHOLD_COUNT);
            report.addLoadBalancerData(applicationLoadBalancersData, "UNDERUTILIZED APPLICATION LOAD BALANCERS", LOAD_BALANCER_STATISTIC, LOAD_BALANCER_THRESHOLD_COUNT);
            report.addLoadBalancerData(networkLoadBalancersData, "UNDERUTILIZED NETWORK LOAD BALANCERS", LOAD_BALANCER_STATISTIC, LOAD_BALANCER_THRESHOLD_COUNT);
            report.addLoadBalancerData(gatewayLoadBalancersData, "UNDERUTILIZED GATEWAY LOAD BALANCERS", LOAD_BALANCER_STATISTIC, LOAD_BALANCER_THRESHOLD_COUNT);
            report.addRowGaps(2);

            report.createSheetAndLoad("EBS and Elastic IPs");
            report.addHeading("EBS VOLUMES", 3);
            report.addUnattachedEbsVolumes(ebsVolumesData, "EBS VOLUMES (NOT IN USE), REVERSE SORTED (SIZE)");
            report.addEbsVolumesUtilizationData(ebsVolumesData, "EBS Volumes (IN USE) UTILIZATION DATA", EBS_THRESHOLD_READ_OPS_PER_SECOND, EBS_THRESHOLD_WRITE_OPS_PER_SECOND);
            report.addRowGaps(2);

            report.addHeading("ELASTIC IPs", 3);
            report.addIpData(elasticIpsData, "UNUSED IP ADDRESSES");
            report.addRowGaps(2);

            report.createSheetAndLoad("Backups (Snapshots)");
            report.addHeading("SNAPSHOTS / BACKUPS", 3);
            report.addBackupData(backupsData, "BACKUPS OLDER THAN", BACKUPS_THRESHOLD_DAYS);
            report.addRowGaps(2);

            report.createSheetAndLoad("S3");
            report.addHeading("S3 BUCKETS", 3);
            report.addS3BucketData(s3bucketsData, "FOR EACH BUCKET OBJECTS OLDER THAN", S3_OBJECTS_THRESHOLD_DAYS);
            report.addRowGaps(2);

            report.create();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    /**
     * Abstract method in the AwsCredentialsProvider interface
     */
    @Override
    public AwsCredentials resolveCredentials() {
        return CREDS;
    }

    @Override
    public String toString() {
        return "AWSCostOptimizerAndReportGenerator{" +
                "REGION=" + REGION +
                "\nec2InstancesData=" + ec2InstancesData +
                "\nec2SpotInstancesData=" + ec2SpotInstancesData +
                "\nec2OnDemandInstancesData=" + ec2OnDemandInstancesData +
                "\nec2ReservedInstancesData=" + ec2ReservedInstancesData +
                "\nreservedInstancesData=" + reservedInstancesData +
                "\nspotRequestsData=" + spotRequestsData +
                "\nelasticLoadBalancersData=" + elasticLoadBalancersData +
                "\napplicationLoadBalancersData=" + applicationLoadBalancersData +
                "\nnetworkLoadBalancersData=" + networkLoadBalancersData +
                "\ngatewayLoadBalancersData=" + gatewayLoadBalancersData +
                "\ns3bucketsData=" + s3bucketsData +
                "\nelasticIpsData=" + elasticIpsData +
                "\nbackupsData=" + backupsData +
                "\nebsVolumesData=" + ebsVolumesData +
                '}';
    }

    /**
     * Method to print the metric data from cloudwatch on the console. This method was earlier
     * in use when all the data was made to output on the console instead of storing them as
     * objects in main memory.
     *
     * @param res    MetricDataResponse from cloudwatch
     * @param offset To make the output more readable, give an offset and then this offset will be prepended to everything which is to be printed on the console
     */
    private void printMetricDataResult(GetMetricDataResponse res, String offset) {
        for (MetricDataResult result : res.metricDataResults()) {
            int sz = result.values().size();
            if (sz != 0) {
                List<Double> values = result.values();
                List<Instant> tstamps = result.timestamps();
                System.out.println(offset + "Daily " + result.id() + " of Last " + sz + " Days (Starting from Today):");
                for (int i = 0; i < sz; i++) {
                    Instant curInstant = tstamps.get(i);
                    Date curDate = Date.from(curInstant);
                    System.out.println(offset + "\t TimeStamp: " + curDate + " ,Value: " + values.get(i));
                }
            } else {
                System.out.println(offset + "No information is available on cloudwatch for the requested metric");
            }
            System.out.println();
        }
    }

    /**
     * Helper method only to reduce the redundancy in the code, because the following snippet has to be
     * written for each metric for each different statistic.
     *
     * @param metric     the metric which I want. The object will contain the metric name, namespace and the related dimensions
     * @param timeWindow time over which we want to take average, minimum, maximum or sum
     * @param statistic  statistic we want to calculate (Acceptable values (Although Dependent on the metric we want) are Average, Maximum, Minimum, Sum)
     * @param id         A unique identifier for each different metric which we are going to retrieve
     * @return Returns the Actual Query object generated which is to be sent to cloudwatch to be answered
     */
    private MetricDataQuery queryBuilder(Metric metric, int timeWindow, String statistic, String id) {
        MetricStat metricstat = MetricStat.builder().metric(metric).period(timeWindow).stat(statistic).build();
        return MetricDataQuery.builder().metricStat(metricstat).id(id).build();
    }

    /**
     * Method to retrieve the CpuUtilization Metric for EC2 Instances from cloudwatch
     * For each instance we use the cloudwatch client getMetricData API to get the data
     * for 3 stats (3 queries) which are Average, Minimum and Maximum
     *
     * @param currentEc2Instance The Object corresponding to the current EC2 instance for which we want the cloudwatch data
     * @param days               The number of days of data we want to retrieve the data
     * @param hours              The number of hours over which we want to club the results according to the stats provided
     */
    private void getCpuUtilization(Ec2InstanceData currentEc2Instance, int days, int hours) {
        /*
         * Namespace for EC2 AWS*/
        String namespace = "AWS/EC2";
        CloudWatchClient cw = CloudWatchClient.builder().credentialsProvider(this).region(REGION).build();

        /*
         * We need to pass the instance id of the Instance for which we want the data. So
         * Creating a dimension for that*/
        Dimension dimen = Dimension.builder().name("InstanceId").value(currentEc2Instance.getId()).build();
        ArrayList<Dimension> dimensions = new ArrayList<>();
        dimensions.add(dimen);
        Metric metric = Metric.builder().metricName("CPUUtilization").namespace(namespace).dimensions(dimensions).build();

        ArrayList<MetricDataQuery> queries = new ArrayList<>();
        queries.add(queryBuilder(metric, 3600 * hours, "Average", "average_values"));
        queries.add(queryBuilder(metric, 3600 * hours, "Minimum", "minimum_values"));
        queries.add(queryBuilder(metric, 3600 * hours, "Maximum", "maximum_values"));

        Instant endInstant = Instant.now();
        Instant startInstant = Instant.ofEpochSecond(endInstant.getEpochSecond() - (long) days * 24 * 3600);

        /*
         * Making the API call*/
        GetMetricDataResponse res = cw.getMetricData(GetMetricDataRequest.builder().startTime(startInstant).endTime(endInstant).metricDataQueries(queries).build());
        List<MetricDataResult> queryResults = res.metricDataResults();

        /*
         * Storing the values in the current Ec2 Instance Object*/
        for (MetricDataResult current : queryResults) {
            if (current.id().equals("average_values")) {
                currentEc2Instance.setAverageCpuUtilizationData(StatisticRecord.listGenerator(current.timestamps(), current.values()));
            } else if (current.id().equals("minimum_values")) {
                currentEc2Instance.setMinimumCpuUtilizationData(StatisticRecord.listGenerator(current.timestamps(), current.values()));
            } else {
                currentEc2Instance.setMaximumCpuUtilizationData(StatisticRecord.listGenerator(current.timestamps(), current.values()));
            }
        }
        cw.close();
    }

    /**
     * Helper Method to check if the ec2 Instance in loop is associated to any spot instance request
     *
     * @param instance The current instance in loop
     * @return Return true if it is associated to a spot instance request else return false
     */
    private boolean isSpot(Ec2InstanceData instance) {
        if (spotRequestsData == null)
            return false;
        /*
         * linear search to search in the spot requests arraylist
         * */
        for (SpotRequestData request : spotRequestsData) {
            if (!request.isAssigned() && request.isRelatedToInstance()) {
                try {
                    if (request.getAssociatedInstanceId().equals(instance.getId())) {
                        request.setAssigned(true);
                        return true;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        return false;
    }

    /**
     * Helper Method to check if the ec2 Instance in loop matches the description of a purchased reserved instance
     *
     * @param instance The current instance in loop
     * @return Return true if it matches to a reserved instance else return false
     */
    private boolean isReserved(Ec2InstanceData instance) {
        if (reservedInstancesData == null)
            return false;
        /*
        // Linear search to search from the reserved instances ArrayList
        for (ReservedInstanceData reservedInstance : reservedInstancesData) {
            if (reservedInstance.isActive() && reservedInstance.isRemaining() && reservedInstance.matchInstance(instance)) {
                reservedInstance.foundOne();
                return true;
            }
        }
         */

        /*
         * New Way using Hashmaps, Supposed to be faster than the above one
         * */
        String representativeString = instance.getAvailabilityZone() + instance.getTenancy() + instance.getType() + instance.getPlatformDetails();
        if (reservedInstanceMatcher.containsKey(representativeString)) {
            ArrayList<Integer> indices = reservedInstanceMatcher.get(representativeString);
            for (Integer index : indices) {
                ReservedInstanceData reservedInstance = reservedInstancesData.get(index);
                if (reservedInstance.isActive() && reservedInstance.isRemaining()) {
                    reservedInstance.foundOne();
                    return true;
                }
            }
        }

        representativeString = instance.getRegion() + instance.getTenancy() + instance.getType() + instance.getPlatformDetails();
        if (reservedInstanceMatcher.containsKey(representativeString)) {
            ArrayList<Integer> indices = reservedInstanceMatcher.get(representativeString);
            for (Integer index : indices) {
                ReservedInstanceData reservedInstance = reservedInstancesData.get(index);
                if (reservedInstance.isActive() && reservedInstance.isRemaining()) {
                    reservedInstance.foundOne();
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Helper method to find the instance type and to insert the instance into its
     * respective ArrayList. This method makes use of isSpot and isReserved methods
     * to check if the instance is a spot instance or a reserved instance. If both
     * these methods return false then the instance in question is an On Demand Instance.
     *
     * @param instance The instance in loop of which we want to find the type
     * @param client the EC2 Client Object which will be used to get pricing information if PRICE_COMPARISON is set to true
     * @return Returns an integer value corresponding to its type [On demand(0), Spot(1), Reserved(2)].
     */
    private int getInstanceTypeAndInsert(Ec2InstanceData instance, AmazonEC2 client) {
        /*
         * Returns 0 if on demand instance or state not running
         * Returns 1 is spot instance
         * Returns 2 if reserved instance
         * */
        int type = 0;
        /*
         * Instances in running state are the only ones which are considered. Instances
         * in other states are only placed in the general instances list.
         * */
        if (instance.getState().equals("running")) {
            /* Earlier part, slow O(n)
             which has been now improved to O(1)
            if(isSpot(instance)) {
                type = 1;
                ec2SpotInstancesData.add(instance);
            }
             */
            if (instance.isSpot()) {
                type = 1;
                ec2SpotInstancesData.add(instance);
            } else if (isReserved(instance)) {
                /*
                Earlier this was o(n), isReserved method
                Now it has been improved to constant time by making use of hashmaps
                 */
                type = 2;
                ec2ReservedInstancesData.add(instance);
            } else {
                ec2OnDemandInstancesData.add(instance);
                if(PRICE_COMPARISON && !instance.getAvailabilityZone().equals(""))
                    instance.getAndStoreCostImplication(client);
            }
        }
        return type;
    }

    /**
     * Method to retrieve the Memory Used Percent and Disk Used Percent Metric(Removed) for EC2 Instances from cloudwatch agent
     * For each instance we use the cloudwatch client getMetricData API to get the data
     *
     * @param currentEc2Instance The Object corresponding to the current EC2 instance for which we want the cloudwatch agent data
     * @param days               The number of days of data we want to retrieve the data
     * @param hours              The number of hours over which we want to club the results according to the stats provided
     */
    private void getCWAgentMetrics(Ec2InstanceData currentEc2Instance, int days, int hours) {
        /*
         * Namespace for Cloud Watch Agent AWS*/
        String namespace = "CWAgent";
        CloudWatchClient cw = CloudWatchClient.builder().credentialsProvider(this).region(REGION).build();

        /*
         * We need to pass the instance id of the Instance for which we want the data. So
         * Creating a dimension for that*/
        Dimension dimen = Dimension.builder().name("InstanceId").value(currentEc2Instance.getId()).build();
        ArrayList<Dimension> dimensions = new ArrayList<>();
        dimensions.add(dimen);
        Metric metricMem = Metric.builder().metricName("mem_used_percent").namespace(namespace).dimensions(dimensions).build();
//        Metric metricDisk = Metric.builder().metricName("disk_used_percent").namespace(namespace).dimensions(dimensions).build();

        ArrayList<MetricDataQuery> queries = new ArrayList<>();
        queries.add(queryBuilder(metricMem, 3600 * hours, "Maximum", "mem_used"));
//        queries.add(queryBuilder(metricDisk, 3600 * hours, "Maximum", "disk_used"));

        Instant endInstant = Instant.now();
        Instant startInstant = Instant.ofEpochSecond(endInstant.getEpochSecond() - (long) days * 24 * 3600);

        /*
         * Making the API call*/
        GetMetricDataResponse res = cw.getMetricData(GetMetricDataRequest.builder().startTime(startInstant).endTime(endInstant).metricDataQueries(queries).build());
        List<MetricDataResult> queryResults = res.metricDataResults();

        /*
         * Storing the values in the current Ec2 Instance Object*/

        currentEc2Instance.setMemoryUsedPercentData(StatisticRecord.listGenerator(queryResults.get(0).timestamps(), queryResults.get(0).values()));

        /*
        for (MetricDataResult current : queryResults) {
            if (current.id().equals("mem_used")) {
                currentEc2Instance.setMemoryUsedPercentData(StatisticRecord.listGenerator(current.timestamps(), current.values()));
            } else {
                currentEc2Instance.setDiskUsedPercentData(StatisticRecord.listGenerator(current.timestamps(), current.values()));
            }
        }
         */
        cw.close();
    }

    /**
     * Method to retrieve metrics from cloudwatch, this method will be only called after we have sufficient
     * number of metrics such that we can reduce time spent in API calls for each instance and also to reduce
     * total getMetricData API calls
     *
     * @param queries The array of different metric queries which we have to retrieve from cloudwatch
     * @param days    The number of days of data which we have to retrieve from cloudwatch
     * @param cw      The cloudwatch client object
     */
    private void ec2GetMetrics(ArrayList<MetricDataQuery> queries, int days, CloudWatchClient cw) {
        if(DEBUG){
            System.out.println("Fetching Data of a batch of EC2 Instances from cloudwatch");
        }

        Instant endInstant = Instant.now();
        Instant startInstant = Instant.ofEpochSecond(endInstant.getEpochSecond() - (long) days * 24 * 3600);

        /*
         * Making the API call*/
        GetMetricDataResponse res = cw.getMetricData(GetMetricDataRequest.builder().startTime(startInstant).endTime(endInstant).metricDataQueries(queries).build());
        String prevToken = null;
        while (true) {
            List<MetricDataResult> queryResults = res.metricDataResults();
            boolean allEmpty = true;
            /*
             * Storing the values in the current Ec2 Instance Object*/
            for (MetricDataResult current : queryResults) {
                String currentId = current.id();

                int index = currentId.indexOf("_");
                String metric = currentId.substring(0, index);
                String id = currentId.substring(index + 1);

                index = Integer.parseInt(id);
                Ec2InstanceData currentEc2Instance = ec2InstancesData.get(index);

                if (current.timestamps().size() > 0)
                    allEmpty = false;

                if (metric.equals("mem")) {
                    currentEc2Instance.setMemoryUsedPercentData(StatisticRecord.listGenerator(current.timestamps(), current.values()));
                } else if (metric.equals("average")) {
                    currentEc2Instance.setAverageCpuUtilizationData(StatisticRecord.listGenerator(current.timestamps(), current.values()));
                } else if (metric.equals("minimum")) {
                    currentEc2Instance.setMinimumCpuUtilizationData(StatisticRecord.listGenerator(current.timestamps(), current.values()));
                } else {
                    currentEc2Instance.setMaximumCpuUtilizationData(StatisticRecord.listGenerator(current.timestamps(), current.values()));
                }

                /*
                if (metric.equals("mem")) {
                    currentEc2Instance.setMemoryUsedPercentData(StatisticRecord.listGenerator(current.timestamps(), current.values()));
                } else if (metric.equals("disk")) {
                    currentEc2Instance.setDiskUsedPercentData(StatisticRecord.listGenerator(current.timestamps(), current.values()));
                } else if (metric.equals("average")) {
                    currentEc2Instance.setAverageCpuUtilizationData(StatisticRecord.listGenerator(current.timestamps(), current.values()));
                } else if (metric.equals("minimum")) {
                    currentEc2Instance.setMinimumCpuUtilizationData(StatisticRecord.listGenerator(current.timestamps(), current.values()));
                } else {
                    currentEc2Instance.setMaximumCpuUtilizationData(StatisticRecord.listGenerator(current.timestamps(), current.values()));
                }
                 */
            }

            if (res.nextToken() == null || res.nextToken().equals("") || res.nextToken().equals(prevToken) || allEmpty)
                break;
            prevToken = res.nextToken();
            res = cw.getMetricData(GetMetricDataRequest.builder().startTime(startInstant).endTime(endInstant).metricDataQueries(queries).nextToken(res.nextToken()).build());
        }

        if(DEBUG){
            System.out.println("Data Fetched from cloudwatch...");
        }
    }

    /**
     * Method to add the metric queries of the ec2 instance in the global query list
     *
     * @param currentEc2Instance the instance in context whose metric queries we have to add in the
     *                           global query list
     * @param hours              the number of hours over which we have to aggregate the data. That is for x hours we
     *                           need one data point. Here x is this "hours" param
     * @param globalEc2Queries   the global query list in which we have to insert the queries for the
     *                           ec2 instance in context
     * @param index              the index corresponding to the ec2 instance in context. The index at which the object
     *                           of current ec2 instance occurs in the ec2InstancesData Arraylist
     */
    private void ec2AttachMetrics(Ec2InstanceData currentEc2Instance, int hours, ArrayList<MetricDataQuery> globalEc2Queries, int index) {
        /*
         * We need to pass the instance id of the Instance for which we want the data. So
         * Creating a dimension for that
         * */
        Dimension dimen = Dimension.builder().name("InstanceId").value(currentEc2Instance.getId()).build();
        ArrayList<Dimension> dimensions = new ArrayList<>();
        dimensions.add(dimen);
        Metric metric = Metric.builder().metricName("CPUUtilization").namespace("AWS/EC2").dimensions(dimensions).build();
        Metric metricMem = Metric.builder().metricName("mem_used_percent").namespace("CWAgent").dimensions(dimensions).build();
//        Metric metricDisk = Metric.builder().metricName("disk_used_percent").namespace("CWAgent").dimensions(dimensions).build();

        globalEc2Queries.add(queryBuilder(metric, 3600 * hours, "Average", "average_" + index));
        globalEc2Queries.add(queryBuilder(metric, 3600 * hours, "Minimum", "minimum_" + index));
        globalEc2Queries.add(queryBuilder(metric, 3600 * hours, "Maximum", "maximum_" + index));
        globalEc2Queries.add(queryBuilder(metricMem, 3600 * hours, "Maximum", "mem_" + index));
//        globalEc2Queries.add(queryBuilder(metricDisk, 3600 * hours, "Maximum", "disk_" + index));
    }

    /**
     * Method which creates the EC2 Client to get information about Spot Instance Requests,
     * Reserved Instances and all instances which are logically present in our AWS account.
     * These instances may be in running or stopped state or some other state.
     *
     * @param region The region in context of which we want the ec2 instances
     * @param days   The number of days of data we want to retrieve the data
     * @param hours  The number of hours over which we want to club the results according to the stats provided
     */
    private void ec2BasicInfo(Region region, int days, int hours) {
        if(DEBUG){
            System.out.println("Fetching Data of EC2 instances, Spot requests and Reserved Instances");
        }

        Integer numberInstancesClub = 120; // Can be at max 124
        Integer metricsPerInstance = 4;
        /*
         * Client Generation
         * */
        AmazonEC2 client = AmazonEC2Client.builder().withCredentials(CREDENTIALS).withRegion(region.toString()).build();

        /*
         * Making a request to describe the purchased Reserved Instances by using the ec2 Client
         * */
        DescribeReservedInstancesResult resultReserved = client.describeReservedInstances();
        for (ReservedInstances instance : resultReserved.getReservedInstances()) {
            /*
             * Using builder to build the Reserved Instance Object. And it is necessary to provide
             * all the information which is provided below by chaining different methods.
             * */
            try {
                ReservedInstanceData currentInstance = new ReservedInstanceData.ReservedInstanceDataBuilder(instance.getReservedInstancesId(), instance.getState())
                        .withInstanceTypeAndCount(instance.getInstanceType(), instance.getInstanceCount())
                        .withDurationInfo(instance.getDuration(), instance.getStart(), instance.getEnd())
                        .withScope(instance.getScope())
                        .withProductDescription(instance.getProductDescription())
                        .withAvailabilityZone(instance.getAvailabilityZone())
                        .withTenancy(instance.getInstanceTenancy())
                        .build();
                currentInstance.setRegion(region);
                currentInstance.insertIntoHashmap(reservedInstanceMatcher,reservedInstancesData.size());
                reservedInstancesData.add(currentInstance);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if(DEBUG){
            System.out.println("Reserved Instances Information Collected...");
            System.out.println("Reserved Instances Found: "+reservedInstancesData.size());
            System.out.println();
        }

        /*
         * Making a request to describe the Spot Instance Requests which have been made
         * using our account. To make the request we use the ec2 Client.
         * */
        DescribeSpotInstanceRequestsResult result = client.describeSpotInstanceRequests(new DescribeSpotInstanceRequestsRequest());
        while (true) {
            for (SpotInstanceRequest request : result.getSpotInstanceRequests()) {
                /*
                 * Using the builder to build the Spot Request Object. And it is necessary to provide all the information
                 * which is provided below by chaining different methods
                 * */
                try {
                    SpotRequestData currentRequest = new SpotRequestData.SpotRequestDataBuilder(request.getSpotInstanceRequestId(), request.getCreateTime())
                            .withState(request.getState())
                            .withRequestType(request.getType())
                            .withStatusCode(request.getStatus().getCode())
                            .withInstanceId(request.getInstanceId())
                            .withInstanceType(request.getLaunchSpecification().getInstanceType())
                            .withInstanceAvailabilityZone(request.getLaunchedAvailabilityZone())
                            .withInstanceDescription(request.getProductDescription())
                            .withRequestUpdateTime(request.getStatus().getUpdateTime())
                            .withValidFromUntil(request.getValidFrom(), request.getValidUntil())
                            .build();
                    currentRequest.setRegion(region);
                    spotRequestsData.add(currentRequest);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            if (result.getNextToken() == null || result.getNextToken().equals(""))
                break;
            result = client.describeSpotInstanceRequests(new DescribeSpotInstanceRequestsRequest().withNextToken(result.getNextToken()));
        }

        if(DEBUG){
            System.out.println("Information of Spot Instance Requests Collected...");
            System.out.println("Spot Instance Request Found: "+spotRequestsData.size());
            System.out.println();
        }

        /*
         * Describing Actual instances which exist in our account. Above two calls
         * are just for describing what we have reserved and what we have requested
         * in terms of spot instances but this call is to describe the actual instances
         * which AWS has allocated for our use, and they actually exist physically somewhere
         * in the AWS data centres
         * */
        DescribeInstancesResult res = client.describeInstances(new DescribeInstancesRequest());
        ArrayList<MetricDataQuery> queries = new ArrayList<>();
        CloudWatchClient cw = CloudWatchClient.builder().credentialsProvider(this).region(REGION).build();
        while (true) {
            for (Reservation reservation : res.getReservations()) {
                for (Instance instance : reservation.getInstances()) {
                    Ec2InstanceData currentEc2Instance;

                    String instanceId = instance.getInstanceId();
                    String state = instance.getState().getName();
                    try {
                        if (state.equals("running")) {
                            /*
                             * Since Instance is in running state, we will have ipv4 and ipv6 both type
                             * of ip Addresses associated with it. So while building the object we have
                             * to provide them. Furthermore, since this instance is in running state we
                             * have to provide the details of the platform, Availability zone, tenancy
                             * value and the instance type. All this is necessary because we have to run
                             * search and matching algorithms down the line. Those algorithms will
                             * basically map these instances to their respective spot request or reserved instance.
                             * */
                            currentEc2Instance = new Ec2InstanceData.Ec2InstanceDataBuilder(instanceId, state)
                                    .withIpv4(instance.getPublicIpAddress(), instance.getPublicDnsName())
                                    .withIpv6(instance.getPrivateIpAddress(), instance.getPrivateDnsName())
                                    .withPlatformDetails(instance.getPlatformDetails())
                                    .withAvailabilityZone(instance.getPlacement().getAvailabilityZone())
                                    .withTenancy(instance.getPlacement().getTenancy())
                                    .withInstanceType(instance.getInstanceType())
                                    .withVcpus(instance.getCpuOptions().getCoreCount())
                                    .build();
                            if (!SAVETIME) {
                                getCpuUtilization(currentEc2Instance, days, hours);
                                getCWAgentMetrics(currentEc2Instance, days, hours);
                            } else {
                                ec2AttachMetrics(currentEc2Instance, hours, queries, ec2InstancesData.size());
                            }
                        } else if (state.equals("stopped")) {
                            /*
                             * Since instance is in stopped state, the only necessary things are instanceId, state and
                             * ipv6 address values. All the other parameters are optional.
                             * */
                            currentEc2Instance = new Ec2InstanceData.Ec2InstanceDataBuilder(instanceId, state)
                                    .withIpv6(instance.getPrivateIpAddress(), instance.getPrivateDnsName())
                                    .build();
                        } else {
                            /*
                             * Only state and instance id are the required parameters here*/
                            currentEc2Instance = new Ec2InstanceData.Ec2InstanceDataBuilder(instanceId, state)
                                    .build();
                        }
                        currentEc2Instance.setSpotRequestId(instance.getSpotInstanceRequestId());
                        currentEc2Instance.setRegion(region);
                        getInstanceTypeAndInsert(currentEc2Instance,client);
                        ec2InstancesData.add(currentEc2Instance);

                        if (queries.size() == (numberInstancesClub * metricsPerInstance)) {
                            ec2GetMetrics(queries, days, cw);
                            queries.clear();
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
            if (res.getNextToken() == null || res.getNextToken().equals(""))
                break;
            res = client.describeInstances(new DescribeInstancesRequest().withNextToken(res.getNextToken()));
        }
        if (queries.size() != 0) {
            ec2GetMetrics(queries, days, cw);
            queries.clear();
        }
        cw.close();

        if(DEBUG){
            System.out.println("Instances Information Collected...");
            System.out.println("Instances Found: "+ec2InstancesData.size());
            System.out.println();
        }
    }

    /**
     * This method retrieves the Request Count metric for Application load balancers
     * and Active Flow Count metric for Network and Gateway load balancers. These metrics
     * are retrieved from the cloudwatch by creating a client for the same and then making
     * a get metric data request.
     *
     * @param lbname              This parameter is not exactly the load balancers name. It is to be retrieved from the ARN value of the load balancer.
     * @param days                The number of days of data we want to retrieve the data
     * @param hours               The number of hours over which we want to club the results according to the stats provided
     * @param currentLoadBalancer the object corresponding to the load balancer in loop
     */
    private void getMetricsOfLoadBalancer(String lbname, int days, int hours, ElasticLoadBalancerData currentLoadBalancer) {
        /*
         * The three below-mentioned variables depend on the load balancer type
         * These variables are given their respective values by using if else statements
         * */
        String namespace;
        String metricName;
        ArrayList<ElasticLoadBalancerData> currentOne;

        /*
         * Assigning their respective values
         * */
        if (currentLoadBalancer.getType().equals("application")) {
            namespace = "AWS/ApplicationELB";
            metricName = "RequestCount";
            currentOne = applicationLoadBalancersData;
        } else if (currentLoadBalancer.getType().equals("network")) {
            namespace = "AWS/NetworkELB";
            metricName = "ActiveFlowCount";
            currentOne = networkLoadBalancersData;
        } else {
            namespace = "AWS/GatewayELB";
            metricName = "ActiveFlowCount";
            currentOne = gatewayLoadBalancersData;
        }
        CloudWatchClient cw = CloudWatchClient.builder().credentialsProvider(this).region(REGION).build();

        Dimension dimen = Dimension.builder().name("LoadBalancer").value(lbname).build();
        ArrayList<Dimension> dimensions = new ArrayList<>();
        dimensions.add(dimen);

        Metric metric = Metric.builder().metricName(metricName).namespace(namespace).dimensions(dimensions).build();
        ArrayList<MetricDataQuery> queries = new ArrayList<>();
        queries.add(queryBuilder(metric, 3600 * hours, "Sum", "sum_values"));
        queries.add(queryBuilder(metric, 3600 * hours, "Average", "average_values"));
        queries.add(queryBuilder(metric, 3600 * hours, "Minimum", "minimum_values"));
        queries.add(queryBuilder(metric, 3600 * hours, "Maximum", "maximum_values"));

        Instant endInstant = Instant.now();
        Instant startInstant = Instant.ofEpochSecond(endInstant.getEpochSecond() - (long) days * 24 * 3600);
        GetMetricDataResponse res = cw.getMetricData(GetMetricDataRequest.builder().startTime(startInstant).endTime(endInstant).metricDataQueries(queries).build());

        List<MetricDataResult> queryResults = res.metricDataResults();

        /*Here the index given as a parameter to get method is corresponding to the above order of adding into queries
         *
         * The setter methods of the ElasticLoadBalancerData class are named for general metric, but here metric
         * will refer to the RequestCount if the load balancer type is application else it will be ActiveFlowCount
         * metric.
         * */
        for (MetricDataResult current : queryResults) {
            switch (current.id()) {
                case "sum_values":
                    currentLoadBalancer.setSumMetricData(StatisticRecord.listGenerator(current.timestamps(), current.values()));
                    break;
                case "average_values":
                    currentLoadBalancer.setAverageMetricData(StatisticRecord.listGenerator(current.timestamps(), current.values()));
                    break;
                case "minimum_values":
                    currentLoadBalancer.setMinimumMetricData(StatisticRecord.listGenerator(current.timestamps(), current.values()));
                    break;
                default:
                    currentLoadBalancer.setMaximumMetricData(StatisticRecord.listGenerator(current.timestamps(), current.values()));
                    break;
            }
        }
        currentOne.add(currentLoadBalancer);

        cw.close();
    }

    /**
     * Method to actually retrieve values from cloudwatch for the queries added in the global
     * load balancer query array
     *
     * @param queries the global query array consisting of the queries to be fetched from the cloudwatch
     * @param days    the number of days of data which we want to get from cloudwatch
     * @param cw      the cloudwatch client object
     */
    private void lbGetMetrics(ArrayList<MetricDataQuery> queries, int days, CloudWatchClient cw) {
        if(DEBUG){
            System.out.println("Fetching Data of a batch of Load Balancers from cloudwatch");
        }

        Instant endInstant = Instant.now();
        Instant startInstant = Instant.ofEpochSecond(endInstant.getEpochSecond() - (long) days * 24 * 3600);

        /*
         * Making the API call*/
        GetMetricDataResponse res = cw.getMetricData(GetMetricDataRequest.builder().startTime(startInstant).endTime(endInstant).metricDataQueries(queries).build());
        String prevToken = null;
        while (true) {
            List<MetricDataResult> queryResults = res.metricDataResults();
            boolean allEmpty = true;
            /*
             * Storing the values in the current load balancer Object*/

            for (MetricDataResult current : queryResults) {
                String currentId = current.id();

                int index = currentId.indexOf("_");
                String metric = currentId.substring(0, index);
                String id = currentId.substring(index + 1);

                index = Integer.parseInt(id);
                ElasticLoadBalancerData currentLoadBalancer = elasticLoadBalancersData.get(index);

                if (current.timestamps().size() > 0)
                    allEmpty = false;

                switch (metric) {
                    case "sum":
                        currentLoadBalancer.setSumMetricData(StatisticRecord.listGenerator(current.timestamps(), current.values()));
                        break;
                    case "average":
                        currentLoadBalancer.setAverageMetricData(StatisticRecord.listGenerator(current.timestamps(), current.values()));
                        break;
                    case "minimum":
                        currentLoadBalancer.setMinimumMetricData(StatisticRecord.listGenerator(current.timestamps(), current.values()));
                        break;
                    default:
                        currentLoadBalancer.setMaximumMetricData(StatisticRecord.listGenerator(current.timestamps(), current.values()));
                        break;
                }
            }

            if (res.nextToken() == null || res.nextToken().equals("") || res.nextToken().equals(prevToken) || allEmpty)
                break;
            prevToken = res.nextToken();
            res = cw.getMetricData(GetMetricDataRequest.builder().startTime(startInstant).endTime(endInstant).metricDataQueries(queries).nextToken(res.nextToken()).build());
        }

        if(DEBUG){
            System.out.println("Data Fetched from load balancers...");
        }
    }

    /**
     * Method to attach/append the queries to the global query array list so that we can group them for
     * several load balancers and fetch them together. This will help in reducing calls made to
     * getMetricData data thereby saving time and money
     *
     * @param lbname              the name of the load balancer which is to be given as a dimension for queries
     * @param currentLoadBalancer the load balancer object corresponding to the load balancer in context
     * @param hours               the hours over which we want to aggregate data
     * @param globalLbQueries     the global query list for the load balancers
     * @param index               the index at which the current load balancer object occurs in the elasticLoadBalancersData array
     */
    private void lbAttachMetrics(String lbname, ElasticLoadBalancerData currentLoadBalancer, int hours, ArrayList<MetricDataQuery> globalLbQueries, int index) {
        /*
         * The three below-mentioned variables depend on the load balancer type
         * These variables are given their respective values by using if else statements
         * */
        String namespace;
        String metricName;
        ArrayList<ElasticLoadBalancerData> currentOne;

        /*
         * Assigning their respective values
         * */
        if (currentLoadBalancer.getType().equals("application")) {
            namespace = "AWS/ApplicationELB";
            metricName = "RequestCount";
            currentOne = applicationLoadBalancersData;
        } else if (currentLoadBalancer.getType().equals("network")) {
            namespace = "AWS/NetworkELB";
            metricName = "ActiveFlowCount";
            currentOne = networkLoadBalancersData;
        } else {
            namespace = "AWS/GatewayELB";
            metricName = "ActiveFlowCount";
            currentOne = gatewayLoadBalancersData;
        }

        Dimension dimen = Dimension.builder().name("LoadBalancer").value(lbname).build();
        ArrayList<Dimension> dimensions = new ArrayList<>();
        dimensions.add(dimen);
        Metric metric = Metric.builder().metricName(metricName).namespace(namespace).dimensions(dimensions).build();

        globalLbQueries.add(queryBuilder(metric, 3600 * hours, "Sum", "sum_" + index));
        globalLbQueries.add(queryBuilder(metric, 3600 * hours, "Average", "average_" + index));
        globalLbQueries.add(queryBuilder(metric, 3600 * hours, "Minimum", "minimum_" + index));
        globalLbQueries.add(queryBuilder(metric, 3600 * hours, "Maximum", "maximum_" + index));
        currentOne.add(currentLoadBalancer);
    }

    /**
     * Method which creates the Elastic Load Balancer Client to get information about
     * load balancers created within our account.
     *
     * @param region The region in context of which we want the ec2 instances
     * @param days   The number of days of data we want to retrieve the data
     * @param hours  The number of hours over which we want to club the results according to the stats provided
     */
    private void elbBasicInfo(Region region, int days, int hours) {
        if(DEBUG){
            System.out.println("Fetching data of Load Balancers");
        }

        Integer numberLbClub = 120; // max can be 124
        Integer metricsPerLb = 4;

        AmazonElasticLoadBalancing elbc = AmazonElasticLoadBalancingClient.builder().withCredentials(CREDENTIALS).withRegion(region.toString()).build();
        /*
         * Making the request to describe the load balancers
         * */
        DescribeLoadBalancersResult result = elbc.describeLoadBalancers(new DescribeLoadBalancersRequest());
        ArrayList<MetricDataQuery> queries = new ArrayList<>();
        CloudWatchClient cw = CloudWatchClient.builder().credentialsProvider(this).region(REGION).build();

        while (true) {
            for (LoadBalancer lb : result.getLoadBalancers()) {
                /*
                 * Using builder methods to create the load balancer object. And it is necessary to provide all the information
                 * which is provided below by chaining methods.
                 * */
                try {
                    ElasticLoadBalancerData currentLoadBalancer = new ElasticLoadBalancerData.ElasticLoadBalancerDataBuilder(lb.getLoadBalancerName(), lb.getType())
                            .withArn(lb.getLoadBalancerArn())
                            .withState(lb.getState().getCode())
                            .withDnsName(lb.getDNSName())
                            .withIpAddressType(lb.getIpAddressType())
                            .build();

                    String arn = lb.getLoadBalancerArn();
                    String lbName = arn.substring(arn.indexOf("loadbalancer") + 13);

                    if (!SAVETIME) {
                        getMetricsOfLoadBalancer(lbName, days, hours, currentLoadBalancer);
                    } else {
                        lbAttachMetrics(lbName, currentLoadBalancer, hours, queries, elasticLoadBalancersData.size());
                    }
                    currentLoadBalancer.setRegion(region);
                    elasticLoadBalancersData.add(currentLoadBalancer);

                    if (queries.size() == (numberLbClub * metricsPerLb)) {
                        lbGetMetrics(queries, days, cw);
                        queries.clear();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            if (result.getNextMarker() == null || result.getNextMarker().equals(""))
                break;
            result = elbc.describeLoadBalancers(new DescribeLoadBalancersRequest().withMarker(result.getNextMarker()));
        }
        if (queries.size() != 0) {
            lbGetMetrics(queries, days, cw);
            queries.clear();
        }
        cw.close();

        if(DEBUG){
            System.out.println("Data of load balancers fetched");
            System.out.println("Load Balancers found: "+elasticLoadBalancersData.size());
            System.out.println();
        }
    }

    /**
     * Method to retrieve the cloudwatch metrics related to the S3 Bucket in loop. We first create a cloudwatch client
     * and then use it to make get metric data requests by specifying appropriate dimension.
     *
     * @param currentBucket The bucket object in loop
     * @param days          The number of days of data we want to retrieve the data
     * @param hours         The number of hours over which we want to club the results according to the stats provided
     * @param curRegion     Region Object of the location in which the Bucket in loop is located
     */
    private void getCloudWatchMetricsS3(S3BucketData currentBucket, int days, int hours, Region curRegion) {
        /*
         * Metric metric = Metric.builder().metricName("AllRequests").namespace(namespace).dimensions(dimensions).build();
         * Tried above but can not check if it works or not because it is a paid metric, and we have to configure some things
         * before which we can use it.
         * */

        String namespace = "AWS/S3";
        CloudWatchClient cw = CloudWatchClient.builder().credentialsProvider(this).region(curRegion).build();
        /*
         * NumberOfObjects
         * For Number of Objects, provide storage type as AllStorageTypes
         * */
        Dimension dimenBucketName = Dimension.builder().name("BucketName").value(currentBucket.getName()).build();
        Dimension dimenStorageTypeForNOB = Dimension.builder().name("StorageType").value("AllStorageTypes").build();
        ArrayList<Dimension> dimensionsForNOB = new ArrayList<>();
        dimensionsForNOB.add(dimenBucketName);
        dimensionsForNOB.add(dimenStorageTypeForNOB);
        Metric metricForNOB = Metric.builder().metricName("NumberOfObjects").namespace(namespace).dimensions(dimensionsForNOB).build();
        /*
         * BucketSizeBytes
         * For Bucket Size Bytes, we have to use both bucket name and bucket storage type
         * */
        Dimension dimenStorageTypeForBSB = Dimension.builder().name("StorageType").value("StandardStorage").build();
        ArrayList<Dimension> dimensionsForBSB = new ArrayList<>();
        dimensionsForBSB.add(dimenBucketName);
        dimensionsForBSB.add(dimenStorageTypeForBSB);
        Metric metricForBSB = Metric.builder().metricName("BucketSizeBytes").namespace(namespace).dimensions(dimensionsForBSB).build();

        ArrayList<MetricDataQuery> queries = new ArrayList<>();
        queries.add(queryBuilder(metricForNOB, 3600 * hours, "Average", "average_values_NOB"));
        queries.add(queryBuilder(metricForBSB, 3600 * hours, "Average", "average_values_BSB"));

        Instant endInstant = Instant.now();
        Instant startInstant = Instant.ofEpochSecond(endInstant.getEpochSecond() - (long) days * 24 * 3600);
        GetMetricDataResponse res = cw.getMetricData(GetMetricDataRequest.builder().startTime(startInstant).endTime(endInstant).metricDataQueries(queries).build());

        List<MetricDataResult> queryResults = res.metricDataResults();

        for (MetricDataResult current : queryResults) {
            if (current.id().equals("average_values_NOB")) {
                currentBucket.setAverageNumberOfObjects(StatisticRecord.listGenerator(current.timestamps(), current.values()));
            } else {
                currentBucket.setAverageBucketSizeBytes(StatisticRecord.listGenerator(current.timestamps(), current.values()));
            }
        }
        cw.close();
    }

    /**
     * Method to retrieve metrics from cloudwatch, this method will be only called after we have sufficient
     * number of metrics such that we can reduce time spent in API calls for each s3 bucket and also to reduce
     * total getMetricData API calls
     *
     * @param queries The array of different metric queries which we have to retrieve from cloudwatch
     * @param days    The number of days of data which we have to retrieve from cloudwatch
     * @param cw      The cloudwatch client object
     */
    private void s3GetMetrics(ArrayList<MetricDataQuery> queries, int days, CloudWatchClient cw) {
        if(DEBUG){
            System.out.println("Fetching data of a batch of S3 buckets from cloudwatch");
        }

        Instant endInstant = Instant.now();
        Instant startInstant = Instant.ofEpochSecond(endInstant.getEpochSecond() - (long) days * 24 * 3600);

        /*
         * Making the API call*/
        GetMetricDataResponse res = cw.getMetricData(GetMetricDataRequest.builder().startTime(startInstant).endTime(endInstant).metricDataQueries(queries).build());
        String prevToken = null;

        while (true) {
            List<MetricDataResult> queryResults = res.metricDataResults();
            boolean allEmpty = true;

            for (MetricDataResult current : queryResults) {
                String currentId = current.id();

                int index = currentId.indexOf("_");
                String metric = currentId.substring(0, index);
                String id = currentId.substring(index + 1);

                index = Integer.parseInt(id);
                S3BucketData currentBucket = s3bucketsData.get(index);
                if (current.timestamps().size() > 0)
                    allEmpty = false;

                switch (metric) {
                    case "nob":
                        currentBucket.setAverageNumberOfObjects(StatisticRecord.listGenerator(current.timestamps(), current.values()));
                        break;
                    default:
                        currentBucket.setAverageBucketSizeBytes(StatisticRecord.listGenerator(current.timestamps(), current.values()));
                        break;
                }
            }

            if (res.nextToken() == null || res.nextToken().equals("") || res.nextToken().equals(prevToken) || allEmpty)
                break;
            prevToken = res.nextToken();
            res = cw.getMetricData(GetMetricDataRequest.builder().startTime(startInstant).endTime(endInstant).metricDataQueries(queries).nextToken(res.nextToken()).build());
        }

        if(DEBUG){
            System.out.println("Data Fetched from cloudwatch...");
        }
    }

    /**
     * Method to add the metric queries of the s3 bucket in the global query list
     *
     * @param currentBucket   the bucket in context whose metric queries we have to add in the
     *                        global query list
     * @param globalS3Queries the global query list in which we have to insert the queries for the
     *                        S3 bucket in context
     * @param hours           the number of hours over which we have to aggregate the data. That is for x hours we
     *                        need one data point. Here x is this "hours" param
     * @param index           the index corresponding to the s3 in context. The index at which the object
     *                        of current s3 bucket occurs in the s3BucketsData Arraylist
     */
    private void s3AttachMetrics(S3BucketData currentBucket, ArrayList<MetricDataQuery> globalS3Queries, int hours, int index) {
        /*
         * NumberOfObjects
         * For Number of Objects, provide storage type as AllStorageTypes
         * */
        Dimension dimenBucketName = Dimension.builder().name("BucketName").value(currentBucket.getName()).build();
        Dimension dimenStorageTypeForNOB = Dimension.builder().name("StorageType").value("AllStorageTypes").build();
        ArrayList<Dimension> dimensionsForNOB = new ArrayList<>();
        dimensionsForNOB.add(dimenBucketName);
        dimensionsForNOB.add(dimenStorageTypeForNOB);
        Metric metricForNOB = Metric.builder().metricName("NumberOfObjects").namespace("AWS/S3").dimensions(dimensionsForNOB).build();
        /*
         * BucketSizeBytes
         * For Bucket Size Bytes, we have to use both bucket name and bucket storage type
         * */
        Dimension dimenStorageTypeForBSB = Dimension.builder().name("StorageType").value("StandardStorage").build();
        ArrayList<Dimension> dimensionsForBSB = new ArrayList<>();
        dimensionsForBSB.add(dimenBucketName);
        dimensionsForBSB.add(dimenStorageTypeForBSB);
        Metric metricForBSB = Metric.builder().metricName("BucketSizeBytes").namespace("AWS/S3").dimensions(dimensionsForBSB).build();

        globalS3Queries.add(queryBuilder(metricForNOB, 3600 * hours, "Average", "nob_" + index));
        globalS3Queries.add(queryBuilder(metricForBSB, 3600 * hours, "Average", "bsb_" + index));

    }

    /**
     * Method to create an elastic search client
     * @return the client created
     */
    private ElasticsearchClient createElasticSearchClient() {
        RestClient httpClient = RestClient.builder(
                new HttpHost("localhost", 9200)
        ).build();

        ElasticsearchTransport transport = new RestClientTransport(
                httpClient,
                new JacksonJsonpMapper()
        );

        ElasticsearchClient esClient = new ElasticsearchClient(transport);
        return esClient;
    }

    /**
     * Method to create a new index
     * @param esClient the elastic search client
     * @param indexName the name of the new index to be created
     * @throws IOException
     */
    public void createElasticSearchIndex(ElasticsearchClient esClient, String indexName) throws IOException {
        String finalIndexName = indexName;
        String alias_name = indexName + "-ALIAS";
        CreateIndexResponse createResponse = esClient.indices()
                .create(createIndexBuilder -> createIndexBuilder
                        .index(finalIndexName)
                        .aliases(alias_name, aliasBuilder -> aliasBuilder
                                .isWriteIndex(true)
                        )
                );
    }

    /**
     * Method to retrieve the basic information of the S3 buckets present in our AWS
     * account. There is no region dependency for S3 buckets that is information of
     * all the buckets in all the regions will be retrieved. We create a S3 client object
     * to achieve our functionality.
     *
     * @param region The region in context of which we want data, although any value can be passed here (just acts as a placeholder)
     * @param days   The number of days of data we want to retrieve the data
     * @param hours  The number of hours over which we want to club the results according to the stats provided
     */
    private void S3BasicInfo(Region region, int days, int hours) {
        if(DEBUG){
            System.out.println("Fetching data of buckets");
        }

        boolean createIndex = true;
        String indexName = "index-july-final-ppt";
        ElasticsearchClient esClient = createElasticSearchClient();

        if(ADD_S3_DATA_TO_ELASTIC_SEARCH && createIndex) {
            try {
                createElasticSearchIndex(esClient, indexName);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        /*
        int numberBucketsClub = 240; // Max can be 249
        int metricsPerBucket = 2;
         */

        /*
         * This API gives all buckets in the AWS account, no REGION dependency
         * */
        AmazonS3 s3c = AmazonS3Client.builder().withCredentials(CREDENTIALS).withRegion(region.toString()).enableForceGlobalBucketAccess().build();

        /*
        ArrayList<MetricDataQuery> queries;
        HashMap<Region, CloudWatchClient> mapperRegionClient = new HashMap<>();
        HashMap<Region, ArrayList<MetricDataQuery>> mapperRegionQueries = new HashMap<>();
         */

        for (Bucket bucket : s3c.listBuckets()) {
            /*
             * We need location information of each bucket to get the cloudwatch metrics for that bucket.
             * */

            /*
            GetBucketLocationRequest rqst = new GetBucketLocationRequest(bucket.getName());
            String location = s3c.getBucketLocation(rqst);
             */

            /*
             * HardCode for "US" output
             * On studying the Output I found that each of the other regions were coming out to be correct but fot us-east-1 it gives US as output.
             * I tried to go into the reasons of it but could not find one.
             * */

            /*
            if (location.equals("US")) {
                location = "us-east-1";
            }
             */

            ObjectListing objects = s3c.listObjects(bucket.getName());
            /*
             * Using builder to build the object. All the information provided below
             * while creating the object is necessary
             * */
            try {
                S3BucketData currentBucket = new S3BucketData
                        .S3BucketDataBuilder(bucket.getName())
                        .withOwner(bucket.getOwner().getDisplayName(), bucket.getOwner().getId())
//                        .withLocation(location)
                        .withNumberOfObjects(objects.getObjectSummaries().size())
                        .build();

                /*
                Region curRegion = Region.of(location);
                if (!mapperRegionClient.containsKey(curRegion)) {
                    CloudWatchClient cloudWatchClient = CloudWatchClient.builder().credentialsProvider(this).region(curRegion).build();
                    mapperRegionClient.put(curRegion, cloudWatchClient);
                    mapperRegionQueries.put(curRegion, new ArrayList<>());
                }
                queries = mapperRegionQueries.get(curRegion);
                CloudWatchClient cw = mapperRegionClient.get(curRegion);
                if (!SAVETIME) {
                    getCloudWatchMetricsS3(currentBucket, days, hours, curRegion);
                } else {
                    s3AttachMetrics(currentBucket, queries, hours, s3bucketsData.size());
                }
                 */

                /*
                 * Files stored in S3 are referred to as Objects
                 * */
                if (objects.getObjectSummaries().size() != 0) {
                    /*
                     * Storing metadata of the objects/files stored in S3 bucket in form of
                     * a collection in the associated bucket
                     * */
                    Instant maxInstant = null;
                    while (true) {
                        for (S3ObjectSummary summary : objects.getObjectSummaries()) {
                            currentBucket.addObject(summary.getKey(), summary.getStorageClass(), summary.getLastModified(), summary.getSize());
                            Instant curInstant = summary.getLastModified().toInstant();
                            if (maxInstant == null) {
                                maxInstant = curInstant;
                            } else {
                                if (curInstant.isAfter(maxInstant)) {
                                    maxInstant = curInstant;
                                }
                            }
                        }
                        if (objects.isTruncated()) {
                            objects = s3c.listNextBatchOfObjects(objects);
                            currentBucket.addInNumberOfObjects(objects.getObjectSummaries().size());
                        } else {
                            break;
                        }
                    }
                    currentBucket.setLastModifiedDate(maxInstant);
                }
                if(ADD_S3_DATA_TO_ELASTIC_SEARCH) {
                    IndexResponse response;
                    try {
                        response = currentBucket.pushToElasticSearch(esClient, indexName);
                        if (DEBUG) {
                            System.out.println("Index Insert Response" + response);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                s3bucketsData.add(currentBucket);

                /*
                if (queries.size() == (numberBucketsClub * metricsPerBucket)) {
                    s3GetMetrics(queries, days, cw);
                    queries.clear();
                }
                 */
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        /*
        for (Map.Entry<Region, ArrayList<MetricDataQuery>> entry : mapperRegionQueries.entrySet()) {
            queries = entry.getValue();
            if (queries.size() != 0) {
                s3GetMetrics(queries, days, mapperRegionClient.get(entry.getKey()));
                queries.clear();
            }
        }
         */

        if(DEBUG) {
            System.out.println("Data S3 Buckets fetched...");
            System.out.println("Buckets Found: "+s3bucketsData.size());
            System.out.println();
        }
    }

    /**
     * Method to retrieve the Metrics for EBS volumes from cloudwatch
     * For each ebs volume we use the cloudwatch client's getMetricData API to get the data
     *
     * @param currentVolume The Object corresponding to the current EBS volume for which we want the cloudwatch data
     * @param days          The number of days of data we want to retrieve the data
     * @param hours         The number of hours over which we want to club the results according to the stats provided
     */
    private void getCloudWatchDataEbsVolumes(EbsVolumeData currentVolume, int days, int hours) {
        /*
         * Namespace for EBS AWS*/
        String namespace = "AWS/EBS";
        CloudWatchClient cw = CloudWatchClient.builder().credentialsProvider(this).region(REGION).build();

        /*
         * We need to pass the volume id of the volume for which we want the data. So
         * Creating a dimension for that*/
        Dimension dimen = Dimension.builder().name("VolumeId").value(currentVolume.getVolumeId()).build();
        ArrayList<Dimension> dimensions = new ArrayList<>();
        dimensions.add(dimen);
        Metric metricRead = Metric.builder().metricName("VolumeReadOps").namespace(namespace).dimensions(dimensions).build();
        Metric metricWrite = Metric.builder().metricName("VolumeWriteOps").namespace(namespace).dimensions(dimensions).build();

        ArrayList<MetricDataQuery> queries = new ArrayList<>();
        queries.add(queryBuilder(metricRead, 3600 * hours, "Sum", "read_values"));
        queries.add(queryBuilder(metricWrite, 3600 * hours, "Sum", "write_values"));

        Instant endInstant = Instant.now();
        Instant startInstant = Instant.ofEpochSecond(endInstant.getEpochSecond() - (long) days * 24 * 3600);

        /*
         * Making the API call*/
        GetMetricDataResponse res = cw.getMetricData(GetMetricDataRequest.builder().startTime(startInstant).endTime(endInstant).metricDataQueries(queries).build());
        List<MetricDataResult> queryResults = res.metricDataResults();

        /*
         * Storing the values in the current EBS Volume Object*/
        for (MetricDataResult current : queryResults) {
            if (current.id().equals("read_values")) {
                currentVolume.setSumReadOps(StatisticRecord.listGenerator(current.timestamps(), current.values()));
            } else {
                currentVolume.setSumWriteOps(StatisticRecord.listGenerator(current.timestamps(), current.values()));
            }
        }
        currentVolume.setStatHours(hours);
        cw.close();
    }

    /**
     * Method to retrieve metrics from cloudwatch, this method will be only called after we have sufficient
     * number of metrics such that we can reduce time spent in API calls for each volume and also to reduce
     * total getMetricData API calls
     *
     * @param queries The array of different metric queries which we have to retrieve from cloudwatch
     * @param days    The number of days of data which we have to retrieve from cloudwatch
     * @param cw      The cloudwatch client object
     */
    private void ebsGetMetrics(ArrayList<MetricDataQuery> queries, int days, CloudWatchClient cw) {
        if(DEBUG){
            System.out.println("Fetching data of a batch of EBS Volumes from cloudwatch");
        }

        Instant endInstant = Instant.now();
        Instant startInstant = Instant.ofEpochSecond(endInstant.getEpochSecond() - (long) days * 24 * 3600);

        /*
         * Making the API call*/
        GetMetricDataResponse res = cw.getMetricData(GetMetricDataRequest.builder().startTime(startInstant).endTime(endInstant).metricDataQueries(queries).build());
        String prevToken = null;

        while (true) {
            List<MetricDataResult> queryResults = res.metricDataResults();
            boolean allEmpty = true;
            for (MetricDataResult current : queryResults) {
                String currentId = current.id();

                int index = currentId.indexOf("_");
                String metric = currentId.substring(0, index);
                String id = currentId.substring(index + 1);

                index = Integer.parseInt(id);
                EbsVolumeData currentVolume = ebsVolumesData.get(index);

                if (current.timestamps().size() > 0)
                    allEmpty = false;

                switch (metric) {
                    case "read":
                        currentVolume.setSumReadOps(StatisticRecord.listGenerator(current.timestamps(), current.values()));
                        break;
                    default:
                        currentVolume.setSumWriteOps(StatisticRecord.listGenerator(current.timestamps(), current.values()));
                        break;
                }
            }

            if (res.nextToken() == null || res.nextToken().equals("") || res.nextToken().equals(prevToken) || allEmpty)
                break;
            prevToken = res.nextToken();
            res = cw.getMetricData(GetMetricDataRequest.builder().startTime(startInstant).endTime(endInstant).metricDataQueries(queries).nextToken(res.nextToken()).build());
        }

        if(DEBUG){
            System.out.println("Data Fetched from cloudwatch");
        }
    }

    /**
     * Method to add the metric queries of the ebs volume in the global query list
     *
     * @param currentVolume    the volume in context whose metric queries we have to add in the
     *                         global query list
     * @param globalEbsQueries the global query list in which we have to insert the queries for the
     *                         ebs volume in context
     * @param hours            the number of hours over which we have to aggregate the data. That is for x hours we
     *                         need one data point. Here x is this "hours" param
     * @param index            the index corresponding to the ebs volume in context. The index at which the object
     *                         of current ebs volume occurs in the ebsVolumesData Arraylist
     */
    private void ebsAttachMetrics(EbsVolumeData currentVolume, ArrayList<MetricDataQuery> globalEbsQueries, int hours, int index) {
        String namespace = "AWS/EBS";

        /*
         * We need to pass the volume id of the volume for which we want the data. So
         * Creating a dimension for that
         * */
        Dimension dimen = Dimension.builder().name("VolumeId").value(currentVolume.getVolumeId()).build();
        ArrayList<Dimension> dimensions = new ArrayList<>();
        dimensions.add(dimen);
        Metric metricRead = Metric.builder().metricName("VolumeReadOps").namespace(namespace).dimensions(dimensions).build();
        Metric metricWrite = Metric.builder().metricName("VolumeWriteOps").namespace(namespace).dimensions(dimensions).build();

        ArrayList<MetricDataQuery> queries = new ArrayList<>();
        globalEbsQueries.add(queryBuilder(metricRead, 3600 * hours, "Sum", "read_" + index));
        globalEbsQueries.add(queryBuilder(metricWrite, 3600 * hours, "Sum", "write_" + index));
        currentVolume.setStatHours(hours);
    }

    /**
     * Method to retrieve the basic information of EBS volumes from AWS along with fetching read write information
     * from cloudwatch for each volume.
     *
     * @param region The AWS Region of which we want the data
     * @param days   The number of days of which we want the data
     * @param hours  The number of hours over which we want to club the results according to the stats provided
     */
    private void ebsBasicInfo(Region region, int days, int hours) {
        if(DEBUG){
            System.out.println("Data of EBS Volumes is to be fetched now");
        }

        int numberClubVolumes = 240; // max value can be 249
        int metricsPerVolume = 2;
        AmazonEC2 client = AmazonEC2Client.builder().withCredentials(CREDENTIALS).withRegion(region.toString()).build();
        DescribeVolumesResult result = client.describeVolumes(new DescribeVolumesRequest().withMaxResults(500));
        ArrayList<MetricDataQuery> queries = new ArrayList<>();
        CloudWatchClient cw = CloudWatchClient.builder().credentialsProvider(this).region(REGION).build();
        while (true) {
            for (Volume volume : result.getVolumes()) {
                try {
                    EbsVolumeData currentVolume = new EbsVolumeData.EbsVolumeDataBuilder(volume.getVolumeId(), volume.getState(), volume.getCreateTime(), volume.getSize())
                            .withAvailabilityZone(volume.getAvailabilityZone())
                            .withMultiAttachStatus(volume.getMultiAttachEnabled())
                            .withSnapshotId(volume.getSnapshotId())
                            .withIops(volume.getIops())
                            .build();
                    currentVolume.setAttachments(volume.getAttachments());

                    /*
                     * Fetching data from cloudwatch
                     * */
                    if (!SAVETIME) {
                        getCloudWatchDataEbsVolumes(currentVolume, days, hours);
                    } else {
                        ebsAttachMetrics(currentVolume, queries, hours, ebsVolumesData.size());
                    }
                    currentVolume.setRegion(region);
                    ebsVolumesData.add(currentVolume);

                    if (queries.size() == (numberClubVolumes * metricsPerVolume)) {
                        ebsGetMetrics(queries, days, cw);
                        queries.clear();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            if (result.getNextToken() == null || result.getNextToken().equals(""))
                break;
            result = client.describeVolumes(new DescribeVolumesRequest().withMaxResults(500).withNextToken(result.getNextToken()));
        }
        if (queries.size() != 0) {
            ebsGetMetrics(queries, days, cw);
            queries.clear();
        }

        if(DEBUG){
            System.out.println("Data of EBS Volumes fetched");
            System.out.println("Number of Volumes Found: "+ebsVolumesData.size());
            System.out.println();
        }
    }

    /**
     * Methods to get the Information about the elastic IPs associated with my account
     * at a particular region which is being passed as a parameter to this method in form
     * of a Region class object.
     *
     * @param region The Region Object of the location for which we want to get the Elastic Ip Information
     */
    private void eipBasicInfo(Region region) {
        if(DEBUG){
            System.out.println("Fetching data of Elastic IPs");
        }

        AmazonEC2 client = AmazonEC2Client.builder().withCredentials(CREDENTIALS).withRegion(region.toString()).build();
        DescribeAddressesResult addressesResult = client.describeAddresses();

        for (Address address : addressesResult.getAddresses()) {
            /*
             * All the below provided information using chaining of methods of the
             * inner builder class is necessary to provide
             * */
            try {
                ElasticIpData currentIp = new ElasticIpData.ElasticIpDataBuilder(address.getPublicIp(), address.getAllocationId(), address.getPublicIpv4Pool())
                        .withInstanceId(address.getInstanceId())
                        .withPrivateIp(address.getPrivateIpAddress())
                        .withAssociationId(address.getAssociationId())
                        .build();
                currentIp.setRegion(region);
                elasticIpsData.add(currentIp);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        if(DEBUG){
            System.out.println("Data of Elastic IPs fetched...");
            System.out.println("Number of IPs found: "+elasticIpsData.size());
            System.out.println();
        }
    }

    /**
     * Method to retrieve information about the backups/snapshots and their
     * respective vaults created in our AWS account.
     *
     * @param region The Region Object associated with the location for which we want our data
     */
    private void backupsBasicInfo(Region region) {
        if(DEBUG){
            System.out.println("Fetching Data of Snapshots/Backups");
        }

        /*
         * Creating the AWS backup client
         * */
        AWSBackup client = AWSBackupClient.builder().withCredentials(CREDENTIALS).withRegion(region.toString()).build();

        /*
         * Listing the vaults present in our account
         * */
        ListBackupVaultsResult response = client.listBackupVaults(new ListBackupVaultsRequest());
        while (true) {
            for (BackupVaultListMember vault : response.getBackupVaultList()) {
                /*
                 * Creating the vault object to store vault information. The information which is provided
                 * below is necessary to provide. I have used builder to build the object and have used further
                 * chaining of the methods to provide the other necessary information
                 * */
                try {
                    BackupVaultData currentVault = new BackupVaultData.BackupVaultDataBuilder(vault.getBackupVaultName(), vault.getBackupVaultArn())
                            .withCreationDate(vault.getCreationDate())
                            .withRetentionDays(vault.getMinRetentionDays(), vault.getMaxRetentionDays())
                            .withNumberOfRecoveryPoints(vault.getNumberOfRecoveryPoints())
                            .build();
                    currentVault.setRegion(region);
                    /*
                     * Listing recovery points/backups/snapshots for the backup vault in context
                     * */
                    ListRecoveryPointsByBackupVaultResult result = client.listRecoveryPointsByBackupVault(new ListRecoveryPointsByBackupVaultRequest().withBackupVaultName(vault.getBackupVaultName()));
                    while (true) {
                        for (RecoveryPointByBackupVault recoveryPoint : result.getRecoveryPoints()) {
                            /*
                             * Creating the BackupData object with the necessary information. The below provided information
                             * is necessary to provide. I have used builder to build the object and have chained methods to
                             * provide other necessary information.
                             * */
                            BackupData currentRecoveryPoint = new BackupData.BackupDataBuilder(currentVault, recoveryPoint.getBackupVaultArn())
                                    .withCreationDate(recoveryPoint.getCreationDate())
                                    .withCompletionDate(recoveryPoint.getCompletionDate())
                                    .withBackupSize(recoveryPoint.getBackupSizeInBytes())
                                    .withStatus(recoveryPoint.getStatus())
                                    .withResourceType(recoveryPoint.getResourceType())
                                    .withLastRestoreTime(recoveryPoint.getLastRestoreTime())
                                    .withMoveToColdStorageInfo(recoveryPoint.getCalculatedLifecycle().getMoveToColdStorageAt(), recoveryPoint.getLifecycle().getMoveToColdStorageAfterDays())
                                    .withDeleteInfo(recoveryPoint.getCalculatedLifecycle().getDeleteAt(), recoveryPoint.getLifecycle().getDeleteAfterDays())
                                    .build();
                            currentRecoveryPoint.setRegion(region);
                            backupsData.add(currentRecoveryPoint);
                        }
                        if (result.getNextToken() == null || result.getNextToken().equals("")) {
                            break;
                        } else {
                            result = client.listRecoveryPointsByBackupVault(new ListRecoveryPointsByBackupVaultRequest().withBackupVaultName(vault.getBackupVaultName()).withNextToken(result.getNextToken()));
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            if (response.getNextToken() == null || response.getNextToken().equals(""))
                break;
            else
                response = client.listBackupVaults(new ListBackupVaultsRequest().withNextToken(response.getNextToken()));
        }

        if(DEBUG){
            System.out.println("Data of Backups Fetched...");
            System.out.println("Number of Backups Found: "+backupsData.size());
            System.out.println();
        }
    }

    /**
     * Method to retrieve the information of memory and vcpu for all the instance types
     * available in a region
     * @param region the region of which we want. It is to passed as the Region class Object
     * @param getData if it is true then we retrieve the data form AWS and store them in local files else we read the data from the files which have been made earlier
     *                this helps in saving the time which the API call takes to get the data
     */
    private void getAllInstanceTypesInfo(Region region, boolean getData) {
        if (getData) {
            AmazonEC2 client = AmazonEC2Client.builder().withCredentials(CREDENTIALS).withRegion(region.toString()).build();

            /*
             * Making the API call to get the data
             * */
            DescribeInstanceTypesResult result = client.describeInstanceTypes(new DescribeInstanceTypesRequest());

            while (true) {
                for (InstanceTypeInfo currentType : result.getInstanceTypes()) {
                    ec2InstanceTypeToMemorySizeInMB.put(currentType.getInstanceType(), currentType.getMemoryInfo().getSizeInMiB());
                    ec2InstanceTypeToVcpuCount.put(currentType.getInstanceType(), currentType.getVCpuInfo().getDefaultVCpus());
                }
                if (result.getNextToken() == null || result.getNextToken().equals("") || result.getInstanceTypes().size() == 0)
                    break;
                result = client.describeInstanceTypes(new DescribeInstanceTypesRequest().withNextToken(result.getNextToken()));
            }

            /*
             * Saving in Files
             * */
            try {
                FileOutputStream outstream = new FileOutputStream("instanceTypeSize.data");
                Properties props = new Properties();
                for (Map.Entry<String, Long> entry : ec2InstanceTypeToMemorySizeInMB.entrySet()) {
                    props.setProperty(entry.getKey(), entry.getValue().toString());
                }
                props.store(outstream, "");

                outstream = new FileOutputStream("instanceTypeVcpu.data");
                props = new Properties();
                for (Map.Entry<String, Integer> entry : ec2InstanceTypeToVcpuCount.entrySet()) {
                    props.setProperty(entry.getKey(), entry.getValue().toString());
                }
                props.store(outstream, "");
            } catch (Exception e) {
                e.printStackTrace();
            }

        } else {

            /*
             * Reading from Files
             * */
            try {
                ec2InstanceTypeToMemorySizeInMB = new HashMap<>();
                FileInputStream istream = new FileInputStream("instanceTypeSize.data");
                Properties property = new Properties();
                property.load(istream);
                for (String prop : property.stringPropertyNames()) {
                    ec2InstanceTypeToMemorySizeInMB.put(prop, Long.valueOf(property.getProperty(prop)));
                }

                ec2InstanceTypeToVcpuCount = new HashMap<>();
                istream = new FileInputStream("instanceTypeVcpu.data");
                property = new Properties();
                property.load(istream);
                for (String prop : property.stringPropertyNames()) {
                    ec2InstanceTypeToVcpuCount.put(prop, Integer.valueOf(property.getProperty(prop)));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
    }

    /**
     * Method to retrieve information of on demand pricing of ec2 instances in a region. Along with that we also fetch the data of vcpus
     * and memory associated with them in this call. So if a call is made to this function then a call to getAllInstanceTypesInfo is not
     * necessary
     * @param region The region of which we want the data
     * @param fetchData if it is true then we fetch the data using the API call from AWS and store it in files, else we read the data from already existing files
     */
    private void getOneTimeEc2Info(Region region, boolean fetchData) {
        if (fetchData) {
            HashSet<String> tempo = new HashSet<>();
            ArrayList<Filter> nuList = new ArrayList<>();
            nuList.add(new Filter().withType("TERM_MATCH").withField("productFamily").withValue("Compute Instance"));
            nuList.add(new Filter().withType("TERM_MATCH").withField("regionCode").withValue(region.toString()));
            nuList.add(new Filter().withType("TERM_MATCH").withField("tenancy").withValue("Shared"));
            nuList.add(new Filter().withType("TERM_MATCH").withField("preInstalledSw").withValue("NA"));
            nuList.add(new Filter().withType("TERM_MATCH").withField("operatingSystem").withValue("Linux"));
            nuList.add(new Filter().withType("TERM_MATCH").withField("licenseModel").withValue("No License required"));
            nuList.add(new Filter().withType("TERM_MATCH").withField("capacitystatus").withValue("used"));

            /*
             * Making the API call*/
            AWSPricing client = AWSPricingClient.builder().withCredentials(CREDENTIALS).withRegion(region.toString()).build();
            GetProductsResult result = client.getProducts(new GetProductsRequest().withServiceCode("AmazonEC2").withFilters(nuList));

            try {
                while (true) {
                    boolean toStop = true;
                    for (String current : result.getPriceList()) {
                        /*
                         * Converting to JSON and using it appropriately so that we can get the exact information which is required*/
                        JSONObject jsonObject = new JSONObject(current);
                        JSONObject attributeObject = (JSONObject) ((JSONObject) jsonObject.get("product")).get("attributes");
                        try {
                            String memory = (String) attributeObject.get("memory");
                            JSONObject tempObj = ((JSONObject) ((JSONObject) jsonObject.get("terms")).get("OnDemand"));
                            JSONObject currObj = (JSONObject) ((JSONObject) tempObj.get((String) tempObj.keys().next())).get("priceDimensions");
                            JSONObject priceDimensions = (JSONObject) currObj.get((String) currObj.keys().next());
                            if (!memory.equals("NA")) {
                                Long mem = (long) (Double.parseDouble(memory.substring(0, memory.indexOf(" "))) * 1024.0);
                                Integer vcpu = Integer.valueOf((String) attributeObject.get("vcpu"));
                                Double moneyVal = Double.valueOf((String) ((JSONObject) priceDimensions.get("pricePerUnit")).get("USD"));
                                String itype = (String) attributeObject.get("instanceType");

                                if (!tempo.contains(itype)) {
                                    tempo.add(itype);
                                    toStop = false;
                                    ec2InstanceTypeToMemorySizeInMB.put(itype, mem);
                                    ec2InstanceTypeToVcpuCount.put(itype, vcpu);
                                    ec2InstanceTypeToPrice.put(itype, moneyVal);
                                }
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    if (result.getNextToken() == null || result.getNextToken().equals("") || result.getPriceList().size() == 0 || toStop)
                        break;
                    result = client.getProducts(new GetProductsRequest().withServiceCode("AmazonEC2").withNextToken(result.getNextToken()).withFilters(nuList));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            /*
             * Saving in files
             * */

            try {
                FileOutputStream outstream = new FileOutputStream("instanceTypeSizeN.data");
                Properties props = new Properties();
                for (Map.Entry<String, Long> entry : ec2InstanceTypeToMemorySizeInMB.entrySet()) {
                    props.setProperty(entry.getKey(), entry.getValue().toString());
                }
                props.store(outstream, "");

                outstream = new FileOutputStream("instanceTypeVcpuN.data");
                props = new Properties();
                for (Map.Entry<String, Integer> entry : ec2InstanceTypeToVcpuCount.entrySet()) {
                    props.setProperty(entry.getKey(), entry.getValue().toString());
                }
                props.store(outstream, "");

                outstream = new FileOutputStream("instanceTypePriceN.data");
                props = new Properties();
                for (Map.Entry<String, Double> entry : ec2InstanceTypeToPrice.entrySet()) {
                    props.setProperty(entry.getKey(), entry.getValue().toString());
                }
                props.store(outstream, "");
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            /*
             * Reading from files
             * */
            try {
                ec2InstanceTypeToMemorySizeInMB = new HashMap<>();
                FileInputStream istream = new FileInputStream("instanceTypeSizeN.data");
                Properties property = new Properties();
                property.load(istream);
                for (String prop : property.stringPropertyNames()) {
                    ec2InstanceTypeToMemorySizeInMB.put(prop, Long.valueOf(property.getProperty(prop)));
                }

                ec2InstanceTypeToVcpuCount = new HashMap<>();
                istream = new FileInputStream("instanceTypeVcpuN.data");
                property = new Properties();
                property.load(istream);
                for (String prop : property.stringPropertyNames()) {
                    ec2InstanceTypeToVcpuCount.put(prop, Integer.valueOf(property.getProperty(prop)));
                }

                ec2InstanceTypeToPrice = new HashMap<>();
                istream = new FileInputStream("instanceTypePriceN.data");
                property = new Properties();
                property.load(istream);
                for (String prop : property.stringPropertyNames()) {
                    ec2InstanceTypeToPrice.put(prop, Double.valueOf(property.getProperty(prop)));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}

public class Main {
    public static void main(String[] args) {
        boolean debug = false;

        /*
         * Credentials to the AWS account are to be read from the project.properties file
         * */
        AWSCostOptimizerAndReportGenerator optimizer = new AWSCostOptimizerAndReportGenerator(debug);

        /*
         * No Need to set Region now, as the code is programmed to run for multiple regions automatically
         * optimizer.setRegion(Region.US_EAST_1); // Check Bottom of the file for reference
         * */
        optimizer.getDataAndGenerateReport();

        if(debug)
            System.out.println(optimizer);

        System.exit(0);
    }
}

/*
 *  Regions available are:
 *  0 ap-south-1,
 *  1 eu-south-1,
 *  2 us-gov-east-1,
 *  3 ca-central-1,
 *  4 eu-central-1,
 *  5 us-west-1,
 *  6 us-west-2,
 *  7 af-south-1,
 *  8 eu-north-1,
 *  9 eu-west-3,
 * 10 eu-west-2,
 * 11 eu-west-1,
 * 12 ap-northeast-2,
 * 13 ap-northeast-1,
 * 14 me-south-1,
 * 15 sa-east-1,
 * 16 ap-east-1,
 * 17 cn-north-1,
 * 18 us-gov-west-1,
 * 19 ap-southeast-1,
 * 20 ap-southeast-2,
 * 21 us-iso-east-1,
 * 22 us-east-1,
 * 23 us-east-2,
 * 24 cn-northwest-1,
 * 25 us-isob-east-1,
 * 26 aws-global,
 * 27 aws-cn-global,
 * 28 aws-us-gov-global,
 * 29 aws-iso-global,
 * 30 aws-iso-b-global
 * */