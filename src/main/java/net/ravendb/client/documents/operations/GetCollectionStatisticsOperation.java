package net.ravendb.client.documents.operations;

import net.ravendb.client.documents.conventions.DocumentConventions;
import net.ravendb.client.http.RavenCommand;
import net.ravendb.client.http.ServerNode;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.classic.methods.HttpGet;

import java.io.IOException;

public class GetCollectionStatisticsOperation implements IMaintenanceOperation<CollectionStatistics> {

    @Override
    public RavenCommand<CollectionStatistics> getCommand(DocumentConventions conventions) {
        return new GetCollectionStatisticsCommand();
    }

    private static class GetCollectionStatisticsCommand extends RavenCommand<CollectionStatistics> {

        public GetCollectionStatisticsCommand() {
            super(CollectionStatistics.class);
        }

        @Override
        public boolean isReadRequest() {
            return true;
        }

        @Override
        public HttpUriRequestBase createRequest(ServerNode node) {
            String url = node.getUrl() + "/databases/" + node.getDatabase() + "/collections/stats";

            return new HttpGet(url);
        }

        @Override
        public void setResponse(String response, boolean fromCache) throws IOException {
            if (response == null) {
                throwInvalidResponse();
            }

            result = mapper.readValue(response, CollectionStatistics.class);
        }
    }
}
