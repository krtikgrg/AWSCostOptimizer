package com.example;

import software.amazon.awssdk.regions.Region;


import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;

/**
 * Class to store the configuration information of a purchased reserved instance
 */
class ReservedInstanceData {
    private String reservedInstanceId;
    private String state;
    private String scope;
    private String productDescription;
    private String instanceType;
    private String availabilityZone;
    private String tenancy;
    private Integer instanceCount;
    private Long duration;
    private Date endTime;
    private Date startTime;
    private Integer found = 0;
    private Region region;

    /**
     * The builder class to help in creating objects of the parent class. The only way to create an object of the parent class ReservedInstanceData is by
     * making a builder class object and then calling the build function on it.
     */
    public static class ReservedInstanceDataBuilder{
        private String reservedInstanceId;
        private String state;
        private String scope;
        private String productDescription;
        private String instanceType;
        private String availabilityZone;
        private String tenancy;
        private Integer instanceCount;
        private Long duration;
        private Date endTime;
        private Date startTime;

        /**
         * Constructor
         * @param instanceId the instance id associated with the reserved instance. This id is different from the instance id of an ec2 instance.
         * @param state the state of the reserved instance purchase (whether it is still active or not)
         */
        public ReservedInstanceDataBuilder(String instanceId,String state){
            this.reservedInstanceId = instanceId;
            this.state = state;
        }

        /**
         * method to add the instance type and the count information for a reserved instance
         * @param instanceType the type of the reserved instance in context that is t1 micro or others
         * @param instanceCount the number of instances reserved with the configuration of the reserved instance in context
         * @return the builder object which can then again be chained to provide remaining information making it easier to understand and this object can then be
         *         ultimately used to build an object of the parent class
         */
        public ReservedInstanceDataBuilder withInstanceTypeAndCount(String instanceType,Integer instanceCount){
            this.instanceCount = instanceCount;
            this.instanceType = instanceType;
            return this;
        }

        /**
         * Method to add the duration of the validity of the purchased reserved instance
         * @param duration the number of seconds for which the reserved instance is valid
         * @param start the Date object corresponding to the time instant of buying that object
         * @param end the Date object corresponding to the time instance on which our instance is expiring
         * @return the builder object which can then again be chained to provide remaining information making it easier to understand and this object can then be
         *         ultimately used to build an object of the parent class
         */
        public ReservedInstanceDataBuilder withDurationInfo(Long duration, Date start, Date end){
            this.duration = duration;
            this.endTime = end;
            this.startTime = start;
            return this;
        }

        /**
         * Method to provide the scope of the reserved instance
         * @param scope teh scope of the concerned reserved instance
         * @return the builder object which can then again be chained to provide remaining information making it easier to understand and this object can then be
         *         ultimately used to build an object of the parent class
         */
        public ReservedInstanceDataBuilder withScope(String scope){
            this.scope = scope;
            return this;
        }

        /**
         * Method to provide the product description like Linux/Unix etc.
         * @param description the description of the reserved instance in context
         * @return the builder object which can then again be chained to provide remaining information making it easier to understand and this object can then be
         *         ultimately used to build an object of the parent class
         */
        public ReservedInstanceDataBuilder withProductDescription(String description){
            this.productDescription = description;
            return this;
        }

        /**
         * method for providing the availability zone of the reserved instance
         * @param zone the availability zone of the reserved instance in context
         * @return the builder object which can then again be chained to provide remaining information making it easier to understand and this object can then be
         *         ultimately used to build an object of the parent class
         */
        public ReservedInstanceDataBuilder withAvailabilityZone(String zone){
            this.availabilityZone = zone;
            return this;
        }

        /**
         * Method to provide the tenancy of the reserved instance
         * @param tenancy the tenancy of the reserved instance in context
         * @return the builder object which can then again be chained to provide remaining information making it easier to understand and this object can then be
         *         ultimately used to build an object of the parent class
         */
        public ReservedInstanceDataBuilder withTenancy(String tenancy){
            this.tenancy = tenancy;
            return this;
        }

        /**
         * Build method to actually create the reserved instance data object from the builder object. This is the only way
         * to create an object of the parent class.
         * @return it returns the object of the parent class that is the ReservedInstanceData class.
         * @throws Exception if the builder object lacked some required params
         */
        public ReservedInstanceData build() throws Exception {
            return new ReservedInstanceData(this);
        }
    }

    /**
     * Check whether the reserved instance is still active or it has not expired yet by comparing the end time with current instant
     * @return true if current instant is before the end time instant and return false in the other case
     */
    public boolean isActive(){
        return Instant.now().isBefore(this.endTime.toInstant());
    }

    /**
     * Method to check if the reserved instance is expiring in next N days
     * @param days the value of N in above description
     * @return true if the reserved instance in context is going to expire in next N days and returns false in the otherwise case
     */
    public boolean isExpiringInNextNDays(int days){
        return Instant.ofEpochSecond(this.endTime.toInstant().getEpochSecond()-days*24L*3600L).isBefore(Instant.now());
    }

    /**
     * Method to return the Date object of the time at which a reserved instance purchase is expiring
     * @return the date object of the expiration time of the reserved instance in context
     */
    public Date getEndTime(){
        return this.endTime;
    }

    /**
     * method to check if the details of the ec2 instance (passed as a param) match with the details
     * of reserved instance in context.
     * @param instance the ec2 instance whose configuration we want to match
     * @return return true if the
     *         platform details/instance description,
     *         the instance type,
     *         the availability zone and
     *         the tenancy
     *         matches for the ec2 instance passed as a param to that of the current reserved instance
     */
    public boolean matchInstance(Ec2InstanceData instance){
        if(this.productDescription.equals(instance.getPlatformDetails())){
            if(this.instanceType.equals(instance.getType())){
                if(this.availabilityZone.equals(instance.getAvailabilityZone())){
                    return this.tenancy.equals(instance.getTenancy());
                }
            }
        }
        return false;
    }

    /**
     * Check if we still have instances remaining that are yet to be found of the same configuration
     * as that of the reserved instance in context
     * @return true if the number of found ec2 instances which match the configuration is less than the capacity we bought else returns false
     */
    public boolean isRemaining(){
        return this.found < this.instanceCount;
    }

    /**
     * If we find an ec2 instance which has the exact same configuration to that of the reserved instance in context
     * then we increment a counter variable. This counter variable keeps track of the number of the instances found.
     * Which further helps us in assuring that we are maintaining an upper bound of the capacity we bought.
     */
    public void foundOne(){
        this.found = this.found + 1;
    }

    /**
     * Method to add the details of the configuration of a reserved instance into hashmap for faster matching
     * @param matcher the hashmap into which we want to insert
     * @param index the index at which the reserved instance in context will occur in reservedInstanceData arraylist
     */
    public void insertIntoHashmap(HashMap<String, HashMap<String, HashMap<String, HashMap<String, ArrayList<Integer>>>>> matcher, int index){
        /*
        * Order of Insertion
        * Availability Zone
        * Tenancy
        * Instance Type
        * Product Description*/
        HashMap<String, HashMap<String, HashMap<String,ArrayList<Integer>>>> firstLevel = new HashMap<>();
        HashMap<String, HashMap<String,ArrayList<Integer>>> secondLevel = new HashMap<>();
        HashMap<String,ArrayList<Integer>> thirdLevel = new HashMap<>();
        ArrayList<Integer> fourthLevel = new ArrayList<>();
        if(!matcher.containsKey(this.availabilityZone))
            matcher.put(this.availabilityZone,firstLevel);
        firstLevel = matcher.get(this.availabilityZone);

        if(!firstLevel.containsKey(this.tenancy))
            firstLevel.put(this.tenancy,secondLevel);
        secondLevel = firstLevel.get(this.tenancy);

        if(!secondLevel.containsKey(this.instanceType))
            secondLevel.put(this.instanceType,thirdLevel);
        thirdLevel = secondLevel.get(this.instanceType);

        if(!thirdLevel.containsKey(this.productDescription))
            thirdLevel.put(this.productDescription,fourthLevel);
        fourthLevel = thirdLevel.get(this.productDescription);
        fourthLevel.add(index);
    }

    /**
     * Method to check if the reserved instance is underutilized in terms of capacity usage based
     * on a threshold.
     * @param thresholdInPercent the threshold based on what we must decide if the reserved instance in context is underutilized or not
     * @return returns true if the instance is underutilized and returns false otherwise
     */
    public boolean isUnderUtilized(double thresholdInPercent){
        double currentUsage = (((double)this.found)/((double)this.instanceCount))*100.0;
        if(currentUsage > thresholdInPercent)
            return false;
        return true;
    }

    /**
     * Method to get the reserved instance id
     * @return the id of the reserved instance in context
     */
    public String getReservedInstanceId(){
        return this.reservedInstanceId;
    }

    /**
     * Method to retrieve the reserved instance type
     * @return the type of the reserved instance in context
     */
    public String getInstanceType(){
        return this.instanceType;
    }

    /**
     * Method to return the Number of instances that actually exist which correspond to the reserved instance in context
     * @return Number of existing instances (EC2) which match the configuration of the reserved instance in context
     */
    public Integer getCapacityInUse(){
        return this.found;
    }

    /**
     * Method to return the Total Number of instances that were purchased with a given configuration
     * @return the number of instances purchased with the configuration of the reserved instance in context
     */
    public Integer getInstanceCount(){
        return this.instanceCount;
    }

    /**
     * Method to set the region of a reserved instance
     * @param region The Region class object of the region of the reserved instance in context
     */
    public void setRegion(Region region){
        this.region = region;
    }

    /**
     * Method to retrieve the region of a reserved instance
     * @return The region in the String format of reserved instance in context
     */
    public String getRegion(){
        return this.region.toString();
    }

    @Override
    public String toString() {
        return "ReservedInstanceData{" +
                "reservedInstanceId='" + reservedInstanceId + '\'' +
                ", state='" + state + '\'' +
                ", scope='" + scope + '\'' +
                ", productDescription='" + productDescription + '\'' +
                ", instanceType='" + instanceType + '\'' +
                ", availabilityZone='" + availabilityZone + '\'' +
                ", tenancy='" + tenancy + '\'' +
                ", instanceCount=" + instanceCount +
                ", duration=" + duration +
                ", endTime=" + endTime +
                ", startTime=" + startTime +
                ", found=" + found +
                '}';
    }

    /**
     * The actual class constructor which makes use of the builder class object to create an object of the ReservedInstanceData class.
     * This constructor is made private, so it can only be invoked by using the build method of the builder class
     * @param builder the builder class object
     * @throws Exception if any parameter other than scope is left out unspecified
     */
    private ReservedInstanceData(ReservedInstanceDataBuilder builder) throws Exception {
        this.reservedInstanceId = builder.reservedInstanceId;
        this.startTime = builder.startTime;
        this.endTime = builder.endTime;
        this.duration = builder.duration;
        this.instanceCount = builder.instanceCount;
        this.availabilityZone = builder.availabilityZone;
        this.instanceType = builder.instanceType;
        this.productDescription = builder.productDescription;
        this.scope = builder.scope;
        this.state = builder.state;
        this.tenancy = builder.tenancy;
        if(this.reservedInstanceId == null){
            throw new Exception("Reserved Instance id must be provided");
        }
        if(this.startTime == null){
            throw new Exception("Start time of the reserved instance must be provided");
        }
        if(this.endTime == null){
            throw new Exception("Ent time of the reserved instance must be provided");
        }
        if (this.duration == null){
            throw new Exception("Duration information of the reserved instance must be provided");
        }
        if(this.instanceCount == null || this.instanceCount <= 0){
            throw new Exception("Either instance count value is not provided or the value is unacceptable");
        }
        if(this.availabilityZone == null){
            throw new Exception("Availability Zone of the reserved instance must be provided");
        }
        if(this.instanceType == null){
            throw new Exception("Instance Type of the reserved instance is a mandatory field");
        }
        if(this.productDescription == null){
            throw new Exception("Product Description of the reserved instance is a mandatory field");
        }
        if(this.state == null){
            throw new Exception("State of the reserved instance is necessary to provide");
        }
        if(this.tenancy == null){
            throw new Exception("Tenancy of the reserved instance must be provided");
        }
    }
}
