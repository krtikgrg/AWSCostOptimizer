package com.example;

import software.amazon.awssdk.regions.Region;

/**
 * Class to store information of the Elastic Ips as Objects
 */
class ElasticIpData {
    private String instanceId = "";
    private String publicIp;
    private String associationId = "";
    private String privateIp = "";
    private String publicIpv4Pool;
    private String allocationId;
    private boolean isAttached = false;
    private Region region;

    /**
     * Builder class to help in creating the objects of the parent class. There is no other way to create the parent
     * class objects as the parent class's constructor is made private.
     */
    public static class ElasticIpDataBuilder{
        private String instanceId;
        private String publicIp;
        private String associationId;
        private String privateIp;
        private String publicIpv4Pool;
        private String allocationId;
        private boolean isAttached = false;

        /**
         * Constructor to create an object of the builder class
         * @param publicIp the public Ip Address value of the elastic Ip in context
         * @param allocationId the allocation id of this particular elastic Ip. When an Ip address is allocated to a user then that ip address is given an allocation id.
         * @param publicIpv4Pool The pool of the elastic ip in context
         * @throws Exception if any of the function params are passed as a null value
         */
        public ElasticIpDataBuilder(String publicIp,String allocationId,String publicIpv4Pool) throws Exception {
            this.publicIp = publicIp;
            this.allocationId = allocationId;
            this.publicIpv4Pool = publicIpv4Pool;
            if(this.publicIp == null)
                throw new Exception("Public ip value can not null");
            else if (this.allocationId == null) {
                throw new Exception("Allocation ID of the Elastic Ip is a mandatory field");
            } else if (this.publicIpv4Pool == null) {
                throw new Exception("Public ipv4 pool is a mandatory field and should be provided");
            }
        }

        /**
         * Method to provide the association id. This id indicates that this elastic ip is associated to some resource in amazon cloud
         * @param associationId the association id value of the elastic ip in context
         * @return returns the builder object which can be further used to chain methods to provide necessary information and to ultimately build the ElasticIpData Object
         */
        public ElasticIpDataBuilder withAssociationId(String associationId){
            if(associationId == null)
                return this;
            this.isAttached = true;
            this.associationId = associationId;
            return this;
        }

        /**
         * Method to Provide the instance id of the instance associated to the elastic ip
         * @param instanceId instance is of the ec2 instance associated to the elastic ip in context
         * @return returns the builder object which can be further used to chain methods to provide necessary information and to ultimately build the ElasticIpData Object
         */
        public ElasticIpDataBuilder withInstanceId(String instanceId){
            this.instanceId = instanceId;
            return this;
        }


        /**
         * Method to specify the private ip of the elastic ip
         * @param privateIp the private ip of the elastic ip in context
         * @return returns the builder object which can be further used to chain methods to provide necessary information and to ultimately build the ElasticIpData Object
         */
        public ElasticIpDataBuilder withPrivateIp(String privateIp){
            this.privateIp = privateIp;
            return this;
        }

        /**
         * method to actually build the object of the parent class from this builder class object
         * @return the actual class object
         */
        public ElasticIpData build() {
            return new ElasticIpData(this);
        }
    }

    /**
     * method to return if an elastic ip is being used anywhere or not
     * @return return true if the ip address is associated to something else return false
     */
    public boolean isUsed(){
        return this.isAttached;
    }

    /**
     * Method to return the public ip of an elastic ip
     * @return returns the public ip of the elastic ip in context
     */
    public String getIp(){
        return this.publicIp;
    }

    /**
     * Method to set the region of an elastic ip
     * @param region the Region class object of the region of the elastic ip in context
     */
    public void setRegion(Region region){
        this.region = region;
    }

    /**
     * Method to retrieve the region of the elastic ip
     * @return the region in string format of the elastic ip in context
     */
    public String getRegion(){
        return this.region.toString();
    }

    @Override
    public String toString() {
        return "ElasticIpData{" +
                "instanceId='" + instanceId + '\'' +
                ", publicIp='" + publicIp + '\'' +
                ", associationId='" + associationId + '\'' +
                ", privateIp='" + privateIp + '\'' +
                ", publicIpv4Pool='" + publicIpv4Pool + '\'' +
                ", allocationId='" + allocationId + '\'' +
                ", isAttached=" + isAttached +
                '}';
    }

    /**
     * Constructor to create the ElasticIpData class object by using the builder method. This is private method so
     * the only way to create an object this class is to use the builder class
     * @param builder the builder class object from which we want to create the object of this class
     */
    private ElasticIpData(ElasticIpDataBuilder builder){
        this.publicIp = builder.publicIp;
        this.publicIpv4Pool = builder.publicIpv4Pool;
        this.allocationId = builder.allocationId;

        if(builder.isAttached){
            this.associationId = builder.associationId;
            this.isAttached = true;
        }
        if(builder.instanceId != null){
            this.instanceId = builder.instanceId;
        }
        if(builder.privateIp != null){
            this.privateIp = builder.privateIp;
        }
    }

}
