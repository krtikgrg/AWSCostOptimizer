package com.example;

import java.io.Serializable;
import java.util.Date;

class S3BucketDataElasticSearch implements Serializable {
    private String bucketName;
    private Double bucketSize = 0.0;
    private Double numberOfObjects;
//    private Date lastModified;
    public S3BucketDataElasticSearch(String bucketName, Double bucketSize, Double numberOfObjects){
        this.bucketName = bucketName;
        this.bucketSize = bucketSize;
        this.numberOfObjects = numberOfObjects;
//        this.lastModified = lastModified;
    }


    public String getBucketName() {
        return bucketName;
    }

    public Double getBucketSize() {
        return bucketSize;
    }

    public Double getNumberOfObjects() {
        return numberOfObjects;
    }

//    public Date getLastModified() {
//        return lastModified;
//    }

    public void setBucketName(String bucketName) {
        this.bucketName = bucketName;
    }

    public void setBucketSize(Double bucketSize) {
        this.bucketSize = bucketSize;
    }

    public void setNumberOfObjects(Double numberOfObjects) {
        this.numberOfObjects = numberOfObjects;
    }

//    public void setLastModified(Date lastModified) {
//        this.lastModified = lastModified;
//    }
}
