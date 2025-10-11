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

- `org.maibot.core`：核心模块，包含 Maibot 的主要逻辑和功能。
  - `cache`：缓存相关，用于加速数据库IO。
  - `cdi`：依赖注入相关，负责管理对象的生命周期和依赖关系。
  - `config`: 配置相关，负责加载和管理配置文件。
  - `db`：数据库相关，负责与数据库的交互。
  - `event`：事件系统，负责事件的发布和订阅。
  - `log`: 日志系统，负责日志的记录和管理。
  - `modloader`: 模组加载器，负责动态加载和管理模组。
  - `net`：网络相关，负责网络通信。
  - `thinking`: 逻辑模块，负责 Maibot 的思考和决策。
  - `util`: 工具类，包含各种辅助功能。
- `org.maibot.sdk`：SDK，为第三方开发者提供接口。