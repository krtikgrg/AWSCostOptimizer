package com.example;

import software.amazon.awssdk.regions.Region;

import java.util.Date;

/**
 * Class to store information of the spot instance requests in form of objects
 */
class SpotRequestData {
    private Date requestValidFrom;
    private Date requestValidUntil;
    private Date requestCreateTime;

    private Date requestUpdateTime;
    private String requestId;
    private String requestState;
    private String requestType;
    private boolean isRelatedToInstance = false;
    private boolean isAssigned = false;

    private String associatedInstanceId = "";
    private String associatedInstanceType = "";
    private String associatedInstanceAvailabilityZone = "";
    private String associatedInstanceDescription = "";
    private Region region;

    /**
     * Builder class to help in creating the SpotRequestData class object. This builder class is the only way
     * to create an object of the parent class because the constructor of the parent class is made private.
     */
    public static class SpotRequestDataBuilder{
        private Date requestValidFrom;
        private Date requestValidUntil;
        private Date requestCreateTime;

        private Date requestUpdateTime;
        private String requestId;
        private String requestState;
        private String requestType;
        private boolean isRelatedToInstance = false;

        private String associatedInstanceId;
        private String associatedInstanceType;
        private String associatedInstanceAvailabilityZone;
        private String associatedInstanceDescription;

        /**
         * Constructor
         * @param requestId the id associated with the spot request (AWS assigns a unique identifier to each request made)
         * @param createTime the Date Object corresponding to the creation time of Spot Request
         */
        public SpotRequestDataBuilder(String requestId,Date createTime){
            this.requestId = requestId;
            this.requestCreateTime = createTime;
        }

        /**
         * Method to specify the state of the request, like whether the request is still active or not.
         * @param state the state in which the Spot request, in context, is
         * @return the builder object which can be further used to chain other methods and ultimately to build an object of the parent class
         */
        public SpotRequestDataBuilder withState(String state){
            this.requestState = state;
            return this;
        }

        /**
         * Method to specify the request Type like "one-time" or other possible values
         * @param type the type of spot request in context
         * @return the builder object which can be further used to chain other methods and ultimately to build an object of the parent class
         */
        public SpotRequestDataBuilder withRequestType(String type){
            this.requestType = type;
            return this;
        }

        /**
         * Function to provide the status code of the spot request. It can be in fulfilled state or in some other state. The status code is
         * fulfilled when AWS has allocated us some ec2 instance aligning with the request
         * @param code the status code of the request in context
         * @return the builder object which can be further used to chain other methods and ultimately to build an object of the parent class
         */
        public SpotRequestDataBuilder withStatusCode(String code){
            if(code.equals("fulfilled"))
                this.isRelatedToInstance = true;
            return this;
        }

        /**
         * Method to set the id of the instance associated with the current spot request (in case the request is fulfilled)
         * @param id the id of the associated instance
         * @return the builder object which can be further used to chain other methods and ultimately to build an object of the parent class
         */
        public SpotRequestDataBuilder withInstanceId(String id){
            this.associatedInstanceId = id;
            return this;
        }

        /**
         * Method to set the instance type of the requested instance in the Spot Request Made. For example t1 micro etc.
         * @param type The type of the associated instance
         * @return the builder object which can be further used to chain other methods and ultimately to build an object of the parent class
         */
        public SpotRequestDataBuilder withInstanceType(String type){
            this.associatedInstanceType = type;
            return this;
        }

        /**
         * Method to set the availability zone of the instance associated with the current spot request
         * @param zone the zone in which the instance (Associated one) is launched
         * @return the builder object which can be further used to chain other methods and ultimately to build an object of the parent class
         */
        public SpotRequestDataBuilder withInstanceAvailabilityZone(String zone){
            this.associatedInstanceAvailabilityZone = zone;
            return this;
        }

        /**
         * Method to set the description of the instance. Description as in Linux/Unix etc.
         * @param description the description of the associated instance
         * @return the builder object which can be further used to chain other methods and ultimately to build an object of the parent class
         */
        public SpotRequestDataBuilder withInstanceDescription(String description){
            this.associatedInstanceDescription = description;
            return this;
        }

        /**
         * Method to set the last update time for this request. It is the Date Object corresponding to the most recent time stamp
         * on which the status code of the request was updated
         * @param updateTime The Date object corresponding to the time stamp on which the request in context was updated
         * @return the builder object which can be further used to chain other methods and ultimately to build an object of the parent class
         */
        public SpotRequestDataBuilder withRequestUpdateTime(Date updateTime){
            this.requestUpdateTime = updateTime;
            return this;
        }

        /**
         * Method to set the Date Objects corresponding to the validity of the request that is the time stamp from
         * which the request became active and the time stamp on which the request will become inactive.
         * @param validFrom The Date Object corresponding the start time of the request (The time from which the request became active)
         * @param validUntil The Date Object corresponding to the end time of the request (The time after which the request will become inactive)
         * @return the builder object which can be further used to chain other methods and ultimately to build an object of the parent class
         */
        public SpotRequestDataBuilder withValidFromUntil(Date validFrom,Date validUntil){
            this.requestValidFrom = validFrom;
            this.requestValidUntil = validUntil;
            return this;
        }

        /**
         * Method to actually build the SpotRequestData object. Only making a call to this method will create an
         * object of the SpotRequestData class. This method will create an object of the parent class by making use
         * of the information provided to the builder object.
         * @return The object of the SpotRequestsData class
         * @throws Exception when builder object was unable to create an object of the parent class due to missing params
         */
        public SpotRequestData build() throws Exception {
            return new SpotRequestData(this);
        }
    }

    /**
     * Method to set the value of isAssigned variable. This variable denotes whether we have found an EC2 instance
     * in the all instances list which is associated with this request.
     * @param value value whether we have found it or not in the all instances list
     */
    public void setAssigned(boolean value){
        this.isAssigned = value;
    }

    /**
     * Method to check if this spot request is related to some EC2 instance or not.
     * @return returns true if it is related and return false if it is not
     */
    public boolean isRelatedToInstance(){
        return this.isRelatedToInstance;
    }

    /**
     * Method to return the value of isAssigned variable
     * @return return true if we have found the related instance in the all instances list else return false
     */
    public boolean isAssigned(){
        return this.isAssigned;
    }

    /**
     * Method to retrieve the instance id of the associated instance to the Spot request
     * This method should be called only after making a call to the isRelatedToInstance method, and
     * if and only if the returned value is true then only a call should be made to this method
     * @return the instance id of the instance associated with spot request in context
     * @throws Exception if a call is made to this function even though it was not associated to any instance
     */
    public String getAssociatedInstanceId() throws Exception {
        if(this.associatedInstanceId != null)
            return this.associatedInstanceId;
        else {
            throw new Exception("This Spot request is not yet associated to any instance");
        }
    }

    /**
     * Method to set the region of the spot request in context
     * @param region the Region class object for the region of the spot request in context
     */
    public void setRegion(Region region){
        this.region = region;
    }

    /**
     * Method to retrieve the region of the spot request
     * @return the region in string format of the spot request in context
     */
    public String getRegion(){
        return this.region.toString();
    }

    @Override
    public String toString() {
        return "SpotRequestData{" +
                "requestValidFrom=" + requestValidFrom +
                ", requestValidUntil=" + requestValidUntil +
                ", requestCreateTime=" + requestCreateTime +
                ", requestUpdateTime=" + requestUpdateTime +
                ", requestId='" + requestId + '\'' +
                ", requestState='" + requestState + '\'' +
                ", requestType='" + requestType + '\'' +
                ", isRelatedToInstance=" + isRelatedToInstance +
                ", isAssigned=" + isAssigned +
                ", associatedInstanceId='" + associatedInstanceId + '\'' +
                ", associatedInstanceType='" + associatedInstanceType + '\'' +
                ", associatedInstanceAvailabilityZone='" + associatedInstanceAvailabilityZone + '\'' +
                ", associatedInstanceDescription='" + associatedInstanceDescription + '\'' +
                '}';
    }

    /**
     * Constructor to construct the object of the SpotRequestsData class by using the builder object. The
     * constructor is made private, so we cannot create an object of this class directly. The only way is to
     * invoke the build method in the builder class object.
     * @param builder the object corresponding to the builder class from which we have to generate the SpotRequestsData class object
     * @throws Exception when the spot request is fulfilled, and we have not provided the following params
     *         instance id of the associated instance,
     *         instance type of the associated instance,
     *         availability zone of the associated instance,
     *         instance description of the associated instance
     */
    private SpotRequestData(SpotRequestDataBuilder builder) throws Exception {
        this.requestValidFrom = builder.requestValidFrom;
        this.requestValidUntil = builder.requestValidUntil;
        this.requestCreateTime = builder.requestCreateTime;
        this.requestUpdateTime = builder.requestUpdateTime;
        this.requestId = builder.requestId;
        this.requestState = builder.requestState;
        this.requestType = builder.requestType;
        this.isRelatedToInstance = builder.isRelatedToInstance;

        if(this.isRelatedToInstance){
            this.associatedInstanceId = builder.associatedInstanceId;
            this.associatedInstanceType = builder.associatedInstanceType;
            this.associatedInstanceAvailabilityZone = builder.associatedInstanceAvailabilityZone;
            this.associatedInstanceDescription = builder.associatedInstanceDescription;

            if(this.associatedInstanceId == null){
                throw new Exception("Instance id of the associated instance is a necessary field to provide when status code is fulfilled");
            }
            if(this.associatedInstanceType == null){
                throw new Exception("Instance type of the associated instance is necessary to provide when status code is fulfilled");
            }
            if(this.associatedInstanceAvailabilityZone == null){
                throw new Exception("Availability zone is a mandatory field when status code is fulfilled");
            }
            if(this.associatedInstanceDescription == null){
                throw new Exception("Providing the instance description is necessary if status code is fulfilled");
            }
        }
    }
}
