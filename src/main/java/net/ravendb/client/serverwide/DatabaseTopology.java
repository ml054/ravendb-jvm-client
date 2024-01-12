package net.ravendb.client.serverwide;

import com.fasterxml.jackson.annotation.JsonIgnore;
import net.ravendb.client.serverwide.operations.DatabasePromotionStatus;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class DatabaseTopology {

    private List<String> members = new ArrayList<>();
    private List<String> promotables = new ArrayList<>();
    private List<String> rehabs = new ArrayList<>();

    private Map<String, String> predefinedMentors;
    private Map<String, String> demotionReasons;
    private Map<String, DatabasePromotionStatus> promotablesStatus;
    private int replicationFactor;
    private boolean dynamicNodesDistribution;
    private LeaderStamp stamp;
    private String databaseTopologyIdBase64;
    private String clusterTransactionIdBase64;
    private List<String> priorityOrder;
    private Date nodesModifiedAt;

    @JsonIgnore
    public List<String> getAllNodes() {
        List<String> result = new ArrayList<>();

        result.addAll(members);
        result.addAll(promotables);
        result.addAll(rehabs);

        return result;
    }

    public List<String> getMembers() {
        return members;
    }

    public void setMembers(List<String> members) {
        this.members = members;
    }

    public List<String> getPromotables() {
        return promotables;
    }

    public void setPromotables(List<String> promotables) {
        this.promotables = promotables;
    }

    public List<String> getRehabs() {
        return rehabs;
    }

    public void setRehabs(List<String> rehabs) {
        this.rehabs = rehabs;
    }

    public Map<String, String> getPredefinedMentors() {
        return predefinedMentors;
    }

    public void setPredefinedMentors(Map<String, String> predefinedMentors) {
        this.predefinedMentors = predefinedMentors;
    }

    public Map<String, String> getDemotionReasons() {
        return demotionReasons;
    }

    public void setDemotionReasons(Map<String, String> demotionReasons) {
        this.demotionReasons = demotionReasons;
    }

    public Map<String, DatabasePromotionStatus> getPromotablesStatus() {
        return promotablesStatus;
    }

    public void setPromotablesStatus(Map<String, DatabasePromotionStatus> promotablesStatus) {
        this.promotablesStatus = promotablesStatus;
    }

    public int getReplicationFactor() {
        return replicationFactor;
    }

    public void setReplicationFactor(int replicationFactor) {
        this.replicationFactor = replicationFactor;
    }

    public boolean isDynamicNodesDistribution() {
        return dynamicNodesDistribution;
    }

    public void setDynamicNodesDistribution(boolean dynamicNodesDistribution) {
        this.dynamicNodesDistribution = dynamicNodesDistribution;
    }

    public LeaderStamp getStamp() {
        return stamp;
    }

    public void setStamp(LeaderStamp stamp) {
        this.stamp = stamp;
    }

    public String getDatabaseTopologyIdBase64() {
        return databaseTopologyIdBase64;
    }

    public void setDatabaseTopologyIdBase64(String databaseTopologyIdBase64) {
        this.databaseTopologyIdBase64 = databaseTopologyIdBase64;
    }

    public String getClusterTransactionIdBase64() {
        return clusterTransactionIdBase64;
    }

    public void setClusterTransactionIdBase64(String clusterTransactionIdBase64) {
        this.clusterTransactionIdBase64 = clusterTransactionIdBase64;
    }

    public Date getNodesModifiedAt() {
        return nodesModifiedAt;
    }

    public void setNodesModifiedAt(Date nodesModifiedAt) {
        this.nodesModifiedAt = nodesModifiedAt;
    }

    public List<String> getPriorityOrder() {
        return priorityOrder;
    }

    public void setPriorityOrder(List<String> priorityOrder) {
        this.priorityOrder = priorityOrder;
    }
}
