package org.maibot.core.db;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceConfiguration;
import jakarta.persistence.criteria.CriteriaBuilder;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.jpa.HibernatePersistenceProvider;
import org.maibot.core.config.MainConfig;
import org.maibot.core.db.dao.DatabaseVersion;
import org.maibot.core.cdi.annotation.AutoInject;
import org.maibot.core.cdi.annotation.Component;
import org.maibot.core.cdi.annotation.Value;
import org.semver4j.Semver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.URL;
import java.util.*;
import java.util.function.BiFunction;
import java.util.function.Function;

@Component
public class DatabaseService {
    private static final Logger log = LoggerFactory.getLogger(DatabaseService.class);
    private static final Semver SUPPORT_VER = new Semver("0.1.0");

    private EntityManagerFactory entityManagerFactory = null;

    @AutoInject
    DatabaseService(@Value("${local_data.database}") MainConfig.LocalData.Database conf) {
        this.init(conf);
    }

    /**
     * 自动扫描实体类
     *
     * @return 实体类列表
     */
    private static List<Class<?>> scanEntityClasses(String packageName) {
        // 通过Reflect自动扫描指定包下被@Entity注解的类
        Enumeration<URL> dirs;
        try {
            dirs = Thread.currentThread().getContextClassLoader().getResources(packageName.replace(".", "/"));
            while (dirs.hasMoreElements()) {
                var url = dirs.nextElement();
                var dir = new File(url.getFile());
                if (dir.isDirectory()) {
                    var classes = new java.util.ArrayList<Class<?>>();
                    for (var file : Objects.requireNonNull(dir.listFiles())) {
                        if (file.getName().endsWith(".class")) {
                            var className = packageName + '.' + file.getName().substring(0, file.getName().length() - 6);
                            var clazz = Class.forName(className);
                            if (clazz.isAnnotationPresent(jakarta.persistence.Entity.class)) {
                                classes.add(clazz);
                                log.debug("Found entity class: {}", className);
                            }
                        }
                    }
                    return classes;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to scan entity classes in package: " + packageName, e);
        }
        return List.of();
    }

    private static PersistenceConfiguration getDbConfiguration(MainConfig.LocalData.Database conf) {
        var cfg = new PersistenceConfiguration("maibot-pu");
        // SQLite 配置
        // TODO: 对其他数据库的支持
        cfg.property("hibernate.connection.driver_class", "org.sqlite.JDBC");
        cfg.property("hibernate.connection.url", "jdbc:sqlite:" + conf.sqlitePath);
        cfg.property("hibernate.dialect", "org.hibernate.community.dialect.SQLiteDialect");
        cfg.property("hibernate.c3p0.min_size", 1);
        cfg.property("hibernate.c3p0.max_size", 1);
        cfg.property("hibernate.c3p0.timeout", 0);

        // 开发时开启 SQL 日志
        cfg.property("hibernate.show_sql", "true");
        cfg.property("hibernate.format_sql", "true");

        return cfg;
    }

    /**
     * 初始化数据库管理器（独立方法，用于热重载）
     *
     * @param conf 数据库配置
     */
    public void init(MainConfig.LocalData.Database conf) {
        try {
            // 检查sqlitePath文件是否存在，不存在则创建
            var dbFile = new File(conf.sqlitePath);
            if (!dbFile.exists()) {
                var parent = dbFile.getParentFile();
                if (parent != null && !parent.exists()) {
                    if (!parent.mkdirs()) {
                        throw new RuntimeException("Failed to create directories for database file: " + conf.sqlitePath);
                    }
                }
                if (!dbFile.createNewFile()) {
                    throw new RuntimeException("Failed to create database file: " + conf.sqlitePath);
                }
            }

            // 获取配置
            var cfg = getDbConfiguration(conf);

            // 注册实体类
            scanEntityClasses("org.maibot.core.db.dao").forEach(clazz -> {
                log.debug("Registering entity class: {}", clazz.getName());
                cfg.managedClass(clazz);
            });

            this.entityManagerFactory = new HibernatePersistenceProvider().createEntityManagerFactory(cfg);

            // 检查数据库版本
            Semver dbVer = getDbVer();
            if (!dbVer.equals(SUPPORT_VER)) {
                log.error("数据库版本与应用程序不兼容。需要: {}, 现有: {}",
                        SUPPORT_VER.getVersion(), dbVer.getVersion());
                throw new RuntimeException("Database version is not compatible with application. " +
                        "Required: " + SUPPORT_VER.getVersion() + ", Found: " + dbVer.getVersion());
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize DatabaseManager", e);
        }
    }

    /**
     * 关闭数据库
     */
    public void close() {
        if (this.entityManagerFactory != null) {
            this.entityManagerFactory.close();
            this.entityManagerFactory = null;
        }
    }

    private Semver getDbVer() {
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
    }

    public <T> T exec(
            Function<EntityManager, T> func
    ) {
        if (this.entityManagerFactory == null) {
            throw new IllegalStateException("DatabaseManager is not initialized. Call init() before using.");
        }

        var em = this.entityManagerFactory.createEntityManager();

        try {
            em.getTransaction().begin();
            var res = func.apply(em);
            em.getTransaction().commit();
            return res;
        } catch (Exception e) {
            if (em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            }
            throw new RuntimeException("Database operation failed", e);
        } finally {
            em.close();
        }
    }
}