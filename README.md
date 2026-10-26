# DialogKits

**Current version: 1.0.2**

DialogKits adds native Minecraft dialog screens to [PlayerKits2](https://github.com/Ajneb97/PlayerKits2).

## Features

- On Java Edition, running bare `/kit` (also `/kits` and `/playerkits`) opens a native dialog instead of PlayerKits2's main chest menu.
- Each PlayerKits2 kit has two side-by-side buttons: **Claim** and **Preview**.
- The native preview dialog shows the kit icon and a compact list of its item contents.
- Claiming is delegated to PlayerKits2, preserving its permissions, cooldowns, requirements, actions, and economy checks.
- Recognizes PlayerKits2's `claim_kit_short_command` setting.
- Floodgate players retain the existing PlayerKits2 chest GUI.
- Kit names and buttons are read from PlayerKits2 at runtime; no duplicate kit config is required.
- Button labels and dialog titles are configurable in `config.yml`.

## Compatibility

- Targets the Paper API for **Minecraft 1.21.11**, the oldest supported server version.
- Intended to run on Paper **1.21.11 through 26.3** while staying on the common dialog/API surface.
- Build bytecode targets Java 21. Paper 26.x servers themselves require Java 25 or newer to run.
- Java clients must support native Minecraft dialogs (1.21.6+).
- PlayerKits2 must be installed and enabled.
- Floodgate is optional and detected automatically for Bedrock fallback.

## Commands

- `/kit` — opens the native kit dialog for Java players.
- `/dialogkits` or `/dialogkits open` — opens the dialog menu.
- `/dialogkits preview <kit>` — opens the native preview dialog.
- `/dialogkits reload` — reloads DialogKits configuration (OP by default).

Other PlayerKits2 commands, such as `/kit claim`, `/kit create`, `/kit edit`, and `/kit reload`, are not intercepted.

## Build

Use Java 21 or newer with Maven:

```sh
mvn clean package
```

The plugin jar is generated at `target/DialogKits.jar`.
