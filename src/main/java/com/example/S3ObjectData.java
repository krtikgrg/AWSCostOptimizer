package com.example;

import java.time.Instant;
import java.util.Date;

/*
* Object here refers to the files stored inside an AWS S3 Bucket.
* This class is used to store information about a file stored inside a s3 Bucket.
* */

/**
 * Class to store information of S3 objects, object here refers to the files stored inside a S3 bucket
 */
class S3ObjectData implements Comparable<S3ObjectData>{
    private String objectName;
    private String storageClass;
    private Date lastModified;
    private long size;

    /**
     * Constructor
     * @param name the objectName of the concerned file/S3 Object
     * @param storageClass the storage class of the file/S3 Object in context
     * @param date the date on which this file/S3 Object was last modified
     * @param size the size of the file/S3 Object in bytes
     * @throws Exception if any of the relevant parameters is not provided
     */
    public S3ObjectData(String name,String storageClass,Date date,long size) throws Exception {
        this.objectName = name;
        this.storageClass = storageClass;
        this.lastModified = date;
        this.size = size;
        if(this.objectName == null)
            throw new Exception("Name of the S3 object/file is a mandatory field");
        if(this.storageClass == null)
            throw new Exception("Storage Class of the S3 object/file is a mandatory field");
        if(this.lastModified == null)
            throw new Exception("Last Modified Date of the S3 object/file is a mandatory field");
        if(this.size < 0)
            throw new Exception("Size of the S3 object/file provided is not acceptable");
    }

    /**
     * Method to compare two objects of this class. This method helps devise an ordering for the objects of this class
     * This method makes use of the size parameter to order the objects
     * @param object the object to be compared.
     * @return returns a positive integer if the current object's size value is greater than the size value passed as a parameter else returns a negative value
     */
    public int compareTo(S3ObjectData object){
        if(this.getSize() > object.getSize())
            return 1;
        return -1;
    }

    /**
     * Method to return the size of the S3 Object/file in context
     * @return the size of the current S3 Object/file
     */
    public long getSize(){
        return this.size;
    }

    /**
     * Returns the objectName of the current S3 Object/file
     * @return the objectName of the S3 Object/file in context
     */
    public String getName(){
        return this.objectName;
    }

    /**
     * Method to return the storage class of a S3 Object/file
     * @return the Storage class value of the S3 Object/file in context
     */
    public String getStorageClass(){
        return this.storageClass;
    }

    /**
     * Method to return the Date Object corresponding to last modified time stamp of the concerned S3 object/file
     * @return the date object corresponding to the time stamp on which the associate S3 Object/file was modified
     */
    public Date getLastModified(){
        return new Date(this.lastModified.getTime());
    }

    /**
     * Method to return if the object is relevant for us or not. An object is relevant if the last modified date is before
     * some number of days. This value of days will be passed as a parameter.
     * @param days the days which will help us in deciding which objects are relevant or not
     * @return return true if the last modified date of this object occurs before the number of days mentioned before
     */
    public boolean isRelevant(int days){
        Instant curInstant = Instant.ofEpochSecond(Instant.now().getEpochSecond() - (long) days*24*3600);
        return this.lastModified.toInstant().isBefore(curInstant);
    }

    @Override
    public String toString() {
        return "S3ObjectData{" +
                "objectName='" + objectName + '\'' +
                ", storageClass='" + storageClass + '\'' +
                ", lastModified=" + lastModified +
                ", size=" + size +
                '}';
    }
}
