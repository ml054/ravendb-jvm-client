package net.ravendb.client.documents.operations.ongoingTasks;

import net.ravendb.client.documents.conventions.DocumentConventions;
import net.ravendb.client.documents.operations.IMaintenanceOperation;
import net.ravendb.client.http.IRaftCommand;
import net.ravendb.client.http.RavenCommand;
import net.ravendb.client.http.ServerNode;
import net.ravendb.client.primitives.SharpEnum;
import net.ravendb.client.serverwide.operations.ModifyOngoingTaskResult;
import net.ravendb.client.util.RaftIdGenerator;
import org.apache.hc.client5.http.classic.methods.HttpDelete;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;

import java.io.IOException;

public class DeleteOngoingTaskOperation implements IMaintenanceOperation<ModifyOngoingTaskResult> {
    private final long _taskId;
    private final OngoingTaskType _taskType;

    public DeleteOngoingTaskOperation(long taskId, OngoingTaskType taskType) {
        _taskId = taskId;
        _taskType = taskType;
    }

    @Override
    public RavenCommand<ModifyOngoingTaskResult> getCommand(DocumentConventions conventions) {
        return new DeleteOngoingTaskCommand(_taskId, _taskType);
    }

    private static class DeleteOngoingTaskCommand extends RavenCommand<ModifyOngoingTaskResult> implements IRaftCommand {
        private final long _taskId;
        private final OngoingTaskType _taskType;

        public DeleteOngoingTaskCommand(long taskId, OngoingTaskType taskType) {
            super(ModifyOngoingTaskResult.class);

            _taskId = taskId;
            _taskType = taskType;
        }

        @Override
        public HttpUriRequestBase createRequest(ServerNode node) {
            String url = node.getUrl() + "/databases/" + node.getDatabase() + "/admin/tasks?id=" + _taskId + "&type=" + SharpEnum.value(_taskType);

            return new HttpDelete(url);
        }

        @Override
        public void setResponse(String response, boolean fromCache) throws IOException {
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
}
