# Tomsmatic

Tomsmatic compares your Litematica material list with the items in your Tom’s Simple Storage network. See what the schematic needs, what you’ve already placed, what is in storage, and what is still missing.

## Check a build

1. Select a schematic placement in Litematica or open its material list.
2. Click the chest-and-checkmark button below the beacon indicator in a Tom’s Simple Storage terminal. You can also assign a material-list hotkey in **Mod Menu → Tomsmatic → Settings**.
3. Hover over a row to see the total and shortfall in stacks and individual items. The biggest shortages appear first.

The hotkey fetches stock in the background when you have a linked wireless terminal and can reach its network. If the terminal supports crafting, the crafting icon in a material row opens it and tries to fill its grid for a recipe you’ve unlocked.

## Refill Easy Place

Enable **Easy Place refill** in the report and carry a linked Advanced Wireless Terminal. When you run out of the item you’re placing, Tomsmatic can request up to one stack and retry the placement.

Install Tomsmatic on the server as well if you want the refill to run without opening the terminal. When the server runs Tom’s Simple Storage without Tomsmatic, refill uses the regular terminal screen.

## Compatibility

- Choose the JAR for your exact Minecraft Java Edition version. Builds are available for **1.21.11, 26.1, 26.1.1, 26.1.2, 26.2, and 26.3** and require **Fabric Loader 0.19.5+**. Use **Java 21+** for 1.21.11 and **Java 25+** for 26.x.
- Install **Fabric API** and the **Tom’s Simple Storage** version listed for your build in the project readme.
- Install **Litematica** and **MaLiLib** on the client for schematic comparison and Easy Place.
- **Mod Menu** is optional. The interface supports English and Russian.
