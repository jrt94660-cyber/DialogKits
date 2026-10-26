# DialogKits

**Current version: 1.0.3**

DialogKits adds native Minecraft dialog screens to [PlayerKits2](https://github.com/Ajneb97/PlayerKits2).

## Features

- On Java Edition, running bare `/kit` (also `/kits` and `/playerkits`) opens a native dialog instead of PlayerKits2's main chest menu.
- Each PlayerKits2 kit has a **Ready** button in green and a **Preview** action without the kit name on its label.
- After a successful claim, unlimited kits show a red **Claimed** state; one-time and cooldown kits use PlayerKits2's stored claim state.
- Successful claims through the menu and normal PlayerKits2 claim commands are reflected in the dialog.
- The dialog menu displays a real Minecraft Spyglass item alongside the preview guidance.
- The native preview dialog shows the kit icon and a compact list of its item contents.
- Claiming uses PlayerKits2's own kit manager, preserving its permission, cooldown, requirement, inventory, economy, and action checks.
- Floodgate players retain the existing PlayerKits2 chest GUI.
- Kit names and kit data are read from PlayerKits2 at runtime; no duplicate kit config is required.
- Ready/Claimed button labels and dialog titles are configurable in `config.yml`.

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

Other PlayerKits2 administrative commands remain available.

## Build

Use Java 21 or newer with Maven:

```sh
mvn clean package
```

The plugin jar is generated at `target/DialogKits.jar`.
