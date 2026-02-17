# SakuraUpdater 使用教程 ([EN Version](#sakuraupdater--usage-guide))

## 简介

SakuraUpdater 是一个 Minecraft NeoForge 模组，用于自动更新服务器的 mod 文件，让玩家能够像其他游戏一样自动获取服务器更新。

## 功能特性

- [x] 🔄 自动检查服务器更新
- [x] 📁 支持多种文件同步模式（mirror、push）
- [x] 🎮 图形化更新界面
- [x] ⚙️ 可配置的客户端和服务器设置
- [x] 🚀 服务端可独立运行

## 安装步骤

### 服务器端安装

1. 将 `sakuraupdater-[version].jar` 放入服务器的 `mods` 文件夹
2. 启动服务器，首次运行会生成配置文件
3. 编辑 `config/sakuraupdater-server.toml` 配置文件

### 客户端安装

1. 将 `sakuraupdater-[version].jar` 放入客户端的 `mods` 文件夹
2. 启动游戏，首次运行会生成配置文件
3. 编辑 `config/sakuraupdater-client.toml` 配置文件

### 服务端独立运行
1. 将 `sakuraupdater-[version].jar` 放入单独文件夹
2. 直接 `java -jar sakuraupdater-[version].jar` 即可运行服务端
3. 编辑 `config/sakuraupdater-client.toml` 配置文件

## 配置说明

### 服务器配置 (sakuraupdater-server.toml)

```toml
[general]
# 文件服务器端口
port = 25564

# 同步目录配置，格式：["目标目录:模式:源目录:额外的源目录:..."]
# 模式说明：
# - mirror: 镜像模式，完全同步源目录到目标目录
# - push: 推送模式，将服务器文件推送到客户端
# - pull: 拉取模式，从客户端拉取文件到服务器
SYNC_DIR = [
    "mods:mirror",                    # 同步 mods 文件夹
    "config:push:clientconfig:config",       # 将 config和clientconfig 推送到客户端的 clientc
    "resourcepacks:mirror"            # 同步资源包文件夹
]
```
### 客户端配置 (sakuraupdater-client.toml)
```toml
[general]
# 服务器主机地址
host = "localhost"

# 服务器端口（需与服务器配置一致）
port = 25564

# 当前客户端版本（自动管理，请勿手动修改）
now_version = ""
```
## 使用方法

### 1. 服务器管理员操作

#### 创建更新版本
`/sakuraupdater commit <版本号> <描述信息/描述文件路径>`
例如:
`/sakuraupdater commit v1.0.1 修复了物品复制bug\n添加了新的附魔`
或文本文件(极少的md格式支持)
`/sakuraupdater commit v1.0.1 description.md`
#### 管理数据版本
```
# 查看所有版本
/sakuraupdater data list

# 查看特定版本详情
/sakuraupdater data show v1.0.1

# 编辑版本描述
/sakuraupdater data edit v1.0.1 "更新了版本描述"

# 删除版本
/sakuraupdater data delete v1.0.1

# 清空所有版本数据
/sakuraupdater data clear
```
#### 重新加载配置
```
# 重新加载服务器配置
/sakuraupdater reload server

# 重新加载数据配置
/sakuraupdater reload data
```
### 2. 客户端玩家操作

#### 自动检查更新

当玩家进入游戏时，如果检测到服务器有新版本，会自动弹出更新界面。

#### 重新加载客户端配置
`/sakuraupdater reload client`


# SakuraUpdater — Usage Guide

## Introduction

SakuraUpdater is a NeoForge Minecraft mod that enables automatic synchronization of mod files between a server and clients, allowing players to receive server updates automatically, similar to other games.

## Features

- [x] 🔄 Automatic server update checks
- [x] 📁 Multiple sync modes supported (mirror, push)
- [x] 🎮 Graphical update UI
- [x] ⚙️ Configurable client and server settings
- [x] 🚀 The server can run independently

## Installation

### Server-side installation

1. Place `sakuraupdater-[version].jar` into the server's `mods` folder.
2. Start the server. Configuration files will be generated on first run.
3. Edit the server configuration file at `config/sakuraupdater-server.toml`.

### Client-side installation

1. Place `sakuraupdater-[version].jar` into the client's `mods` folder.
2. Start the game. Configuration files will be generated on first run.
3. Edit the client configuration file at `config/sakuraupdater-client.toml`.

### Server-side independent operation
1. place `sakuraupdater-[version].jar` into a separate folder.
2. Run the server independently with `java -jar sakuraupdater-[version].jar`.
3. Edit the client configuration file at `config/sakuraupdater-client.toml`.

## Configuration

### Server configuration (`sakuraupdater-server.toml`)

```toml
[general]
# File server port
port = 25564

# Sync directory configuration, format: ["target_dir:mode:source_dir:extra_source_dir:..."]
# Mode explanations:
# - mirror: mirror mode, fully synchronize source directory to the target directory
# - push: push mode, push server files to clients
# - pull: pull mode, pull files from clients to the server
SYNC_DIR = [
    "mods:mirror",                    # synchronize the mods folder
    "config:push:clientconfig:config",       # push config and clientconfig to the client
    "resourcepacks:mirror"            # synchronize resourcepacks folder
]
```

### Client configuration (`sakuraupdater-client.toml`)

```toml
[general]
# Server host address
host = "localhost"

# Server port (should match the server configuration)
port = 25564

# Current client version (managed automatically; do not edit manually)
now_version = ""
```

## Usage

### 1. Server administrator operations

#### Create a release/commit
`/sakuraupdater commit <version> <description-or-path-to-description-file>`
Example:
`/sakuraupdater commit v1.0.1 Fixed item duplication bug\nAdded new enchantments`
or using a text file (minimal markdown supported):
`/sakuraupdater commit v1.0.1 description.md`

#### Manage data versions

```
# List all versions
/sakuraupdater data list

# Show details for a specific version
/sakuraupdater data show v1.0.1

# Edit version description
/sakuraupdater data edit v1.0.1 "Updated version description"

# Delete a version
/sakuraupdater data delete v1.0.1

# Clear all version data
/sakuraupdater data clear
```

#### Reload configuration

```
# Reload server config
/sakuraupdater reload server

# Reload data config
/sakuraupdater reload data
```

### 2. Client player operations

#### Automatic update check

When a player joins the game, the client will automatically check whether the server has a newer version and will open the update UI if an update is available.

#### Reload client config

`/sakuraupdater reload client`
