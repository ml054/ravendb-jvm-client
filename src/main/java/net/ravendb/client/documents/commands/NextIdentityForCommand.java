package net.ravendb.client.documents.commands;

import com.fasterxml.jackson.databind.JsonNode;
import net.ravendb.client.documents.conventions.DocumentConventions;
import net.ravendb.client.http.IBroadcast;
import net.ravendb.client.http.IRaftCommand;
import net.ravendb.client.http.RavenCommand;
import net.ravendb.client.http.ServerNode;
import net.ravendb.client.util.RaftIdGenerator;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;

import java.io.IOException;

public class NextIdentityForCommand extends RavenCommand<Long> implements IRaftCommand, IBroadcast {

    private final String _id;
    private String _raftUniqueRequestId = RaftIdGenerator.newId();

    public NextIdentityForCommand(String id) {
        super(Long.class);

        if (id == null) {
            throw new IllegalArgumentException("Id cannot be null");
        }

        _id = id;
    }

    @Override
    public boolean isReadRequest() {
        return false;
    }

    @Override
    public HttpUriRequestBase createRequest(ServerNode node) {
        ensureIsNotNullOrString(_id, "id");

        String url = node.getUrl() + "/databases/" + node.getDatabase() + "/identity/next?name=" + urlEncode(_id);

        return new HttpPost(url);
    }

    @Override
    public void setResponse(String response, boolean fromCache) throws IOException {
        if (response == null) {
            throwInvalidResponse();
        }

        JsonNode jsonNode = mapper.readTree(response);
        if (!jsonNode.has("NewIdentityValue")) {
            throwInvalidResponse();
        }

        result = jsonNode.get("NewIdentityValue").asLong();
    }

    @Override
    public String getRaftUniqueRequestId() {
        return _raftUniqueRequestId;
    }

    @Override
    public IBroadcast prepareToBroadcast(DocumentConventions conventions) {
        return new NextIdentityForCommand(this);
    }

    private NextIdentityForCommand(NextIdentityForCommand copy) {
        super(copy);

        _raftUniqueRequestId = copy._raftUniqueRequestId;
        _id = copy._id;
    }
}
