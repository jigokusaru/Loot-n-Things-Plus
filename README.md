# Loot and Things Plus

Welcome to Loot and Things Plus, a highly configurable and powerful loot system for Minecraft NeoForge. This mod allows server administrators to create custom loot chests and loot bags with advanced features like weighted rewards, variables, pity timers, and server-wide announcements.

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

- `parent` (Optional): Inherits all `loot` and `vars` from another file.
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
  - `click`: Sound that plays during the animation.
  - `win`: Sound that plays when the animation finishes.
- `broadcast` (Optional): Configurable server-wide messages.
  - `open`: Sent when a player opens the loot.
  - `win`: Sent for each item won.
  - `shuffle`: Sent when a global deck is reshuffled.
- `vars` (Optional): A map of global variables for this loot table.

### Loot Entry Fields

- `type`: The type of reward. Can be `item`, `command`, `economy`, `multi`, `loot_table`, or `nothing`.
- `weight`: A number representing the chance of this item being picked in a normal roll. Higher is more common.
- `pity_weight` (Optional): The weight used during a pity roll.
- `display_name` (Optional): A custom name for the reward, used in chat and broadcasts.
- `count` (Optional, Default: 1): The amount of the item or currency. Can be a number, a range object (`{"1": 50, "5": 10}`), or a variable (`"<my_var>"`).
- `id` (Required for `item` and `loot_table`): The item ID or the path to another loot table file.
- `command` (Required for `command`): The command to execute.
- `rewards` (Required for `multi`): An array of sub-reward entries.
- `group` (Optional): A name used by `unique_rolls` to group similar items.
- `always` (Optional, Default: `false`): If `true`, this item will not be consumed from a `deck`.

## Commands

### Admin Commands (Permission Level 2)
- `/lnt set <tier>`: Sets the loot tier of the chest you are looking at.
- `/lnt remove`: Removes the loot tier from the chest you are looking at.
- `/lnt key <tier> [target]`: Gives a key for the specified tier to you or an optional target.
- `/lnt givebag <target> <tier>`: Gives a loot bag to the specified player.
- `/lnt reload`: Reloads all loot table JSONs from the config folder.

### Player Commands (Permission Level 0)
- `/lnt pity <tier>`: Checks your current pity counter for a specific loot tier.

## Placeholders

You can use these placeholders in `display_name`, `command`, and `broadcast` messages.

- `[player]`: The player's name.
- `[x]`, `[y]`, `[z]`: The player's current block coordinates.
- `[xp_level]`: The player's current experience level.
- `[score.objective_name]`: The player's score in the specified scoreboard objective.
- `[tier]`: The clean name of the loot tier (e.g., `exampleChest`).
- `<variable_name>`: A custom variable defined in a `vars` block.
- `[<xp_level>*10]`: Simple math operations. Supports `+`, `-`, `*`, `/` and can be combined with other numeric placeholders.

Enjoy creating your unique loot experiences!
