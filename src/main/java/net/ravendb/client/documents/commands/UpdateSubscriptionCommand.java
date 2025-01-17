package net.ravendb.client.documents.commands;

import com.fasterxml.jackson.core.JsonGenerator;
import net.ravendb.client.documents.subscriptions.SubscriptionUpdateOptions;
import net.ravendb.client.documents.subscriptions.UpdateSubscriptionResult;
import net.ravendb.client.http.IRaftCommand;
import net.ravendb.client.http.RavenCommand;
import net.ravendb.client.http.ServerNode;
import net.ravendb.client.json.ContentProviderHttpEntity;
import net.ravendb.client.primitives.Reference;
import net.ravendb.client.util.RaftIdGenerator;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.entity.ContentType;

import java.io.IOException;

public class UpdateSubscriptionCommand extends RavenCommand<UpdateSubscriptionResult> implements IRaftCommand {
    private final SubscriptionUpdateOptions _options;

    public UpdateSubscriptionCommand(SubscriptionUpdateOptions options) {
        super(UpdateSubscriptionResult.class);
        _options = options;
    }

    @Override
    public HttpRequestBase createRequest(ServerNode node, Reference<String> url) {
        url.value = node.getUrl() + "/databases/" + node.getDatabase() + "/subscriptions/update";

        HttpPost request = new HttpPost();
        request.setEntity(new ContentProviderHttpEntity(outputStream -> {
            try (JsonGenerator generator = createSafeJsonGenerator(outputStream)) {
                generator.getCodec().writeValue(generator, _options);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }, ContentType.APPLICATION_JSON));

        return request;
    }

    @Override
    public void setResponse(String response, boolean fromCache) throws IOException {
        if (fromCache) {
            result = new UpdateSubscriptionResult();
            result.setName(_options.getName());

            return;
        }

        if (response == null) {
            throwInvalidResponse();
        }

        result = mapper.readValue(response, resultClass);
    }

    @Override
    public boolean isReadRequest() {
        return false;
    }

    @Override
    public String getRaftUniqueRequestId() {
        return RaftIdGenerator.newId();
    }
}
