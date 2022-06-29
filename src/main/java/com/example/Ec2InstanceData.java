package com.example;


import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.model.*;
import software.amazon.awssdk.regions.Region;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;

/**
 * Class to create objects to store information about the EC2 instances
 */
class Ec2InstanceData {
    private String id;
    private String state;
    private String publicIpv4 ="";
    private String publicIpv4Dns ="";
    private String privateIpv6 ="";
    private String privateIpv6Dns ="";
    private String platformDetails = "";
    private String availabilityZone = "";
    private String tenancy = "";
    private String type = "";
    private String spotRequestId;
    private Integer vcpuCount;
    private ArrayList<StatisticRecord> averageCpuUtilizationData = new ArrayList<>();
    private ArrayList<StatisticRecord> minimumCpuUtilizationData = new ArrayList<>();
    private ArrayList<StatisticRecord> maximumCpuUtilizationData = new ArrayList<>();
    private ArrayList<StatisticRecord> memoryUsedPercentData = new ArrayList<>();
    private ArrayList<StatisticRecord> diskUsedPercentData = new ArrayList<>();
    private Double spotPrice = null;
    private Double reservedPrice = null;
    private Double demandPrice = null;
    private boolean memoryData = false;
    private Region region;

    /**
     * Builder class to help in creating objects of the parent class. The parent class's constructor is made private which
     * means this is the only way to create an object of the parent class.
     */
    public static class Ec2InstanceDataBuilder {
        private String id;
        private String state;
        private String publicIpv4;
        private String publicIpv4Dns;
        private String privateIpv6;
        private String privateIpv6Dns;
        private String platformDetails;
        private String availabilityZone;
        private String tenancy;
        private String type;
        private Integer vcpuCount;

        /**
         * The constructor for the builder class
         * @param id the "instance id" of the instance in context
         * @param state the state of the instance in context
         */
        public Ec2InstanceDataBuilder(String id, String state){
            this.id = id;
            this.state = state;
        }

        /**
         * Method to provide information about the ipv4, basically the ip address itself and the dns name. It is there only if the instance is in running state
         * @param ipaddress The ip address value of the instance in context
         * @param dnsName the dns name of the instance in context
         * @return the builder object which can be further used to chain other methods and ultimately to create the ec2InstanceData class object
         */
        public Ec2InstanceDataBuilder withIpv4(String ipaddress, String dnsName){
            this.publicIpv4 = ipaddress;
            this.publicIpv4Dns = dnsName;
            return this;
        }

        /**
         * Method to provide information about the ipv6, basically the ip address itself and the dns name. It is there only if the instance is in running and stopped state
         * @param ipaddress The ip address value of the instance in context
         * @param dnsName the dns name of the instance in context
         * @return the builder object which can be further used to chain other methods and ultimately to create the ec2InstanceData class object
         */
        public Ec2InstanceDataBuilder withIpv6(String ipaddress, String dnsName){
            this.privateIpv6 = ipaddress;
            this.privateIpv6Dns = dnsName;
            return this;
        }

        /**
         * method to specify the instance type for example t1 micro etc.
         * @param type the type of the current instance
         * @return the builder object which can be further used to chain other methods and ultimately to create the ec2InstanceData class object
         */
        public Ec2InstanceDataBuilder withInstanceType(String type){
            this.type = type;
            return this;
        }

        /**
         * Method to provide the platform details like Linux/Unix
         * @param details the details of the instance in context
         * @return the builder object which can be further used to chain other methods and ultimately to create the ec2InstanceData class object
         */
        public Ec2InstanceDataBuilder withPlatformDetails(String details){
            this.platformDetails = details;
            return this;
        }

        /**
         * Method to specify the availability zone in which the instance is created
         * @param zone the zone of the instance in context
         * @return the builder object which can be further used to chain other methods and ultimately to create the ec2InstanceData class object
         */
        public Ec2InstanceDataBuilder withAvailabilityZone(String zone){
            this.availabilityZone = zone;
            return this;
        }

        /**
         * Method to provide the tenancy value of the instance
         * @param tenancy the tenancy value of the instance in context
         * @return the builder object which can be further used to chain other methods and ultimately to create the ec2InstanceData class object
         */
        public Ec2InstanceDataBuilder withTenancy(String tenancy){
            this.tenancy = tenancy;
            return this;
        }

        /**
         * Method to set the number of vcpus for an instance
         * @param count the number of vcpus in the ec2 instance in context
         * @return the builder object which can be further used to chain other methods and ultimately to create the ec2InstanceData class object
         */
        public Ec2InstanceDataBuilder withVcpus(Integer count){
            this.vcpuCount = count;
            return this;
        }

        /**
         * Method to build the actual class object using this builder object
         * @return Actual ec2InstanceData Object
         * @throws Exception when build method was unable to create an object due absence of certain params
         */
        public Ec2InstanceData build() throws Exception {
            return new Ec2InstanceData(this);
        }
    }

    /**
     * method to set the cpu utilization for Average stat
     * @param array array corresponding to the Average stat for the instance in context
     */
    public void setAverageCpuUtilizationData(ArrayList<StatisticRecord> array){
        if(array == null)
            return;
        this.averageCpuUtilizationData.addAll(array);
    }

    /**
     * method to set the cpu utilization for Maximum stat
     * @param array array corresponding to the Maximum stat for the instance in context
     */
    public void setMaximumCpuUtilizationData(ArrayList<StatisticRecord> array){
        if(array == null)
            return;
        this.maximumCpuUtilizationData.addAll(array);
    }

    /**
     * method to set the cpu utilization for Minimum stat
     * @param array array corresponding to the Minimum stat for the instance in context
     */
    public void setMinimumCpuUtilizationData(ArrayList<StatisticRecord> array){
        if(array == null)
            return;
        this.minimumCpuUtilizationData.addAll(array);
    }

    /**
     * method to set the disk used percentage data from cloudwatch agent
     * @param array array having the data
     */
    public void setDiskUsedPercentData(ArrayList<StatisticRecord> array) {
        if(array == null)
            return;
        this.diskUsedPercentData.addAll(array);
    }

    /**
     * method to set the memory used percentage data from cloudwatch agent
     * @param array array having the data
     */
    public void setMemoryUsedPercentData(ArrayList<StatisticRecord> array) {
        if(array == null)
            return;
        this.memoryUsedPercentData.addAll(array);
        if(!this.memoryData && this.memoryUsedPercentData.size()>0)
            this.memoryData = true;
    }

    /**
     * Method to check if we are monitoring the memory utilization data of an ec2 instance
     * @return Return true if we have memory utilization data else returns false
     */
    public boolean ifMemoryDataAvailable(){
        return this.memoryData;
    }

    /**
     * method to return the platform details
     * @return platform details of the instance in context
     */
    public String getPlatformDetails(){
        return this.platformDetails;
    }

    /**
     * Method to return the type of the instance
     * @return the type of the instance in context
     */
    public String getType(){
        return this.type;
    }

    /**
     * The tenancy value of the instance
     * @return the tenancy value of the instance in context
     */
    public String getTenancy(){
        return this.tenancy;
    }

    /**
     * Method to return the availability zone of an instance
     * @return the availability zone of the instance in context
     */
    public String getAvailabilityZone(){
        return this.availabilityZone;
    }

    /**
     * method to return the state of the instance in which it is currently
     * @return the state of the instance in context
     */
    public String getState(){
        return this.state;
    }

    /**
     * returns the id of the instance
     * @return the instance id of the instance in context
     */
    public String getId(){
        return this.id;
    }

    /**
     * Method to set the value of spot Request id if that exists
     * @param spotRequestId the id of the spot request the ec2 instance in context is fulfilling
     */
    public void setSpotRequestId(String spotRequestId) {
        this.spotRequestId = spotRequestId;
    }

    /**
     * Method to set the region of an instance
     * @param region The Region class object corresponding to the region in which the instance in context lies
     */
    public void setRegion(Region region){
        this.region = region;
    }

    /**
     * Method to retrieve the region of an ec2 instance
     * @return the region in string format of the ec2 instance in context
     */
    public String getRegion(){
        return this.region.toString();
    }

    /**
     * The method to return the spot request id associated to the ec2 instance in context.
     * @return the id of the spot request of the current ec2 instance. Returns null if none was associated.
     */
    public String getSpotRequestId(){
        return this.spotRequestId;
    }

    /**
     * Method to check if an ec2 instance is a spot instance or not
     * @return return true if the instance in context is a spot instance else returns false
     */
    public boolean isSpot(){
        return this.spotRequestId != null;
    }

    /**
     * Method to check whether the instance in context is under utilized or not (in terms of CPU). It is decided based on the threshold passed as a parameter.
     * @param statistic the statistic which is to be used. it can be "average", "maximum" or "minimum"
     * @param threshold the threshold value, when all the values of CPU utilization of the statistic in context drop below this threshold then we return true
     * @return returns true if all the values fall below the threshold value else return false
     * @throws Exception if the statistic value is not recognised that is anything other than "average", "maximum" or "minimum" is specified
     */
    public boolean isUnderUtilizedCpu(String statistic, double threshold) throws Exception {
        if(!this.getState().equals("running"))
            return false;
        switch (statistic) {
            case "average":
                return isUnderUtilizedResource(averageCpuUtilizationData, threshold);
            case "maximum":
                return isUnderUtilizedResource(maximumCpuUtilizationData, threshold);
            case "minimum":
                return isUnderUtilizedResource(minimumCpuUtilizationData, threshold);
            default:
                throw new Exception("statistic not recognised, acceptable values are 'average', 'minimum' and 'maximum'");
        }
    }

    /**
     * Method to check whether the instance in context is over utilized or not (in terms of CPU). It is decided based on the threshold passed as a parameter.
     * @param statistic the statistic which is to be used. it can be "average", "maximum" or "minimum"
     * @param threshold the threshold value, when all the values of CPU utilization of the statistic in context are above this threshold then we return true
     * @return returns true if all the values are above the threshold value else return false
     * @throws Exception if the statistic value is not recognised that is anything other than "average", "maximum" or "minimum" is specified
     */
    public boolean isOverUtilizedCpu(String statistic, double threshold) throws Exception {
        if(!this.getState().equals("running"))
            return false;
        switch (statistic) {
            case "average":
                return isOverUtilizedResource(averageCpuUtilizationData, threshold);
            case "maximum":
                return isOverUtilizedResource(maximumCpuUtilizationData, threshold);
            case "minimum":
                return isOverUtilizedResource(minimumCpuUtilizationData, threshold);
            default:
                throw new Exception("statistic not recognised, acceptable values are 'average', 'minimum' and 'maximum'");
        }
    }

    /**
     * Method to check whether the instance in context is under utilized or not (In terms of Memory). It is decided based on the threshold passed as a parameter.
     * @param statistic the statistic which is to be used. The only supported statistic in this release is "maximum"
     * @param threshold the threshold value, when all the values of Memory Used Percentage data of the statistic in context drop below this threshold then we return true
     * @return returns true if all the values fall below the threshold value else return false
     * @throws Exception if the statistic value is not recognised that is anything other than "maximum" specified
     */
    public boolean isUnderUtilizedMemory(String statistic, double threshold) throws Exception {
        if(!this.getState().equals("running"))
            return false;
        switch (statistic) {
            case "maximum":
                return isUnderUtilizedResource(memoryUsedPercentData, threshold);
            default:
                throw new Exception("statistic not recognised, only acceptable value in this release is 'maximum'");
        }
    }

    /**
     * Method to check whether the instance in context is over utilized or not (In terms of Memory). It is decided based on the threshold passed as a parameter.
     * @param statistic the statistic which is to be used. Only supported statistic is "maximum" in this release
     * @param threshold the threshold value, when all the values of Memory Used Percentage data of the statistic in context are above this threshold then we return true
     * @return returns true if all the values are above the threshold value else return false
     * @throws Exception if the statistic value is not recognised that is anything other than "maximum" is specified
     */
    public boolean isOverUtilizedMemory(String statistic, double threshold) throws Exception {
        if(!this.getState().equals("running"))
            return false;
        switch (statistic) {
            case "maximum":
                return isOverUtilizedResource(memoryUsedPercentData, threshold);
            default:
                throw new Exception("statistic not recognised, only acceptable value in this release is 'maximum'");
        }
    }

    /**
     * Method to retrieve the cost of a reserved instance and spot instance(REMOVED), if it is created with the same configuration as
     * the instance in context. This method is invoked only if the PRICE_COMPARISON is true.
     * @param client the EC2 client object which is to be used to make the API calls
     */
    public void getAndStoreCostImplication(AmazonEC2 client){
        /*
        if(this.type.equals("") || this.platformDetails.equals(""))
            return;
        Instant endInstant = Instant.now();
        Instant startInstant = Instant.ofEpochSecond(endInstant.getEpochSecond()-3600L);
        DescribeSpotPriceHistoryResult result = client.describeSpotPriceHistory(new DescribeSpotPriceHistoryRequest()
                .withAvailabilityZone(this.availabilityZone)
                .withProductDescriptions(this.platformDetails)
                .withStartTime(Date.from(startInstant))
                .withEndTime(Date.from(endInstant))
                .withInstanceTypes(this.type));
        if(result.getSpotPriceHistory().size() == 0)
            return;
        this.spotPrice =  Double.valueOf(result.getSpotPriceHistory().get(0).getSpotPrice());
        */

        if(this.type.equals("") || this.platformDetails.equals("") || this.tenancy.equals(""))
            return;

        DescribeReservedInstancesOfferingsResult resultReserved = client.describeReservedInstancesOfferings(new DescribeReservedInstancesOfferingsRequest()
                .withAvailabilityZone(this.availabilityZone)
                .withInstanceTenancy(this.tenancy)
                .withInstanceType(this.type)
                .withMaxInstanceCount(1)
                .withMaxDuration(94608000L)
                .withMinDuration(94608000L)
                .withOfferingType("All Upfront")
                .withOfferingClass("standard")
                .withProductDescription(this.platformDetails));
        if(resultReserved.getReservedInstancesOfferings().size() == 0)
            return;
        Double price = (resultReserved.getReservedInstancesOfferings().get(0).getFixedPrice()*3600.0)/94608000.0;
        this.reservedPrice = price;
    }

    /**
     * Method to retrieve the spot price if a spot request is made with the same configuration as that of the ec2 instance in context
     * @return the spot price of the instance (if made with the same configuration as that of the ec2 instance in context)
     */
    public Double getSpotPrice(){
        return this.spotPrice;
    }

    /**
     * Method to retrieve the reserved price if a reserved instance is made with the same configuration as that of the ec2 instance in cntext
     * @return the reserved price of the instance (if made with the same configuration as that of the ec2 instance in context)
     */
    public Double getReservedPrice(){
        return this.reservedPrice;
    }

    /**
     * Method to return the actual cpu utilization values
     * @param statistic the statistic to consider. Since this method will only be called after making a call to isUnderUtilized* or isOverUtilized* methods
     *                  so assuming that the previous call to those methods was successfully executed. That is the statistic value
     *                  was correct. So assuming it will be correct in this call too. Therefore, not applying any additional checks
     * @param underUtilized if its true then we have to return the cpu utilization for underutilized resources, else it is for over utilization resources
     * @return return the CPU utilization value, a single value is returned which is the maximum out of all the values present in the
     *                associated statistic array (underUtilized = true), when underUtilized is false this method return a single value which is the minimum out
     *                of all the values present in the associated array
     */
    public double getCpuUtilization(String statistic, boolean underUtilized){
        if(statistic.equals("average")){
            return getResourceUtilization(averageCpuUtilizationData, underUtilized);
        } else if (statistic.equals("maximum")) {
            return getResourceUtilization(maximumCpuUtilizationData, underUtilized);
        } else {
            return getResourceUtilization(minimumCpuUtilizationData, underUtilized);
        }
    }

    /**
     * Method to return the actual memory utilization values
     * @param statistic the statistic to consider. Since this method will only be called after making a call to isUnderUtilized* or isOverUtilized* methods
     *                  so assuming that the previous call to those methods was successfully executed. That is the statistic value
     *                  was correct. So assuming it will be correct in this call too. Therefore, not applying any additional checks
     * @param underUtilized if its true then we have to return the memory utilization for underutilized resources, else it is for over utilization resources
     * @return return the Memory utilization value, a single value is returned which is the maximum out of all the values present in the
     *                associated statistic array (underUtilized = true), when underUtilized is false then a single value which is the minimum
     *                out of all the values present in the associated array.
     */
    public double getMemoryUtilization(String statistic, boolean underUtilized){
            return getResourceUtilization(memoryUsedPercentData, underUtilized);
    }

    @Override
    public String toString() {
        return "Ec2InstanceData{" +
                "id='" + id + '\'' +
                ", state='" + state + '\'' +
                ", publicIpv4='" + publicIpv4 + '\'' +
                ", publicIpv4Dns='" + publicIpv4Dns + '\'' +
                ", privateIpv6='" + privateIpv6 + '\'' +
                ", privateIpv6Dns='" + privateIpv6Dns + '\'' +
                ", platformDetails='" + platformDetails + '\'' +
                ", availabilityZone='" + availabilityZone + '\'' +
                ", tenancy='" + tenancy + '\'' +
                ", type='" + type + '\'' +
                ", spotRequestId='" + spotRequestId + '\'' +
                ", vcpuCount=" + vcpuCount +
                ", averageCpuUtilizationData=" + averageCpuUtilizationData +
                ", minimumCpuUtilizationData=" + minimumCpuUtilizationData +
                ", maximumCpuUtilizationData=" + maximumCpuUtilizationData +
                ", memoryUsedPercentData=" + memoryUsedPercentData +
                ", diskUsedPercentData=" + diskUsedPercentData +
                '}';
    }

    /**
     * Method to return maximum/minimum resource utilization value from the array passed as an argument
     * @param array the array from which we want the value
     * @param underUtilized if its true then we have to return the resource utilization for underutilized resources, else it is for over utilization resources
     * @return return the maximum value out of the values present in the array passed as a param if underutilized is true
     *         else return the minimum value out of the values present in the array passed as a param
     */
    private double getResourceUtilization(ArrayList<StatisticRecord> array, boolean underUtilized){
        if(underUtilized) {
            double maxi = 0;
            for (StatisticRecord record : array) {
                maxi = Math.max(maxi, record.getValue());
            }
            return maxi;
        } else {
            double mini = 100;
            for (StatisticRecord record : array) {
                mini = Math.min(mini, record.getValue());
            }
            return mini;
        }
    }

    /**
     * Method to retrieve the number of vcpus of an ec2 instance
     * @return the number of vcpus associated with the ec2 instance in context
     */
    public Integer getVcpuCount(){
        return this.vcpuCount;
    }

    /**
     * method to check if the values present in the array passed as a parameter are below the threshold value or not. if they are below it then
     * this instance is under utilized for the resource in context else it is not
     * @param array the array to take values from
     * @param threshold the threshold value
     * @return returns true if the instance is underutilized else returns false
     */
    private boolean isUnderUtilizedResource(ArrayList<StatisticRecord> array,double threshold){
        if(array.size() == 0)
            return false;
        for(StatisticRecord record:array){
            if(record.getValue()>threshold)
                return false;
        }
        return true;
    }

    /**
     * method to check if the values present in the array passed as a parameter are above the threshold value or not. if they are above it then
     * this instance is over utilized for the resource in context else it is not
     * @param array the array to take values from
     * @param threshold the threshold value
     * @return returns true if the instance is over-utilized else returns false
     */
    private boolean isOverUtilizedResource(ArrayList<StatisticRecord> array,double threshold){
        if(array.size() == 0)
            return false;
        for(StatisticRecord record:array){
            if(record.getValue()<threshold)
                return false;
        }
        return true;
    }

    /**
     * Method to build the actual Ec2InstanceData class object from the builder object
     * @param builder the builder object
     * @throws Exception when builder object does not have an instance id and instance state. Both these params are necessary
     *         Any other params if they were null in the builder object then those params would have an empty string as their value
     *         in the Ec2InstanceData object. Also throws an expection if an invalid value is provided for the number of vcpus/core count
     */
    private Ec2InstanceData(Ec2InstanceDataBuilder builder) throws Exception {
        id = builder.id;
        state = builder.state;
        this.vcpuCount = builder.vcpuCount;

        if(this.id == null){
            throw new Exception("Instance Id is a mandatory field to provide");
        }
        if(this.state == null){
            throw new Exception("Instance State is a mandatory field to provide");
        }
        if(this.state.equals("running") && (this.vcpuCount == null || this.vcpuCount <= 0)){
            throw new Exception("Provide a valid value for the Number of VCPUs");
        }

        if(builder.platformDetails != null)
            platformDetails = builder.platformDetails;

        if(builder.tenancy != null)
            tenancy = builder.tenancy;

        if(builder.availabilityZone != null)
            availabilityZone = builder.availabilityZone;

        if(builder.type != null)
            type = builder.type;

        if(builder.publicIpv4 !=null) {
            publicIpv4 = builder.publicIpv4;
        }
        if(builder.publicIpv4Dns !=null){
            publicIpv4Dns = builder.publicIpv4Dns;
        }
        if(builder.privateIpv6 !=null){
            privateIpv6 = builder.privateIpv6;
        }
        if(builder.privateIpv6Dns !=null){
            privateIpv6Dns = builder.privateIpv6Dns;
        }

    }
}
