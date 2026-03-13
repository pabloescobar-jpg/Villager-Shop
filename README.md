# VillagerShop

A lightweight Paper plugin that turns Minecraft's native villager trade UI into a fully manageable player shop — no economy plugin required.

Players browse trades through a familiar vanilla merchant interface. Admins create and delete trades in-game through intuitive chest GUIs. Everything persists across restarts via YAML.

---

## Features

- **Vanilla merchant GUI** — feels completely native, no custom UI
- **In-game trade management** — create and delete trades without touching any config files
- **Paginated delete GUI** — browse and remove trades one click at a time
- **LuckPerms compatible** — permission-gated admin commands work out of the box
- **Smart tab completion** — subcommands only visible to admins
- **No dependencies** — physical items only, zero economy plugin requirement
- **Trades never lock** — unlimited uses, discounts disabled

---

## Commands

| Command | Description | Permission |
|---|---|---|
| `/shop` | Open the shop | Everyone |
| `/vs create` | Open the trade creator GUI | `villagershop.admin` |
| `/vs delete` | Open the trade deletion GUI | `villagershop.admin` |
| `/vs info` | Show command help | `villagershop.admin` |

`/villagershop` is a full alias for `/vs`.

---

## Permissions

| Node | Default | Description |
|---|---|---|
| `villagershop.admin` | OP | Access to all `/vs` admin commands |

**LuckPerms:** `/lp group admin permission set villagershop.admin true`

---

## Installation

1. Drop `VillagerShop-x.x.x.jar` into your server's `plugins/` folder
2. Restart the server
3. Use `/vs create` in-game to add your first trade

---

## Usage

### Creating a trade
1. Run `/vs create`
2. Place your **input item(s)** in the left slot(s) and your **output item** in the center slot
3. Click **Confirm** — the trade is saved instantly

### Deleting a trade
1. Run `/vs delete`
2. Browse your trades (5 per page)
3. Click the red **✗** button next to any trade to remove it

---

## Requirements

- Paper 1.21.4+
- Java 21+
