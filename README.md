# Maibot Java Edition (Maibot-JE)

![Static Badge](https://img.shields.io/badge/OpenJDK-21%2B-blue?style=plastic&logo=openjdk)
[![Static Badge](https://img.shields.io/badge/Core-v0.1.0--Alpha-blue?style=plastic)](./core)
[![Static Badge](https://img.shields.io/badge/SDK-v0.1.0--Alpha-blue?style=plastic)](./sdk)
[![Static Badge](https://img.shields.io/badge/Launcher-v0.1.0--Alpha-blue?style=plastic)](./launcher)

Maibot 的 Java 重构版，推倒全部代码，重新设计，使用 Java 语言实现。

> **[点击前往Python版仓库](https://github.com/MaiM-with-u/MaiBot)**

> [!WARNING]
> - 本仓库目前为技术验证，非官方重构版本。
> - 早期原型阶段，功能不完整，**可能存在恶性bug**。

## 开发环境

- JDK 21
- Gradle 8.14

## 模块架构

> [!WARNING]
> 目前模块划分尚不完善，后续可能会有较大调整。

- `launcher`：启动器，负责启动和管理 Maibot-JE 实例。
- `core`：核心模块，包含 Maibot 的主要逻辑和功能；
- `sdk`：SDK，为第三方开发者提供接口；
- `example_mods`：示例模组，展示如何使用 SDK 开发模组。

## 开源协议（License）

本项目的代码使用了多种不同的开源协议：

- Maibot-JE Launcher，采用GPL-3.0 许可证开源；（见 [Launcher README文件](./launcher/README.md)）
- Maibot-JE CORE，采用 GPL-3.0 许可证开源；（见 [CORE README文件](./core/README.md)）
- Maibot-JE SDK，采用 MIT 许可证开源；（见 [SDK README文件](./sdk/README.md)）
- example_mods 目录下的示例模组采用 MIT 许可证开源；

在使用本项目的代码时，请遵守相应的开源许可证要求。