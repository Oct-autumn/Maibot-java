package org.maibot.core.persistence;

import io.github.classgraph.ClassGraph;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceConfiguration;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.exception.FlywayValidateException;
import org.hibernate.jpa.HibernatePersistenceProvider;
import org.maibot.core.cache.GlobalCacheManagerImpl;
import org.maibot.core.util.TaskExecuteServiceImpl;
import org.maibot.sdk.exceptions.DbOperationException;
import org.maibot.sdk.exceptions.FatalError;
import org.maibot.sdk.exceptions.NotInitialized;
import org.maibot.sdk.ioc.AutoInject;
import org.maibot.sdk.ioc.Component;
import org.maibot.sdk.ioc.DestroyableComponent;
import org.maibot.sdk.storage.db.DatabaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;

@Component
public class DatabaseServiceImpl implements DestroyableComponent, DatabaseService {
    private static final Logger log = LoggerFactory.getLogger(DatabaseServiceImpl.class);

    private final TaskExecuteServiceImpl taskExecutorService;

    private EntityManagerFactory entityManagerFactory = null;

    @AutoInject
    DatabaseServiceImpl(TaskExecuteServiceImpl taskExecutorService, GlobalCacheManagerImpl globalCacheManager) {
        this.taskExecutorService = taskExecutorService;
        this.init(globalCacheManager);
    }

    /**
     * 初始化数据库管理器（独立方法，用于热重载）
     *
     * @param globalCacheManager 全局缓存管理器
     */
    public void init(GlobalCacheManagerImpl globalCacheManager) {
        // 获取配置
        var cfg = getSQLiteConfiguration();

        // 二级缓存使用的缓存管理器
        cfg.property("hibernate.javax.cache.cache_manager", globalCacheManager.cacheManager());

        // Debug: 开发时开启 SQL 日志
        cfg.property("hibernate.show_sql", "true");
        cfg.property("hibernate.format_sql", "true");


        try {
            flywayCheckAndMigrate(cfg);
        } catch (FlywayValidateException e) {
            log.error("数据库版本验证失败，可能是由于数据库文件损坏或版本过旧引起的");
            throw new FatalError("Database migration validation failed. Please check the database state.", e);
        } catch (FlywayException e) {
            throw new FatalError("Database migration failed", e);
        }

        // 注册实体类
        Set<Class<?>> entityClasses = new HashSet<>();
        var classLoader = Thread.currentThread().getContextClassLoader();
        try (var findResult = new ClassGraph().overrideClassLoaders(classLoader)
          .acceptPackages("org.maibot")
          .enableAllInfo()
          .scan()) {
            var entityClassInfo = findResult.getClassesWithAnnotation(Entity.class);
            for (var classInfo : entityClassInfo) {
                var clazz = Class.forName(classInfo.getName(), false, classLoader);
                entityClasses.add(clazz);
            }
        } catch (ClassNotFoundException ignored) {
            // 不可能发生，因为 ClassGraph 和 Class.forName 使用的是同一个类加载器
            // ClassGraph 已经确保了类的存在
        }

        entityClasses.forEach(clazz -> {
            log.debug("Registering entity class: {}", clazz.getName());
            cfg.managedClass(clazz);
        });

        this.entityManagerFactory = new HibernatePersistenceProvider().createEntityManagerFactory(cfg);
    }

    /**
     * 使用 Flyway 检查并迁移数据库
     *
     * @param cfg 持久化配置
     * @throws FlywayException 如果验证&迁移过程中发生错误
     */
    private void flywayCheckAndMigrate(PersistenceConfiguration cfg)
    throws FlywayException {
        var properties = cfg.properties();
        var url = requireNonNull(properties.get("hibernate.connection.url"));
        var user = properties.get("hibernate.connection.username");
        var pwd = properties.get("hibernate.connection.password");

        var flyway = Flyway.configure().dataSource(
          url.toString(),
          user == null ? null : user.toString(),
          pwd == null ? null : pwd.toString()
        ).communityDBSupportEnabled(true).locations("classpath:org/maibot/core/db_migration").load();

        flyway.migrate();
    }

    private static PersistenceConfiguration getSQLiteConfiguration() {
        var cfg = new PersistenceConfiguration("maibot-sqlite-pu");

        cfg.property("hibernate.connection.url", "jdbc:sqlite:data/maibot.db");

        // 检查sqlitePath文件是否存在，不存在则创建
        var dbFilePath = Path.of("data/maibot.db");
        var dbFile = dbFilePath.toFile();
        if (!dbFile.exists()) {
            var parent = dbFile.getParentFile();
            if (parent != null && !parent.exists()) {
                if (!parent.mkdirs()) {
                    throw new FatalError("Failed to create directories for database file: %s", dbFilePath);
                }
            }
            try {
                var ignore = dbFile.createNewFile();
            } catch (IOException e) {
                throw new FatalError("Failed to create database file: %s", dbFilePath, e);
            }
        }

        return cfg;
    }

    @Override
    public <T> T exec(Function<EntityManager, T> func)
    throws DbOperationException {
        if (this.entityManagerFactory == null) {
            throw new NotInitialized("DatabaseService is not initialized. Please call init() before using it.");
        }

        var em = this.entityManagerFactory.createEntityManager();

        try {
            em.getTransaction().begin();
            var res = func.apply(em);
            em.getTransaction().commit();
            return res;
        } catch (Throwable e) {
            if (em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            }
            throw new DbOperationException("Database operation failed", e);
        } finally {
            em.close();
        }
    }

    @Override
    public <T> CompletableFuture<T> execAsync(Function<EntityManager, T> func) {
        return this.taskExecutorService.submit(() -> exec(func), false);
    }

    @Override
    public CompletableFuture<Object> execAsync(Consumer<EntityManager> func) {
        return this.taskExecutorService.submit(() -> exec(func), false);
    }

    @Override
    public void exec(Consumer<EntityManager> func)
    throws DbOperationException {
        if (this.entityManagerFactory == null) {
            throw new NotInitialized("DatabaseService is not initialized. Please call init() before using it.");
        }

        var em = this.entityManagerFactory.createEntityManager();

        try {
            em.getTransaction().begin();
            func.accept(em);
            em.getTransaction().commit();
        } catch (Throwable e) {
            if (em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            }
            throw new DbOperationException("Database operation failed", e);
        } finally {
            em.close();
        }
    }

    /**
     * 关闭数据库
     */
    @Override
    public void preDestroy() {
        if (this.entityManagerFactory != null) {
            try {
                this.entityManagerFactory.close();
            } catch (Exception e) {
                log.error("关闭数据库服务时发生错误", e);
            }
            this.entityManagerFactory = null;
        }
    }
}