# DialogKits

**Current version: 1.2.9 — OP access for admin layout and verification**

DialogKits is now an independent kit plugin. It no longer needs PlayerKits2 at runtime or imports its Java classes. Its first-start importer reads the existing PlayerKits2 folder, copies compatible kit YAML files, imports supported settings, uses the old inventory layout to seed manual menu ordering, and migrates matching cooldown/claim history when kit names can be mapped. The original PlayerKits2 files are never changed or deleted.

## Highlights

- `/kit` and `/kit help` print the command list. `/kit open`, `/kits open`, `/playerkits open`, or `/dialogkits` opens the kit menu.
- Floodgate/Bedrock players use DialogKits' own chest GUI; left-click to claim and right-click to preview.
- Independent plugins/DialogKits/kits/*.yml kit definitions and player-data.yml claim/cooldown storage.
- Supports kit cooldowns, one-time kits, permission-gated kits, automatic armor placement, clear-inventory kits, inventory overflow/drop behavior, optional Vault economy prices, PlaceholderAPI expansion, simple extra requirements, claim/error actions, first-join kits, and player claim resets.
- /kit create <name> creates a kit from the player's current inventory. /kit edit <name> opens a 54-slot item editor; changes save when the editor closes.
- `/kit create <name>`, `/kit remove <kit>` (alias: `/kit delete <kit>`), `/kit replace <kit>`, and `/kit reload` require an in-game player with actual Minecraft OP status; console senders, LuckPerms group membership, and granted permissions cannot bypass these checks. `/kit replace <kit>` overwrites the kit's saved item list with your current storage inventory, armor, and off-hand while preserving the kit's settings. Other management commands include `/kit edit <kit>` and `/kit give <kit> <player>` (LuckPerms owner group); `/kit verify` and `/dialogkitsadmin edit` require in-game OP status. Use `/kit help` to see the commands in-game.
- The configured LuckPerms owner primary group (default: owner) can use /dialogkitsadmin edit to manually assign positions 1–50. The owner-only Add Kit button stays visible when the menu is empty.
- Existing PlayerKits2 permission nodes playerkits.kit.<kit> are accepted alongside dialogkits.kit.<kit>.
- Imported Bukkit/Paper item serialization is kept, preserving supported enchantments, item components, custom names, and lore.

## Separate folders

    plugins/
    ├── PlayerKits2/             # left untouched as a backup
    │   ├── config.yml
    │   ├── inventory.yml
    │   ├── kits/
    │   └── players/
    └── DialogKits/
        ├── config.yml           # standalone DialogKits settings
        ├── messages.yml         # standalone messages
        ├── kits/                # independent copies of imported kit files
        ├── kit-order.yml        # manual native-dialog positions
        ├── player-data.yml      # standalone claims and cooldowns
        └── migration.yml        # one-time import status

## Migration

1. Stop the server and back it up.
2. Move the PlayerKits2 JAR out of the plugins folder, but leave plugins/PlayerKits2/ and all its files in place.
3. Install DialogKits 1.2.9 and start the server. The importer reads the old folder and copies compatible data without changing it. Removing the JAR first prevents both plugins from competing for /kit.
4. Verify plugins/DialogKits/kits/, previews, menu ordering, permission nodes, cooldowns, and imported claim-data mappings.
5. Keep plugins/PlayerKits2/ as a backup until you are satisfied, then edit only DialogKits. The two config.yml files and kit folders remain separate.

The importer uses explicit aliases for the uploaded server data, including StarterKit to Starter, BoosterKit to booster, OgKit to og, Taho+Kit to taho+, Taho++Kit to taho++, and ComedianKit to media. Claims that cannot be confidently mapped are skipped instead of assigned to a potentially wrong kit. Advanced third-party PlayerKits2 extensions and MySQL-specific storage are not imported by this initial standalone conversion; Vault, PlaceholderAPI, and Floodgate integrations are optional.

## Build

Requires Java 21+ and Maven:

    mvn clean package

The versioned JAR is generated at `target/DialogKits-${project.version}.jar` (for example, `DialogKits-1.2.9.jar`). The embedded `plugin.yml` also carries the plugin version.


## Command tab completion

Version 1.2.9 provides OP-only tab suggestions for `/kit create`, `/kit remove`/`delete`, `/kit replace`, `/kit reload`, `/kit verify`, and `/dialogkitsadmin`. Tab completion for `/kit edit` and `/kit give` follows the configured LuckPerms owner group. Typing `/kit ` suggests the available subcommands and direct kit names. Running bare `/kit` opens the kit menu; `/kit help` shows the command list.
