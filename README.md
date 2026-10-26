# DialogKits

**Current version: 1.2.0 — standalone conversion**

DialogKits is now an independent kit plugin. It no longer needs PlayerKits2 at runtime or imports its Java classes. Its first-start importer reads the existing PlayerKits2 folder, copies compatible kit YAML files, imports supported settings, uses the old inventory layout to seed manual menu ordering, and migrates matching cooldown/claim history when kit names can be mapped. The original PlayerKits2 files are never changed or deleted.

## Highlights

- Java players open a native Minecraft dialog using /kit, /kits, /playerkits, or /dialogkits.
- Floodgate/Bedrock players use DialogKits' own chest GUI; left-click to claim and right-click to preview.
- Independent plugins/DialogKits/kits/*.yml kit definitions and player-data.yml claim/cooldown storage.
- Supports kit cooldowns, one-time kits, permission-gated kits, automatic armor placement, clear-inventory kits, inventory overflow/drop behavior, optional Vault economy prices, PlaceholderAPI expansion, simple extra requirements, claim/error actions, first-join kits, and player claim resets.
- /kit create <name> creates a kit from the player's current inventory. /kit edit <name> opens a 54-slot item editor; changes save when the editor closes.
- /kit give <kit> <player>, /kit delete <kit>, /kit reset <kit> <player>, /kit preview <kit>, /kit verify, and /kit reload.
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

1. Back up the server before changing kit plugins.
2. Install DialogKits 1.2.0 while plugins/PlayerKits2/ is still present. DialogKits copies kit YAML files and supported settings on first start.
3. Verify plugins/DialogKits/kits/, previews, menu ordering, permission nodes, cooldowns, and imported claim-data mappings.
4. Disable/remove the PlayerKits2 JAR after verifying the migration. Keep its folder as a backup until satisfied.
5. Edit only DialogKits from then on; its config and kit data are independent.

The importer uses explicit aliases for the uploaded server data, including StarterKit to Starter, BoosterKit to booster, OgKit to og, Taho+Kit to taho+, Taho++Kit to taho++, and ComedianKit to media. Claims that cannot be confidently mapped are skipped instead of assigned to a potentially wrong kit. Advanced third-party PlayerKits2 extensions and MySQL-specific storage are not imported by this initial standalone conversion; Vault, PlaceholderAPI, and Floodgate integrations are optional.

## Build

Requires Java 21+ and Maven:

    mvn clean package

The JAR is generated at target/DialogKits.jar.
