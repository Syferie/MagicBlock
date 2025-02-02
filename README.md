# MagicBlock Plugin

[点击查看中文版介绍](README_zh.md)

MagicBlock is a feature-rich Minecraft plugin that enables players to use magic blocks and magic food items with limited usage counts. These special items can be bound to specific players and managed through an intuitive GUI interface.

## Features
* Magic Block System
   * Configurable usage counts for blocks
   * Block binding system
   * Intuitive GUI interface
   * Block search functionality
* Magic Food System
   * Reusable food items
   * Custom food effects
* Multi-language Support
   * English (en)
   * Simplified Chinese (zh_CN)
* PlaceholderAPI Support
* Detailed Usage Statistics
* Permission System

## Requirements
* Minecraft Server Version: 1.19+
* Optional Dependency: PlaceholderAPI

## Commands
Main Command:
* `/magicblock` or `/mb` - Main plugin command

Subcommands:
* `/mb help` - Show help information
* `/mb get [times]` - Get a magic block (use -1 for infinite uses)
* `/mb give <player> [times]` - Give a magic block to a player
* `/mb getfood <food> [times]` - Get magic food
* `/mb settimes <times>` - Set uses for held magic block
* `/mb addtimes <times>` - Add uses to held magic block
* `/mb list` - View bound blocks
* `/mb reload` - Reload plugin configuration

## Permissions
### Administrator Permission
* `magicblock.admin`
   * Includes all permissions
   * Default: OP only
   * Includes sub-permissions:
      * `magicblock.use`
      * `magicblock.give`
      * `magicblock.reload`
      * `magicblock.settimes`
      * `magicblock.addtimes`
      * `magicblock.food`

### Basic Permissions
* `magicblock.use`
   * Allows basic magic block functionality
   * Default: All players
   * Command: `/mb get`

### Management Permissions
* `magicblock.give`
   * Allows giving magic blocks to others
   * Default: OP only
   * Command: `/mb give <player> [times]`
* `magicblock.reload`
   * Allows reloading plugin configuration
   * Default: OP only
   * Command: `/mb reload`
* `magicblock.settimes`
   * Allows setting magic block uses
   * Default: OP only
   * Command: `/mb settimes <times>`
* `magicblock.addtimes`
   * Allows adding magic block uses
   * Default: OP only
   * Command: `/mb addtimes <times>`

### Feature Permissions
* `magicblock.food`
   * Allows using magic food
   * Default: All players
   * Command: `/mb getfood <food> [times]`
* `magicblock.list`
   * Allows viewing bound block list
   * Default: All players
   * Command: `/mb list`

### Special Block Permissions
* `magicblock.vip` - Allows using VIP-exclusive blocks
* `magicblock.mvp` - Allows using MVP-exclusive blocks

## Basic Usage
### Magic Block Usage
1. Get magic block: Use `/mb get` command
2. Bind block: Sneak + Right-click
3. Place block: Place normally
4. Change block type: Sneak + Left-click to open GUI
5. View bound blocks: Use `/mb list` command

### GUI Operations
* Left-click: Select block type
* Search button: Search for specific blocks
* Page buttons: Browse more block options

### Bound List Operations
* Left-click: Retrieve bound block
* Double right-click: Hide block from list (doesn't unbind)

## Configuration Files
### config.yml Main Settings
```yaml
# Debug mode
debug-mode: false

# Language setting
language: "en"  # Options: "en" or "zh_CN"

# Default usage count
default-block-times: 1000000000

# Blacklisted worlds
blacklisted-worlds:
  - world_nether
  - world_the_end
```

### foodconf.yml Food Configuration
```yaml
# Food configuration example
foods:
  GOLDEN_APPLE:
    heal: 4
    saturation: 9.6
    effects:
      REGENERATION:
        duration: 100
        amplifier: 1
```

## Usage Examples
1. Basic player permissions:
```yaml
permissions:
  - magicblock.use
  - magicblock.food
  - magicblock.list
```

2. VIP player permissions:
```yaml
permissions:
  - magicblock.use
  - magicblock.food
  - magicblock.list
  - magicblock.vip
```

3. Administrator permissions:
```yaml
permissions:
  - magicblock.admin
```

## PlaceholderAPI Variables
Supported variables:
* `%magicblock_block_uses%` - Total magic block uses
* `%magicblock_food_uses%` - Total magic food uses
* `%magicblock_remaining_uses%` - Remaining uses of held magic block
* `%magicblock_has_block%` - Whether player has magic block
* `%magicblock_has_food%` - Whether player has magic food
* `%magicblock_max_uses%` - Maximum uses of held magic block
* `%magicblock_uses_progress%` - Usage progress (percentage)

## Customization
### Item Group Permissions
Configure available block types for different permission groups:
```yaml
group:
  vip-material:
    - DIAMOND_BLOCK
    - EMERALD_BLOCK
  mvp-material:
    - BEACON
    - DRAGON_EGG
```

### Statistics
* Plugin automatically records magic block and food usage
* Supports displaying statistics via PlaceholderAPI

## Important Notes
1. Magic blocks disappear when uses are depleted
2. Bound blocks can only be used/broken by the binding player
3. Blocks cannot be used in blacklisted worlds
4. Blocks are unaffected by pistons
5. Explosions don't destroy magic blocks
6. Binding system requires no extra permissions beyond `magicblock.use`
7. Infinite use blocks require `magicblock.give` or `magicblock.settimes`
8. VIP/MVP blocks need configured block lists

## Troubleshooting
Common issues:
1. Cannot use commands: Check permission nodes
2. Cannot place blocks: Check blacklisted worlds
3. GUI won't open: Verify holding magic block
4. Cannot bind block: Check if already bound

## License
Modified MIT License:
1. Free Use
   * Use on any server
   * Modify source code
   * Distribute modified versions
2. Restrictions
   * No commercial use
   * No selling plugin/modifications
   * Must retain original author information
3. Disclaimer
   * Provided "as is" without warranty
   * Author not liable for any damages

## Support
For issues or suggestions:
* GitHub Issues (Include reproducible steps for bugs)
* QQ Group: [134484522]

## Changelog

**MagicBlock 3.1.0 Changelog**

**New Features:**

*   MagicBlock items now support multi-line lores (thanks to suggestion #3).
*   Added config option to allow/disallow players using MagicBlocks bound by other players.
*   Added support for Folia server (resolves #6).

**Fixes:**

*   Fixed connection issues between fences, walls, glass panes, etc.
*   Fixed a bug where MagicBlocks would be accidentally disintegrated in furnaces and workbenches.
*   Fixed a bug where MagicBlocks would be accidentally replaced when players clicked on an item in their backpack.
*   Fixed pressure plate redstone behavior and prevented attached blocks from dropping unexpectedly (resolves #9).

**Improvements:**

*   Improved the configuration description in config.

**In summary,** this update mainly adds support for Folia server, implements multi-line lore support for MagicBlock items, and fixes several important bugs.


MagicBlock v3.0.0 Changelog

* Enhanced multilingual support
* Optimized GUI display
* Improved block binding
* Code cleanup
* Performance improvements
* Complete restructure
* Many more changes...
* Note: Backup config before updating from 2.X

© 2024 MagicBlock. All Rights Reserved.
