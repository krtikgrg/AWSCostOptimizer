package com.example;

import software.amazon.awssdk.regions.Region;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;

/**
 * Class to store the information of Recovery Points aka Backups aka Snapshots.
 */
class BackupData implements Comparable<BackupData>{
    private BackupVaultData vault; // Object of the backup vault in which the current backup lies
    private Date creationDate;
    private Date completionDate;
    private Date lastRestoreTime;
    private String arn;
    private String resourceType;
    private String status;
    private Date moveToColdStorageAt;
    private Date deleteAt;
    private Long size;
    private Long deleteAfterDays;
    private Long moveToColdStorageAfterDays;
    private Region region;

    /**
     * Public Builder class to create the BackupData Object. The Constructor of the BackupData Class
     * is made private so the only method to create its objects is using this builder class.
     */
    public static class BackupDataBuilder{
        private BackupVaultData vault;
        private Date creationDate;
        private Date completionDate;
        private Date lastRestoreTime;
        private String arn;
        private String resourceType;
        private String status;
        private Date moveToColdStorageAt;
        private Date deleteAt;
        private Long size;
        private Long deleteAfterDays;
        private Long moveToColdStorageAfterDays;

        /**
         * Default Constructor for the builder class
         * @param vault the BackupVaultData Object which stores the information of the related vault in which this current backup is stored
         * @param arn The arn value of the recovery point in context
         * @throws Exception if any of the constructor params is null
         */
        public BackupDataBuilder(BackupVaultData vault,String arn) throws Exception {
            this.vault = vault;
            this.arn = arn;
            if(this.vault == null)
                throw new Exception("Provide a valid object of BackupVaultData Class");
            if(this.arn == null)
                throw new Exception("ARN can not be null");
        }

        /**
         * Method to set the creation date corresponding to the current recovery point. It represents the date on which the current backup was created.
         * @param date The Date Object corresponding to the date of creation of the backup.
         * @return It returns the builder object which can be used to chain more methods to set other parameter values and ultimately for building the actual object
         */
        public BackupDataBuilder withCreationDate(Date date){
            this.creationDate = date;
            return this;
        }

        /**
         * Method to set the completion date corresponding to the current recovery point. It represents the date on which the current backup was completed.
         * @param date The Date Object corresponding to the date of completion of the backup.
         * @return It returns the builder object which can be used to chain more methods to set other parameter values and ultimately for building the actual object
         */
        public BackupDataBuilder withCompletionDate(Date date){
            this.completionDate = date;
            return this;
        }

        /**
         * Method to set the size of the backup in context
         * @param size The size of the Backup in Bytes
         * @return It returns the builder object which can be used to chain more methods to set other parameter values and ultimately for building the actual object
         */
        public BackupDataBuilder withBackupSize(Long size){
            this.size = size;
            return this;
        }

        /**
         * Method to set the status of the backup in context
         * @param status It indicates the backup status. For example whether the backup generation is completed or if the backup has expired etc.
         * @return It returns the builder object which can be used to chain more methods to set other parameter values and ultimately for building the actual object
         */
        public BackupDataBuilder withStatus(String status){
            this.status = status;
            return this;
        }

        /**
         * Method to set the resource type. Resource type in the sense that of which resource the backup is. For example
         * whether the backup is of an EC2 Instance or S3 bucket or of any other resource
         * @param type The Resource Type of which the current recovery point is
         * @return It returns the builder object which can be used to chain more methods to set other parameter values and ultimately for building the actual object
         */
        public BackupDataBuilder withResourceType(String type){
            this.resourceType = type;
            return this;
        }

        /**
         * Method to set the last restore time of the Backup Data object. The last restore time is the time stamp on which
         * this recovery point was used to restore the actual resource.
         * @param date The Date Object related to the Last Restore Time of the current recovery point
         * @return It returns the builder object which can be used to chain more methods to set other parameter values and ultimately for building the actual object
         */
        public BackupDataBuilder withLastRestoreTime(Date date){
            this.lastRestoreTime = date;
            return this;
        }

        /**
         * Method to store the information about when the recovery point should be moved to cold storage.
         * @param date The Date Object representing the time stamp on which the current backup/recovery point should be moved to cold storage
         * @param days The number of days after which we should move the current recovery point to the cold storage
         * @return It returns the builder object which can be used to chain more methods to set other parameter values and ultimately for building the actual object
         */
        public BackupDataBuilder withMoveToColdStorageInfo(Date date, Long days){
            this.moveToColdStorageAt = date;
            this.moveToColdStorageAfterDays = days;
            return this;
        }

        /**
         * Method to store the information about when to delete the recovery point
         * @param date The Date object representing the time stamp on which the current recovery point should be deleted
         * @param days The number of days after which the current recovery point should be deleted
         * @return It returns the builder object which can be used to chain more methods to set other parameter values and ultimately for building the actual object
         */
        public BackupDataBuilder withDeleteInfo(Date date, Long days){
            this.deleteAt = date;
            this.deleteAfterDays = days;
            return this;
        }

        /**
         * Method which actually generated the BackupData Object from this builder class object
         * @return The BackupData object for the current recovery point/ backup/ snapshot
         * @throws Exception if some relevant parameters are null
         */
        public BackupData build() throws Exception {
            return new BackupData(this);
        }
    }

    @Override
    public String toString() {
        return "BackupData{" +
                "vault=" + vault +
                ", creationDate=" + creationDate +
                ", completionDate=" + completionDate +
                ", lastRestoreTime=" + lastRestoreTime +
                ", arn='" + arn + '\'' +
                ", resourceType='" + resourceType + '\'' +
                ", status='" + status + '\'' +
                ", moveToColdStorageAt=" + moveToColdStorageAt +
                ", deleteAt=" + deleteAt +
                ", size=" + size +
                ", deleteAfterDays=" + deleteAfterDays +
                ", moveToColdStorageAfterDays=" + moveToColdStorageAfterDays +
                '}';
    }

    /**
     * compareTo method from Comparable interface. Here we use it to sort the BackupData object based on their size
     * @param backup the object to be compared.
     * @return It returns a positive integer value if current backup's size is greater than the object to be compared else it returns a negative value
     */
    public int compareTo(BackupData backup){
        if(this.getSize() > backup.getSize())
            return 1;
        else
            return -1;
    }

    /**
     * Method to get the ARN value of this BackupData Object
     * @return the arn string object of this BackupData Object
     */
    public String getArn(){
        return this.arn;
    }

    /**
     * Method to return the size of the BackupData Object
     * @return The size of this Object (In Bytes)
     */
    public Long getSize(){
        return this.size;
    }

    /**
     * Overloaded getSize method. This method is called if we want to divide the size with a factor. Let's say we want size in kb then we have to divide with 1024
     * In a similar way we can pass on a factor by which we should divide the size and get the appropriate value.
     * @param factor The factor by which we should divide the size value
     * @return Returns the appropriately scaled value
     * @throws Exception if factor value is 0
     */
    public Long getSize(Long factor) throws Exception {
        if(factor != 0)
            return this.size/factor;
        else
            throw new Exception("Division from zero");
    }

    /**
     * Method to return the associated BackupVaultData object
     * @return the BackupVaultData object
     */
    public BackupVaultData getVault(){
        return this.vault; //Can be modified later so defensive copy can be created
    }

    /**
     * Method to retrieve the Creation Date of the BackupData Object
     * @return The Date object corresponding to the creation date
     */
    public Date getCreationDate(){
        return new Date(this.creationDate.getTime());
    }

    /**
     * Method to retrieve the Restore Date of the BackupData Object
     * @return The Date object corresponding to the Restore Date
     */
    public Date getRestoreDate(){
        if(this.lastRestoreTime != null)
            return new Date(this.lastRestoreTime.getTime());
        return null;
    }

    /**
     * Method to retrieve the Completion Date of the BackupData Object
     * @return The Date object corresponding to the Completion Date
     */
    public Date getCompletionDate(){
        return new Date(this.completionDate.getTime());
    }

    /**
     * Method to set the region associated with the Backup Object
     * @param region Region class object of the region in which the current backup in context is located
     */
    public void setRegion(Region region){
        this.region = region;
    }

    /**
     * Method to retrieve the region associated with a backup object
     * @return the string form of the region associated with the backup in context
     */
    public String getRegion(){
        return this.region.toString();
    }

    /**
     * Method to return all the backups which were completed before the number of days specified
     * @param days the number days. We want all the backups which were completed before these many days.
     * @param backups the array of all the backups from which we have to select the ones which were completed before the number of days specified
     * @return the array of the backups which were completed before the number of days specified (Modifying backups in the original backups array will also modify this resultant array)
     */
    public static ArrayList<BackupData> getBackupsOlderThan(int days,ArrayList<BackupData> backups){
        ArrayList<BackupData> array = new ArrayList<>();

        if(backups != null) {
            Instant curInstant = Instant.ofEpochSecond(Instant.now().getEpochSecond() - (long) days * 24 * 3600);
            for (BackupData backup : backups) {
                if (backup.getCreationDate().toInstant().isBefore(curInstant)) {
                    array.add(backup);
                }
            }
        }
        return array;
    }

    /**
     * Constructor to construct the BackupData Object
     * @param builder the builder class object from which we have to create the actual backup data class object
     * @throws Exception if some relevant parameters are null
     */
    private BackupData(BackupDataBuilder builder) throws Exception {
        this.vault = builder.vault;
        if(builder.completionDate != null)
            this.completionDate = new Date(builder.completionDate.getTime());
        if(builder.creationDate != null)
            this.creationDate = new Date(builder.creationDate.getTime());
        this.arn = builder.arn;
        this.size = builder.size;
        if(builder.lastRestoreTime!=null)
            this.lastRestoreTime = new Date(builder.lastRestoreTime.getTime());
        this.resourceType = builder.resourceType;
        this.status = builder.status;
        this.moveToColdStorageAfterDays = builder.moveToColdStorageAfterDays;
        this.deleteAfterDays = builder.deleteAfterDays;
        if(builder.moveToColdStorageAt != null)
            this.moveToColdStorageAt = new Date(builder.moveToColdStorageAt.getTime());
        if(builder.deleteAt != null)
            this.deleteAt = new Date(builder.deleteAt.getTime());

        if(this.size == null || this.size<0)
            throw new Exception("Provide a valid value for size of the Backup in context");
        if(this.creationDate == null)
            throw new Exception("Provide a valid Date Object for creation date of the Backup in context");
        if(this.completionDate == null)
            throw new Exception("Provide a valid Date object for the Completion date of the Backup in context");

    }
}
