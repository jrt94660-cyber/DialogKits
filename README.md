# DialogKits

**Current version: 1.0.1**

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

## Requirements

- Paper 1.21.7 or newer (the build targets Paper 26.1.2).
- PlayerKits2 installed and enabled.
- Java clients that support native Minecraft dialogs (1.21.6+).
- Floodgate is optional and detected automatically for Bedrock fallback.

## Commands

- `/kit` — opens the native kit dialog for Java players.
- `/dialogkits` or `/dialogkits open` — opens the dialog menu.
- `/dialogkits preview <kit>` — opens the native preview dialog.
- `/dialogkits reload` — reloads DialogKits configuration (OP by default).

Other PlayerKits2 commands, such as `/kit claim`, `/kit create`, `/kit edit`, and `/kit reload`, are not intercepted.

## Build

Use Java 25 and Maven:

```sh
mvn clean package
```

The plugin jar is generated at `target/DialogKits.jar`.
