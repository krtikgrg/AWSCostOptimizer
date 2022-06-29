package com.example;

import com.amazonaws.services.ec2.model.VolumeAttachment;
import software.amazon.awssdk.regions.Region;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Class to store data of EBS Volumes as objects
 */
class EbsVolumeData implements Comparable<EbsVolumeData>{
    private String availabilityZone = "-";
    private String snapshotId = "-";
    private ArrayList<EbsAttachmentData> attachments = new ArrayList<>();
    private ArrayList<StatisticRecord> sumReadOps = new ArrayList<>();
    private ArrayList<StatisticRecord> sumWriteOps = new ArrayList<>();
    private boolean multiAttach = false;

    private Date createTime;
    private Integer size ; // in GBs
    private String volumeId;
    private String state;

    private Region region;

    private int statHours;

    private Integer iops;

    /**
     * Builder class to help in creating objects of parent class. This helps in making the code more readable.
     */
    public static class EbsVolumeDataBuilder{
        private Integer iops;
        private String availabilityZone = "-";
        private String snapshotId = "-";
        private boolean multiAttach = false;

        private Date createTime;
        private Integer size ; // in GBs
        private String volumeId;
        private String state;

        /**
         * Constructor
         * @param volumeId the id of the volume in context
         * @param state the state of the volume in context
         * @param createTime the creation time of the volume in context
         * @param size the size of the volume in context (in GBs)
         * @throws Exception if any of the parameters have null value
         */
        public EbsVolumeDataBuilder(String volumeId, String state, Date createTime, Integer size) throws Exception {
            this.volumeId = volumeId;
            this.size = size;
            this.state = state;
            this.createTime = new Date(createTime.getTime());
            if(this.volumeId == null)
                throw new Exception("Volume ID is a necessary parameter and it should not be null");
            if(this.state == null)
                throw new Exception("State of the Volume is a necessary parameter and it should not be null");
            if(this.createTime == null)
                throw new Exception("Creation Time of the snapshot should be provided as it is a necessary parameter");
            if(this.size == null || this.size <= 0)
                throw new Exception("Invalid value of size parameter");
        }

        /**
         * Method to provide the availability zone of the volume
         * @param zone the zone of the volume in context
         * @return builder object to help in chaining of methods
         */
        public EbsVolumeDataBuilder withAvailabilityZone(String zone){
            this.availabilityZone = zone;
            return this;
        }

        /**
         * Method to provide snapshot id of the volume
         * @param id the snapshot id of the volume in context
         * @return builder object to help in chaining of methods
         */
        public EbsVolumeDataBuilder withSnapshotId(String id){
            this.snapshotId = id;
            return this;
        }

        /**
         * Method to specify the iops value (Provisioned)
         * @param value the value if IO Operations per second provisioned for the type in context
         * @return the builder object which is to be used in further provisioning
         */
        public EbsVolumeDataBuilder withIops(Integer value){
            this.iops = value;
            return this;
        }

        /**
         * Method to provide whether the volume's multi attach is enabled or not
         * @param status the status value, if true then multi attach is enabled and vice versa
         * @return the builder object to help in chaining of methods
         */
        public EbsVolumeDataBuilder withMultiAttachStatus(boolean status){
            this.multiAttach = status;
            return this;
        }

        /**
         * Method to build the EbsVolumeDate class object from its builder. Using this builder
         * class and this build method is the only way to create an object of EbsVolumeData class
         * as the constructor of this class is made private.
         * @return returns the actual object made from the builder
         */
        public EbsVolumeData build(){
            return new EbsVolumeData(this);
        }
    }

    /**
     * Method to store the data of the attachments in EbsVolumeData object
     * @param attachmentList the list of the attachments as given by ec2
     */
    public void setAttachments(List<VolumeAttachment> attachmentList){
        for(VolumeAttachment attachment:attachmentList){
            try {
                this.attachments.add(new EbsAttachmentData(attachment.getAttachTime(), attachment.getDevice(), attachment.getInstanceId(), attachment.getState()));
            } catch (Exception e){
                e.printStackTrace();
            }
        }
    }

    /**
     * Method to help in sorting the EBS Volumes based on their size values
     * @param volume the object to be compared.
     * @return returns a positive value if volume in context has higher size than the volume to be compared
     *         and returns a negative value in the other case.
     */
    public int compareTo(EbsVolumeData volume){
        return this.getSize()-volume.getSize();
    }

    /**
     * Method to return the size(in GBs) of an EBS Volume
     * @return the size of the ebs Volume in context
     */
    public Integer getSize(){
        return this.size;
    }

    /**
     * Method to set read ops array from cloudwatch (sum statistic)
     * @param array the array of StatisticRecord objects having time stamps bound to their corresponding values
     */
    public void setSumReadOps(ArrayList<StatisticRecord> array){
        if(array != null)
            this.sumReadOps.addAll(array);
    }

    /**
     * Method to set write ops array from cloudwatch (sum statistic)
     * @param array the array of StatisticRecord objects having time stamps bound to their corresponding values
     */
    public void setSumWriteOps(ArrayList<StatisticRecord> array){
        if(array != null)
            this.sumWriteOps.addAll(array);
    }

    /**
     * Method to set the value of Stat hours, which is the number of hours over which we have collected statistics
     * @param val the value of stat hours
     */
    public void setStatHours(int val){
        this.statHours = val;
    }

    /**
     * Method to set the region of an EBS Volume
     * @param region the Region class object corresponding to the region of the EBS Volume in context
     */
    public void setRegion(Region region){
        this.region = region;
    }

    /**
     * Method to retrieve the region of an EBS Volume
     * @return the region in string form of the region of the EBS Volume in Context
     */
    public String getRegion(){
        return this.region.toString();
    }

    /**
     * Method to check if the volume is in "in-use" state or not
     * @return return true if the volume is in "in-use" state and returns false if it is not in "in-use" state
     */
    public boolean isUsed(){
        if(this.state.equals("in-use"))
            return true;
        return false;
    }

    /**
     * Method to check if current volume is critical or not, critical in the sense
     * if the volume is not optimally used. When any of the read ops/sec and write ops/sec is
     * below the threshold value (respective) then this function returns true.
     * @param readThreshold the threshold value for read ops (ops/sec)
     * @param writeThreshold the threshold value for write ops (ops/sec)
     * @return returns true if any of the read and write ops/sec is below the threshold value (respective) else returns false
     */
    public boolean isCritical(double readThreshold, double writeThreshold){
        boolean isCritical = false;
        for(StatisticRecord record:this.sumReadOps){
            double curVal = record.getValue()/(this.statHours*3600);
            if(curVal>readThreshold)
                isCritical = true;
        }
        if(this.sumReadOps.size()!=0) {
            if (Boolean.compare(isCritical, false) == 0)
                return true;
        }

        for(StatisticRecord record:this.sumWriteOps){
            double curVal = record.getValue()/(this.statHours*3600);
            if(curVal>writeThreshold)
                return false;
        }
        return this.sumWriteOps.size() != 0;
    }

    /**
     * Method to return the max of all read Ops/sec for all the data points collected from cloudwatch
     * @return the max of all read Ops/sec
     */
    public Double getReadOps(){
        double maxi = 0;
        for(StatisticRecord record:this.sumReadOps){
            double curVal = record.getValue()/(this.statHours*3600);
            if(curVal > maxi)
                maxi = curVal;
        }
        return maxi;
    }

    /**
     * Method to return the max of all write Ops/sec for all the data points collected from cloudwatch
     * @return the max of all write Ops/sec
     */
    public Double getWriteOps(){
        double maxi = 0;
        for(StatisticRecord record:this.sumWriteOps){
            double curVal = record.getValue()/(this.statHours*3600);
            if(curVal > maxi)
                maxi = curVal;
        }
        return maxi;
    }

    /**
     * Method to return an array list of EBS volumes which are either in "in-use" state or not in "in-use" state
     * @param volumes the array list of the volumes from which we have to select the relevant volumes
     * @param useStatus if true then we want the volumes in "in-use" status else we want the volumes not in "in-use" state
     * @return the volumes which are not in "in-use" state if useStatus is false else we return the volumes in "in-use" state
     */
    public static ArrayList<EbsVolumeData> getVolumes(ArrayList<EbsVolumeData> volumes,boolean useStatus){
        ArrayList<EbsVolumeData> toBeReturned = new ArrayList<>();
        for(EbsVolumeData volume : volumes){
            if(Boolean.compare(volume.isUsed(),useStatus) == 0)
                toBeReturned.add(volume);
        }
        return toBeReturned;
    }

    /**
     * Method to get the volume id
     * @return the id of the volume in context
     */
    public String getVolumeId(){
        return this.volumeId;
    }

    /**
     * method to get the instance id with which the EBS Volume is associated
     * Instance id is only returned when the volume in context is attached to only one instance
     * @return instance id when only one instance is associated, else return "-"
     * @throws Exception When requested for instance id of a volume which is not in "in-use" state
     */
    public String getInstanceId() throws Exception {
        if(!this.state.equals("in-use"))
            throw new Exception("Instance Id requested for a volume not in \"in-use\"state");
        if(this.attachments.size() > 1)
            return "-";
        return this.attachments.get(0).getInstanceId();
    }

    /**
     * Method to return the iops value
     * @return the iops value for the volume in context
     */
    public Integer getIops(){
        return this.iops;
    }
    @Override
    public String toString() {
        return "EbsVolumeData{" +
                "availabilityZone='" + availabilityZone + '\'' +
                ", snapshotId='" + snapshotId + '\'' +
                ", attachments=" + attachments +
                ", sumReadOps=" + sumReadOps +
                ", sumWriteOps=" + sumWriteOps +
                ", multiAttach=" + multiAttach +
                ", createTime=" + createTime +
                ", size=" + size +
                ", volumeId='" + volumeId + '\'' +
                ", state='" + state + '\'' +
                '}';
    }

    /**
     * Constructor
     * @param builder builder object from which we have to create the object
     */
    private EbsVolumeData(EbsVolumeDataBuilder builder){
        this.availabilityZone = builder.availabilityZone;
        this.snapshotId = builder.snapshotId;
        this.multiAttach = builder.multiAttach;
        this.createTime = builder.createTime;
        this.size = builder.size;
        this.volumeId = builder.volumeId;
        this.state = builder.state;
        this.iops = builder.iops;
    }
}
