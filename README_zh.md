# MagicBlock Plugin

[Switch to English](README.md)

MagicBlock 是一个功能丰富的 Minecraft 插件，允许玩家使用具有有限使用次数的魔法方块和魔法食物。这些特殊物品可以被绑定到特定玩家，并且可以通过直观的 GUI 界面进行管理。

## 功能特点
* 魔法方块系统
   * 可配置使用次数的方块
   * 方块绑定系统
   * 直观的 GUI 界面
   * 方块搜索功能
* 魔法食物系统
   * 可重复使用的食物
   * 自定义食物效果
* 多语言支持
   * 英语 (en)
   * 简体中文 (zh_CN)
* PlaceholderAPI 支持
* 详细的使用统计
* 权限系统

## 安装要求
* Minecraft 服务器版本: 1.19+
* 可选依赖: PlaceholderAPI

## 命令系统
主命令：
* `/magicblock` 或 `/mb` - 插件主命令

子命令：
* `/mb help` - 显示帮助信息
* `/mb get [次数]` - 获取一个魔法方块（次数为-1时获取无限次数的魔术方块）
* `/mb give <玩家> [次数]` - 给予玩家魔法方块
* `/mb getfood <食物> [次数]` - 获取魔法食物
* `/mb settimes <次数>` - 设置手持魔法方块的使用次数
* `/mb addtimes <次数>` - 增加手持魔法方块的使用次数
* `/mb list` - 查看已绑定的方块
* `/mb reload` - 重载插件配置

## 权限节点
### 管理员权限
* `magicblock.admin`
   * 包含所有权限
   * 默认仅 OP 拥有
   * 包含以下子权限:
      * `magicblock.use`
      * `magicblock.give`
      * `magicblock.reload`
      * `magicblock.settimes`
      * `magicblock.addtimes`
      * `magicblock.food`

### 基础权限
* `magicblock.use`
   * 允许使用魔法方块的基本功能
   * 默认所有玩家拥有
   * 命令: `/mb get`

### 管理类权限
* `magicblock.give`
   * 允许给予其他玩家魔法方块
   * 默认仅 OP 拥有
   * 命令: `/mb give <玩家> [次数]`
* `magicblock.reload`
   * 允许重载插件配置
   * 默认仅 OP 拥有
   * 命令: `/mb reload`
* `magicblock.settimes`
   * 允许设置魔法方块使用次数
   * 默认仅 OP 拥有
   * 命令: `/mb settimes <次数>`
* `magicblock.addtimes`
   * 允许增加魔法方块使用次数
   * 默认仅 OP 拥有
   * 命令: `/mb addtimes <次数>`

### 功能权限
* `magicblock.food`
   * 允许使用魔法食物
   * 默认所有玩家拥有
   * 命令: `/mb getfood <食物> [次数]`
* `magicblock.list`
   * 允许查看已绑定的方块列表
   * 默认所有玩家拥有
   * 命令: `/mb list`

### 特殊方块权限
* `magicblock.vip` - 允许使用VIP专属方块
* `magicblock.mvp` - 允许使用MVP专属方块

## 基本操作说明
### 魔法方块使用
1. 获取魔法方块：使用 `/mb get` 命令
2. 绑定方块：潜行 + 右键点击
3. 放置方块：直接放置即可
4. 更改方块类型：潜行 + 左键打开GUI界面
5. 查看绑定方块：使用 `/mb list` 命令

### GUI 界面操作
* 左键点击：选择方块类型
* 使用搜索按钮：可以搜索特定方块
* 翻页按钮：浏览更多方块选项

### 绑定列表操作
* 左键点击：找回绑定的方块
* 右键双击：从列表中隐藏方块（不会解除绑定）

## 配置文件
### config.yml 主要配置项
```yaml
# 调试模式
debug-mode: false

# 语言设置
language: "en"  # 可选 "en" 或 "zh_CN"

# 默认使用次数
default-block-times: 1000000000

# 黑名单世界
blacklisted-worlds:
  - world_nether
  - world_the_end
```

### foodconf.yml 食物配置
```yaml
# 食物配置示例
foods:
  GOLDEN_APPLE:
    heal: 4
    saturation: 9.6
    effects:
      REGENERATION:
        duration: 100
        amplifier: 1
```

## 使用示例
1. 给予玩家基础使用权限：
```yaml
permissions:
  - magicblock.use
  - magicblock.food
  - magicblock.list
```

2. 给予玩家VIP权限：
```yaml
permissions:
  - magicblock.use
  - magicblock.food
  - magicblock.list
  - magicblock.vip
```

3. 给予玩家管理员权限：
```yaml
permissions:
  - magicblock.admin
```

## PlaceholderAPI 变量
支持的变量：
* `%magicblock_block_uses%` - 显示玩家使用魔法方块的总次数
* `%magicblock_food_uses%` - 显示玩家使用魔法食物的总次数
* `%magicblock_remaining_uses%` - 显示当前手持魔法方块的剩余使用次数
* `%magicblock_has_block%` - 显示玩家是否持有魔法方块
* `%magicblock_has_food%` - 显示玩家是否持有魔法食物
* `%magicblock_max_uses%` - 显示当前手持魔法方块的最大使用次数
* `%magicblock_uses_progress%` - 显示使用进度（百分比）

## 定制功能
### 物品组权限
可以通过配置文件为不同权限组设置可用的方块类型：
```yaml
group:
  vip-material:
    - DIAMOND_BLOCK
    - EMERALD_BLOCK
  mvp-material:
    - BEACON
    - DRAGON_EGG
```

### 统计功能
* 插件会自动记录玩家使用魔法方块和魔法食物的次数
* 支持通过 PlaceholderAPI 在计分板等地方显示统计信息

## 注意事项
1. 魔法方块在使用次数耗尽后会自动消失
2. 绑定的方块只能被绑定者使用和破坏
3. 方块不能在黑名单世界中使用
4. 方块不受活塞影响
5. 爆炸不会破坏魔法方块
6. 绑定系统不需要额外权限，任何拥有 `magicblock.use` 的玩家都可以使用
7. 无限次数方块的创建需要 `magicblock.give` 或 `magicblock.settimes` 权限
8. VIP和MVP方块需要在配置文件中设置相应的方块列表

## 问题排查
常见问题：
1. 无法使用命令：检查权限节点设置
2. 方块无法放置：检查是否在黑名单世界
3. GUI无法打开：确认是否手持魔法方块
4. 方块无法绑定：检查是否已被其他玩家绑定

## 许可协议
本插件采用修改版MIT许可证：
1. 允许自由使用
   * 可以在任何服务器上使用本插件
   * 允许修改源代码
   * 允许分发修改后的版本
2. 限制条款
   * 禁止将插件或其修改版本用于商业用途
   * 禁止销售插件或其修改版本
   * 二次开发时必须保留原作者信息
3. 免责声明
   * 本插件按"原样"提供，不提供任何形式的保证
   * 作者不对使用本插件造成的任何损失负责

## 技术支持
如有问题或建议，请通过以下方式联系：
* GitHub Issues，BUG反馈请在能够进行复现的情况下反馈，否则无法修复，功能建议并不是提了就会添加，是否能够实现需要根据实际情况决定。
* QQ交流群：[134484522]

## 更新日志
v3.0
* 完善多语言支持系统
* 优化GUI界面显示
* 改进方块绑定机制
* 清理冗余代码
* 提升性能和稳定性
* 整体重构
* 过多内容不进行一一列举………………………………
* 注：3.0版本与2.X版本之间改动过多，请注意备份您的配置之后删除原配置文件夹，进行重新生成

© 2024 MagicBlock. All Rights Reserved.
