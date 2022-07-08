package com.example;

import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.util.CellRangeAddress;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

/**
 * Class having the functions to generate the report(In Excel Format). It is kind of an util class.
 */
class Report {
    private int rowNum;
    private boolean SUGGESTION_MODE;
    private HashMap<String,Integer> typeToVcpu;
    private HashMap<String,Long> typeToSize;
    private HashMap<String, Double> typeToPrice;
    private HSSFWorkbook workbook;
    private HSSFSheet sheet;
    private FileOutputStream file;
    private static final Double BUFFER_EC2_UTIL_PERCENTAGE = 30.0;
    private static final Double EC2_REQUIRED_UTIL = 75.0;
    private final String separator = "============================================================================";


    /**
     * Constructor
     * @param filename filename with appropriate path for the report to be generated
     * @param SUGGESTION_MODE If true then we have to make suggestion of the optimal vcpu and memory requirement and some other suggestions too
     * @param typeToVcpu Hashmap storing the mapping of the instance type to the vcpu count
     * @param typeToSize Hashmap storing the mapping of the instance type to the Memory Size
     * @param typeToPrice Hashmap storing the mapping of the instance type to the On Demand Price
     * @throws FileNotFoundException in case the path provided is not correct, it may be due to the non-existence of
     *                              certain directories in the path provided
     */
    public Report(String filename, boolean SUGGESTION_MODE, HashMap<String ,Integer> typeToVcpu, HashMap<String ,Long> typeToSize, HashMap<String, Double> typeToPrice) throws FileNotFoundException {
        this.rowNum = 0;
        /*
        Creating Workbook
         */
        this.workbook = new HSSFWorkbook();

        /*
        Creating the Sheet in the workbook
         */
//        this.sheet = workbook.createSheet("Report");

        /*
        Creating the FileOutputStream Object. This will create the file if it does not exist already at the path provided
         */
        this.file = new FileOutputStream(filename);
        this.SUGGESTION_MODE = SUGGESTION_MODE;
        this.typeToSize = typeToSize;
        this.typeToVcpu = typeToVcpu;
        this.typeToPrice = typeToPrice;
    }

    /**
     * For better readability of the report, one can create multiple sheets and add the relevant data in them
     * This method helps in creating a sheet in the workbook and after calling this method any further calls made
     * to any add data method will add data in this new sheet unless and until a call is made to this method again
     * @param sheetName the name to be given to the newly created sheet
     */
    public void createSheetAndLoad(String sheetName){
        this.sheet = workbook.createSheet(sheetName);
        this.rowNum = 0;
    }

    /**
     * Method to add heading in the report at the current line.
     * @param heading the text of the heading which is to be added
     */
    public void addHeading(String heading, int index){
        HSSFRow currentRow = this.sheet.createRow(this.rowNum);
        currentRow.createCell(index).setCellValue(heading);
        this.rowNum += 1;
        insertSeparator();
    }

    /**
     * Method to Add some row gaps in the report (At the current location of the cursor)
     * @param num the number of row gaps we want to add
     */
    public void addRowGaps(int num){
        rowNum += num;
    }

    /**
     * Method to add the Suggestion info of the on demand ec2 instances in the report
     * @param cpuUtil the cpu utilization value of the instance in context
     * @param memUtil the memory utilization value of the instance in context
     * @param instance the instance in context
     * @param currentRow the row object corresponding to the row in which the data is being added currently
     */
    private void addSuggestionInfo(Double cpuUtil, Double memUtil, Ec2InstanceData instance, HSSFRow currentRow){

        cpuUtil = Math.min(cpuUtil*((BUFFER_EC2_UTIL_PERCENTAGE/100)+1.0),100.0);
        Double cpuRequired = cpuUtil*(instance.getVcpuCount().doubleValue());
        Integer suggestedVcpus = (int)Math.ceil(cpuRequired/EC2_REQUIRED_UTIL);

        Double suggestedMemory;
        if(memUtil == null){
            suggestedMemory = Math.ceil(this.typeToSize.get(instance.getType()).doubleValue()/1024.0);
        } else {
            memUtil = Math.min(memUtil*((BUFFER_EC2_UTIL_PERCENTAGE/100)+1.0),100.0);
            Double memRequired = memUtil*(this.typeToSize.get(instance.getType()).doubleValue());
            suggestedMemory = Math.ceil(Math.ceil(memRequired/EC2_REQUIRED_UTIL)/1024.0);
        }


        int ratio = (int)Math.ceil(suggestedMemory/suggestedVcpus.doubleValue());
        String suggestedFamily = "";
        if(ratio<=3) {
            suggestedFamily = "Compute-Optimised";
        } else if (ratio <= 6) {
            suggestedFamily = "General-Purpose";
        } else if (ratio <=32) {
            suggestedFamily = "Memory-Optimised";
        } else {
            suggestedFamily = "Storage-Optimised(If DB Related)";
        }
        currentRow.createCell(15).setCellValue(suggestedFamily);
        sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 15, 17));
        currentRow.createCell(19).setCellValue(suggestedVcpus.toString());
        sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 19, 20));

        if(memUtil!=null)
            currentRow.createCell(22).setCellValue(suggestedMemory.toString());
        else
            currentRow.createCell(22).setCellValue(suggestedMemory+"(No Data, So same)");

        sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 22, 24));

        /*
        ArrayList<String> possibleInstances = new ArrayList<>();
        Double minCost = Double.MAX_VALUE;
        String minTpe = null;
        for(String tpe : typeToSize.keySet()){
            double mem = Math.ceil((double)typeToSize.get(tpe)/1024.0);
            double vcpus = typeToVcpu.get(tpe);

            if((mem-suggestedMemory)>=0 && (mem-suggestedMemory)<=2 && (vcpus-suggestedVcpus)>=0 && (vcpus-suggestedVcpus)<=1) {
                possibleInstances.add(tpe);
                if(this.typeToPrice.containsKey(tpe)){
                    Double curPrice = this.typeToPrice.get(tpe);
                    if(curPrice < minCost){
                        minCost = curPrice;
                        minTpe = tpe;
                    }
                }
            }
        }

        if(possibleInstances.size() == 0){
            currentRow.createCell(26).setCellValue("No Suggestion");
            sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 26, 30));
        } else if(minTpe == null){
            currentRow.createCell(26).setCellValue(possibleInstances.get(0));
            sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 26, 30));
        } else {
            currentRow.createCell(26).setCellValue(minTpe);
            sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 26, 30));
        }
        */
    }

    /**
     * Method to add the data of ec2 instances in the report. This is a general method to add data of all types of ec2 instances. This
     * method can be used to add data of on demand instances, spot instances and reserved instances.
     * @param instances the array list of the instances which are to be added in the report
     * @param heading the heading to give for this section of the report, before adding the current collection of ec2 instances
     * @param cpuStatistic the statistic which we are considering for differentiating between utilized and not utilized or between utilized and over utilized ec2 instances
     *                  it can take the following values "maximum", "minimum" and "average" [FOR CPU]
     * @param cpuThresholdInPercent the threshold value, the ec2 instances with their CPU utilization below this threshold will be considered
     *                           as not utilized and the ones with larger values than this threshold will be considered utilized resources (underUtilizedOnes = true).
     *                           When underUtilizedOnes = false, then the threshold value which is to be considered for over utilization of
     *                           ec2 instances. The ec2 instances with their CPU utilization above this threshold will be considered
     *                           as over utilized and the ones with lower values than this threshold will be considered utilized resources
     * @param memoryStatistic the statistic which we are considering for differentiating between utilized and not utilized or between utilized and over utilized ec2 instances
     *                        The only value it can take is "maximum" [FOR MEMORY]
     * @param memoryThresholdInPercent the threshold value, the ec2 instances with their Memory utilization below this threshold will be considered
     *                           as not utilized and the ones with larger values than this threshold will be considered utilized resources (underUtilizedOnes = true).
     *                           When underUtilizedOnes = false, then the threshold value which is to be considered for over utilization of
     *                           ec2 instances. The ec2 instances with their Memory utilization above this threshold will be considered
     *                           as over utilized and the ones with lower values than this threshold will be considered utilized resources
     * @param underUtilizedOnes if its true then we are adding data for under utilization of ec2 instances else we are adding data for over utilization of ec2 instances
     * @param isOnDemand if its true then we are adding data of on demand instance else we are adding data of other types
     */
    public void addEc2InstanceData(ArrayList<Ec2InstanceData> instances,String heading,String cpuStatistic,double cpuThresholdInPercent,String memoryStatistic,double memoryThresholdInPercent, boolean underUtilizedOnes, boolean isOnDemand) {

        /*
         * Adding the heading and the columns which we need to add for EC2 instances data
         * */
        HSSFRow currentRow = this.sheet.createRow(this.rowNum);
        currentRow.createCell(0).setCellValue(heading);
        sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 0, 4));
        currentRow.createCell(5).setCellValue("Statistics Used: \"" + cpuStatistic + "\" (cpu), \"" + memoryStatistic + "\" (memory) [Cpu Threshold: " + cpuThresholdInPercent + "%, " + "Memory Threshold: " + memoryThresholdInPercent + "%]");
        sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 5, 14));

        this.rowNum += 1;
        currentRow = this.sheet.createRow(this.rowNum);

        /*
         * Adding the columns
         * */
        currentRow.createCell(0).setCellValue("Sr. No.");
        currentRow.createCell(2).setCellValue("Instance ID");
        currentRow.createCell(5).setCellValue("Region");
        currentRow.createCell(8).setCellValue("CPU Utilization(In %)");
        currentRow.createCell(11).setCellValue("Memory Utilization(In %)");
        sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 2, 3));
        sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 5, 6));
        sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 8, 9));
        sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 11, 13));
        if (isOnDemand && this.SUGGESTION_MODE) {
            currentRow.createCell(15).setCellValue("Suggested Family");
            sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 15, 17));
            currentRow.createCell(19).setCellValue("Suggested VCPUs");
            sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 19, 20));
            currentRow.createCell(22).setCellValue("Suggested Memory(GBs)");
            sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 22, 24));
//            currentRow.createCell(26).setCellValue("Suggested Instance Type(Linux)");
//            sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 26, 30));
        }
        this.rowNum += 1;

        Integer ctr = 1;

        for (Ec2InstanceData instance : instances) {
            try {
                if (underUtilizedOnes) {
                    if (instance.isUnderUtilizedCpu(cpuStatistic, cpuThresholdInPercent) || (instance.ifMemoryDataAvailable() && instance.isUnderUtilizedMemory(memoryStatistic, memoryThresholdInPercent))) {
                        currentRow = sheet.createRow(rowNum);
                        currentRow.createCell(0).setCellValue(ctr.toString());
                        currentRow.createCell(2).setCellValue(instance.getId());
                        currentRow.createCell(5).setCellValue(instance.getRegion());
                        Double cpuUtil, memUtil=null;
                        cpuUtil = instance.getCpuUtilization(cpuStatistic, true);

                        if(instance.ifMemoryDataAvailable())
                            memUtil = instance.getMemoryUtilization(memoryStatistic, true);

                        currentRow.createCell(8).setCellValue(cpuUtil.toString());

                        if(memUtil!=null)
                            currentRow.createCell(11).setCellValue(memUtil.toString());
                        else
                            currentRow.createCell(11).setCellValue("-");

                        sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 2, 3));
                        sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 5, 6));
                        sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 8, 9));
                        sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 11, 13));

                        if (isOnDemand && this.SUGGESTION_MODE)
                            addSuggestionInfo(cpuUtil, memUtil, instance, currentRow);

                        this.rowNum += 1;
                        ctr += 1;
                    }
                } else {
                    if (instance.isOverUtilizedCpu(cpuStatistic, cpuThresholdInPercent) || (instance.ifMemoryDataAvailable() && instance.isOverUtilizedMemory(memoryStatistic, memoryThresholdInPercent))) {
                        currentRow = sheet.createRow(rowNum);
                        currentRow.createCell(0).setCellValue(ctr.toString());
                        currentRow.createCell(2).setCellValue(instance.getId());
                        currentRow.createCell(5).setCellValue(instance.getRegion());
                        Double cpuUtil, memUtil = null;

                        /*
                         * Intentionally given true value
                         * */
                        cpuUtil = instance.getCpuUtilization(cpuStatistic, true);
                        if(instance.ifMemoryDataAvailable())
                            memUtil = instance.getMemoryUtilization(memoryStatistic, true);

                        currentRow.createCell(8).setCellValue(((Double) instance.getCpuUtilization(cpuStatistic, false)).toString());

                        if(memUtil != null)
                            currentRow.createCell(11).setCellValue(((Double) instance.getMemoryUtilization(memoryStatistic, false)).toString());
                        else
                            currentRow.createCell(11).setCellValue("-");

                        sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 2, 3));
                        sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 5, 6));
                        sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 8, 9));
                        sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 11, 13));

                        if (isOnDemand && this.SUGGESTION_MODE)
                            addSuggestionInfo(cpuUtil, memUtil, instance, currentRow);

                        this.rowNum += 1;
                        ctr += 1;
                    }
                }
            } catch (Exception e) {
                /*
                 * In case where the statistic value passed was not an acceptable one
                 * */
                e.printStackTrace();
            }
        }
        insertSeparator();
    }

    /**
     * Method to add data of cost implication of the on demand instances. Here we compare the prices of on demand
     * instance to that of spot(REMOVED) and reserved instances
     * @param instances the instances for which we have to do this analysis
     * @param heading the heading to be given for this section of the report
     */
    public void addOnDemandCostImplication(ArrayList<Ec2InstanceData> instances, String heading){
        /*
         * Adding the heading and the columns which we need to add for EC2 instances data
         * */
        HSSFRow currentRow = this.sheet.createRow(this.rowNum);
        currentRow.createCell(0).setCellValue(heading);
        sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 0, 4));
        this.rowNum += 1;
        currentRow.createCell(0).setCellValue("Total Number of Running On Demand Instance: "+instances.size());
        sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 0, 8));

        this.rowNum += 1;
        currentRow = this.sheet.createRow(this.rowNum);

        /*
         * Adding the columns
         * */
        currentRow.createCell(0).setCellValue("Sr. No.");
        currentRow.createCell(2).setCellValue("Instance ID");
        currentRow.createCell(5).setCellValue("Region");
        currentRow.createCell(8).setCellValue("Instance Type");
        currentRow.createCell(11).setCellValue("On Demand Price(Linux)");
//        currentRow.createCell(15).setCellValue("Spot Price(Actual Platform)");
        currentRow.createCell(15).setCellValue("Reserved Price(Actual Platform)");
        sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 2, 3));
        sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 5, 6));
        sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 8, 9));
        sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 11, 13));
        sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 15, 17));
//        sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 16, 18));

        this.rowNum += 1;

        Integer ctr = 1;

        for (Ec2InstanceData instance : instances) {
            currentRow = sheet.createRow(rowNum);
            currentRow.createCell(0).setCellValue(ctr.toString());
            currentRow.createCell(2).setCellValue(instance.getId());
            currentRow.createCell(5).setCellValue(instance.getRegion());
            currentRow.createCell(8).setCellValue(instance.getType());
            if(typeToPrice.containsKey(instance.getType()))
                currentRow.createCell(11).setCellValue(typeToPrice.get(instance.getType()).toString());
            else
                currentRow.createCell(11).setCellValue("-");
//            if(instance.getSpotPrice() != null)
//                currentRow.createCell(15).setCellValue(instance.getSpotPrice().toString());
//            else
//                currentRow.createCell(15).setCellValue("-");

            if(instance.getReservedPrice() != null)
                currentRow.createCell(15).setCellValue(instance.getReservedPrice().toString());
            else
                currentRow.createCell(15).setCellValue("-");

            sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 2, 3));
            sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 5, 6));
            sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 8, 9));
            sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 11, 13));
            sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 15, 17));
//            sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 16, 18));

            this.rowNum += 1;
            ctr += 1;
        }
        insertSeparator();
    }

    /**
     * Method to add the Reserved instances which are underutilized in terms of the capacity
     * bought. Let's say if we have bought 10 instances and out of them we are only using 4 then
     * we are using it at 40% capacity. So, depending on the threshold value, if it is greater than 40%
     * then this reserved instance will be flagged and printed on the report.
     * @param instances The ArrayList of the reserved instances
     * @param heading the heading to give to this section of the report
     * @param thresholdInPercent the threshold value to consider for the under-utilization of reserved instances in terms of capacity used
     */
    public void addReservedInstancesAtLowCapacity(ArrayList<ReservedInstanceData> instances, String heading, double thresholdInPercent) {
        /*
         * Adding the heading and the columns which we need to add for Reserved EC2 instances data
         * */
        HSSFRow currentRow = this.sheet.createRow(this.rowNum);
        currentRow.createCell(0).setCellValue(heading);
        sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 0, 6));
        currentRow.createCell(7).setCellValue("(Threshold: " + thresholdInPercent + "%)");
        sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 7, 9));

        this.rowNum += 1;
        currentRow = this.sheet.createRow(this.rowNum);

        /*
         * Adding the columns
         * */
        currentRow.createCell(0).setCellValue("Sr. No.");
        currentRow.createCell(2).setCellValue("Reserved Instance ID");
        currentRow.createCell(6).setCellValue("Instance Type");
        currentRow.createCell(9).setCellValue("Capacity In Use");
        currentRow.createCell(12).setCellValue("Total Capacity");
        currentRow.createCell(15).setCellValue("Region");
        sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 2, 4));
        sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 6, 7));
        sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 9, 10));
        sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 12, 13));
        sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 15, 16));
        this.rowNum += 1;

        Integer ctr = 1;

        for (ReservedInstanceData instance : instances) {
            if (instance.isActive() && instance.isUnderUtilized(thresholdInPercent)) {
                currentRow = sheet.createRow(rowNum);
                currentRow.createCell(0).setCellValue(ctr.toString());
                currentRow.createCell(2).setCellValue(instance.getReservedInstanceId());
                currentRow.createCell(6).setCellValue(instance.getInstanceType());
                currentRow.createCell(9).setCellValue(instance.getCapacityInUse().toString());
                currentRow.createCell(12).setCellValue(instance.getInstanceCount().toString());
                currentRow.createCell(15).setCellValue(instance.getRegion());
                sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 2, 4));
                sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 6, 7));
                sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 9, 10));
                sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 12, 13));
                sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 15, 16));
                this.rowNum += 1;
                ctr += 1;
            }
        }
        insertSeparator();
    }

    /**
     * Method to add data of those reserved instances which are about to expire
     * @param instances the reserved instance purchases list which we have made
     * @param heading the heading to be given to this section of the report
     * @param thresholdDays the threshold value which is to be considered to shortlist the expiring instance purchases.
     *                      The instances expiring in next threshold days will be listed down
     */
    public void addExpiringReservedInstances(ArrayList<ReservedInstanceData> instances, String heading, int thresholdDays){
        /*
         * Adding the heading and the columns which we need to add for Reserved EC2 instances data
         * */
        HSSFRow currentRow = this.sheet.createRow(this.rowNum);
        currentRow.createCell(0).setCellValue(heading+" "+thresholdDays+" DAYS");
        sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 0, 6));

        this.rowNum += 1;
        currentRow = this.sheet.createRow(this.rowNum);

        /*
         * Adding the columns
         * */
        currentRow.createCell(0).setCellValue("Sr. No.");
        currentRow.createCell(2).setCellValue("Reserved Instance ID");
        currentRow.createCell(6).setCellValue("Expiring At");
        currentRow.createCell(9).setCellValue("Region");
        sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 2, 4));
        sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 6, 7));
        sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 9, 10));
        this.rowNum += 1;

        Integer ctr = 1;

        for (ReservedInstanceData instance : instances) {
            if (instance.isActive() && instance.isExpiringInNextNDays(thresholdDays)) {
                currentRow = sheet.createRow(rowNum);
                currentRow.createCell(0).setCellValue(ctr.toString());
                currentRow.createCell(2).setCellValue(instance.getReservedInstanceId());
                currentRow.createCell(6).setCellValue(instance.getEndTime().toString());
                currentRow.createCell(9).setCellValue(instance.getRegion());
                sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 2, 4));
                sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 6, 7));
                sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 9, 10));
                this.rowNum += 1;
                ctr += 1;
            }
        }
        insertSeparator();
    }

    /**
     * Method to add the EBS volumes which are not in "in-use" state, this method takes a general list
     * of EbsVolumeData Objects and will make another list of the volumes which are not in "in-use" state
     * The final output in the report is also reverse sorted based on the size value
     * @param volumes the list of volumes from which we have to identify the ones not in "in-use" state
     * @param heading the heading to give for this section of the report
     */
    public void addUnattachedEbsVolumes(ArrayList<EbsVolumeData> volumes, String heading){
        /*
         * Adding the heading and the columns which we need to add for EBS Volumes data
         * */
        HSSFRow currentRow = this.sheet.createRow(this.rowNum);
        currentRow.createCell(0).setCellValue(heading);
        sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 0, 6));

        this.rowNum += 1;
        currentRow = this.sheet.createRow(this.rowNum);

        /*
         * Adding the columns
         * */
        currentRow.createCell(0).setCellValue("Sr. No.");
        currentRow.createCell(2).setCellValue("Volume ID");
        currentRow.createCell(5).setCellValue("Region");
        currentRow.createCell(8).setCellValue("Size (In GBs)");
        sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 2, 3));
        sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 5, 6));
        sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 8, 9));
        this.rowNum += 1;

        ArrayList<EbsVolumeData> unusedVolumes = EbsVolumeData.getVolumes(volumes,false);
        Collections.sort(unusedVolumes,Collections.reverseOrder());

        Integer ctr = 1;

        for (EbsVolumeData volume : unusedVolumes) {
            currentRow = sheet.createRow(rowNum);
            currentRow.createCell(0).setCellValue(ctr.toString());
            currentRow.createCell(2).setCellValue(volume.getVolumeId());
            currentRow.createCell(5).setCellValue(volume.getRegion());
            currentRow.createCell(8).setCellValue(volume.getSize().toString());
            sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 2, 3));
            sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 5, 6));
            sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 8, 9));
            this.rowNum += 1;
            ctr += 1;
        }
        insertSeparator();
    }

    /**
     * Method to add the EBS Volumes which have their read and write ops/sec values less than the thresholds given
     * as params. Such Volumes are considered to be under utilized. And if the read+write Ops value is close to supported
     * IOPs value then it is considered to be Over Utilized. Only the EBS Volumes in "in-use" state are considered here
     * @param volumes the list of volumes from which we will select the ones which are in "in-use" state and then those having read write ops/sec values less than the thresholds
     * @param heading the heading to give for this section of the report
     * @param readThreshold the threshold value for read ops/sec
     * @param writeThreshold the threshold value for write ops/sec
     */
    public void addEbsVolumesUtilizationData(ArrayList<EbsVolumeData> volumes, String heading, double readThreshold, double writeThreshold){
        /*
         * Adding the heading and the columns which we need to add for EBS Volumes data
         * */
        HSSFRow currentRow = this.sheet.createRow(this.rowNum);
        currentRow.createCell(0).setCellValue(heading);
        sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 0, 6));
        this.rowNum += 1;

        currentRow = this.sheet.createRow(this.rowNum);
        currentRow.createCell(0).setCellValue("Volumes with Low Read Write Activity");
        sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 0, 6));
        currentRow.createCell(7).setCellValue("(Read Threshold: "+readThreshold+" Ops/Sec, Write Threshold: "+writeThreshold+" Ops/Sec)");
        sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 7, 13));

        this.rowNum += 1;
        currentRow = this.sheet.createRow(this.rowNum);

        /*
         * Adding the columns
         * */
        currentRow.createCell(0).setCellValue("Sr. No.");
        currentRow.createCell(2).setCellValue("Volume ID");
        currentRow.createCell(5).setCellValue("Region");
        currentRow.createCell(8).setCellValue("Size(GBs)");
        currentRow.createCell(10).setCellValue("Read Ops/Sec");
        currentRow.createCell(13).setCellValue("Write Ops/Sec");
        currentRow.createCell(16).setCellValue("Instance ID(When only one is attached)");
        sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 2, 3));
        sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 5, 6));
        sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 10, 11));
        sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 13, 14));
        this.rowNum += 1;

        ArrayList<EbsVolumeData> usedVolumes = EbsVolumeData.getVolumes(volumes, true);
        Collections.sort(usedVolumes,Collections.reverseOrder());

        Integer ctr = 1;

        for (EbsVolumeData volume : usedVolumes) {
            try {
                if (volume.isCritical(readThreshold, writeThreshold)) {
                    currentRow = sheet.createRow(rowNum);
                    currentRow.createCell(0).setCellValue(ctr.toString());
                    currentRow.createCell(2).setCellValue(volume.getVolumeId());
                    currentRow.createCell(5).setCellValue(volume.getRegion());
                    currentRow.createCell(8).setCellValue(volume.getSize().toString());
                    currentRow.createCell(10).setCellValue(volume.getReadOps().toString());
                    currentRow.createCell(13).setCellValue(volume.getWriteOps().toString());
                    currentRow.createCell(16).setCellValue(volume.getInstanceId());
                    sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 2, 3));
                    sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 5, 6));
                    sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 10, 11));
                    sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 13, 14));
                    sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 16, 18));
                    this.rowNum += 1;
                    ctr += 1;
                }
            }catch (Exception e){
                e.printStackTrace();
            }
        }
        insertSeparator();

        currentRow = this.sheet.createRow(this.rowNum);
        currentRow.createCell(0).setCellValue("Volumes with Read Write Activity greater than 90% to that of Supported Value");
        sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 0, 9));

        this.rowNum += 1;
        currentRow = this.sheet.createRow(this.rowNum);

        /*
         * Adding the columns
         * */
        currentRow.createCell(0).setCellValue("Sr. No.");
        currentRow.createCell(2).setCellValue("Volume ID");
        currentRow.createCell(5).setCellValue("Region");
        currentRow.createCell(8).setCellValue("Size(GBs)");
        sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 2, 3));
        sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 5, 6));
        this.rowNum += 1;

        ctr = 1;

        for (EbsVolumeData volume : usedVolumes) {
            try {
                Double opsps = volume.getReadOps()+volume.getWriteOps();
                Double threshold = volume.getIops().doubleValue()*0.9;
                if (opsps >= threshold) {
                    currentRow = sheet.createRow(rowNum);
                    currentRow.createCell(0).setCellValue(ctr.toString());
                    currentRow.createCell(2).setCellValue(volume.getVolumeId());
                    currentRow.createCell(5).setCellValue(volume.getRegion());
                    currentRow.createCell(8).setCellValue(volume.getSize().toString());
                    sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 2, 3));
                    sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 5, 6));
                    this.rowNum += 1;
                    ctr += 1;
                }
            }catch (Exception e){
                e.printStackTrace();
            }
        }
        insertSeparator();
    }

    /**
     * Method to add load balancer information to the report. This method is a general one, and it can add any general list
     * of load balancer data objects in the report. The passed lists can contain all load balancers or only application load
     * balancers, or only network load balancers or only gateway load balancers.
     * @param loadBalancers The collection of load balancers about which we want to add information in our report
     * @param heading the heading which is to be given for this collection before we add data for this particular collection
     * @param statistic the statistic which is to be used to differentiate between utilized and not utilized load balancers
     *                  it can take these values: "maximum", "minimum", "sum" and "average". Any other value will result
     *                  in an exception
     * @param thresholdCount the threshold value, this is value which will bifurcate between the load balancers. If the metric
     *                       data value is above this threshold then that load balancer is utilized, and it is the other way then
     *                       it is under utilized
     */
    public void addLoadBalancerData(ArrayList<ElasticLoadBalancerData> loadBalancers, String heading, String statistic, double thresholdCount) {

        /*
         * Adding the heading and the respective columns for load balancers which should be there
         * */
        HSSFRow currentRow = this.sheet.createRow(this.rowNum);
        currentRow.createCell(0).setCellValue(heading);
        sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 0, 4));
        currentRow.createCell(5).setCellValue("Statistic Used: \"" + statistic + "\" (Threshold: " + thresholdCount + ")");
        sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 5, 9));
        this.rowNum += 1;

        currentRow = this.sheet.createRow(this.rowNum);
        currentRow.createCell(0).setCellValue("Sr. No.");
        currentRow.createCell(2).setCellValue("Load Balancer Name");
        currentRow.createCell(5).setCellValue("Region");
        currentRow.createCell(8).setCellValue("Request Count/Active Flow Count");
        sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 2, 3));
        sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 5, 6));
        sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 8, 12));
        this.rowNum += 1;


        Integer ctr = 1;
        for (ElasticLoadBalancerData loadBalancer : loadBalancers) {
            try {
                if (loadBalancer.isUnderUtilized(statistic, thresholdCount)) {
                    currentRow = sheet.createRow(rowNum);
                    currentRow.createCell(0).setCellValue(ctr.toString());
                    currentRow.createCell(2).setCellValue(loadBalancer.getName());
                    currentRow.createCell(5).setCellValue(loadBalancer.getRegion());
                    currentRow.createCell(8).setCellValue(((Double) loadBalancer.getMetricData(statistic)).toString());
                    sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 2, 3));
                    sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 5, 6));
                    sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 8, 12));
                    this.rowNum += 1;
                    ctr += 1;
                }
            } catch (Exception e) {
                /*
                 * In case where the statistic value passed was not an acceptable one
                 * */
                e.printStackTrace();
            }
        }
        insertSeparator();
    }

    /**
     * Method to add the Idle load balancers which have 0 targets attached or having 0 healthy targets
     * @param loadBalancers the list of the load balancers
     * @param heading the heading to give to this section of the report
     */
    public void addLoadBalancerTargetData(ArrayList<ElasticLoadBalancerData> loadBalancers, String heading) {
        /*
         * Adding the heading and the respective columns for load balancers which should be there
         * */
        HSSFRow currentRow = this.sheet.createRow(this.rowNum);
        currentRow.createCell(0).setCellValue(heading);
        sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 0, 4));

        currentRow = this.sheet.createRow(this.rowNum);
        currentRow.createCell(0).setCellValue("Sr. No.");
        currentRow.createCell(2).setCellValue("Load Balancer Name");
        currentRow.createCell(5).setCellValue("Region");
        currentRow.createCell(8).setCellValue("Total Targets");
        currentRow.createCell(11).setCellValue("Healthy Targets");
        sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 2, 3));
        sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 5, 6));
        sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 8, 9));
        sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 11, 12));
        this.rowNum += 1;


        Integer ctr = 1;
        for (ElasticLoadBalancerData loadBalancer : loadBalancers) {
            if (loadBalancer.isIdle()) {
                currentRow = sheet.createRow(rowNum);
                currentRow.createCell(0).setCellValue(ctr.toString());
                currentRow.createCell(2).setCellValue(loadBalancer.getName());
                currentRow.createCell(5).setCellValue(loadBalancer.getRegion());
                currentRow.createCell(8).setCellValue(loadBalancer.getTotalTargets().toString());
                currentRow.createCell(11).setCellValue(loadBalancer.getTotalHealthyTargets().toString());
                sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 2, 3));
                sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 5, 6));
                sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 8, 9));
                sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 11, 12));
                this.rowNum += 1;
                ctr += 1;
            }
        }
        insertSeparator();
    }

    /**
     * Method to add the information of a collection elastic ips (Only the IPs which are not associated to anything) in the report
     * @param elasticIps the collection of the elastic ips which are to be added in the report
     * @param heading the heading which is to be added before the information about the elastic ips
     */
    public void addIpData(ArrayList<ElasticIpData> elasticIps,String heading) {
        /*
         * Adding the heading the columns for elastic ips
         * */
        HSSFRow currentRow = this.sheet.createRow(this.rowNum);
        currentRow.createCell(0).setCellValue(heading);
        sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 0, 3));
        this.rowNum += 1;
        currentRow = this.sheet.createRow(this.rowNum);
        currentRow.createCell(0).setCellValue("Sr. No.");
        currentRow.createCell(2).setCellValue("IP ADDRESS");
        currentRow.createCell(5).setCellValue("Region");
        sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 2, 3));
        sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 5, 6));
        this.rowNum += 1;

        Integer ctr = 1;
        for (ElasticIpData elasticIp : elasticIps) {
            if (!elasticIp.isUsed()) {
                currentRow = sheet.createRow(rowNum);
                currentRow.createCell(0).setCellValue(ctr.toString());
                currentRow.createCell(2).setCellValue(elasticIp.getIp());
                currentRow.createCell(5).setCellValue(elasticIp.getRegion());
                sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 2, 3));
                sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 5, 6));
                this.rowNum += 1;
                ctr += 1;
            }
        }
        insertSeparator();
    }

    /**
     * Method to add meta data of s3 buckets to the excel report we have
     * @param buckets the list of buckets
     * @param heading the head to be given for this section of the report
     */
    public void addS3MetaData(ArrayList<S3BucketData> buckets,String heading) {
        /*
         * Adding the heading the columns for S3
         * */
        HSSFRow currentRow = this.sheet.createRow(this.rowNum);
        currentRow.createCell(0).setCellValue(heading);
        sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 0, 5));
        this.rowNum += 1;
        currentRow = this.sheet.createRow(this.rowNum);
        currentRow.createCell(0).setCellValue("Sr. No.");
        currentRow.createCell(2).setCellValue("Bucket Name");
        currentRow.createCell(5).setCellValue("Number of Objects");
        currentRow.createCell(8).setCellValue("Bucket Size");
        currentRow.createCell(11).setCellValue("Location");
        sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 2, 3));
        sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 5, 6));
        sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 8, 9));
        sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 11, 12));
        this.rowNum += 1;

        Integer ctr = 1;
        for (S3BucketData bucket : buckets) {
            currentRow = sheet.createRow(rowNum);
            currentRow.createCell(0).setCellValue(ctr.toString());
            currentRow.createCell(2).setCellValue(bucket.getName());
            currentRow.createCell(5).setCellValue(bucket.getNumberOfObjects().toString());
            currentRow.createCell(8).setCellValue(bucket.getBucketSizeBytes().toString());
            currentRow.createCell(11).setCellValue(bucket.getLocation());
            sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 2, 3));
            sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 5, 6));
            sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 8, 9));
            sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 11, 12));
            this.rowNum += 1;
            ctr += 1;
        }
        insertSeparator();
    }

    /**
     * Method to add the data of backups. Only those backups out of a collection which have been there for
     * more than x days
     * @param backups the collection of backups from which we first want to get the relevant backups and then
     *                add them to our report
     * @param heading the heading we want to give this section of the report
     * @param days the value x, defined in the description of the function
     */
    public void addBackupData(ArrayList<BackupData> backups, String heading, int days) {

        /*
         * Adding the heading and the required columns
         * */
        HSSFRow currentRow = this.sheet.createRow(this.rowNum);
        currentRow.createCell(0).setCellValue(heading + " " + days + " DAYS (SORTED IN DESCENDING ORDER ACCORDING TO SIZE)");
        sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 0, 9));
        this.rowNum += 1;

        currentRow = this.sheet.createRow(this.rowNum);
        currentRow.createCell(0).setCellValue("Sr. No.");
        currentRow.createCell(1).setCellValue("BACKUP ARN");
        currentRow.createCell(5).setCellValue("VAULT NAME");
        currentRow.createCell(8).setCellValue("Region");
        currentRow.createCell(11).setCellValue("CREATION DATE");
        currentRow.createCell(14).setCellValue("LAST RESTORE DATE");
        currentRow.createCell(18).setCellValue("SIZE (IN GBs)");

        sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 1, 3));
        sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 5, 6));
        sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 8, 9));
        sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 11, 12));
        sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 14, 16));
        sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 18, 19));
        this.rowNum += 1;

        Integer ctr = 1;
        /*
         * Getting the relevant backups
         * */
        ArrayList<BackupData> relevantBackups = BackupData.getBackupsOlderThan(days, backups);

        /*
         * Reverse Sorting the relevant backups according to their size
         * */
        Collections.sort(relevantBackups, Collections.reverseOrder());

        /*
         * Adding data of the relevant backups
         * */
        for (BackupData backup : relevantBackups) {
            try {
                currentRow = sheet.createRow(rowNum);
                currentRow.createCell(0).setCellValue(ctr.toString());
                currentRow.createCell(1).setCellValue(backup.getArn());
                currentRow.createCell(5).setCellValue(backup.getVault().getName());
                currentRow.createCell(8).setCellValue(backup.getRegion());
                currentRow.createCell(11).setCellValue(backup.getCreationDate().toString());
                if (backup.getRestoreDate() != null)
                    currentRow.createCell(14).setCellValue(backup.getRestoreDate().toString());
                else
                    currentRow.createCell(14).setCellValue("-");
                currentRow.createCell(18).setCellValue(backup.getSize((long) 1024 * 1024 * 1024).toString());

                sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 1, 3));
                sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 5, 6));
                sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 8, 9));
                sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 11, 12));
                sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 14, 16));
                sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 18, 19));

                this.rowNum += 1;
                ctr += 1;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        insertSeparator();
    }

    /**
     * Method to add data of the S3 buckets individually. For each Bucket we add the data of the
     * objects stored in that bucket. We only add those objects which have not been modified in the last x days
     * @param buckets the collection of the buckets for which we are supposed to add the data to the report
     * @param heading the heading which is to be given for this section of the report before adding the data
     * @param days the value x, as defined in the description of the function
     */
    public void addS3BucketData(ArrayList<S3BucketData> buckets,String heading,int days) {

        /*
         * Adding the general heading
         * */
        HSSFRow currentRow = this.sheet.createRow(this.rowNum);
        currentRow.createCell(0).setCellValue(heading + " " + days + " DAYS (REVERSE SORTED (SIZE) FOR EACH BUCKET)");
        sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 0, 9));
        this.rowNum += 2;

        for (S3BucketData bucket : buckets) {
            bucket.sortObjects();
            bucket.getAndStoreRelevantObjects(days);
        }
        Collections.sort(buckets,Collections.reverseOrder());

        for (S3BucketData bucket : buckets) {

            /*
             * Adding the heading for a bucket
             * */
            currentRow = this.sheet.createRow(this.rowNum);
            currentRow.createCell(0).setCellValue("FOR BUCKET '" + bucket.getName() + "'");
            sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 0, 9));
            this.rowNum += 1;

            /*
             * Adding the columns for a bucket
             * */
            currentRow = this.sheet.createRow(this.rowNum);
            currentRow.createCell(0).setCellValue("Sr. No.");
            currentRow.createCell(1).setCellValue("OBJECT NAME");
            currentRow.createCell(5).setCellValue("STORAGE CLASS");
            currentRow.createCell(8).setCellValue("LAST MODIFIED");
            currentRow.createCell(11).setCellValue("OBJECT SIZE (IN BYTES)");

            sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 1, 4));
            sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 5, 7));
            sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 8, 10));
            sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 11, 13));
            this.rowNum += 1;

            Integer ctr = 1;

            /*
             * Getting the relevant S3 objects out of all objects. here objects refer to the
             * files stored in the S3 bucket in context. The relevant objects are the objects
             * which have not been in the last x days. Here x is the method parameter days.
             * */
            try {
                ArrayList<S3ObjectData> objects = bucket.getRelevantObjects();
                for (S3ObjectData object : objects) {
                    currentRow = sheet.createRow(rowNum);
                    currentRow.createCell(0).setCellValue(ctr.toString());
                    currentRow.createCell(1).setCellValue(object.getName());
                    currentRow.createCell(5).setCellValue(object.getStorageClass());
                    currentRow.createCell(8).setCellValue(object.getLastModified().toString());
                    currentRow.createCell(11).setCellValue(((Long) object.getSize()).toString());

                    sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 1, 4));
                    sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 5, 7));
                    sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 8, 10));
                    sheet.addMergedRegion(new CellRangeAddress(this.rowNum, this.rowNum, 11, 13));

                    this.rowNum += 1;
                    ctr += 1;
                }
                this.rowNum += 1;
            } catch (Exception e){
                e.printStackTrace();
            }
        }
        insertSeparator();
    }

    /**
     * Method to write the entire report to the file
     */
    public void create(){
        try {
            this.workbook.write(this.file);
            this.file.close();
        }catch (IOException exception){
            exception.printStackTrace();
        }
    }

    /**
     * Method to insert a separator in order to distinguish between different sections of the report
     */
    private void insertSeparator(){
        HSSFRow currentRow = this.sheet.createRow(rowNum);
        currentRow.createCell(0).setCellValue(this.separator);
        this.rowNum += 1;
    }


}

