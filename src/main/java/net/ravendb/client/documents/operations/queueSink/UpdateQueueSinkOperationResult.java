package net.ravendb.client.documents.operations.queueSink;

public class UpdateQueueSinkOperationResult {
    private long raftCommandIndex;
    private long taskId;

    public long getRaftCommandIndex() {
        return raftCommandIndex;
    }

    public void setRaftCommandIndex(long raftCommandIndex) {
        this.raftCommandIndex = raftCommandIndex;
    }

    public long getTaskId() {
        return taskId;
    }

    public void setTaskId(long taskId) {
        this.taskId = taskId;
    }
}
