package org.maibot.sdk.storage.db

import jakarta.persistence.EntityManager
import org.maibot.sdk.exceptions.DbOperationException
import java.util.concurrent.CompletableFuture
import java.util.function.Consumer

@Suppress("unused")
interface DatabaseService {
    @Throws(DbOperationException::class)
    fun <T> exec(func: (em: EntityManager) -> T): T

    fun <T> execAsync(func: (em: EntityManager) -> T): CompletableFuture<T>

    @Throws(DbOperationException::class)
    fun <T> exec(func: Consumer<EntityManager>) {
        return exec { func.accept(it) }
    }

    fun <T> execAsync(func: Consumer<EntityManager>): CompletableFuture<Unit> {
        return execAsync { func.accept(it) }
    }
}
