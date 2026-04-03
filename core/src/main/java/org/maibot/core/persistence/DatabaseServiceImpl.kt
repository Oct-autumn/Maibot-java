package org.maibot.core.persistence

import io.github.classgraph.ClassGraph
import jakarta.persistence.Entity
import jakarta.persistence.EntityManager
import jakarta.persistence.EntityManagerFactory
import jakarta.persistence.PersistenceConfiguration
import org.flywaydb.core.Flyway
import org.flywaydb.core.api.FlywayException
import org.flywaydb.core.api.exception.FlywayValidateException
import org.hibernate.jpa.HibernatePersistenceProvider
import org.maibot.core.cache.GlobalCacheManagerImpl
import org.maibot.core.util.TaskExecuteServiceImpl
import org.maibot.sdk.exceptions.DbOperationException
import org.maibot.sdk.exceptions.FatalError
import org.maibot.sdk.exceptions.NotInitialized
import org.maibot.sdk.ioc.AutoInject
import org.maibot.sdk.ioc.Component
import org.maibot.sdk.ioc.DestroyableComponent
import org.maibot.sdk.storage.db.DatabaseService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.CompletableFuture

@Component
class DatabaseServiceImpl
@AutoInject private constructor(
    private val taskExecutorService: TaskExecuteServiceImpl, globalCacheManager: GlobalCacheManagerImpl
) : DestroyableComponent, DatabaseService {
    private var entityManagerFactory: EntityManagerFactory? = null

    init {
        this.init(globalCacheManager)
    }

    /**
     * 初始化数据库管理器（独立方法，用于热重载）
     *
     * @param globalCacheManager 全局缓存管理器
     */
    fun init(globalCacheManager: GlobalCacheManagerImpl) {
        // 获取配置
        val cfg = sQLiteConfiguration.apply {
            // 二级缓存使用的缓存管理器
            property("hibernate.javax.cache.cache_manager", globalCacheManager.cacheManager())

            // Debug: 开发时开启 SQL 日志
            property("hibernate.show_sql", "true")
            property("hibernate.format_sql", "true")
        }

        try {
            flywayCheckAndMigrate(cfg)
        } catch (e: FlywayValidateException) {
            log.error("数据库版本验证失败，可能是由于数据库文件损坏或版本过旧引起的")
            throw FatalError("Database migration validation failed. Please check the database state.", e)
        } catch (e: FlywayException) {
            throw FatalError("Database migration failed", e)
        }

        // 注册实体类
        val entityClasses = HashSet<Class<*>>()
        val classLoader = Thread.currentThread().getContextClassLoader()
        try {
            ClassGraph().overrideClassLoaders(classLoader)
                .acceptPackages("org.maibot")
                .enableAllInfo()
                .scan()
                .use { findResult ->
                    val entityClassInfo = findResult.getClassesWithAnnotation(Entity::class.java)
                    for (classInfo in entityClassInfo) {
                        val clazz = Class.forName(classInfo.getName(), false, classLoader)
                        entityClasses.add(clazz)
                    }
                }
        } catch (_: ClassNotFoundException) {
            // 不可能发生，因为 ClassGraph 和 Class.forName 使用的是同一个类加载器
            // ClassGraph 已经确保了类的存在
        }

        entityClasses.forEach { clazz ->
            log.debug("Registering entity class: {}", clazz.getName())
            cfg.managedClass(clazz)
        }

        this.entityManagerFactory = HibernatePersistenceProvider().createEntityManagerFactory(cfg)
    }

    /**
     * 使用 Flyway 检查并迁移数据库
     *
     * @param cfg 持久化配置
     * @throws FlywayException 如果验证&迁移过程中发生错误
     */
    private fun flywayCheckAndMigrate(cfg: PersistenceConfiguration) {
        val properties = cfg.properties()
        val url = properties["hibernate.connection.url"]!!
        val user = properties["hibernate.connection.username"]
        val password = properties["hibernate.connection.password"]

        val flyway = Flyway.configure().dataSource(
            url.toString(), user?.toString(), password?.toString()
        ).communityDBSupportEnabled(true).locations("classpath:org/maibot/core/db_migration").load()

        flyway.migrate()
    }

    override fun <T> exec(func: (em: EntityManager) -> T): T {
        return this.entityManagerFactory?.createEntityManager()?.use { em ->
            try {
                em.transaction.begin()
                val res = em.run(func)
                em.transaction.commit()
                return@use res
            } catch (e: Throwable) {
                if (em.transaction.isActive) {
                    em.transaction.rollback()
                }
                throw DbOperationException("Database operation failed", e)
            }
        } ?: throw NotInitialized("DatabaseService is not initialized. Please call init() before using it.")
    }

    override fun <T> execAsync(func: (em: EntityManager) -> T): CompletableFuture<T> {
        return this.taskExecutorService.submit(false) { exec(func) }
    }

    /**
     * 关闭数据库
     */
    override fun preDestroy() {
        this.entityManagerFactory?.run {
            try {
                close()
            } catch (e: Exception) {
                log.error("关闭数据库服务时发生错误", e)
            }
        }
        this.entityManagerFactory = null
    }

    companion object {
        private val log: Logger = LoggerFactory.getLogger(DatabaseServiceImpl::class.java)

        private val sQLiteConfiguration: PersistenceConfiguration
            get() {
                val cfg = PersistenceConfiguration("maibot-sqlite-pu")

                cfg.property("hibernate.connection.url", "jdbc:sqlite:data/maibot.db")

                // 检查sqlitePath文件是否存在，不存在则创建
                val dbFilePath = Path.of("data/maibot.db")
                dbFilePath.toFile().let { dbFile ->
                    if (!dbFile.exists()) {
                        val parent = dbFile.getParentFile()
                        if (parent != null && !parent.exists()) {
                            if (!parent.mkdirs()) {
                                throw FatalError(
                                    "Failed to create directories for database file: %s", dbFilePath
                                )
                            }
                        }
                        try {
                            dbFile.createNewFile()
                        } catch (e: IOException) {
                            throw FatalError("Failed to create database file: %s", dbFilePath, e)
                        }
                    }
                }


                return cfg
            }
    }
}