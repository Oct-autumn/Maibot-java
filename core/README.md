# Maibot-JE 核心模块

- `cache`：缓存相关，提供 CacheManager 用于加速数据库 IO 操作；
- `ioc`：简易的 IoC 容器，包含依赖注入、单例生命周期管理等功能；
- `commandline`：命令行接口，基于 picocli 和 jline3，支持命令补全与交互式终端；
- `config`：配置管理，自动注入配置，支持配置数据加载与校验；
- `event`：事件系统，负责事件发布、订阅；
- `log`：日志系统，基于 SLF4J+Logback，支持自定义过滤器和终端输出格式；
- `modloader`：模组加载器，动态加载和管理模组及其依赖关系；
- `net`：网络相关，基于 Netty 实现 HTTP/WebSocket 服务端，包含请求分发与异常处理；
    - `client`：提供 HTTP 客户端实现；
- `persistence`：持久化相关；
    - `db`：数据库服务实现（如 DatabaseServiceImpl）
    - `localstorage`：本地存储服务实现；
- `thinking`：逻辑模块，负责思考与决策，支持并行思维流处理；
- `util`：工具类，包含前缀树、任务执行器、定时器等辅助功能。

## 开源协议（License）

Copyright (C) 2025 Maibot-JE Project Developers  
Licensed under the GNU General Public License v3.0 (GPL-3.0).  
See the [LICENSE](./LICENSE) file for details.