package org.maibot.sdk.db;

import jakarta.persistence.EntityManager;
import org.maibot.sdk.exceptions.DbOperationException;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;


public interface DatabaseService {
    <T> T exec(Function<EntityManager, T> func)
    throws DbOperationException;

    <T> CompletableFuture<T> execAsync(Function<EntityManager, T> func);

    CompletableFuture<Object> execAsync(Consumer<EntityManager> func);

    void exec(Consumer<EntityManager> func)
    throws DbOperationException;
}
