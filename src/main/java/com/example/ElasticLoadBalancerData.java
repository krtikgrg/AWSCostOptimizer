package com.example;

import software.amazon.awssdk.regions.Region;

import java.util.ArrayList;

/**
 * Class to store the information of load balancers in form objects
 */
class ElasticLoadBalancerData {
    private String name;
    private String arn;
    private String dnsName;
    private String ipAddressType;
    private String state;
    private String type;
    /*
    * The below 4 variables and the setter methods of this class are named for general metric, but here metric
    * will refer to the "RequestCount" if the load balancer type is "application" else it will be "ActiveFlowCount"
    * metric.
    */
    private ArrayList<StatisticRecord> averageMetricData = new ArrayList<>();
    private ArrayList<StatisticRecord> minimumMetricData = new ArrayList<>();
    private ArrayList<StatisticRecord> maximumMetricData = new ArrayList<>();
    private ArrayList<StatisticRecord> sumMetricData = new ArrayList<>();

    private Region region;

    /**
     * Builder class to help in creating objects of the parent elastic load balancer data class
     */
    public static class ElasticLoadBalancerDataBuilder{
        private String name;
        private String arn;
        private String state;
        private String dnsName;
        private String ipAddressType;
        private String type;

        /**
         * Constructor to create an object of the builder class
         * @param name the name of the load balancer in context
         * @param type the type of the load balancer in context (network, application or gateway)
         */
        public ElasticLoadBalancerDataBuilder(String name,String type){
            this.name = name;
            this.type = type;
        }

        /**
         * Method to specify the state of the load balancer
         * @param state the state of the load balancer in context
         * @return returns the builder object which can be used to chain the methods in a more readable format and to ultimately build the parent class object
         */
        public ElasticLoadBalancerDataBuilder withState(String state){
            this.state = state;
            return this;
        }

        /**
         * Method to specify the DNS name of the load balancer
         * @param dnsName the dns name of the load balancer in context
         * @return returns the builder object which can be used to chain the methods in a more readable format and to ultimately build the parent class object
         */
        public ElasticLoadBalancerDataBuilder withDnsName(String dnsName){
            this.dnsName = dnsName;
            return this;
        }

        /**
         * Method to specify the ARN of the load balancer
         * @param arn the arn of the load balancer in context
         * @return returns the builder object which can be used to chain the methods in a more readable format and to ultimately build the parent class object
         */
        public ElasticLoadBalancerDataBuilder withArn(String arn){
            this.arn = arn;
            return this;
        }

        /**
         * Method to specify the ipAddressType of the load balancer
         * @param ipAddressType the ip address type of the load balancer in context
         * @return returns the builder object which can be used to chain the methods in a more readable format and to ultimately build the parent class object
         */
        public ElasticLoadBalancerDataBuilder withIpAddressType(String ipAddressType){
            this.ipAddressType = ipAddressType;
            return this;
        }

        /**
         * method to actually build an object of load balancer data class
         * @return the load balancer data class object created from this builder
         * @throws Exception if builder object is unable to create a parent class object due to absence of some parameters
         */
        public ElasticLoadBalancerData build() throws Exception {
            return new ElasticLoadBalancerData(this);
        }
    }

    /**
     * Method to set the average metric data of the load balancer
     * @param array the array storing the timestamps and the corresponding values
     */
    public void setAverageMetricData(ArrayList<StatisticRecord> array){
        if(array!=null)
            this.averageMetricData.addAll(array);
    }

    /**
     * Method to set the maximum metric data of the load balancer
     * @param array the array storing the timestamps and the corresponding values
     */
    public void setMaximumMetricData(ArrayList<StatisticRecord> array){
        if(array!=null)
            this.maximumMetricData.addAll(array);
    }

    /**
     * Method to set the minimum metric data of the load balancer
     * @param array the array storing the timestamps and the corresponding values
     */
    public void setMinimumMetricData(ArrayList<StatisticRecord> array){
        if(array!=null)
            this.minimumMetricData.addAll(array);
    }

    /**
     * Method to set the Sum metric data of the load balancer
     * @param array the array storing the timestamps and the corresponding values
     */
    public void setSumMetricData(ArrayList<StatisticRecord> array){
        if(array!=null)
            this.sumMetricData.addAll(array);
    }

    /**
     * Method to get the type of the load balancer
     * @return the type of the load balancer in context
     */
    public String getType(){
        return this.type;
    }

    /**
     * Method to return the name of the load balancer
     * @return the name of the load balancer in context
     */
    public String getName(){
        return this.name;
    }

    /**
     * Method to check whether the load balancer in context is under utilized or not. It is decided based on the threshold passed as a parameter.
     * @param statistic the statistic which is to be used. it can be "average", "maximum", "minimum" or "sum"
     * @param threshold the threshold value, when the value of CPU utilization of the statistic in context drops below this threshold then we return true
     * @return returns true if any of the values fall below the threshold value else return false
     * @throws Exception is the statistic value is not recognised that is anything other than "average", "maximum", "minimum" or "sum" is specified
     */
    public boolean isUnderUtilized(String statistic,double threshold) throws Exception {
        switch (statistic) {
            case "average":
                return this.isUnderUtilized(this.averageMetricData, threshold);
            case "maximum":
                return this.isUnderUtilized(this.maximumMetricData, threshold);
            case "minimum":
                return this.isUnderUtilized(this.minimumMetricData, threshold);
            case "sum":
                return this.isUnderUtilized(this.sumMetricData, threshold);
            default:
                throw new Exception("Unrecognized statistic value, accepted values are 'average', 'minimum', 'maximum' and 'sum'");
        }
    }

    /**
     * Method to return the actual metric values
     * @param statistic the statistic to consider. Since this method will only be called after making a call to isUnderUtilized method
     *                  so assuming that the previous call to isUnderutilized was successfully executed. That is the statistic value
     *                  was correct. So assuming it will be correct in this call too. There not applying any additional checks
     * @return return the metric value, a single value is returned which is the maximum out of all the values present in the
     *                associated statistic array.
     */
    public double getMetricData(String statistic){
        switch (statistic) {
            case "average":
                return this.getMetricData(this.averageMetricData);
            case "maximum":
                return this.getMetricData(this.maximumMetricData);
            case "minimum":
                return this.getMetricData(this.minimumMetricData);
            default:
                return this.getMetricData(this.sumMetricData);
        }
    }

    /**
     * Method to set the region of a load balancer
     * @param region the Region class object corresponding to the region of the load balancer in context
     */
    public void setRegion(Region region){
        this.region = region;
    }

    /**
     * Method to retrieve the region of a load balancer
     * @return The region in string format of the load balancer in context
     */
    public String getRegion(){
        return this.region.toString();
    }

    @Override
    public String toString() {
        return "ElasticLoadBalancerData{" +
                "name='" + name + '\'' +
                ", arn='" + arn + '\'' +
                ", dnsName='" + dnsName + '\'' +
                ", ipAddressType='" + ipAddressType + '\'' +
                ", state='" + state + '\'' +
                ", type='" + type + '\'' +
                ", averageMetricData=" + averageMetricData +
                ", minimumMetricData=" + minimumMetricData +
                ", maximumMetricData=" + maximumMetricData +
                ", sumMetricData=" + sumMetricData +
                '}';
    }

    /**
     * method to check if the values present in the array passed as a parameter are below the threshold value or not. if they are below it then
     * this load balancer is under utilized else it is not
     * @param array the array to take values from
     * @param threshold the threshold value
     * @return returns true if the load balancer is underutilized else returns false
     */
    private boolean isUnderUtilized(ArrayList<StatisticRecord> array,double threshold){
        if(array.size() == 0)
            return false;
        for(StatisticRecord record:array){
            if(record.getValue()>threshold)
                return false;
        }
        return true;
    }

    /**
     * Method to return maximum metric value from the array passed as an argument
     * @param array the array from which we want the value
     * @return return the maximum value out of the values present in the array passed as a param
     */
    private double getMetricData(ArrayList<StatisticRecord> array){
        double maxi = 0;
        for(StatisticRecord record:array){
            maxi = Math.max(maxi,record.getValue());
        }
        return maxi;
    }

    /**
     * Parent class constructor to construct an object of the class using the builder class object
     * This constructor is made private so the only way to create an object of this
     * class is by using the builder class.
     * @param builder the builder class object from which we want to take the values to create an object of this class
     * @throws Exception if any of the parameter is not available from the builder object
     */
    private ElasticLoadBalancerData(ElasticLoadBalancerDataBuilder builder) throws Exception {
        this.name = builder.name;
        this.arn = builder.arn;
        this.state = builder.state;
        this.dnsName = builder.dnsName;
        this.ipAddressType = builder.ipAddressType;
        this.type = builder.type;

        if(this.name == null){
            throw new Exception("Load Balancer Name is a mandatory parameter");
        }
        if(this.arn == null){
            throw new Exception("Load Balancer ARN is a mandatory parameter");
        }
        if(this.state == null){
            throw new Exception("Load Balancer State is a mandatory parameter");
        }
        if(this.dnsName == null){
            throw new Exception("Load Balancer DNS Name is a mandatory parameter");
        }
        if(this.ipAddressType == null){
            throw new Exception("Load Balancer IP Address Type is a mandatory parameter");
        }
        if(this.type == null){
            throw new Exception("Load Balancer Type is a mandatory parameter");
        }
    }
}
