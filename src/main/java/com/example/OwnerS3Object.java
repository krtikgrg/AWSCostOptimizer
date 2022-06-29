package com.example;

/*
* Object here refers to files stored inside a S3 bucket, terminology used by aws.
* This class stores the information about the owner of an object in s3 bucket.
* */


/**
 * Class to store information about an owner of a S3 Object, object here is a file stored inside a S3 bucket
 */
class OwnerS3Object {
    private String ownerName;
    private String id;

    /**
     * Constructor
     * @param name the ownerName of the owner of the current S3 object/file
     * @param id the id (unique id given by amazon) of the owner of the S3 object/file
     * @throws Exception if owner ownerName or owner id is not provided
     */
    public OwnerS3Object(String name, String id) throws Exception {
        this.ownerName = name;
        this.id = id;
        if(this.ownerName == null)
            throw new Exception("Name of Owner is a mandatory field");
        else if(this.id == null)
            throw new Exception("Owner ID is a mandatory field");
    }

    @Override
    public String toString() {
        return "OwnerS3Object{" +
                "ownerName='" + ownerName + '\'' +
                ", id='" + id + '\'' +
                '}';
    }
}
