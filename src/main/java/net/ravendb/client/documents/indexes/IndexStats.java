package net.ravendb.client.documents.indexes;

import net.ravendb.client.documents.dataArchival.ArchivedDataProcessingBehavior;

import java.util.Date;
import java.util.Map;

public class IndexStats {

    private String name;
    private int mapAttempts;
    private int mapSuccesses;
    private int mapErrors;
    private Integer mapReferenceAttempts;
    private Integer mapReferenceSuccesses;
    private Integer mapReferenceErrors;
    private Long reduceAttempts;
    private Long reduceSuccesses;
    private Long reduceErrors;
    private String reduceOutputCollection;
    private String reduceOutputReferencePattern;
    private String patternReferencesCollectionName;
    private double mappedPerSecondRate;
    private double reducedPerSecondRate;
    private int maxNumberOfOutputsPerDocument;
    private Map<String, CollectionStats> collections;
    private Date lastQueryingTime;
    private IndexState state;
    private IndexPriority priority;
    private Date createdTimestamp;
    private Date lastIndexingTime;
    private boolean stale;
    private IndexLockMode lockMode;
    private IndexType type;
    private SearchEngineType searchEngineType;
    private ArchivedDataProcessingBehavior archivedDataProcessingBehavior;
    private IndexRunningStatus status;
    private long entriesCount;
    private int errorsCount;
    private IndexSourceType sourceType;
    private boolean isTestIndex;


    /**
     * Index name.
     * @return Index name
     */
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * Indicates how many times the database tried to index documents (map) using this index.
     * @return map attempts
     */
    public int getMapAttempts() {
        return mapAttempts;
    }

    /**
     * Indicates how many times the database tried to index documents (map) using this index.
     * @param mapAttempts Sets the value
     */
    public void setMapAttempts(int mapAttempts) {
        this.mapAttempts = mapAttempts;
    }

    /**
     * Indicates how many indexing attempts succeeded.
     * @return  map successes
     */
    public int getMapSuccesses() {
        return mapSuccesses;
    }

    /**
     * Indicates how many indexing attempts succeeded.
     * @param mapSuccesses Sets the value
     */
    public void setMapSuccesses(int mapSuccesses) {
        this.mapSuccesses = mapSuccesses;
    }

    /**
     * Indicates how many indexing attempts failed.
     * @return map errors
     */
    public int getMapErrors() {
        return mapErrors;
    }

    /**
     * Indicates how many indexing attempts failed.
     * @param mapErrors sets the value
     */
    public void setMapErrors(int mapErrors) {
        this.mapErrors = mapErrors;
    }

    /**
     * Indicates how many times the database tried to index referenced documents (map) using this index.
     * @return map reference attempts
     */
    public Integer getMapReferenceAttempts() {
        return mapReferenceAttempts;
    }

    /**
     * Indicates how many times the database tried to index referenced documents (map) using this index.
     * @param mapReferenceAttempts attempts
     */
    public void setMapReferenceAttempts(Integer mapReferenceAttempts) {
        this.mapReferenceAttempts = mapReferenceAttempts;
    }

    /**
     * Indicates how many indexing attempts of referenced documents succeeded.
     * @return map reference success
     */
    public Integer getMapReferenceSuccesses() {
        return mapReferenceSuccesses;
    }

    /**
     * Indicates how many indexing attempts of referenced documents succeeded.
     * @param mapReferenceSuccesses map reference success
     */
    public void setMapReferenceSuccesses(Integer mapReferenceSuccesses) {
        this.mapReferenceSuccesses = mapReferenceSuccesses;
    }

    /**
     * Indicates how many indexing attempts of referenced documents failed.
     * @return map reference errors
     */
    public Integer getMapReferenceErrors() {
        return mapReferenceErrors;
    }

    /**
     * Indicates how many indexing attempts of referenced documents failed.
     * @param mapReferenceErrors map reference errors
     */
    public void setMapReferenceErrors(Integer mapReferenceErrors) {
        this.mapReferenceErrors = mapReferenceErrors;
    }

    /**
     * Indicates how many times database tried to index documents (reduce) using this index.
     * @return reduce attempts
     */
    public Long getReduceAttempts() {
        return reduceAttempts;
    }

    /**
     * Indicates how many times database tried to index documents (reduce) using this index.
     * @param reduceAttempts sets the value
     */
    public void setReduceAttempts(Long reduceAttempts) {
        this.reduceAttempts = reduceAttempts;
    }

    /**
     * Indicates how many reducing attempts succeeded.
     * @return reduce success count
     */
    public Long getReduceSuccesses() {
        return reduceSuccesses;
    }

    /**
     * Indicates how many reducing attempts succeeded.
     * @param reduceSuccesses sets the value
     */
    public void setReduceSuccesses(Long reduceSuccesses) {
        this.reduceSuccesses = reduceSuccesses;
    }

    /**
     * Indicates how many reducing attempts failed.
     * @return reduce errors
     */
    public Long getReduceErrors() {
        return reduceErrors;
    }

    /**
     * Indicates how many reducing attempts failed.
     * @param reduceErrors Sets the value
     */
    public void setReduceErrors(Long reduceErrors) {
        this.reduceErrors = reduceErrors;
    }

    /**
     * The reduce output collection.
     * @return reduce output collection
     */
    public String getReduceOutputCollection() {
        return reduceOutputCollection;
    }

    /**
     * The reduce output collection.
     * @param reduceOutputCollection reduce output
     */
    public void setReduceOutputCollection(String reduceOutputCollection) {
        this.reduceOutputCollection = reduceOutputCollection;
    }

    /**
     * Pattern for creating IDs for the reduce output reference-collection
     * @return output pattern
     */
    public String getReduceOutputReferencePattern() {
        return reduceOutputReferencePattern;
    }

    /**
     * Pattern for creating IDs for the reduce output reference-collection
     * @param reduceOutputReferencePattern reference pattern
     */
    public void setReduceOutputReferencePattern(String reduceOutputReferencePattern) {
        this.reduceOutputReferencePattern = reduceOutputReferencePattern;
    }

    /**
     * @return Collection name for reduce output reference-collection
     */
    public String getPatternReferencesCollectionName() {
        return patternReferencesCollectionName;
    }

    /**
     * @param patternReferencesCollectionName Collection name for reduce output reference-collection
     */
    public void setPatternReferencesCollectionName(String patternReferencesCollectionName) {
        this.patternReferencesCollectionName = patternReferencesCollectionName;
    }

    /**
     * The value of docs/sec rate for the index over the last minute
     * @return amount of documents mapped/second
     */
    public double getMappedPerSecondRate() {
        return mappedPerSecondRate;
    }

    /**
     * The value of docs/sec rate for the index over the last minute
     * @param mappedPerSecondRate sets the value
     */
    public void setMappedPerSecondRate(double mappedPerSecondRate) {
        this.mappedPerSecondRate = mappedPerSecondRate;
    }

    /**
     * The value of reduces/sec rate for the index over the last minute
     * @return amount of documents reduced per second
     */
    public double getReducedPerSecondRate() {
        return reducedPerSecondRate;
    }

    /**
     * The value of reduces/sec rate for the index over the last minute
     * @param reducedPerSecondRate Sets the value
     */
    public void setReducedPerSecondRate(double reducedPerSecondRate) {
        this.reducedPerSecondRate = reducedPerSecondRate;
    }

    /**
     * Indicates the maximum number of produced indexing outputs from a single document
     * @return maximum number of outputs per document
     */
    public int getMaxNumberOfOutputsPerDocument() {
        return maxNumberOfOutputsPerDocument;
    }

    /**
     * Indicates the maximum number of produced indexing outputs from a single document
     * @param maxNumberOfOutputsPerDocument sets the value
     */
    public void setMaxNumberOfOutputsPerDocument(int maxNumberOfOutputsPerDocument) {
        this.maxNumberOfOutputsPerDocument = maxNumberOfOutputsPerDocument;
    }

    public Map<String, CollectionStats> getCollections() {
        return collections;
    }

    public void setCollections(Map<String, CollectionStats> collections) {
        this.collections = collections;
    }

    /**
     * Time of last query for this index.
     * @return Last query time for this index
     */
    public Date getLastQueryingTime() {
        return lastQueryingTime;
    }

    /**
     * Time of last query for this index.
     * @param lastQueryingTime Sets the value
     */
    public void setLastQueryingTime(Date lastQueryingTime) {
        this.lastQueryingTime = lastQueryingTime;
    }

    /**
     * Index state (Normal, Disabled, Idle, Abandoned, Error)
     * @return index state
     */
    public IndexState getState() {
        return state;
    }

    /**
     * Index state (Normal, Disabled, Idle, Abandoned, Error)
     * @param state Sets the value
     */
    public void setState(IndexState state) {
        this.state = state;
    }

    /**
     * Index priority (Low, Normal, High)
     * @return index priority
     */
    public IndexPriority getPriority() {
        return priority;
    }

    /**
     * Index priority (Low, Normal, High)param priority
     * @param priority sets the value
     */
    public void setPriority(IndexPriority priority) {
        this.priority = priority;
    }

    /**
     * Date of index creation.
     * @return Date of index creation
     */
    public Date getCreatedTimestamp() {
        return createdTimestamp;
    }

    /**
     * Date of index creation.
     * @param createdTimestamp Sets the value
     */
    public void setCreatedTimestamp(Date createdTimestamp) {
        this.createdTimestamp = createdTimestamp;
    }

    /**
     * Time of last indexing (map or reduce) for this index.
     * @return Time of last indexing
     */
    public Date getLastIndexingTime() {
        return lastIndexingTime;
    }

    /**
     * Time of last indexing (map or reduce) for this index.
     * @param lastIndexingTime Sets the value
     */
    public void setLastIndexingTime(Date lastIndexingTime) {
        this.lastIndexingTime = lastIndexingTime;
    }

    public boolean isStale() {
        return stale;
    }

    public void setStale(boolean stale) {
        this.stale = stale;
    }

    /**
     * Indicates current lock mode:
     * - Unlock - all index definition changes acceptable
     * - LockedIgnore - all index definition changes will be ignored, only log entry will be created
     * - LockedError - all index definition changes will raise exception
     * @return index lock mode
     */
    public IndexLockMode getLockMode() {
        return lockMode;
    }

    /**
     * Indicates current lock mode:
     * - Unlock - all index definition changes acceptable
     * - LockedIgnore - all index definition changes will be ignored, only log entry will be created
     * - LockedError - all index definition changes will raise exception
     * @param lockMode Sets the value
     */
    public void setLockMode(IndexLockMode lockMode) {
        this.lockMode = lockMode;
    }

    /**
     * Indicates index type.
     * @return index type
     */
    public IndexType getType() {
        return type;
    }

    /**
     * Indicates index type.
     * @param type Sets the value
     */
    public void setType(IndexType type) {
        this.type = type;
    }

    public SearchEngineType getSearchEngineType() {
        return searchEngineType;
    }

    public void setSearchEngineType(SearchEngineType searchEngineType) {
        this.searchEngineType = searchEngineType;
    }

    public ArchivedDataProcessingBehavior getArchivedDataProcessingBehavior() {
        return archivedDataProcessingBehavior;
    }

    public void setArchivedDataProcessingBehavior(ArchivedDataProcessingBehavior archivedDataProcessingBehavior) {
        this.archivedDataProcessingBehavior = archivedDataProcessingBehavior;
    }

    public IndexRunningStatus getStatus() {
        return status;
    }

    public void setStatus(IndexRunningStatus status) {
        this.status = status;
    }

    /**
     * Total number of entries in this index.
     * @return index entries count
     */
    public long getEntriesCount() {
        return entriesCount;
    }

    /**
     * Total number of entries in this index.
     * @param entriesCount sets the value
     */
    public void setEntriesCount(long entriesCount) {
        this.entriesCount = entriesCount;
    }

    public int getErrorsCount() {
        return errorsCount;
    }

    public void setErrorsCount(int errorsCount) {
        this.errorsCount = errorsCount;
    }

    /**
     * Indicates if this is a test index (works on a limited data set - for testing purposes only)
     * @return true if test index
     */
    public boolean isTestIndex() {
        return isTestIndex;
    }

    /**
     * Indicates if this is a test index (works on a limited data set - for testing purposes only)
     * @param testIndex Sets the value
     */
    public void setTestIndex(boolean testIndex) {
        isTestIndex = testIndex;
    }

    public IndexSourceType getSourceType() {
        return sourceType;
    }

    public void setSourceType(IndexSourceType sourceType) {
        this.sourceType = sourceType;
    }

    public static class CollectionStats {
        private long lastProcessedDocumentEtag;
        private long lastProcessedTombstoneEtag;
        private long documentLag;
        private long tombstoneLag;

        public CollectionStats() {
            documentLag = -1;
            tombstoneLag = -1;
        }

        public long getLastProcessedDocumentEtag() {
            return lastProcessedDocumentEtag;
        }

        public void setLastProcessedDocumentEtag(long lastProcessedDocumentEtag) {
            this.lastProcessedDocumentEtag = lastProcessedDocumentEtag;
        }

        public long getLastProcessedTombstoneEtag() {
            return lastProcessedTombstoneEtag;
        }

        public void setLastProcessedTombstoneEtag(long lastProcessedTombstoneEtag) {
            this.lastProcessedTombstoneEtag = lastProcessedTombstoneEtag;
        }

        public long getDocumentLag() {
            return documentLag;
        }

        public void setDocumentLag(long documentLag) {
            this.documentLag = documentLag;
        }

        public long getTombstoneLag() {
            return tombstoneLag;
        }

        public void setTombstoneLag(long tombstoneLag) {
            this.tombstoneLag = tombstoneLag;
        }
    }
}
