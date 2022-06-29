package com.example;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Class to bind the timestamp with the value associated with it. This class is primarily designed to
 * store the data returned from cloudwatch.
 */
class StatisticRecord {
    private Date date;
    private Double value;

    /**
     * Constructor
     * @param date the Date object representing the time stamp
     * @param value the value associated with the before-mentioned time stamp
     */
    public StatisticRecord(Date date,Double value){
        this.date = date;
        this.value = value;
    }

    /**
     * Method to generate a Collection of this class's objects from the timestamps and the values returned from the cloudwatch
     * client as a result of the getMetricData API call
     * @param timestamps the list of the timestamps returned from the AWS server
     * @param values corresponding values to the timestamps provided earlier
     * @return the arraylist of the objects containing the bound timestamp and data value
     */
    public static ArrayList<StatisticRecord> listGenerator(List<Instant> timestamps, List<Double> values){
        ArrayList<StatisticRecord> current = new ArrayList<>();
        for(int i=0;i<timestamps.size();i++){
            Instant curInstant = timestamps.get(i);
            Date curDate = Date.from(curInstant);
            StatisticRecord curObject = new StatisticRecord(curDate,values.get(i));
            current.add(curObject);
        }
        return current;
    }

    /**
     * Method to return the value associated with the object in context
     * @return the value associated
     */
    public double getValue(){
        return value;
    }

    @Override
    public String toString() {
        return "StatisticRecord{" +
                "date=" + date +
                ", value=" + value +
                '}';
    }
}


