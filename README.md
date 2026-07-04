# CustomDrops - 自定义方块掉落物插件

[![GitHub release](https://img.shields.io/github/v/release/CGSuperNova/CustomDrops)](https://github.com/CGSuperNova/CustomDrops/releases)
[![License](https://img.shields.io/github/license/CGSuperNova/CustomDrops)](LICENSE)
[![Paper API](https://img.shields.io/badge/Paper-1.20%2B-blue)](https://papermc.io/)

本插件可以允许管理员 **自定义任意方块被破坏时的掉落物**，支持 **时运附魔影响掉落数量**（按原版机制）、**多命令执行**（控制台/玩家/OP）、**按世界启用** 等功能。
该插件已在Paper 1.20.4，1.21.11，26.1.2三个核心中测试，理论支持1.20~26.1.2
不支持1.20以下的版本

---

## 功能特性

- **按世界启用** – 可指定哪些世界生效，不影响其他世界。
- **自定义掉落列表** – 为不同方块分别配置多种掉落物品，每种物品独立设置：
  - 物品类型、基础数量
  - 掉落概率（0~1）
  - 是否受时运附魔影响数量（按原版随机倍率）
  - 是否每个掉落物品执行一次命令
- **多命令支持** – 每个掉落项可配置多条命令，支持三种执行身份：
  - `CONSOLE` – 由控制台执行（支持 PlaceholderAPI 占位符）
  - `PLAYER` – 由触发玩家执行
  - `OP` – 临时提升为 OP 执行（执行后恢复）
- **覆盖原版掉落** – 可选择是否彻底替换原版掉落物和经验值。
- **调试模式** – 开启后控制台输出详细处理日志，便于排错。

---

## 依赖

- 插件软依赖于PlaceHolderAPI

---

## 安装

1. **下载插件**  
   从 [Releases](https://github.com/CGSuperNova/CustomDrops/releases) 下载最新版的 `CustomDrops-*.jar`。

2. **放入服务器**  
   将 jar 文件放入服务器的 `plugins/` 目录。

3. **重启服务器**（或使用 `PlugMan` 等热加载工具）  
   插件将自动生成默认配置文件 `plugins/CustomDrops/config.yml`。

4. **修改配置** – 按需编辑配置文件，然后执行 `/customdrops reload` 使配置生效。

---

## 常见问题

- Q：为什么我的自定义掉落没有生效？
- A：检查以下几点：
  - 确认该世界在 enabled-worlds 列表中。
  - 确认方块类型名称拼写正确（如 COAL_ORE 而非 COAL）。
  - 查看控制台是否开启 debug: true 以获取详细日志。

- Q：时运为什么对掉落概率无影响？
- A：本插件中时运影响的是数量，不是概率。概率始终由 base-chance 决定。

- Q：命令中的 PAPI 占位符没解析？
- A：只有 executor: CONSOLE 的命令会自动调用 PAPI 解析。请确保服务器已安装 PlaceholderAPI。
