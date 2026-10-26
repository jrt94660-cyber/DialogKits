# DialogKits

**Current version: 1.0.9**

DialogKits adds native Minecraft dialog screens to [PlayerKits2](https://github.com/Ajneb97/PlayerKits2).

## Features

- On Java Edition, running bare `/kit` (also `/kits` and `/playerkits`) opens a native dialog instead of PlayerKits2's main chest menu.
- Every kit shows a green **Ready** button if the player can claim it.
- Kits the player lacks permission to claim show a red **Locked** button; clicking it displays a permission message instead of attempting a claim.
- Successfully claimed unlimited kits show **Claimed** in yellow. One-time and cooldown kits use PlayerKits2's stored claim state.
- Successful claims through the dialog and intercepted PlayerKits2 claim commands refresh the status display.
- Preview actions are clean text-only **Preview** buttons; no spyglass item is displayed in the dialog.
- Native preview dialogs show the kit icon and a compact list of its item contents.
- Claiming is delegated to PlayerKits2's kit manager, preserving its permission, cooldown, requirement, inventory, economy, and action checks.
- Floodgate players retain the existing PlayerKits2 chest GUI.
- Kit names and kit data are read from PlayerKits2 at runtime; no duplicate kit config is required.
- The Java kit dialog displays only kits that an admin has explicitly assigned to a manual slot (1–50). Unassigned kits remain hidden until assigned a slot.
- Ready, Claimed, and Locked button labels and dialog titles are configurable in `config.yml`.
- The main `/kit` and `/kits` dialog always includes exactly one **Add Kit** button, even after kits have been added. It opens `/dialogkitsadmin edit`.

## Compatibility

- Targets the Paper API for **Minecraft 1.21.11** and is intended for Paper **1.21.11 through 26.3**.
- Build bytecode targets Java 21. Use the Java runtime required by the Paper version installed on your server.
- Java clients must support native Minecraft dialogs (1.21.6+).
- PlayerKits2 must be installed and enabled.
- Floodgate is optional and detected automatically for Bedrock fallback.

## Commands

- `/kit` — opens the native kit dialog for Java players.
- `/dialogkits` or `/dialogkits open` — opens the dialog menu.
- `/dialogkits claim <kit>` — claims via PlayerKits2 and refreshes the Ready/Claimed state.
- `/dialogkits preview <kit>` — opens the native preview dialog.
- `/dialogkits reload` — reloads DialogKits configuration (OP by default).
- `/dialogkitsadmin edit` — opens a native admin dialog listing all PlayerKits2 kits. Select a kit, then choose Slot 1–50 to make it appear in that manual position. Manual positions are saved in `plugins/DialogKits/kit-order.yml`. If a slot is occupied, its kit swaps to the selected kit's previous slot if applicable; otherwise it becomes unassigned.

Other PlayerKits2 administrative commands remain available.

## Build

Use Java 21 or newer with Maven:

```sh
mvn clean package
```

The plugin jar is generated at `target/DialogKits.jar`.
