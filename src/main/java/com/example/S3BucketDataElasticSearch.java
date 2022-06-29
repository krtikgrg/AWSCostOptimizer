package com.example;

import java.io.Serializable;
import java.util.Date;

class S3BucketDataElasticSearch implements Serializable {
    private String bucketName;
    private Long bucketSize = 0L;
    private int numberOfObjects;
    private Date lastModified;
    public S3BucketDataElasticSearch(String bucketName, Long bucketSize, int numberOfObjects, Date lastModified){
        this.bucketName = bucketName;
        this.bucketSize = bucketSize;
        this.numberOfObjects = numberOfObjects;
        this.lastModified = lastModified;
    }

    public String getBucketName() {
        return bucketName;
    }

    public Long getBucketSize() {
        return bucketSize;
    }

    public int getNumberOfObjects() {
        return numberOfObjects;
    }

    public Date getLastModified() {
        return lastModified;
    }

    public void setBucketName(String bucketName) {
        this.bucketName = bucketName;
    }

    public void setBucketSize(Long bucketSize) {
        this.bucketSize = bucketSize;
    }

    public void setNumberOfObjects(int numberOfObjects) {
        this.numberOfObjects = numberOfObjects;
    }

    public void setLastModified(Date lastModified) {
        this.lastModified = lastModified;
    }
}
