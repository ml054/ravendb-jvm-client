package net.ravendb.client.documents.operations.connectionStrings;

import net.ravendb.client.documents.conventions.DocumentConventions;
import net.ravendb.client.documents.operations.IMaintenanceOperation;
import net.ravendb.client.http.RavenCommand;
import net.ravendb.client.http.ServerNode;
import net.ravendb.client.primitives.SharpEnum;
import net.ravendb.client.serverwide.ConnectionStringType;
import net.ravendb.client.util.UrlUtils;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;

import java.io.IOException;

public class GetConnectionStringsOperation implements IMaintenanceOperation<GetConnectionStringsResult> {
    private final String _connectionStringName;
    private final ConnectionStringType _type;

    public GetConnectionStringsOperation(String connectionStringName, ConnectionStringType type) {
        _connectionStringName = connectionStringName;
        _type = type;
    }

    public GetConnectionStringsOperation() {
        _connectionStringName = null;
        _type = null;
        // get them all
    }

    @Override
    public RavenCommand<GetConnectionStringsResult> getCommand(DocumentConventions conventions) {
        return new GetConnectionStringCommand(_connectionStringName, _type);
    }

    private static class GetConnectionStringCommand extends RavenCommand<GetConnectionStringsResult> {
        private final String _connectionStringName;
        private final ConnectionStringType _type;

        public GetConnectionStringCommand(String connectionStringName, ConnectionStringType type) {
            super(GetConnectionStringsResult.class);
            _connectionStringName = connectionStringName;
            _type = type;
        }

        @Override
        public boolean isReadRequest() {
            return true;
        }

        @Override
        public HttpUriRequestBase createRequest(ServerNode node) {
            String url = node.getUrl() + "/databases/" + node.getDatabase() + "/admin/connection-strings";
            if (_connectionStringName != null) {
                url += "?connectionStringName=" + UrlUtils.escapeDataString(_connectionStringName) + "&type=" + SharpEnum.value(_type);
            }

            return new HttpGet(url);
        }

        @Override
        public void setResponse(String response, boolean fromCache) throws IOException {
            if (response == null) {
                throwInvalidResponse();
            }

            result = mapper.readValue(response, resultClass);
        }
    }
}
