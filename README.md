# Maibot Java Edition (Maibot-JE)

**前往[Python版原始仓库](https://github.com/MaiM-with-u/MaiBot)**

Maibot 的 Java 重构版，推倒 Python 版的全部代码，重新设计，使用 Java 语言实现。

> [!WARNING]
> - 本仓库目前为技术验证，非官方重构版本。
> - 早期原型阶段，功能不完整，**可能存在恶性bug**。

## 运行环境
- JDK 21
- Gradle 8.14

## 模块架构

> [!WARNING]
> 目前模块划分尚不完善，后续可能会有较大调整。

- `org.maibot.core`：核心模块，包含 Maibot 的主要逻辑和功能；
  - `cache`：缓存相关，用于加速数据库IO；
  - `cdi`：依赖注入相关，提供简易的IoC容器：
    - 提供依赖注入功能，简化组件间的依赖管理，减轻脑力负担；
  - `commandline`：命令行接口，负责命令行交互：
    - 基于picocli+jline3实现，提供命令补全、help页面，构建交互式命令行；
    - 使用JANSI提供跨平台的终端颜色支持；
  - `config`: 配置相关，负责加载和管理配置文件：
    - 基于IoC容器自动注入，简化配置管理；
    - 支持数据校验，确保配置字段完整性；
  - `db`：数据库相关，负责与数据库的交互：
    - 基于Hibernate的ORM实现，通过JPA简化数据库操作；
  - `event`：事件系统，负责事件的发布和订阅；
  - `log`: 日志系统，负责日志的记录和管理：
    - 基于SLF4J+Logback实现，提供灵活的日志配置和输出；
    - 实现自定义过滤器，支持按模块和级别过滤日志；
  - `modloader`: 模组加载器，负责动态加载和管理模组；
  - `net`：网络相关：
    - 基于Netty搭建内置服务端；
    - 根据请求类型自动组装处理链，在同一端口实现了HTTP和WebSocket协议的支持（依赖URI区分）；
  - `thinking`: 逻辑模块，负责 Maibot 的思考和决策：
    - 并行思维流模块，对每个交互流独立并行处理。
  - `util`: 工具类，包含各种辅助功能。
- `org.maibot.sdk`：SDK，为第三方开发者提供接口；