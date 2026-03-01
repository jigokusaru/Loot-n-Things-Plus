# Loot and Things Plus

Welcome to Loot and Things Plus, a highly configurable and powerful loot system for Minecraft NeoForge. This mod allows server administrators to create custom loot chests and loot bags with advanced features like weighted rewards, variables, pity timers, and server-wide announcements.

**This is a SERVER-SIDE ONLY mod. Players do not need to install it to join a server that is running it.**

## Compatibility
- **Minecraft Version:** 1.21.1
- **NeoForge Version:** 21.1.219+

## Features

- **JSON-Based Loot Tables:** Easily create and edit loot tables using simple JSON files.
- **Chests & Bags:** Create both placeable loot chests and consumable loot bag items.
- **Advanced Reward Types:**
    - **Items:** Grant any Minecraft item, with support for NBT.
    - **Commands:** Execute server commands as the player or the console.
    - **Economy:** Give players currency (requires a compatible economy system).
    - **Multi-Rewards:** Grant a "kit" of multiple items from a single reward entry.
    - **Nested Loot:** Rewards can include other loot bags or keys.
- **Dynamic Placeholders & Variables:**
    - Use placeholders like `[player]`, `[xp_level]`, and `[score.objective]` in commands and display names.
    - Define and use custom variables with weighted outcomes or random ranges.
    - Perform simple math operations directly in placeholders.
- **Fairness Systems:**
    - **Pity System:** Guarantees a special roll after a configurable number of attempts.
    - **Deck System:** Treats the loot pool like a deck of cards, ensuring no repeats until the pool is exhausted.
    - **Unique Rolls:** Prevent similar items (e.g., different tiers of the same potion) from appearing in the same spin.
- **Economic Integration:**
    - Set costs (items, XP levels, or economy) to open loot.
    - Configure server-wide cooldowns for loot tiers.
    - Universal command-based economy support for any plugin with commands.
- **Server-Wide Broadcasts:** Announce when players open rare chests, win special items, or when a global deck is reshuffled.
- **In-Game Commands:** A full suite of commands for admins to set loot chests, give keys/bags, and for players to check their pity status.
- **Permissions:** Full support for permission plugins like LuckPerms, with a fallback to default operator levels.

## Getting Started: A Quick Tutorial

Here’s a step-by-step guide to creating your first loot chest and loot bag.

### Part 1: Creating a Loot Chest

1.  **Create the JSON File:**
    - Navigate to your server's `config/lootnthings/chests/` directory.
    - Create a new file named `my_first_chest.json`. The name of the file (`my_first_chest`) is your new **tier name**.
    - Paste the example JSON from below into your new file and save it.

2.  **Place a Chest in the World:**
    - In-game, place a regular Minecraft chest (or any block with an inventory, like a barrel) where you want your loot chest to be.

3.  **Set the Loot Tier:**
    - Look directly at the chest you just placed.
    - Type the command: `/lnt set my_first_chest`
    - A floating nameplate should appear above the chest, and it is now a protected, unbreakable loot chest.

4.  **Get the Key:**
    - To open the chest, you need the correct key.
    - Type the command: `/lnt key my_first_chest`
    - This will give you a key specifically for that tier.

5.  **Open the Chest:**
    - Right-click the chest with the key in your hand to open the spinner and get your rewards!
    - Left-click the chest to see a preview of all possible loot.

### Part 2: Creating a Loot Bag

1.  **Create the JSON File:**
    - Navigate to your server's `config/lootnthings/bags/` directory.
    - Create a new file named `my_first_bag.json`.
    - Paste the example JSON from below into your new file and save it.

2.  **Get the Loot Bag:**
    - Type the command: `/lnt givebag @s my_first_bag`
    - This will give you a loot bag item.

3.  **Use the Bag:**
    - **Right-click** the bag in your hand to open the spinner and get your rewards. The bag will be consumed.
    - **Shift + Right-click** the bag to see a preview of all possible loot without consuming it.

## Configuration

### 1. Main Config (`config/lootnthings-common.toml`)

This file handles the universal, command-based economy system. It is generated on the first run.

- `command_based_enabled`: `true` or `false`. Enables the command-based economy system if no native integration is found.
- `currency_symbol`: The symbol to use for currency (e.g., "$").
- `has_decimals`: `true` if your economy uses decimals (e.g., 5.25), `false` if it uses whole numbers.
- `give_command`: The command to give a player money. Use `[player]` and `<amount>`.
- `take_command`: The command to take money from a player.
- `balance_command`: The command to check a player's balance. Its output must contain a number.

### 2. Loot Tables (`config/lootnthings/`)

Your loot table JSON files go here.
- `config/lootnthings/chests/`: Place all loot chest JSONs here.
- `config/lootnthings/bags/`: Place all loot bag JSONs here.

You can create as many files as you want. The filename (without `.json`) becomes the tier name.

## JSON Structure

Here is a detailed breakdown of a loot table JSON file.

```json
{
  "parent": "chests/base_chest",
  "display_name": "[#FFA500]The Ultimate Showcase Chest",
  "permission": "my.custom.permission",
  "spins": 5,
  "pity_after": 5,
  "pity_spins": 3,
  "pity_unique": true,
  "key_item": "minecraft:tripwire_hook",
  "deck": true,
  "unique_rolls": true,
  "cooldown": 10,
  "cost": {
    "type": "xp",
    "amount": 1
  },
  "sounds": {
    "click": "minecraft:block.note_block.hat",
    "win": "minecraft:entity.player.levelup"
  },
  "broadcast": {
    "open": "[player] is opening The Ultimate Showcase Chest!",
    "win": "[player] won a <display_name> from the [tier] chest!",
    "shuffle": "The deck for the [tier] chest has been reshuffled!"
  },
  "vars": {
    "color": [
      {"value": "red", "weight": 1},
      {"value": "blue", "weight": 1}
    ]
  },
  "loot": [
    {
      "type": "item",
      "id": "minecraft:diamond",
      "display_name": "A Shiny Diamond",
      "icon": "minecraft:diamond",
      "count": 1,
      "weight": 10,
      "pity_weight": 5,
      "group": "gems",
      "always": false
    }
  ]
}
```

### Root Fields

- `permission` (Optional): A custom permission node for this specific loot tier.
- `display_name` (Optional): The name shown in GUIs. Supports color codes.
- `spins` (Optional, Default: 1): The number of rewards to grant per opening.
- `pity_after` (Optional): The number of openings required to trigger a pity roll.
- `pity_spins` (Optional, Default: `spins`): The number of rewards to grant on a pity roll.
- `pity_unique` (Optional, Default: `false`): If `true`, a pity roll will try its best to not give duplicate rewards.
- `key_item` (Optional): The item ID required to open this loot chest.
- `deck` (Optional, Default: `false`): If `true`, treats the loot pool as a server-wide deck, removing items as they are won until it's empty, then reshuffles.
- `unique_rolls` (Optional, Default: `false`): If `true`, prevents rewards from the same `group` from appearing in a single spin session.
- `cooldown` (Optional): The time in seconds a player must wait before opening this tier again.
- `cost` (Optional): An additional cost to open.
  - `type`: Can be `"xp"`, `"item"`, or `"economy"`.
  - `amount`: The number of XP levels, items, or currency required.
  - `id` (Required for `item` type): The item ID of the cost.
- `sounds` (Optional): Custom sounds for the spinner.
- `broadcast` (Optional): Configurable server-wide messages.
- `vars` (Optional): A map of global variables for this loot table.

### Loot Entry Fields

- `type`: The type of reward.
- `weight`: The chance of this item being picked in a normal roll.
- `pity_weight` (Optional): The weight used during a pity roll.
- `display_name` (Optional): A custom name for the reward.
- `icon` (Optional): Overrides the item shown in the spinner GUI.
- `count` (Optional, Default: 1): The amount of the item or currency.
- `id` (Required for `item` and `loot_table`): The item ID or the path to another loot table file.
- `command` (Required for `command`): The command to execute.
- `rewards` (Required for `multi`): An array of sub-reward entries.
- `group` (Optional): A name used by `unique_rolls` to group similar items.
- `always` (Optional, Default: `false`): If `true`, this item will not be consumed from a `deck`.

## Commands & Permissions

This mod features a comprehensive permission system. If LuckPerms is installed, it will use the nodes below. If not, it will fall back to the default Minecraft operator levels (2 for admin, 0 for player).

### Admin Commands
- `/lnt set <tier>` - `lootnthings.command.set`
- `/lnt remove` - `lootnthings.command.remove`
- `/lnt key <tier> [target]` - `lootnthings.command.key`
- `/lnt givebag <target> <tier>` - `lootnthings.command.givebag`
- `/lnt reload` - `lootnthings.command.reload`

### Player Commands
- `/lnt pity <tier>` - `lootnthings.command.pity`

### Action Permissions
These permissions control a player's ability to interact with loot chests and bags.

- **Opening:**
  - Default: `lootnthings.open.<type>.<tier_name>` (e.g., `lootnthings.open.chests.exampleChest`)
  - Custom: If you define `"permission": "my.custom.node"` in your JSON, the required node becomes `my.custom.node.open`.
- **Previewing:**
  - Default: `lootnthings.preview.<type>.<tier_name>` (e.g., `lootnthings.preview.bags.exampleBag`)
  - Custom: Uses the custom permission from the JSON: `my.custom.node.preview`.

## Placeholders

- `[player]`: The player's name.
- `[x]`, `[y]`, `[z]`: The player's current block coordinates.
- `[xp_level]`: The player's current experience level.
- `[score.objective_name]`: The player's score in the specified scoreboard objective.
- `[tier]`: The clean name of the loot tier (e.g., `exampleChest`).
- `<variable_name>`: A custom variable defined in a `vars` block.
- `[<xp_level>*10]`: Simple math operations. Supports `+`, `-`, `*`, `/` and can be combined with other numeric placeholders.

Enjoy creating your unique loot experiences!
