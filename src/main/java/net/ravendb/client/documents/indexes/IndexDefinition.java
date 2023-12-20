package net.ravendb.client.documents.indexes;

import net.ravendb.client.documents.dataArchival.ArchivedDataProcessingBehavior;

import java.util.*;

/**
 * A definition of a RavenIndex
 */
public class IndexDefinition extends IndexDefinitionBase {

    public IndexDefinition() {
        configuration = new IndexConfiguration();
    }

    private IndexLockMode lockMode;
    private Map<String, String> additionalSources;
    private List<String[]> compoundFields;
    private Set<AdditionalAssembly> additionalAssemblies;
    private Set<String> maps;
    private String reduce;
    private Map<String, IndexFieldOptions> fields;
    private IndexConfiguration configuration;
    private IndexSourceType indexSourceType;
    private ArchivedDataProcessingBehavior archivedDataProcessingBehavior;
    private IndexType indexType;
    private String outputReduceToCollection;
    private Long reduceOutputIndex;
    private String patternForOutputReduceToCollectionReferences;
    private String patternReferencesCollectionName;
    private IndexDeploymentMode deploymentMode;

    /**
     * Index lock mode:
     * - Unlock - all index definition changes acceptable
     * - LockedIgnore - all index definition changes will be ignored, only log entry will be created
     * - LockedError - all index definition changes will raise exception
     * @return index lock mode
     */
    public IndexLockMode getLockMode() {
        return lockMode;
    }

    /**
     * Index lock mode:
     * - Unlock - all index definition changes acceptable
     * - LockedIgnore - all index definition changes will be ignored, only log entry will be created
     * - LockedError - all index definition changes will raise exception
     * @param lockMode sets the value
     */
    public void setLockMode(IndexLockMode lockMode) {
        this.lockMode = lockMode;
    }

    /**
     * Additional code files to be compiled with this index.
     * @return additional sources
     */
    public Map<String, String> getAdditionalSources() {
        if (additionalSources == null) {
            additionalSources = new HashMap<>();
        }
        return additionalSources;
    }

    /**
     * Additional code files to be compiled with this index.
     * @param additionalSources Sets the value
     */
    public void setAdditionalSources(Map<String, String> additionalSources) {
        this.additionalSources = additionalSources;
    }

    /**
     * Expert:
     * List of compound fields that can be used by the Corax indexing engine to
     * optimize certain queries. Has no effect on Lucene indexes.
     * @return list of fields
     */
    public List<String[]> getCompoundFields() {
        if (compoundFields == null) {
            compoundFields = new ArrayList<>();
        }
        return compoundFields;
    }

    /**
     * Expert:
     * List of compound fields that can be used by the Corax indexing engine to
     * optimize certain queries. Has no effect on Lucene indexes.
     * @param compoundFields  list of fields
     */
    public void setCompoundFields(List<String[]> compoundFields) {
        this.compoundFields = compoundFields;
    }

    public Set<AdditionalAssembly> getAdditionalAssemblies() {
        if (additionalAssemblies == null) {
            additionalAssemblies = new HashSet<>();
        }
        return additionalAssemblies;
    }

    public void setAdditionalAssemblies(Set<AdditionalAssembly> additionalAssemblies) {
        this.additionalAssemblies = additionalAssemblies;
    }

    /**
     * All the map functions for this index
     * @return index maps
     */
    public Set<String> getMaps() {
        if (maps == null) {
            maps = new HashSet<>();
        }
        return maps;
    }

    /**
     * All the map functions for this index
     * @param maps Sets the value
     */
    public void setMaps(Set<String> maps) {
        this.maps = maps;
    }

    /**
     * Index reduce function
     * @return reduce function
     */
    public String getReduce() {
        return reduce;
    }

    /**
     * Index reduce function
     * @param reduce Sets the reduce function
     */
    public void setReduce(String reduce) {
        this.reduce = reduce;
    }

    @Override
    public String toString() {
        return getName();
    }

    public Map<String, IndexFieldOptions> getFields() {
        if (fields == null) {
            fields = new HashMap<>();
        }
        return fields;
    }

    public void setFields(Map<String, IndexFieldOptions> fields) {
        this.fields = fields;
    }

    public IndexConfiguration getConfiguration() {
        if (configuration == null) {
            configuration = new IndexConfiguration();
        }
        return configuration;
    }

    public void setConfiguration(IndexConfiguration configuration) {
        this.configuration = configuration;
    }

    public IndexSourceType getSourceType() {
        if (indexSourceType == null || indexSourceType == IndexSourceType.NONE) {
            indexSourceType = detectStaticIndexSourceType();
        }
        return indexSourceType;
    }

    public void setSourceType(IndexSourceType sourceType) {
        this.indexSourceType = sourceType;
    }

    public ArchivedDataProcessingBehavior getArchivedDataProcessingBehavior() {
        return archivedDataProcessingBehavior;
    }

    public void setArchivedDataProcessingBehavior(ArchivedDataProcessingBehavior archivedDataProcessingBehavior) {
        this.archivedDataProcessingBehavior = archivedDataProcessingBehavior;
    }

    public IndexType getType() {
        if (indexType == null || indexType == IndexType.NONE) {
            indexType = detectStaticIndexType();
        }

        return indexType;
    }

    public void setType(IndexType indexType) {
        this.indexType = indexType;
    }

    public IndexSourceType detectStaticIndexSourceType() {
        if (maps == null || maps.isEmpty()) {
            throw new IllegalArgumentException("Index definition contains no Maps");
        }

        IndexSourceType sourceType = IndexSourceType.NONE;
        for (String map : maps) {
            IndexSourceType mapSourceType = IndexDefinitionHelper.detectStaticIndexSourceType(map);
            if (sourceType == IndexSourceType.NONE) {
                sourceType = mapSourceType;
                continue;
            }

            if (sourceType != mapSourceType) {
                throw new IllegalStateException("Index definition cannot contain maps with different source types.");
            }
        }
        return sourceType;
    }

    public IndexType detectStaticIndexType() {
        String firstMap = maps.iterator().next();

        if (firstMap == null) {
            throw new IllegalArgumentException("Index  definitions contains no Maps");
        }

        return IndexDefinitionHelper.detectStaticIndexType(firstMap, getReduce());
    }

    /**
     * If not null than each reduce result will be created as a document in the specified collection name.
     * @return true if index outputs should be saved to collection
     */
    public String getOutputReduceToCollection() {
        return outputReduceToCollection;
    }

    /**
     * If not null than each reduce result will be created as a document in the specified collection name.
     * @param outputReduceToCollection Sets the value
     */
    public void setOutputReduceToCollection(String outputReduceToCollection) {
        this.outputReduceToCollection = outputReduceToCollection;
    }

    /**
     * If not null then this number will be part of identifier of a created document being output of reduce function
     * @return output index
     */
    public Long getReduceOutputIndex() {
        return reduceOutputIndex;
    }

    /**
     * If not null then this number will be part of identifier of a created document being output of reduce function
     * @param reduceOutputIndex output index
     */
    public void setReduceOutputIndex(Long reduceOutputIndex) {
        this.reduceOutputIndex = reduceOutputIndex;
    }

    /**
     * Defines pattern for identifiers of documents which reference IDs of reduce outputs documents
     * @return pattern
     */
    public String getPatternForOutputReduceToCollectionReferences() {
        return patternForOutputReduceToCollectionReferences;
    }

    /**
     * Defines pattern for identifiers of documents which reference IDs of reduce outputs documents
     * @param patternForOutputReduceToCollectionReferences pattern
     */
    public void setPatternForOutputReduceToCollectionReferences(String patternForOutputReduceToCollectionReferences) {
        this.patternForOutputReduceToCollectionReferences = patternForOutputReduceToCollectionReferences;
    }

    /**
     * @return Defines a collection name for reference documents created based on provided pattern
     */
    public String getPatternReferencesCollectionName() {
        return patternReferencesCollectionName;
    }

    /**
     * @param patternReferencesCollectionName Defines a collection name for reference documents created based on provided pattern
     */
    public void setPatternReferencesCollectionName(String patternReferencesCollectionName) {
        this.patternReferencesCollectionName = patternReferencesCollectionName;
    }

    public IndexDeploymentMode getDeploymentMode() {
        return deploymentMode;
    }

    public void setDeploymentMode(IndexDeploymentMode deploymentMode) {
        this.deploymentMode = deploymentMode;
    }
}
