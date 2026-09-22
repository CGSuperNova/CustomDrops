# CustomDrops - 自定义方块掉落物插件

[![GitHub release](https://img.shields.io/github/v/release/CGSuperNova/CustomDrops)](https://github.com/CGSuperNova/CustomDrops/releases)
[![License](https://img.shields.io/github/license/CGSuperNova/CustomDrops)](LICENSE)
[![Paper API](https://img.shields.io/badge/Paper-1.20%2B-blue)](https://papermc.io/)

本插件可以允许管理员 **自定义任意方块被破坏时的掉落物**，支持 **时运附魔影响掉落数量**（按原版机制）、**多命令执行**（控制台/玩家/OP）、**按世界启用**、**按权限掉落**、**全量 PlaceholderAPI 变量** 与 **游戏内命令配置** 等功能。
该插件已在 Paper 1.17, 1.18, 1.19, 1.20.4，1.21.11，26.1.2 等核心中测试，理论支持 1.17~26.1.2。
不支持 1.17 以下的版本。

---

## 功能特性

- **按世界启用** – 可指定哪些世界生效，不影响其他世界。
- **按方块启用/禁用** – 每个方块独立的 `enabled` 开关，可临时停用某个方块的特殊掉落而不必删除配置。
- **自定义掉落列表** – 为不同方块分别配置多种掉落物品，每种物品独立设置：
  - 物品类型、基础数量
  - 掉落概率（0~1），以及每级时运额外增加的概率
  - 掉落金币和经验的数量
  - 是否受时运附魔影响数量（按原版随机倍率）
  - 是否每个掉落物品执行一次命令
  - 对精准采集附魔的自定义
  - **所需权限**（不设置则所有玩家都能触发）
- **多命令支持** – 每个掉落项可配置多条命令，支持三种执行身份：
  - `CONSOLE` – 由控制台执行（支持 PlaceholderAPI 占位符）
  - `PLAYER` – 由触发玩家执行
  - `OP` – 临时提升为 OP 执行（执行后恢复）
- **覆盖原版掉落** – 可选择是否彻底替换原版掉落物和经验值。
- **PlaceholderAPI 全量变量** – 配置文件中一个方块的每一个可配置项都有对应变量，详见
  [PlaceholderAPI 变量](#placeholderapi-变量)。
- **游戏内命令配置** – 用 `/customdrops set` 直接创建 / 修改配置，并用 `/customdrops undo` 撤回，无需手动编辑 YAML。
- **更新检测** – 启动后与每 6 小时自动检测 GitHub Release，发现新版本时通知管理员。
- **调试模式** – 开启后控制台输出详细处理日志，便于排错。

---

## 依赖

- 插件软依赖 Vault，若未装载则无法使用掉落金币的功能。<br>
  此外，你需要装载一个适配的经济插件(如 EssentialsX，CMI 等)来使其生效
- 插件软依赖 PlaceholderAPI：安装后可解析命令中的占位符，并启用本插件的 `%customdrops_*%` 变量

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

## 命令

主命令 `/customdrops`，别名 `/cd`。

| 命令 | 权限 | 说明 |
| --- | --- | --- |
| `/customdrops help` | `customdrops.use` | 显示帮助 |
| `/customdrops reload` | `customdrops.reload` | 重载配置文件 |
| `/customdrops list [页码]` | `customdrops.list` | 列出已配置的方块 |
| `/customdrops info <方块id>` | `customdrops.info` | 查询某方块的特殊掉落信息，未配置则返回「无」 |
| `/customdrops set <方块id> <选项> [参数...]` | `customdrops.set` | 设置某方块的特殊掉落，方块不存在时自动创建 |
| `/customdrops target <方块id> <true\|false>` | `customdrops.target` | 启用/禁用某方块的特殊掉落 |
| `/customdrops delete <方块id>` | `customdrops.delete` | 删除某方块的特殊掉落配置 |
| `/customdrops undo [步数]` | `customdrops.undo` | 撤回前几步命令操作，最多 5 步 |
| `/customdrops placeholders` | `customdrops.info` | 查看全部可用变量 |
| `/customdrops update` | `customdrops.update` | 手动检查更新 |

另有 `customdrops.admin` 权限节点，拥有以上全部权限。

### `/customdrops set` 可配置选项

方块级（直接写选项名）：

| 选项 | 参数 | 对应配置项 |
| --- | --- | --- |
| `enabled` | `<true\|false>` | `enabled` |
| `override-default` | `<true\|false>` | `override-default` |
| `silk-touch-preserve-original` | `<true\|false>` | `silk-touch-preserve-original` |
| `add-drop` | `<物品id>` | 在 `drops` 末尾追加一项 |
| `remove-drop` | `<序号\|物品id>` | 删除某项 |
| `clear-drops` | | 清空全部掉落项 |

掉落项级（`drops.<序号|物品id>.<选项>`，也可简写为 `drop-<序号>-<选项>`，序号从 1 开始）：

| 选项 | 参数 | 对应配置项 |
| --- | --- | --- |
| `item` | `<物品id>` | `item` |
| `amount` | `<整数>` | `amount` |
| `chance` | `<0~1>` | `base-chance` |
| `fortune-affects-count` | `<true\|false>` | `fortune-affects-count` |
| `fortune-chance-bonus` | `<数字>` | `fortune-chance-bonus` |
| `execute-command-per-item` | `<true\|false>` | `execute-command-per-item` |
| `silk-touch-ignore` | `<true\|false>` | `silk-touch-ignore` |
| `exp` | `<整数>` | `exp` |
| `money` | `<数字>` | `money` |
| `exp-multiplier` | `<数字>` | `exp-multiplier` |
| `money-multiplier` | `<数字>` | `money-multiplier` |
| `permission` | `<权限节点\|none>` | `permission` |
| `add-command` | `<命令>` | 追加一条控制台命令 |
| `add-command-player` | `<命令>` | 追加一条玩家身份命令 |
| `add-command-op` | `<命令>` | 追加一条 OP 身份命令 |
| `remove-command` | `<序号>` | 删除第 n 条命令 |
| `clear-commands` | | 清空全部命令 |

示例：

```
/customdrops set DIAMOND_ORE add-drop DIAMOND
/customdrops set DIAMOND_ORE drops.1.amount 2
/customdrops set DIAMOND_ORE drops.1.chance 0.15
/customdrops set DIAMOND_ORE drops.DIAMOND.permission customdrops.drop.diamond
/customdrops set DIAMOND_ORE drops.1.add-command-op "say %player% 挖到了钻石"
/customdrops set DIAMOND_ORE override-default false
/customdrops target DIAMOND_ORE false
/customdrops undo 2
```

`undo` 记录会持久化到 `plugins/CustomDrops/undo-history.yml`，服务器重启后依然可以撤回。

---

## PlaceholderAPI 变量

安装 PlaceholderAPI 后，本插件会注册 `customdrops` 变量扩展。可用 `/customdrops placeholders` 在游戏内查看完整列表。

变量格式为 `%customdrops_<元素>_<对象>_<字段>%`。分隔符 `_` 也可以统一写成 `-`；**推荐使用 `-`**，
这样方块 id 中就能保留原来的下划线：

```
%customdrops_block-COAL_ORE-drop-1-amount%     <- 推荐：'-' 分隔，方块 id 可含下划线
%customdrops_block_COAL_drop_1_amount%         <- '_' 分隔，方块 id 不含下划线
```

`has`、`world`、`blocks` 等元素同样支持两种写法：

```
%customdrops_has_coal_ore%        %customdrops_has-COAL_ORE%
%customdrops_world_world_nether%  %customdrops_world-world_nether%
%customdrops_blocks_2%            %customdrops_blocks-2%
```

常用变量：

| 变量 | 说明 |
| --- | --- |
| `%customdrops_version%` | 插件版本 |
| `%customdrops_debug%` | 是否开启调试模式 |
| `%customdrops_check_update%` | 是否开启更新检测 |
| `%customdrops_update_available%` | 是否有新版本（`true`/`false`/`unknown`） |
| `%customdrops_update_latest%` | 远端最新版本号 |
| `%customdrops_update_url%` | 最新版本下载地址 |
| `%customdrops_total_blocks%` | 已配置的方块数量 |
| `%customdrops_total_drops%` | 全部掉落项总数 |
| `%customdrops_total_permission_drops%` | 需要权限的掉落项总数 |
| `%customdrops_total_enabled_blocks%` | 启用了特殊掉落的方块数量 |
| `%customdrops_total_commands%` | 全部掉落命令条数 |
| `%customdrops_blocks%` | 已配置方块列表（支持 `%customdrops_blocks-2%` 翻页） |
| `%customdrops_worlds%` | 启用自定义掉落的世界列表 |
| `%customdrops_world-<世界名>%` | 指定世界是否启用 |
| `%customdrops_has-<方块id>%` | 某方块是否已配置 |
| `%customdrops_block-<方块id>_enabled%` | 该方块的特殊掉落是否启用 |
| `%customdrops_block-<方块id>-override_default%` | 是否覆盖原版掉落 |
| `%customdrops_block-<方块id>-silk_touch_preserve_original%` | 精准采集是否保留原版掉落 |
| `%customdrops_block-<方块id>-drops%` | 掉落项数量 |
| `%customdrops_block-<方块id>-items%` | 掉落物列表（`DIAMONDx1,COALx3`） |
| `%customdrops_block-<方块id>-chance_sum%` | 各掉落项基础概率之和（0~1） |
| `%customdrops_block-<方块id>-chance_sum_percent%` | 各掉落项基础概率之和（百分比） |
| `%customdrops_block-<方块id>-permission_drops%` | 需要权限的掉落项数量 |
| `%customdrops_block-<方块id>-max_amount%` | 单个掉落项的最大基础数量 |
| `%customdrops_block-<方块id>-exp_total%` | 各掉落项基础经验之和 |
| `%customdrops_block-<方块id>-money_total%` | 各掉落项基础金币之和 |
| `%customdrops_block-<方块id>-command_total%` | 全部命令条数 |
| `%customdrops_block-<方块id>-drop-<序号>-item%` | 掉落物 id |
| `%customdrops_block-<方块id>-drop-<序号>-amount%` | 基础掉落数量 |
| `%customdrops_block-<方块id>-drop-<序号>-chance%` | 基础概率（0~1） |
| `%customdrops_block-<方块id>-drop-<序号>-chance_percent%` | 基础概率（百分比） |
| `%customdrops_block-<方块id>-drop-<序号>-fortune_affects_count%` | 时运是否影响数量 |
| `%customdrops_block-<方块id>-drop-<序号>-fortune_chance_bonus%` | 每级时运增加的概率 |
| `%customdrops_block-<方块id>-drop-<序号>-execute_command_per_item%` | 是否每物品执行一次命令 |
| `%customdrops_block-<方块id>-drop-<序号>-silk_touch_ignore%` | 精准采集时是否禁止掉落 |
| `%customdrops_block-<方块id>-drop-<序号>-exp%` | 经验 |
| `%customdrops_block-<方块id>-drop-<序号>-money%` | 金币 |
| `%customdrops_block-<方块id>-drop-<序号>-exp_multiplier%` | 每级时运增加的经验比例 |
| `%customdrops_block-<方块id>-drop-<序号>-money_multiplier%` | 每级时运增加的金币比例 |
| `%customdrops_block-<方块id>-drop-<序号>-permission%` | 所需权限（未设置为 `none`） |
| `%customdrops_block-<方块id>-drop-<序号>-commands%` | 命令条数 |
| `%customdrops_block-<方块id>-drop-<序号>-command-<n>%` | 第 n 条命令内容 |
| `%customdrops_block-<方块id>-drop-<序号>-command-<n>-executor%` | 第 n 条命令的执行者 |
| `%customdrops_player_block-<方块id>-drop-<序号>-can_drop%` | 该玩家是否满足该掉落的权限要求 |

`<序号>` 从 1 开始，也可以直接写掉落物 id（如 `%customdrops_block-COAL_ORE-drop-DIAMOND-amount%`）。

---

## 常见问题

- Q：为什么我的自定义掉落没有生效？
- A：检查以下几点：
  - 确认该世界在 `enabled-worlds` 列表中。
  - 确认该方块的 `enabled` 为 `true`（可用 `/customdrops info <方块id>` 查看）。
  - 确认方块类型名称拼写正确（如 `COAL_ORE` 而非 `COAL`）。
  - 查看控制台是否开启 `debug: true` 以获取详细日志。

- Q：时运为什么对掉落概率无影响？
- A：默认情况下时运只影响 **数量**，概率始终由 `base-chance` 决定。
  如果你希望时运也能提升概率，请为该掉落项设置 `fortune-chance-bonus`（例如 `0.01` 表示每级时运 +1% 概率）。

- Q：某个掉落物只有特定玩家能获得？
- A：为该掉落项设置 `permission`，例如 `permission: "customdrops.drop.diamond"`，
  只有拥有该权限的玩家才会触发这个掉落。留空表示不需要权限。

- Q：命令中的 PAPI 占位符没解析？
- A：只有 `executor: CONSOLE` 的命令会自动调用 PAPI 解析。请确保服务器已安装 PlaceholderAPI。

- Q：金币功能不生效？
- A：请确认已安装 Vault 和经济插件（如 EssentialsX），并在启动日志中查看 “成功连接到经济系统” 的提示。

- Q：为什么提示“检查更新失败”？
- A：本插件的更新检测会访问 GitHub API。请确认服务器可以访问 `api.github.com`；
  若日志提示限流（HTTP 403/429），稍后重试即可。也可以把 `check-update` 设为 `false` 完全关闭。

- Q：如何完全禁用更新检测？
- A：在配置中设置 `check-update: false` 即可。

- Q：如何禁用 bStats 统计？
- A：在配置中设置 `metrics: false` 即可。

---

## 从源码构建

需要 JDK 17 及以上版本与 Maven：

```bash
mvn clean package
```

产物为 `target/CustomDrops-<版本号>.jar`。

### 离线自检

`verification/` 下有两个不依赖运行中服务端的自检程序：

- `CustomDropsSelfCheck.java` —— 校验 **配置快照往返**（`undo` 的基础）、**撤销历史 YAML 格式**、
  **掉落项下标解析**、**版本比较** 与 **掉落项语义**（概率夹取、权限默认值等）。
- `ExpansionAudit.java` —— 用真实的变量扩展跑一遍全部 PAPI 变量，
  逐个核对返回值，并确认非法输入不会误匹配。

```bash
mvn -q -DskipTests compile
javac -encoding UTF-8 -cp "target/classes;<paper-api.jar>;<snakeyaml 2.x>;<guava.jar>;<gson.jar>;<placeholderapi.jar>;<kyori 各 jar 用于 Player 代理>" \
      -d verification/out verification/*.java
java -cp "target/classes;verification/out;<同上依赖>" CustomDropsSelfCheck
java -cp "target/classes;verification/out;<同上依赖>" org.example.com.customDrops.ExpansionAudit
```

全部通过时分别输出 `ALL CHECKS PASSED` 与 `AUDIT CLEAN`。上述依赖 jar 均可从本地 Maven 仓库
（`~/.m2/repository`）中取得。

---

## 更新日志

见 [CHANGELOG.md](CHANGELOG.md)。
