package com.example;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.IndexResponse;
import com.amazonaws.services.kafka.model.S3;
import com.google.gson.Gson;

import java.io.IOException;
import java.io.Serializable;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;

/**
 * Class to store a S3 bucket's data in form of Objects
 */
class S3BucketData implements Comparable<S3BucketData>{
    private String bucketName;
    private String location;
    private Long bucketSize = 0L;
    private int numberOfObjects;
    private OwnerS3Object owner;
    private Date lastModified;
    private ArrayList<S3ObjectData> objects = new ArrayList<>();
    private ArrayList<StatisticRecord> averageNumberOfObjects = new ArrayList<>();
    private ArrayList<StatisticRecord> averageBucketSizeBytes = new ArrayList<>();
    private ArrayList<S3ObjectData> currentRelevant = null;
    private Long relevantSize;

    /**
     * Builder class to help in building objects of the parent class. The parent class's constructor is made private
     * so the only way to create an object of parent class that is S3BucketData is by using this builder class.
     */
    public static class S3BucketDataBuilder{
        private String name;
        private String location;
        private int numberOfObjects;
        private OwnerS3Object owner;

        /**
         * Constructor
         * @param name the bucketName of the S3 bucket in context
         */
        public S3BucketDataBuilder(String name){
            this.name = name;
        }

        /**
         * Method to provide location of a S3 bucket to its builder object
         * @param location the location of the S3 bucket
         * @return returns the builder object which can be used to chain the remaining methods to provide remaining information and to ultimately build the S3BucketData Object
         */
        public S3BucketDataBuilder withLocation(String location){
            this.location = location;
            return this;
        }

        /**
         * Method to provide the owner information of a S3 bucket
         * @param name the bucketName of the owner
         * @param id the id of the associated owner
         * @return returns the builder object which can be used to chain the remaining methods to provide remaining information and to ultimately build the S3BucketData Object
         * @throws Exception if owner bucketName or owner id is not provided
         */
        public S3BucketDataBuilder withOwner(String name, String id) throws Exception {
            try {
                this.owner = new OwnerS3Object(name, id);
            }catch (Exception e){
                throw new Exception("Either Owner Name or Owner ID or both were not provided");
            }
            return this;
        }

        /**
         * Method to provide the number of objects that are present in the S3 bucket
         * @param number the number which represents the number of the objects in the S3 bucket in context
         * @return returns the builder object which can be used to chain the remaining methods to provide remaining information and to ultimately build the S3BucketData Object
         */
        public S3BucketDataBuilder withNumberOfObjects(int number){
            this.numberOfObjects = number;
            return this;
        }

        /**
         * build method to build the S3BucketData Object from this builder object. The only way to create S3BucketData Object is to call this build method on a builder object
         * @return the S3BucketData Object
         * @throws Exception if the builder object cannot create the parent class object
         */
        public S3BucketData build() throws Exception {
            return new S3BucketData(this);
        }
    }

    /**
     * Method to add an object, here object refers to the S3 Object or file stored inside a S3 Bucket
     * This method adds the specified object to the array of objects maintained by a S3BucketObject
     * @param name the bucketName of the S3 object/file
     * @param storageClass the storage class of the S3 object/file
     * @param date the last modified Date object corresponding to the last modified time stamp of the S3 object/file
     * @param size the size(in bytes) of the S3 object/file
     */
    public void addObject(String name,String storageClass,Date date,long size) throws Exception {
        this.addInBucketSize(size);
        objects.add(new S3ObjectData(name,storageClass,date,size));
    }

    /**
     * Method to set the last modified date of the bucket
     * This value is calculated by going over the last modified date of all the objects/files of the bucket
     * and then the maximum value out them is picked and that value is chosen to be the last modified date
     * of the bucket
     * @param instant the Instant class object which represents the largest instant value on which some object/file of the bucket in context was modified
     */
    public void setLastModifiedDate(Instant instant){
        this.lastModified = Date.from(instant);
    }

    /**
     * Method to set the average number of objects metric retrieved from the cloudwatch
     * @param array the array of the objects binding the timestamp and the corresponding value
     */
    public void setAverageNumberOfObjects(ArrayList<StatisticRecord> array){
        if(array!=null)
            this.averageNumberOfObjects.addAll(array);
    }

    /**
     * Method to set the average Bucket Size in bytes metric retrieved from the cloudwatch
     * @param array the array of the objects binding the timestamp and the corresponding value
     */
    public void setAverageBucketSizeBytes(ArrayList<StatisticRecord> array){
        if(array!=null)
            this.averageBucketSizeBytes.addAll(array);
    }

    /**
     * Function to retrieve the bucketName of the bucket
     * @return the bucketName of the bucket in context
     */
    public String getName(){
        return this.bucketName;
    }

    /**
     * Function to reverse sort the objects array(based on their size) of S3BucketData Object
     */
    public void sortObjects(){
        Collections.sort(this.objects,Collections.reverseOrder());
    }

    /**
     * Method to compute and store the relevant objects out of all the objects. Relevant objects here are the objects which have not
     * been modified in the last x number of days. The value x is passed a parameter to this method. Remember the stored collection can
     * only be reported once by making a call to getRelevantObjects. Once a call to that method is made the stored collection is erased.
     * @param days the value x which is defined in the function description
     * @return returns the collection of the relevant objects
     */
    public void getAndStoreRelevantObjects(int days){
        ArrayList<S3ObjectData> toBeReturned = new ArrayList<>();
        this.relevantSize = 0L;
        for(S3ObjectData object : this.objects){
            if(object.isRelevant(days)) {
                toBeReturned.add(object);
                this.relevantSize += object.getSize();
            }
        }
        this.currentRelevant = toBeReturned;
    }

    /**
     * Method to return the relevant objects which have already been calculated by making a call to getAndStoreRelevantObjects method.
     * So this method should always be called after making a call to getAndStoreRelevantObjects
     * @return returns the collection of the relevant objects
     * @throws Exception if the prerequisite method is not called before making a call to this function.
     */
    public ArrayList<S3ObjectData> getRelevantObjects() throws Exception {
        if(this.currentRelevant == null)
            throw new Exception("Method called without making a call to the prerequisite method (check in description)");
        ArrayList<S3ObjectData> toBeReturned = this.currentRelevant;
        this.currentRelevant = null;
        this.relevantSize = 0L;
        return toBeReturned;
    }

    /**
     * Method to add some number in the Number of Objects parameter of this class objects
     * @param toBeAdded number which is to be added
     */
    public void addInNumberOfObjects(int toBeAdded){
        this.numberOfObjects += toBeAdded;
    }

    /**
     * Method to add a value in bucketSize variable. This method should be called for each object/file which
     * is present in the S3 bucket in context. So that we can calculate the exact size of the bucket.
     * @param size the size of the current S3 Object/file in loop
     */
    public void addInBucketSize(Long size) { this.bucketSize += size; }

    /**
     * Method to return the bucket size
     * @return bucket size in bytes of the bucket in context
     */
    public Long getBucketSize() { return this.bucketSize; }

    /**
     * Method to return the total size of the relevant objects inside that bucket
     * @return The relevant bucket size
     */
    public Long getRelevantSize() {
        return this.relevantSize;
    }

    /**
     * Method to provide an ordering in the objects of this class
     * @param bucket the object to be compared.
     * @return a positive value if the relevant size of the current bucket is greater than the relevant size
     *          of the bucket passed as a param else returns a negative value
     */
    public int compareTo(S3BucketData bucket){
        if(this.getRelevantSize() > bucket.getRelevantSize())
            return 1;
        return -1;
    }

    /**
     * Method to add the bucket into elastic search
     * @param esClient the elastic search client which is to be used
     * @param indexName the index in which the bucket object is to be inserted
     * @return IndexResponse the response returned by elastic search for our insertion query
     * @throws IOException
     */
    public IndexResponse pushToElasticSearch(ElasticsearchClient esClient, String indexName) throws IOException {

        S3BucketDataElasticSearch curObj = new S3BucketDataElasticSearch(this.bucketName,this.bucketSize,this.numberOfObjects,this.lastModified);

        IndexRequest.Builder<S3BucketDataElasticSearch> indexReqBuilder = new IndexRequest.Builder<>();
        indexReqBuilder.index(indexName);
        indexReqBuilder.id(this.getName());
        indexReqBuilder.document(curObj);

        IndexResponse response = esClient.index(indexReqBuilder.build());
        return response;
    }

    @Override
    public String toString() {
        return "S3BucketData{" +
                "bucketName='" + bucketName + '\'' +
                ", location='" + location + '\'' +
                ", numberOfObjects=" + numberOfObjects +
                ", owner=" + owner +
                ", lastModified=" + lastModified +
                ", objects=" + objects +
                ", averageNumberOfObjects=" + averageNumberOfObjects +
                ", averageBucketSizeBytes=" + averageBucketSizeBytes +
                '}';
    }

    /**
     * The constructor to create an object of the S3BucketData object using the builder object. This constructor is private, so it is not
     * possible to create an object of this class directly. The only way is to make a builder object and then calling its build method.
     * @param builder the builder object from which we have to create the object of S3BucketData Object
     * @throws Exception if any of the relevant parameters are not provided
     */
    private S3BucketData(S3BucketDataBuilder builder) throws Exception {
        this.bucketName = builder.name;
        this.location = builder.location;
        this.numberOfObjects = builder.numberOfObjects;
        this.owner = builder.owner;
        if(this.bucketName == null){
            throw new Exception("Name of S3 bucket is necessary");
        }
//        if(this.location == null){
//            throw new Exception("Location of S3 Bucket is a mandatory parameter");
//        }

        if(this.numberOfObjects < 0){
            throw new Exception("Number of Objects parameter's value is not correct");
        }

    }
}
