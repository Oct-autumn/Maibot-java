package org.maibot.sdk.storage.db

import jakarta.persistence.EntityManager
import org.maibot.sdk.exceptions.DbOperationException
import org.slf4j.MDC
import java.util.function.Consumer

@Suppress("unused")
interface DatabaseService {
    @Throws(DbOperationException::class)
    fun <T> exec(func: (em: EntityManager) -> T): T

    @Throws(DbOperationException::class)
    fun <T> exec(func: Consumer<EntityManager>) {
        return exec { func.accept(it) }
    }

    companion object {
        fun <T> execInTransaction(em: EntityManager, func: () -> T): T {
            try {
                em.transaction.begin()
                MDC.put("db-ts-id", em.transaction.hashCode().toHexString())
                val res = func()
                em.transaction.commit()
                return res
            } catch (e: Exception) {
                if (em.transaction.isActive) {
                    em.transaction.rollback()
                }
                throw DbOperationException("Database transaction failed", e)
            } finally {
                MDC.remove("db-ts-id")
            }
        }
    }
}
