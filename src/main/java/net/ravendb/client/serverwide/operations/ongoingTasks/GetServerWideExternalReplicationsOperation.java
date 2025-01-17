package net.ravendb.client.serverwide.operations.ongoingTasks;

import net.ravendb.client.documents.conventions.DocumentConventions;
import net.ravendb.client.documents.operations.ResultsResponse;
import net.ravendb.client.documents.operations.ongoingTasks.OngoingTaskType;
import net.ravendb.client.http.RavenCommand;
import net.ravendb.client.http.ServerNode;
import net.ravendb.client.primitives.Reference;
import net.ravendb.client.primitives.SharpEnum;
import net.ravendb.client.serverwide.operations.IServerOperation;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpRequestBase;

import java.io.IOException;

public class GetServerWideExternalReplicationsOperation implements IServerOperation<ServerWideExternalReplication[]> {
    @Override
    public RavenCommand<ServerWideExternalReplication[]> getCommand(DocumentConventions conventions) {
        return new GetServerWideExternalReplicationsCommand();
    }

    private static class GetServerWideExternalReplicationsCommand extends RavenCommand<ServerWideExternalReplication[]> {

        public GetServerWideExternalReplicationsCommand() {
            super(ServerWideExternalReplication[].class);
        }

        @Override
        public boolean isReadRequest() {
            return true;
        }

        @Override
        public HttpRequestBase createRequest(ServerNode node, Reference<String> url) {
            url.value = node.getUrl() + "/admin/configuration/server-wide/tasks?type=" + SharpEnum.value(OngoingTaskType.REPLICATION);

            return new HttpGet();
        }

        @Override
        public void setResponse(String response, boolean fromCache) throws IOException {
            if (response == null) {
                return;
            }

            result = mapper.readValue(response, ResultsResponse.GetServerWideExternalReplicationsResponse.class).getResults();
        }
    }
}