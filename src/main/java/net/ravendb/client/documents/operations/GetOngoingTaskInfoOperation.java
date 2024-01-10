package net.ravendb.client.documents.operations;

import net.ravendb.client.documents.conventions.DocumentConventions;
import net.ravendb.client.documents.operations.ongoingTasks.*;
import net.ravendb.client.http.RavenCommand;
import net.ravendb.client.http.ServerNode;
import net.ravendb.client.primitives.SharpEnum;
import net.ravendb.client.util.UrlUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.classic.methods.HttpGet;

import java.io.IOException;

public class GetOngoingTaskInfoOperation implements IMaintenanceOperation<OngoingTask> {

    private final String _taskName;
    private final long _taskId;
    private final OngoingTaskType _type;

    public GetOngoingTaskInfoOperation(long taskId, OngoingTaskType type) {
        this(null, taskId, type);
    }

    public GetOngoingTaskInfoOperation(String taskName, OngoingTaskType type) {
        this(taskName, 0, type);
    }

    private GetOngoingTaskInfoOperation(String taskName, long taskId, OngoingTaskType type) {
        _taskName = taskName;
        _type = type;
        _taskId = taskId;

        if (type == OngoingTaskType.PULL_REPLICATION_AS_HUB) {
            throw new IllegalArgumentException(OngoingTaskType.PULL_REPLICATION_AS_HUB + " type is not supported. Please use GetPullReplicationTasksInfoOperation instead.");
        }
    }

    @Override
    public RavenCommand<OngoingTask> getCommand(DocumentConventions conventions) {
        if (_taskName != null) {
            return new GetOngoingTaskInfoCommand(_taskName, _type);
        }

        return new GetOngoingTaskInfoCommand(_taskId, _type);
    }

    private static class GetOngoingTaskInfoCommand extends RavenCommand<OngoingTask> {
        private final String _taskName;
        private final long _taskId;
        private final OngoingTaskType _type;

        public GetOngoingTaskInfoCommand(long taskId, OngoingTaskType type) {
            super(OngoingTask.class);

            _taskId = taskId;
            _type = type;
            _taskName = null;
        }

        public GetOngoingTaskInfoCommand(String taskName, OngoingTaskType type) {
            super(OngoingTask.class);

            if (StringUtils.isEmpty(taskName)) {
                throw new IllegalArgumentException("Value cannot be empty");
            }

            _taskName = taskName;
            _type = type;
            _taskId = 0;
        }

        @Override
        public HttpUriRequestBase createRequest(ServerNode node) {
            String url = _taskName != null ?
                    node.getUrl() + "/databases/" + node.getDatabase() + "/task?taskName=" + UrlUtils.escapeDataString(_taskName) + "&type=" + SharpEnum.value(_type) :
                    node.getUrl() + "/databases/" + node.getDatabase() + "/task?key=" + _taskId + "&type=" + SharpEnum.value(_type);

            return new HttpGet(url);
        }

        @Override
        public void setResponse(String response, boolean fromCache) throws IOException {
            if (response != null) {
                switch (_type) {
                    case REPLICATION:
                        result = mapper.readValue(response, OngoingTaskReplication.class);
                        break;
                    case RAVEN_ETL:
                        result = mapper.readValue(response, OngoingTaskRavenEtl.class);
                        break;
                    case SQL_ETL:
                        result = mapper.readValue(response, OngoingTaskSqlEtl.class);
                        break;
                    case BACKUP:
                        result = mapper.readValue(response, OngoingTaskBackup.class);
                        break;
                    case SUBSCRIPTION:
                        result = mapper.readValue(response, OngoingTaskSubscription.class);
                        break;
                    case OLAP_ETL:
                        result = mapper.readValue(response, OngoingTaskOlapEtl.class);
                        break;
                    case ELASTIC_SEARCH_ETL:
                        result = mapper.readValue(response, OngoingTaskElasticSearchEtl.class);
                        break;
                    case QUEUE_ETL:
                        result = mapper.readValue(response, OngoingTaskQueueEtl.class);
                        break;
                    case PULL_REPLICATION_AS_SINK:
                        result = mapper.readValue(response, OngoingTaskPullReplicationAsSink.class);
                        break;
                    case PULL_REPLICATION_AS_HUB:
                        result = mapper.readValue(response, OngoingTaskPullReplicationAsHub.class);
                        break;
                    default:
                        throw new IllegalStateException();
                }
            }
        }

        @Override
        public boolean isReadRequest() {
            return false;
        }
    }
}
