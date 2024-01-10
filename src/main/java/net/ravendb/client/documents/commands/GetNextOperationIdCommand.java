package net.ravendb.client.documents.commands;

import com.fasterxml.jackson.databind.JsonNode;
import net.ravendb.client.http.RavenCommand;
import net.ravendb.client.http.ServerNode;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.classic.methods.HttpGet;

import java.io.IOException;

public class GetNextOperationIdCommand extends RavenCommand<Long> {
    public GetNextOperationIdCommand() {
        super(Long.class);
    }

    private String _nodeTag;

    public String getNodeTag() {
        return _nodeTag;
    }

    @Override
    public boolean isReadRequest() {
        return false; // disable caching
    }

    @Override
    public HttpUriRequestBase createRequest(ServerNode node) {
        String url = node.getUrl() + "/databases/" + node.getDatabase() + "/operations/next-operation-id";

        return new HttpGet(url);
    }

    @Override
    public void setResponse(String response, boolean fromCache) throws IOException {
        JsonNode jsonNode = mapper.readTree(response);

        if (jsonNode.has("Id")) {
            result = jsonNode.get("Id").asLong();
        }

        if (jsonNode.has("NodeTag")) {
            _nodeTag = jsonNode.get("NodeTag").asText();
        }
    }
}
