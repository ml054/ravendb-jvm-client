package net.ravendb.client.documents.operations.indexes;

import net.ravendb.client.documents.conventions.DocumentConventions;
import net.ravendb.client.documents.operations.IMaintenanceOperation;
import net.ravendb.client.documents.queries.TermsQueryResult;
import net.ravendb.client.http.RavenCommand;
import net.ravendb.client.http.ServerNode;
import net.ravendb.client.util.UrlUtils;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;

import java.io.IOException;

public class GetTermsOperation implements IMaintenanceOperation<String[]> {

    private final String _indexName;
    private final String _field;
    private final String _fromValue;
    private final Integer _pageSize;

    public GetTermsOperation(String indexName, String field, String fromValue) {
        this(indexName, field, fromValue, null);
    }

    public GetTermsOperation(String indexName, String field, String fromValue, Integer pageSize) {
        if (indexName == null) {
            throw new IllegalArgumentException("IndexName cannot be null");
        }

        if (field == null) {
            throw new IllegalArgumentException("Field cannot be null");
        }

        _indexName = indexName;
        _field = field;
        _fromValue = fromValue;
        _pageSize = pageSize;
    }

    @Override
    public RavenCommand<String[]> getCommand(DocumentConventions conventions) {
        return new GetTermsCommand(_indexName, _field, _fromValue, _pageSize);
    }

    private static class GetTermsCommand extends RavenCommand<String[]> {
        private final String _indexName;
        private final String _field;
        private final String _fromValue;
        private final Integer _pageSize;

        public GetTermsCommand(String indexName, String field, String fromValue) {
            this(indexName, field, fromValue, null);
        }

        public GetTermsCommand(String indexName, String field, String fromValue, Integer pageSize) {
            super(String[].class);
            if (indexName == null) {
                throw new IllegalArgumentException("IndexName cannot be null");
            }

            if (field == null) {
                throw new IllegalArgumentException("Field cannot be null");
            }

            _indexName = indexName;
            _field = field;
            _fromValue = fromValue;
            _pageSize = pageSize;
        }

        @Override
        public HttpUriRequestBase createRequest(ServerNode node) {
            String url = node.getUrl() + "/databases/" + node.getDatabase()
                    + "/indexes/terms?name=" + UrlUtils.escapeDataString(_indexName)
                    + "&field=" + UrlUtils.escapeDataString(_field)
                    + "&fromValue=" + (_fromValue != null ? _fromValue : "")
                    + "&pageSize=" + (_pageSize != null ? _pageSize : "");

            return new HttpGet(url);
        }

        @Override
        public void setResponse(String response, boolean fromCache) throws IOException {
            if (response == null) {
                throwInvalidResponse();
            }

            TermsQueryResult termResult = mapper.readValue(response, TermsQueryResult.class);
            result = termResult.getTerms().toArray(new String[0]);
        }

        @Override
        public boolean isReadRequest() {
            return true;
        }
    }
}
