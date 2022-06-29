package com.example;

import software.amazon.awssdk.regions.Region;

import java.util.Date;

/**
 * The class to create objects of Backup Vaults which will store the information of the vault.
 */
class BackupVaultData {
    private String vaultArn;
    private String vaultName;
    private Date creationDate;
    private Long maxRetentionDays;
    private Long minRetentionDays;
    private Long numberOfBackups;
    private Region region;

    /**
     * The builder class which is used to create the object of the BackupVaultData class. We can chain the methods of this class
     * to provide all the relevant information.
     */
    public static class BackupVaultDataBuilder{
        private String vaultArn;
        private String vaultName;
        private Date creationDate;
        private Long maxRetentionDays;
        private Long minRetentionDays;
        private Long numberOfBackups;

        /**
         * Constructor for the builder class
         * @param name The name of the Vault in context
         * @param arn The ARN of the Vault in context
         * @throws Exception if any of the parameters are null
         */
        public BackupVaultDataBuilder(String name,String arn) throws Exception {
            this.vaultArn = arn;
            this.vaultName = name;
            if(this.vaultName == null)
                throw new Exception("Vault Name is a mandatory field and it should not be null");
            if(this.vaultArn == null)
                throw new Exception("Vault ARN is a mandatory field and it should not be null");
        }

        /**
         * Method to specify the creation date of a vault
         * @param date The creation Date of the vault in context
         * @return The same builder object which will help in chaining of methods. It will make things more readable.
         */
        public BackupVaultDataBuilder withCreationDate(Date date){
            this.creationDate = date;
            return this;
        }

        /**
         * Method to specify the minimum and maximum retention days
         * @param minimumValue The minimum value
         * @param maximumValue The maximum value
         * @return The same builder object which will help in chaining of methods. It will make things more readable.
         */
        public BackupVaultDataBuilder withRetentionDays(Long minimumValue, Long maximumValue){
            this.maxRetentionDays = maximumValue;
            this.minRetentionDays = minimumValue;
            return this;
        }

        /**
         * Method to specify the number of backups/snapshots/recovery points present in a vault
         * @param number The number of backups/snapshots/recovery points present in the vault in context
         * @return The same builder object which will help in chaining of methods. It will make things more readable.
         */
        public BackupVaultDataBuilder withNumberOfRecoveryPoints(Long number){
            this.numberOfBackups = number;
            return this;
        }

        /**
         * Method to build the actual vault data object from the builder class object
         * @return the actual vault data object
         */
        public BackupVaultData build(){
            return new BackupVaultData(this);
        }
    }

    /**
     * Method to return the name of the vault
     * @return The name of the vault in context
     */
    public String getName(){
        return this.vaultName;
    }

    /**
     * Method to set the region of a backup vault
     * @param region the Region class object corresponding to the region in which the backup vault in context lies
     */
    public void setRegion(Region region){
        this.region = region;
    }

    /**
     * Method to retrieve the region of a backup vault
     * @return the region in string form of the backup vault in context
     */
    public String getRegion(){
        return this.region.toString();
    }

    @Override
    public String toString() {
        return "BackupVaultData{" +
                "vaultArn='" + vaultArn + '\'' +
                ", vaultName='" + vaultName + '\'' +
                ", creationDate=" + creationDate +
                ", maxRetentionDays=" + maxRetentionDays +
                ", minRetentionDays=" + minRetentionDays +
                ", numberOfBackups=" + numberOfBackups +
                '}';
    }

    /**
     * Actual BackupVaultData class constructor which constructs the actual class object by using the builder class object
     * @param builder the builder class object from which we want to create the actual class object
     */
    private BackupVaultData(BackupVaultDataBuilder builder){
        this.vaultArn = builder.vaultArn;
        this.vaultName = builder.vaultName;
        if(builder.creationDate != null)
            this.creationDate = new Date(builder.creationDate.getTime());
        this.maxRetentionDays = builder.maxRetentionDays;
        this.minRetentionDays = builder.minRetentionDays;
        this.numberOfBackups = builder.numberOfBackups;
    }
}
