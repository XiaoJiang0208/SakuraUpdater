# SakuraUpdater 使用教程

## 简介

SakuraUpdater 是一个 Minecraft NeoForge 模组，用于自动更新服务器的 mod 文件，让玩家能够像其他游戏一样自动获取服务器更新。

## 功能特性

- [x] 🔄 自动检查服务器更新
- [x] 📁 支持多种文件同步模式（mirror、push）
- [x] 🎮 图形化更新界面
- [ ] 📋 支持版本管理和回滚
- [x] 🌐 基于 Netty 的高效文件传输
- [x] ⚙️ 可配置的客户端和服务器设置

## 安装步骤

### 服务器端安装

1. 将 `sakuraupdater-0.1.1.jar` 放入服务器的 `mods` 文件夹
2. 启动服务器，首次运行会生成配置文件
3. 编辑 `config/sakuraupdater-server.toml` 配置文件

### 客户端安装

1. 将 `sakuraupdater-0.1.1.jar` 放入客户端的 `mods` 文件夹
2. 启动游戏，首次运行会生成配置文件
3. 编辑 `config/sakuraupdater-client.toml` 配置文件

## 配置说明

### 服务器配置 (sakuraupdater-server.toml)

```toml
[general]
# 文件服务器端口
port = 25564

# 同步目录配置，格式：["源目录:模式:目标目录"]
# 模式说明：
# - mirror: 镜像模式，完全同步源目录到目标目录
# - push: 推送模式，将服务器文件推送到客户端
# - pull: 拉取模式，从客户端拉取文件到服务器
SYNC_DIR = [
    "mods:mirror",                    # 同步 mods 文件夹
    "config:push:clientconfig",       # 将 config 推送到客户端的 clientconfig
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
`/ssync commit <版本号> <描述信息>`
例如：
`/ssync commit v1.0.1 "修复了物品复制bug，添加了新的附魔"`
#### 管理数据版本
```
# 查看所有版本
/ssync data list

# 查看特定版本详情
/ssync data show v1.0.1

# 编辑版本描述
/ssync data edit v1.0.1 "更新了版本描述"

# 删除版本
/ssync data delete v1.0.1

# 清空所有版本数据
/ssync data clear
```
#### 重新加载配置
```
# 重新加载服务器配置
/ssync reload server

# 重新加载数据配置
/ssync reload data
```
### 2. 客户端玩家操作

#### 自动检查更新

当玩家进入游戏时，如果检测到服务器有新版本，会自动弹出更新界面。

#### 重新加载客户端配置
`/sakuraupdater reload client`