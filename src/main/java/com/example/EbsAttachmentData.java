package com.example;

import java.util.Date;

/**
 * Class to store data of EBS attachments, one EBS volume can be attached to multiple
 * instances if multi attach is enabled
 */
class EbsAttachmentData {
    private Date attachTime;
    private String device;
    private String instanceId;
    private String state;

    /**
     * Constructor
     * @param attachTime The Date object corresponding to the time instance when the volume in context was attached to an EC2 instance
     * @param device The device name that was given to the volume in context while attaching, for example /dev/sda
     * @param instanceId The id of the instance to which the volume in context was attached
     * @param state The state of this particular attachment (between the volume and the instance)
     * @throws Exception If state or instance id is not provided
     */
    public EbsAttachmentData(Date attachTime, String device, String instanceId, String state) throws Exception {
        this.attachTime = new Date(attachTime.getTime());
        this.device = device;
        this.instanceId = instanceId;
        this.state = state;
        if(this.instanceId == null)
            throw new Exception("Attached instance id is necessary to provide");
        if(this.state == null)
            throw new Exception("Attachment status is a must parameter and should be provided");
    }

    /**
     * Method to return the instance id of the attachment in context
     * @return the instance id
     */
    public String getInstanceId(){
        return this.instanceId;
    }

    @Override
    public String toString() {
        return "EbsAttachmentData{" +
                "attachTime=" + attachTime +
                ", device='" + device + '\'' +
                ", instanceId='" + instanceId + '\'' +
                ", state='" + state + '\'' +
                '}';
    }
}
