package net.ravendb.client.documents.changes;

import net.ravendb.client.primitives.CleanCloseable;
import net.ravendb.client.primitives.EventHandler;
import net.ravendb.client.primitives.VoidArgs;

import java.util.function.Consumer;

public interface IConnectableChanges<TChanges> extends CleanCloseable {

    boolean isConnected();

    TChanges ensureConnectedNow();

    void addConnectionStatusChanged(EventHandler<VoidArgs> handler);

    void removeConnectionStatusChanged(EventHandler<VoidArgs> handler);

    void addOnError(Consumer<Exception> handler);

    void removeOnError(Consumer<Exception> handler);
}
