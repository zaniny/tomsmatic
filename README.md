# Tomsmatic

[Русская версия](README_ru.md)

<img src="src/main/resources/assets/schematic_stock/icon.png" width="96" alt="Tomsmatic icon">

Tomsmatic compares the materials in a Litematica schematic with the items in your Tom’s Simple Storage network. One report shows how much you need, what you’ve already placed, what’s in stock, and what you’re still missing. Open it from the terminal or with a hotkey. When building with Easy Place, you can also enable automatic restocking from your storage network.

For **Minecraft Java 26.3** and **Fabric**. [Changelog](CHANGELOG.md) · [Modrinth description](MODRINTH.md)

## Installation

Copy `build/tomsmatic-0.3.12.jar` into your profile’s `mods` folder. When updating, remove the previous Tomsmatic JAR from `mods` and restart the client. This build uses the following versions.

| Component | Version |
| --- | --- |
| Minecraft Java Edition | 26.3 |
| Fabric Loader | 0.19.5 |
| Fabric API | 0.162.0 or later for Minecraft 26.3 |
| Litematica | 0.29.1 |
| MaLiLib | 0.30.2 |
| Tom’s Simple Storage | 2.12.1 |

Material checks and client-side auto-refill work on a server running Tom’s Simple Storage and its dependencies. For seamless auto-refill, install the same Tomsmatic JAR on the server and restart it. Litematica and MaLiLib are required on the client.

If Tomsmatic is installed on the server, clients with the server’s usual mod set can still connect without Tomsmatic and use the standard terminal. Tomsmatic’s interface uses Russian or English according to Minecraft’s language setting.

## Checking materials

1. In Litematica, open a schematic and its material list. Wait for the rows to appear.
2. Open a terminal for the network you want to check. You can use a regular terminal, a crafting terminal, or a wireless terminal that can access the network under Tom’s Simple Storage rules.
3. Click **Check schematic**, the chest-and-checkmark button below the beacon level indicator. The report shows **Required**, **Placed**, **In stock**, and **Shortfall**. The largest shortfalls appear first. Hover over a row to see the total and shortfall in stacks and individual items.

Tomsmatic uses the last material list you opened in the current dimension. If you haven’t opened a list yet, Tomsmatic uses the selected schematic placement and starts Litematica’s calculation in the background if needed. When you open the report, Litematica recalculates the materials for the placed schematic. **Required** includes Litematica’s multiplier. **Placed** counts blocks in the world that already match the schematic. **Shortfall** subtracts the placed blocks and terminal stock from the required amount. For a list opened directly from a schematic file without a placement, **Placed** shows a dash. The calculation excludes your personal inventory and ingredients for items that could be crafted later.

For `.schem` files (Sponge/WorldEdit) and vanilla `.nbt` structures, open or import the file in Litematica and generate its material list the same way you would for a `.litematic` file.

Each mouse wheel notch scrolls the report by one row. You can also use the ↑ and ↓ arrow keys or drag the scrollbar on the right.

If you open the report from a terminal, **Close** and **Esc** return you to that terminal. To open the report on its own, go to **Mod Menu → Tomsmatic → Settings**. Click **Material list key** and press the key you want to assign. **Esc** cancels the assignment, and **Clear** removes the binding. You can also click **Open material list** there. When opened this way, Tomsmatic fetches stock through the wireless terminal in the background. **In stock** shows dashes while the data loads or the network is unavailable. Closing this report takes you straight back to the game.

When you use a crafting terminal, the **Craft** column appears on the right. Clicking the crafting table icon in a row opens the terminal and uses its standard request to fill the crafting grid. The icon is available for recipes you’ve unlocked in Minecraft’s recipe book. If there aren’t enough ingredients, the report shows a message and keeps the current grid intact. To craft from a report opened with the hotkey or from settings, you need a linked Advanced Wireless Terminal that opens a crafting terminal.

## Easy Place auto-refill

1. Turn on **Easy Place auto-refill** in the report. The setting is saved in your profile and is off by default.
2. Link an **Advanced Wireless Terminal** to the network and keep it in your inventory. Check that the normal Tom’s Simple Storage keybind opens the right network where you’re building.
3. Enable Easy Place in Litematica and try to place a block you don’t have in your inventory. Tomsmatic requests up to one standard stack and retries placement at the same spot after your inventory updates.

If the server supports seamless auto-refill, the item goes directly into your inventory. On a server running only Tom’s Simple Storage, the terminal briefly opens to withdraw the item normally. Tomsmatic shows a message if the network is unavailable, the item is out of stock, or your inventory is full.

## Building from source

To build without an installed game profile, run `python scripts/build.py` with Python 3.11+ and JDK 25. The JAR and its checksum go into `build/release`. See [RELEASING.md](RELEASING.md) for GitHub Actions setup and publishing instructions.

The PowerShell build uses local files from your Modrinth profile and JDK 25. Run the following command from the project folder in PowerShell 7.

```powershell
.\build.ps1 -Jdk 'C:\Program Files\JetBrains\JetBrains Rider 2026.1.2\jbr'
```

Use `-Profile` to select another profile or `-Modrinth` to point to another Modrinth App folder. To compile the settings integration, the script uses Mod Menu from the profile or the file supplied through `-ModMenuJar`. If `JAVA_HOME` points to JDK 25 and the profile contains Mod Menu, you can run `./build.ps1` without arguments. The output is `build/tomsmatic-0.3.12.jar`.
