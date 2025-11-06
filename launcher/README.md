# Oi! Maibot-Core Launcher (OMCL) for Maibot-JE

## 如何使用

> 请确保您的系统已安装 `21` 或更高版本的Java运行环境（JRE）

使用 `java -jar OMCL-x.x.x.jar` 命令启动 Maibot-JE 启动器，或双击运行该 JAR 文件。

启动器会自行解析依赖并下载运行所需的文件，首次运行时可能需要一些时间，请耐心等待。

## 运行目录结构

```text
OMCL/
├── .maibot-launcher/       # 启动器工作目录
│   ├── libs/               # 依赖库目录
│   │   └── ...             # 各种依赖库文件
│   ├── mods/               # 运行时 mod 目录
│   │   └── ...             # 各种运行时会被加载的 mod 文件
│   └── core-x.x.x.jar      # Maibot-JE 核心文件
├── config/                 # 配置文件目录（用于存储mod配置等）
├── data/                   # 数据文件目录（用于存储Maibot-JE运行时产生的数据）
├── logs/                   # 日志文件目录（用于存储Maibot-JE运行时产生的日志）
├── mods/                   # mod文件目录（用于存储mod文件）
├── config.toml             # Maibot-JE 核心配置文件
└── OMCL-x.x.x.jar          # 启动器主程序
```

## 开源协议（License）

Copyright (C) 2025 Maibot-JE Project Developers  
Licensed under the GNU General Public License v3.0 (GPL-3.0).  
See the [LICENSE](./LICENSE) file for details.