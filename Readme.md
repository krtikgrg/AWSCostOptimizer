# AWS Cost Optimizer

## How to Use
1. Provide the credentials for the AWS Account in the __project.properties__ file. You need to have programmatic access and need to have the following policies attached to it
   1. AmazonEC2ReadOnlyAccess
   2. AmazonS3ReadOnlyAccess
   3. CloudWatchReadOnlyAccess
   4. AWSBackupOperatorAccess
2. Provide the Absolute path of the location where you want to create the final report along with the desired name at line 102(variable REPORT_FILENAME_WITH_PATH) of the __main.java__ file.
   1. eg. "/Users/kartik-mdcharw/kartik/codes/testFile.xlsx"
3. Run
   1. gradle build
   2. gradle run

## Additional DEBUG mode
Using this mode will result in printing of some additional information by which we can keep trach of how much the code has progressed
1. By Default the DEBUG mode is set to true
2. But will be changed while creating an object of the AWSCostOptimizerAndReportGenerator class
   1. Passed as an argument to the constructor
   2. If passed true then code runs in this DEBUG mode
   3. Else Runs in normal mode

## Sheets generated in the created Report
1. __EC2 Utilization__ : This sheet has the data of Underutilized and Overutilized ec2 instances for all the three type
   1. On Demand
   2. Spot
   3. Reserved

   We classify as underutilized if either CPU Utilization or Memory Utilization (If available) falls below the threshold for under-utilization(Configurable) value and classify as over-utilized if any of the cpu or memory utilization is above the threshold for over-utilization(Configurable)

   This sheet also contains suggestion for On Demand Instances in terms of required vCPUs and required memory is the corresponding variable(SUGGESTION_MODE) in __main.java__ file is true
2. __Reserved Instances__ : In this sheet We list those Purchased EC2 Reserved Instances for which we are using less than X%(Configurable, variable EC2_RESERVED_CAPACITY_THRESHOLD_IN_PERCENT) of the total bought capacity. In this sheet we also list down those purchased reserved instances which are about to expire in next 14 days(Configurable, variable EC2_RESERVED_EXPIRING_IN_NEXT_N_DAYS_THRESHOLD) .
3. __EC2 Cost Implication__ : CAUTION Major bottleneck of Code (Takes time to execute)
   1. Only create when PRICE_COMPARISON variable is set to true
   2. Here we Compare the price of On demand instances to that if the same instance was a reserved isntance
   3. We only support Linux instances as of now, for other platforms like windows we compare the Linux On Demand price for the instance type in context to that of windows Reserved instance of the same type
4. __ELB Under Utilization__ : Load Balancers are of three types
   1. Application
   2. Network
   3. Gateway

   This sheet lists the load balancers which are considered to be underutilized depending upon the threshold (Configurable, LOAD_BALANCER_THRESHOLD_COUNT)
   1. We use RequestCount metric for Application Load Balancers
   2. We use ActiveFlowCount metric for Network and Gateway Load Balancers

   Suggestion is to not refer this sheet as the metrics are not good enough (Said by a SLT @ Sprinklr) instead refer to _Idle Load Balancers_ sheet for a better understanding.
5. __Idle Load Balancers__ : In this sheet we list the load balancers which either dont have any targets attached to it or has no healthy targets.
6. __EBS and Elastic IPs__ : In this sheet we list the following data
   1. The Unused EBS Volumes (volumes which are not in "in use" state)
   2. Then we list the EBS Volume with either low read activity or low write activity based on a threshold (Configurable, varibales EBS_THRESHOLD_READ_OPS_PER_SECOND EBS_THRESHOLD_WRITE_OPS_PER_SECOND)
   3. Then we list the EBS Volumes where the read-write activity is greater than the 90% of the supported value.
   4. Then we list the Elastic IPs present in our account which have been allocated to us but are not associated with anything.
7. __Backups (Snapshots)__ : In this sheet we add the data for those backups/recovery points/snapshots which are stored in our account and are older than 120 days(Configurable, variable BACKUPS_THRESHOLD_DAYS)
   1. Reverse Sorted Based on their Size
8. __S3 Meta Data__ : In this sheet we list down some meta data for each bucket
   1. Bucket Name
   2. Bucket Size (Average, in bytes) over the last N hours (Configurable, variable GRANULARITY_IN_HOURS)
      1. Above-mentioned variable is not something to tamper with, think carefully read the description mentioned in the code itself before modifying it.
   3. Number of Objects (Average)
      1. Here Objects are the files stored inside a S3 bucket
   4. Above-mentioned Bucket Size and Number Of Objects are corresponding to the values collected by cloudwatch
9. __S3__ : In this sheet we list the buckets in our account
   1. Reverse Sorted Based on the Relevant Size
   2. Relevant Size : For Each Bucket we find the Objects/Files which have not been modified in the last N daya (Configurable, variable S3_OBJECTS_THRESHOLD_DAYS)
      1. So the total size of all such files is relevant size of a bucket and we reverse sort the bucket based on this Relevant Size
   3. For each bucket we then list down the objects which have not been modified in the last N Days (Configurable, variable S3_OBJECTS_THRESHOLD_DAYS)
      1. Here Again the Objects are Reverse Sorted for Each Bucket based on their Size.
   4. The Sheet is just an empty sheet in this current release of the product but we can add it by making a change in the _main.java_ file
      1. Uncomment line 1537, 1547, 1571-1597
   5. Do at your own risk, may serve a serious time-consuming factor

## ElasticSearch Aspects
The Meta-Data of the S3 Buckets can be made to go to an elastic search index if a Configurable variable (ADD_S3_DATA_TO_ELASTIC_SEARCH) is set to true
1. Prerequisite is that elasticsearch server should be running on the local machine
2. If We want to create a new index then set the variable createIndex true (line 1488) and provide the name of the index to be created (line 1489)

## Additional Configurable Parameters
1. __SAVETIME__ : if true then clubbing of API calls happen we save time spent
   1. it makes no sense to run the code while setting this variable to false
2. __DAYS_OF_DATA__ : The number of days of data we want from cloudwatch to be considered
3. __EC2_CPU_THRESHOLD_IN_PERCENT_UNDER__ : Threshold CPU Utilization for identifying Under Utilized EC2 Instances. All instances having CPU Utilization below this threshold are marked as under utilized
4. __EC2_MEMORY_THRESHOLD_IN_PERCENT_UNDER__ : Threshold Memory Utilization for identifying Under Utilized EC2 Instances. All instances having Memory Utilization below this threshold are marked as under utilized
5. __EC2_CPU_THRESHOLD_IN_PERCENT_OVER__ : Threshold CPU Utilization for identifying Over Utilized EC2 Instances. All instances having CPU Utilization above this threshold are marked as over utilized
6. __EC2_MEMORY_THRESHOLD_IN_PERCENT_OVER__ : Threshold Memory Utilization for identifying Over Utilized EC2 Instances. All instances having Memory Utilization above this threshold are marked as over utilized
7. __EC2_CPU_STATISTIC_UNDER__ : Statistic which is to be used while identifying under utilized resources (CPU). Possible values are "average", "minimum" and "maximum"
8. __EC2_CPU_STATISTIC_OVER__ : Statistic which is to be used while identifying over utilized resources (CPU). Possible values are "average", "minimum" and "maximum"
9. __EC2_MEMORY_STATISTIC_UNDER__ : Statistic which is to be used while identifying under utilized resources (Memory). Only Possible value is "maximum" in this current release
10. __EC2_MEMORY_STATISTIC_OVER__ : Statistic which is to be used while identifying Over utilized resources (Memory). Only Possible value is "maximum" in this current release
11. __LOAD_BALANCER_STATISTIC__ : "sum" makes the most sense out of all the metrics. Statistic to be used for identifying underutilized load balancers. Possible values are "maximum", "minimum", "average" and "sum"
12. __GRANULARITY_IN_HOURS__ : Refer to Code
13. __REPORT_FILENAME_WITH_PATH__ : The name to be given to the excel report to be generated by the code along with the absolute path, CAUTION provide extension of the file too

## Link to the presentation
WILL BE ADDED LATER ON