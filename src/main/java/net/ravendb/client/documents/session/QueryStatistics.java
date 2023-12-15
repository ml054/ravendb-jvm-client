package net.ravendb.client.documents.session;

import net.ravendb.client.documents.queries.QueryResult;

import java.util.Date;

/**
 * Statistics about a raven query.
 * Such as how many records match the query
 */
public class QueryStatistics {

    private boolean isStale;
    private long durationInMs;
    private long totalResults;
    private long skippedResults;
    private Long scannedResults;
    private Date timestamp;
    private String indexName;
    private Date indexTimestamp;
    private Date lastQueryTime;
    private Long resultEtag;
    private String nodeTag;

    /**
     * Whether the query returned potentially stale results
     * @return true is query result is stale
     */
    public boolean isStale() {
        return isStale;
    }

    /**
     * Whether the query returned potentially stale results
     * @param stale sets the value
     */
    public void setStale(boolean stale) {
        isStale = stale;
    }

    /**
     * The duration of the query _server side_
     * @return duration of the query
     */
    public long getDurationInMs() {
        return durationInMs;
    }

    /**
     * The duration of the query _server side_
     * @param durationInMs Sets the value
     */
    public void setDurationInMs(long durationInMs) {
        this.durationInMs = durationInMs;
    }

    /**
     * What was the total count of the results that matched the query
     * @return total results
     */
    public long getTotalResults() {
        return totalResults;
    }

    /**
     * What was the total count of the results that matched the query
     * @param totalResults Sets the value
     */
    public void setTotalResults(long totalResults) {
        this.totalResults = totalResults;
    }

    /**
     * Gets the skipped results
     * @return amount of skipped results
     */
    public long getSkippedResults() {
        return skippedResults;
    }

    /**
     * Sets the skipped results
     * @param skippedResults Sets the value
     */
    public void setSkippedResults(long skippedResults) {
        this.skippedResults = skippedResults;
    }

    /**
     * The number of results (filtered or matches)
     * that were scanned by the query. This is relevant
     * only if you are using a filter clause in the query.
     * @return scanned results
     */
    public Long getScannedResults() {
        return scannedResults;
    }

    /**
     * The number of results (filtered or matches)
     * that were scanned by the query. This is relevant
     * only if you are using a filter clause in the query.
     * @param scannedResults scanned results
     */
    public void setScannedResults(Long scannedResults) {
        this.scannedResults = scannedResults;
    }

    /**
     * The time when the query results were non stale.
     * @return Query timestamp
     */
    public Date getTimestamp() {
        return timestamp;
    }

    /**
     * The time when the query results were non stale.
     * @param timestamp Sets the value
     */
    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }

    /**
     * The name of the index queried
     * @return index name used for query
     */
    public String getIndexName() {
        return indexName;
    }

    /**
     * The name of the index queried
     * @param indexName Sets the value
     */
    public void setIndexName(String indexName) {
        this.indexName = indexName;
    }

    /**
     * The timestamp of the queried index
     * @return the index timestamp
     */
    public Date getIndexTimestamp() {
        return indexTimestamp;
    }

    /**
     * The timestamp of the queried index
     * @param indexTimestamp Sets the value
     */
    public void setIndexTimestamp(Date indexTimestamp) {
        this.indexTimestamp = indexTimestamp;
    }

    /**
     * The timestamp of the last time the index was queried
     * @return last query time
     */
    public Date getLastQueryTime() {
        return lastQueryTime;
    }

    /**
     * The timestamp of the last time the index was queried
     * @param lastQueryTime Sets the query time
     */
    public void setLastQueryTime(Date lastQueryTime) {
        this.lastQueryTime = lastQueryTime;
    }

    public Long getResultEtag() {
        return resultEtag;
    }

    public void setResultEtag(Long resultEtag) {
        this.resultEtag = resultEtag;
    }

    public String getNodeTag() {
        return nodeTag;
    }

    public void setNodeTag(String nodeTag) {
        this.nodeTag = nodeTag;
    }

    public void updateQueryStats(QueryResult qr) {
        isStale = qr.isStale();
        durationInMs = qr.getDurationInMs();
        totalResults = qr.getTotalResults();
        skippedResults = qr.getSkippedResults();
        scannedResults = qr.getScannedResults();
        timestamp = qr.getIndexTimestamp();
        indexName = qr.getIndexName();
        indexTimestamp = qr.getIndexTimestamp();
        lastQueryTime = qr.getLastQueryTime();
        resultEtag = qr.getResultEtag();
        nodeTag = qr.getNodeTag();
    }

}
