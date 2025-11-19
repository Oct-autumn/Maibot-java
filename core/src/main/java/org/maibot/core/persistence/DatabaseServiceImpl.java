package org.maibot.core.persistence;

import io.github.classgraph.ClassGraph;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceConfiguration;
import org.hibernate.jpa.HibernatePersistenceProvider;
import org.maibot.core.cache.GlobalCacheManagerImpl;
import org.maibot.core.config.MainConfig;
import org.maibot.core.util.TaskExecuteServiceImpl;
import org.maibot.sdk.storage.db.DatabaseService;
import org.maibot.sdk.storage.db.dao.DatabaseVersion;
import org.maibot.sdk.exceptions.DbOperationException;
import org.maibot.sdk.exceptions.FatalError;
import org.maibot.sdk.exceptions.NotInitialized;
import org.maibot.sdk.ioc.AutoInject;
import org.maibot.sdk.ioc.Component;
import org.maibot.sdk.ioc.DestroyableComponent;
import org.maibot.sdk.ioc.Value;
import org.semver4j.Semver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;

@Component
public class DatabaseServiceImpl implements DestroyableComponent, DatabaseService {
    private static final Logger log         = LoggerFactory.getLogger(DatabaseServiceImpl.class);
    private static final Semver SUPPORT_VER = new Semver("0.1.0");

    private final TaskExecuteServiceImpl taskExecutorService;

    private EntityManagerFactory entityManagerFactory = null;

    @AutoInject
    DatabaseServiceImpl(
      @Value("${local_data.database}") MainConfig.LocalData.Database conf,
      TaskExecuteServiceImpl taskExecutorService,
      GlobalCacheManagerImpl globalCacheManager
    ) {
        this.taskExecutorService = taskExecutorService;
        this.init(conf, globalCacheManager);
    }

    /**
     * 初始化数据库管理器（独立方法，用于热重载）
     *
     * @param conf 数据库配置
     */
    public void init(MainConfig.LocalData.Database conf, GlobalCacheManagerImpl globalCacheManager) {
        // 检查sqlitePath文件是否存在，不存在则创建

        var dbFile = new File(conf.sqlitePath());
        if (!dbFile.exists()) {
            var parent = dbFile.getParentFile();
            if (parent != null && !parent.exists()) {
                if (!parent.mkdirs()) {
                    throw new FatalError("Failed to create directories for database file: %s", conf.sqlitePath());
                }
            }
            try {
                var res = dbFile.createNewFile();
            } catch (IOException e) {
                throw new FatalError("Failed to create database file: %s", conf.sqlitePath(), e);
            }
        }

        // 获取配置
        var cfg = getDbConfiguration(conf, globalCacheManager);

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

        // 检查数据库版本
        Semver dbVer = getDbVer();
        if (!dbVer.isApiCompatible(SUPPORT_VER)) {
            log.warn("数据库版本与应用程序不兼容。需要: {}, 现有: {}", SUPPORT_VER.getVersion(), dbVer.getVersion());
            throw new FatalError(
              "Database version is not compatible with application. Required: %s, Found: %s",
              SUPPORT_VER.getVersion(),
              dbVer.getVersion()
            );
        }
    }

    private static PersistenceConfiguration getDbConfiguration(
      MainConfig.LocalData.Database conf,
      GlobalCacheManagerImpl globalCacheManager
    ) {
        var cfg = new PersistenceConfiguration("maibot-pu");
        // SQLite 配置
        // TODO: 对其他数据库的支持
        cfg.property("hibernate.connection.driver_class", "org.sqlite.JDBC");
        cfg.property("hibernate.connection.url", "jdbc:sqlite:" + conf.sqlitePath());
        cfg.property("hibernate.dialect", "org.hibernate.community.dialect.SQLiteDialect");
        cfg.property("hibernate.hbm2ddl.auto", "update");
        cfg.property("hibernate.c3p0.min_size", 1);
        cfg.property("hibernate.c3p0.max_size", 1);
        cfg.property("hibernate.c3p0.timeout", 0);

        cfg.property("hibernate.cache.use_second_level_cache", "true");
        cfg.property("hibernate.cache.region.factory_class", "org.hibernate.cache.jcache.JCacheRegionFactory");
        cfg.property("hibernate.javax.cache.cache_manager", globalCacheManager.cacheManager());

        // 开发时开启 SQL 日志
        cfg.property("hibernate.show_sql", "true");
        cfg.property("hibernate.format_sql", "true");

        return cfg;
    }

    private Semver getDbVer() {
        try {
            return this.exec(em -> {
                // 查询版本号
                DatabaseVersion ver = em.find(DatabaseVersion.class, 0L);

                if (ver == null) {
                    ver = new DatabaseVersion();
                    ver.setId(0L);
                    ver.setVersion(SUPPORT_VER.getVersion());

                    em.persist(ver);

                    return SUPPORT_VER;
                } else {
                    return new Semver(ver.getVersion());
                }
            });
        } catch (DbOperationException e) {
            log.warn("获取数据库版本时发生错误，假定版本为0.0.0", e);
            return new Semver("0.0.0");
        }
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