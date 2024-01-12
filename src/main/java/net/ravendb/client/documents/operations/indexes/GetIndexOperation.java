package net.ravendb.client.documents.operations.indexes;

import net.ravendb.client.documents.conventions.DocumentConventions;
import net.ravendb.client.documents.indexes.IndexDefinition;
import net.ravendb.client.documents.operations.IMaintenanceOperation;
import net.ravendb.client.documents.operations.ResultsResponse;
import net.ravendb.client.http.RavenCommand;
import net.ravendb.client.http.ServerNode;
import net.ravendb.client.util.UrlUtils;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;

import java.io.IOException;

public class GetIndexOperation implements IMaintenanceOperation<IndexDefinition> {

    private final String _indexName;

    public GetIndexOperation(String indexName) {
        if (indexName == null) {
            throw new IllegalArgumentException("IndexName cannot be null");
        }

        _indexName = indexName;
    }

    @Override
    public RavenCommand<IndexDefinition> getCommand(DocumentConventions conventions) {
        return new GetIndexCommand(_indexName);
    }

    private static class GetIndexCommand extends RavenCommand<IndexDefinition> {
        private final String _indexName;

        public GetIndexCommand(String indexName) {
            super(IndexDefinition.class);
            if (indexName == null) {
                throw new IllegalArgumentException("IndexName cannot be null");
            }

            _indexName = indexName;
        }

        @Override
        public HttpUriRequestBase createRequest(ServerNode node) {
            String url = node.getUrl() + "/databases/" + node.getDatabase()
                    + "/indexes?name=" + UrlUtils.escapeDataString(_indexName);

            return new HttpGet(url);
        }

        @Override
        public void setResponse(String response, boolean fromCache) throws IOException {
            if (response == null) {
                return;
            }

            result = mapper.readValue(response, ResultsResponse.GetIndexesResponse.class).getResults()[0];
        }

        @Override
        public boolean isReadRequest() {
            return true;
        }
    }
}
