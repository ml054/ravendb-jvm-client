package net.ravendb.client.documents.session;

import net.ravendb.client.documents.indexes.AbstractCommonApiForIndexes;

/**
 * It gives the ability to construct queries with the usage of {@link IDocumentQuery} interface
 */
public interface IDocumentQueryBuilder {

    /**
     * Query the specified index
     * @param clazz  The result of the query
     * @param indexClazz The index class
     * @return DocumentQuery
     * @param <T> Class of query result
     * @param <TIndex> Class of index
     */
    <T, TIndex extends AbstractCommonApiForIndexes> IDocumentQuery<T> documentQuery(Class<T> clazz, Class<TIndex> indexClazz);

    /**
     * Query the specified index
     * @param <T> Class of query result
     * @param clazz The result of the query
     * @param indexName Name of the index (mutually exclusive with collectionName)
     * @param collectionName Name of the collection (mutually exclusive with indexName)
     * @param isMapReduce Whether we are querying a map/reduce index (modify how we treat identifier properties)
     * @return Document query
     */
    <T> IDocumentQuery<T> documentQuery(Class<T> clazz, String indexName, String collectionName, boolean isMapReduce);

    /**
     * Query the specified index
     * @param <T> Class of query result
     * @param clazz The result of the query
     * @return Document query
     */
    <T> IDocumentQuery<T> documentQuery(Class<T> clazz);

}
