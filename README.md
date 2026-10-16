# Tool Wheel

[![Minecraft](https://img.shields.io/badge/Minecraft-1.21.1-brightgreen.svg)](https://minecraft.net)
[![Loader](https://img.shields.io/badge/Loader-NeoForge-orange.svg)](https://neoforged.net)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

**Tool Wheel** gives every player a native, lightweight tactical tool belt and radial wheel—**without** requiring you to craft a belt item, occupy a Curios/Trinkets slot, or clutter your inventory.

---

## ✨ Key Features

* 🎯 **Smooth Radial Tool Wheel**
  Hold **`Z`** (configurable) anywhere in-game to open a smooth, hardware-rendered radial wheel. Move your mouse to a sector and release to instantly swap that tool, weapon, or food item into **Hotbar Slot 1** and select it automatically. Move to the center (`↓`) to holster the active item back into the belt.

* 🗄️ **Sliding Survival Inventory Drawer**
  Seamlessly attaches a vanilla-styled sliding drawer to the right side of your survival inventory. Includes smooth open/close animations, empty-slot ghost icons, active-in-hand green highlights, and automatic potion effect shifting.

* 🏹 **Dedicated 256-Arrow Ammo Slot**
  Located at the bottom of the drawer, the dedicated ammo slot holds up to **256 arrows or firework rockets** in a single slot! Bows and crossbows automatically draw ammunition directly from this slot (while still respecting arrows/fireworks held in your off-hand).

* 🍖 **Smart Item Filter & Mod Compatibility**
  Supports all vanilla tools, weapons, bows, crossbows, tridents, maces, shields, utility tools (shears, fishing rod, spyglass, brush, flint & steel), **all food items**, and modded weapons/guns/staves (customizable via datapack item tags `#tool_wheel:belt_tools` and `#tool_wheel:belt_ammo`).

* ⚙️ **Configurable Capacity & Death Rules**
  * **Tool Slots (`4` – `8`, default `5`)**: Adjust the number of radial/drawer tool slots per world (via `tool_wheel-server.toml` or the in-game NeoForge Config GUI). If reduced, items in disabled slots are safely returned to your inventory.
  * **Keep on Death (`true` / `false`, default `false`)**: Choose whether belt items always stay with you on death or follow the vanilla `keepInventory` gamerule (via `tool_wheel-common.toml`).

---

## 🎮 Controls

| Action | Default Key | Description |
| :--- | :--- | :--- |
| **Tool Wheel (Hold)** | `Z` | Hold to open radial wheel, release to equip/holster. |
| **Open Tool Wheel GUI** | *Unbound* | Opens standalone belt screen (handy in Creative mode). |

---

## 🛠️ Building from Source

1. Clone the repository:
   ```bash
   git clone https://github.com/<your-username>/tool-wheel.git
   cd tool-wheel
   ```
2. Build with Gradle (requires **JDK 21**):
   ```bash
   ./gradlew build
   ```
3. The compiled mod JAR will be located in `build/libs/`.

---

## 📄 License

This project is licensed under the [MIT License](LICENSE).
