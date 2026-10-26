package com.taho.dialogkits;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class DialogKitsPlugin extends JavaPlugin implements Listener, CommandExecutor {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private final Map<String, KitDefinition> kits = new LinkedHashMap<>();

    private File messagesFile;
    private YamlConfiguration messages;
    private File playerDataFile;
    private YamlConfiguration playerData;
    private File orderFile;
    private YamlConfiguration orderData;
    private File migrationFile;
    private YamlConfiguration migrationData;
    private File kitsFolder;

    @Override
    public void onEnable() {
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            getLogger().severe("Could not create the DialogKits data folder.");
        }
        saveDefaultConfig();
        kitsFolder = new File(getDataFolder(), "kits");
        if (!kitsFolder.exists() && !kitsFolder.mkdirs()) {
            getLogger().severe("Could not create the DialogKits/kits folder.");
        }

        loadMigrationData();
        migrateLegacyKitFilesAndSettings();
        loadKits();
        loadAdminOrderData();
        migrateLegacyInventoryLayout();
        loadPlayerData();
        migrateLegacyPlayerData();
        migratePreviousDialogKitsClaimData();
        loadMessages();

        registerCommand("kit");
        registerCommand("dialogkits");
        registerCommand("dialogkitsadmin");
        Bukkit.getPluginManager().registerEvents(this, this);

        Plugin oldPlugin = Bukkit.getPluginManager().getPlugin("PlayerKits2");
        if (oldPlugin != null && oldPlugin.isEnabled()) {
            getLogger().warning("PlayerKits2 is still enabled. For predictable /kit routing, move its JAR out of the plugins folder before the next restart; keep plugins/PlayerKits2 as your untouched backup.");
        }
        getLogger().info("Standalone DialogKits enabled with " + kits.size() + " kit(s).");
    }

    @Override
    public void onDisable() {
        savePlayerData();
        saveAdminOrderData();
    }

    private void registerCommand(String name) {
        PluginCommand command = getCommand(name);
        if (command != null) command.setExecutor(this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("dialogkitsadmin")) return handleAdminCommand(sender, args);
        if (name.equals("dialogkits") && args.length == 0) {
            if (sender instanceof Player player) openFor(player);
            else sender.sendMessage("Usage: /dialogkits reload");
            return true;
        }
        if (name.equals("dialogkits") && args.length > 0 && args[0].equalsIgnoreCase("open")) {
            if (sender instanceof Player player) openFor(player);
            else sender.sendMessage("The kit menu can only be opened by a player.");
            return true;
        }
        return handleKitCommand(sender, args);
    }

    private boolean handleKitCommand(CommandSender sender, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            showKitHelp(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("open")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Only players can open the kit menu.");
                return true;
            }
            if (!player.hasPermission("dialogkits.open")) {
                tell(player, "no-permission", "&cYou do not have permission to open kits.");
                return true;
            }
            openFor(player);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("claim")) {
            if (args.length < 2) {
                sender.sendMessage(colored("&cUsage: /kit claim <kit>"));
                return true;
            }
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Only players can claim kits.");
                return true;
            }
            claimKit(player, args[1], false, false);
            return true;
        }

        if (sub.equals("preview")) {
            if (args.length < 2) {
                sender.sendMessage(colored("&cUsage: /kit preview <kit> [player]"));
                return true;
            }
            Player target = sender instanceof Player player ? player : null;
            if (args.length >= 3) target = Bukkit.getPlayerExact(args[2]);
            if (target == null) {
                sender.sendMessage(colored("&cThat player is not online."));
                return true;
            }
            openPreview(target, args[1]);
            return true;
        }

        if (sub.equals("locked")) {
            if (!(sender instanceof Player player)) return true;
            tell(player, "kit-locked", "&cYou do not have permission to claim that kit.");
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (player.isOnline()) openFor(player);
            }, 1L);
            return true;
        }

        if (sub.equals("reload")) {
            if (sender instanceof Player player && !player.hasPermission("dialogkits.reload") && !isOwnerRank(player)) {
                tell(player, "no-permission", "&cYou do not have permission to reload DialogKits.");
                return true;
            }
            reloadConfig();
            loadMessages();
            loadKits();
            loadAdminOrderData();
            loadPlayerData();
            sender.sendMessage(colored("&aDialogKits configuration, kits, and messages reloaded."));
            return true;
        }

        if (sub.equals("create")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Only players can create kits from an inventory.");
                return true;
            }
            if (!requireOwner(player)) return true;
            if (args.length < 2) {
                player.sendMessage(colored("&cUsage: /kit create <name>"));
                return true;
            }
            createKitFromInventory(player, args[1]);
            return true;
        }

        if (sub.equals("edit")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Only players can edit kits.");
                return true;
            }
            if (!requireOwner(player)) return true;
            if (args.length < 2) {
                player.sendMessage(colored("&cUsage: /kit edit <name>"));
                return true;
            }
            openKitEditor(player, args[1]);
            return true;
        }

        if (sub.equals("delete")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Only players can delete kits.");
                return true;
            }
            if (!requireOwner(player)) return true;
            if (args.length < 2) {
                player.sendMessage(colored("&cUsage: /kit delete <name>"));
                return true;
            }
            deleteKit(player, args[1]);
            return true;
        }

        if (sub.equals("give")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Usage: /kit give <kit> <player>");
                return true;
            }
            if (!requireOwner(player)) return true;
            if (args.length < 3) {
                player.sendMessage(colored("&cUsage: /kit give <kit> <player>"));
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[2]);
            KitDefinition kit = findKit(args[1]);
            if (target == null || kit == null) {
                player.sendMessage(colored("&cThat kit or player could not be found."));
                return true;
            }
            if (giveKitItems(target, kit)) {
                tell(player, "kit-given", "&aKit &f%kit% &agiven to &f%player%&a.", "%kit%", kit.name, "%player%", target.getName());
                tell(target, "kit-received", "&aYou received kit &f%kit%&a.", "%kit%", kit.name);
            }
            return true;
        }

        if (sub.equals("replace")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Only a player can replace kit contents from their inventory.");
                return true;
            }
            if (!requireOwner(player)) return true;
            if (args.length < 2) {
                player.sendMessage(colored("&cUsage: /kit replace <kit>"));
                return true;
            }
            replaceKitFromInventory(player, args[1]);
            return true;
        }

        if (sub.equals("verify")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Only players with the configured LuckPerms owner rank can run this command.");
                return true;
            }
            if (!requireOwner(player)) return true;
            int bad = 0;
            for (KitDefinition kit : getKits()) {
                if (getKitItems(kit).isEmpty()) {
                    player.sendMessage(colored("&eKit &f" + kit.name + " &ehas no readable items."));
                    bad++;
                }
            }
            player.sendMessage(colored(bad == 0
                    ? "&aVerification complete. All " + kits.size() + " kit(s) have readable items."
                    : "&eVerification complete. Problems found in " + bad + " kit(s)."));
            return true;
        }

        if (args.length == 1 && getConfig().getBoolean("commands.short-claim", false)
                && sender instanceof Player player && findKit(args[0]) != null) {
            claimKit(player, args[0], false, false);
            return true;
        }

        sender.sendMessage(colored("&7Kit commands: &f/kit help, /kit open, /kit claim <kit>, /kit preview <kit>, /kit create <name>, /kit edit <name>, /kit replace <kit>, /kit delete <kit>, /kit give <kit> <player>, /kit reload"));
        return true;
    }

    private void showKitHelp(CommandSender sender) {
        sender.sendMessage(colored("&8&m---------------- &bDialogKits Commands &8&m----------------"));
        sender.sendMessage(colored("&b/kit open &7- Open the kit menu"));
        sender.sendMessage(colored("&b/kit help &7- Show this command list"));
        sender.sendMessage(colored("&b/kit claim <kit> &7- Claim a kit"));
        sender.sendMessage(colored("&b/kit preview <kit> &7- Preview a kit"));
        sender.sendMessage(colored("&b/kit reload &7- Reload DialogKits settings, messages, and kits"));

        if (sender instanceof Player player && isOwnerRank(player)) {
            sender.sendMessage(colored("&8&m---------------- &eOwner Commands &8&m----------------"));
            sender.sendMessage(colored("&b/kit create <name> &7- Create a kit from your inventory"));
            sender.sendMessage(colored("&b/kit edit <name> &7- Edit a kit's item contents"));
            sender.sendMessage(colored("&b/kit delete <name> &7- Delete a kit"));
            sender.sendMessage(colored("&b/kit give <kit> <player> &7- Give a kit to a player"));
            sender.sendMessage(colored("&b/kit replace <kit> &7- Replace kit items with your inventory, armor, and off-hand"));
            sender.sendMessage(colored("&b/kit verify &7- Check kit item files"));
            sender.sendMessage(colored("&b/dialogkitsadmin edit &7- Set the kit dialog positions"));
        }
        sender.sendMessage(colored("&7You can also open the menu with &f/dialogkits"));
    }

    private void openFor(Player player) {
        if (isBedrockPlayer(player) && getConfig().getBoolean("bedrock.enabled", true)) openBedrockMain(player);
        else openNativeKitDialog(player);
    }

    private void openNativeKitDialog(Player player) {
        List<KitDefinition> assigned = getManuallyAssignedKits();
        List<ActionButton> buttons = new ArrayList<>();

        for (KitDefinition kit : assigned) {
            String displayName = getKitDisplayName(kit);
            boolean locked = isKitLocked(player, kit);
            boolean claimed = hasClaimedKit(player, kit);
            String statusKey = locked ? "ui.locked-button" : claimed ? "ui.claimed-button" : "ui.ready-button";
            String fallback = locked ? "&c%kit% &8| &cLocked"
                    : claimed ? "&e%kit% &8| &eClaimed" : "&a%kit% &8| &aReady";
            String status = getConfig().getString(statusKey, fallback);
            buttons.add(button(format(status, displayName, kit.name),
                    locked ? "/dialogkits locked " + kit.name : "/dialogkits claim " + kit.name,
                    color(locked ? "&cYou do not have permission to claim " + displayName + "."
                            : claimed ? "&eClaim status: Claimed."
                            : "&aReady to claim " + displayName + ".")));
            buttons.add(button(color(getConfig().getString("ui.preview-button", "&bPreview")),
                    "/dialogkits preview " + kit.name,
                    color("&7Preview the contents of &f" + displayName + "&7.")));
        }

        if (isOwnerRank(player)) {
            buttons.add(button(color(getConfig().getString("ui.add-kit-button", "&aAdd Kit")),
                    "/dialogkitsadmin edit",
                    color("&7Create kits with &f/kit create <name> &7or select a kit to assign its dialog position.")));
        }

        if (buttons.isEmpty()) {
            showNotice(player, getConfig().getString("ui.title", "&8Player Kits"),
                    getConfig().getString("ui.no-kits-for-player", "&7No kits are available yet."));
            return;
        }

        String description = assigned.isEmpty()
                ? getConfig().getString("ui.empty-description", "&7No kits have been assigned yet.")
                : getConfig().getString("ui.description", "&7Choose an action for each kit. Ready is green, Claimed is yellow, Locked is red.");
        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(color(getConfig().getString("ui.title", "&8Player Kits")))
                        .canCloseWithEscape(true)
                        .pause(false)
                        .body(List.of(DialogBody.plainMessage(color(description), 340)))
                        .build())
                .type(DialogType.multiAction(buttons, null, 2)));
        showDialogOrFallback(player, dialog);
    }

    private void openPreview(Player player, String kitName) {
        if (!getConfig().getBoolean("preview.enabled", true)) {
            tell(player, "preview-disabled", "&cKit previews are disabled.");
            return;
        }
        KitDefinition kit = findKit(resolveKitName(kitName));
        if (kit == null) {
            tell(player, "kit-not-found", "&cKit &f%kit% &cdoes not exist.", "%kit%", kitName);
            openFor(player);
            return;
        }
        if (isBedrockPlayer(player) && getConfig().getBoolean("bedrock.enabled", true)) {
            openBedrockPreview(player, kit);
            return;
        }
        if (isKitLocked(player, kit) && getConfig().getBoolean("preview.requires-permission", false)) {
            tell(player, "kit-locked", "&cYou do not have permission to preview this kit.");
            return;
        }

        List<DialogBody> body = new ArrayList<>();
        ItemStack icon = getKitIcon(kit);
        if (icon != null) body.add(DialogBody.item(icon, null, true, true, 32, 32));

        List<ItemStack> kitItems = getKitItems(kit);
        List<String> lines = new ArrayList<>();
        int limit = Math.max(1, getConfig().getInt("ui.preview-list-limit", 14));
        for (ItemStack item : kitItems) {
            if (item == null || item.getType().isAir() || lines.size() >= limit) continue;
            lines.add("&f" + item.getAmount() + " &7× &f" + itemLabel(item));
        }
        StringBuilder itemText = new StringBuilder("&7Items included: &f").append(kitItems.size());
        if (lines.isEmpty()) itemText.append("\n&8No item preview data was available.");
        else {
            for (String line : lines) itemText.append("\n").append(line);
            if (kitItems.size() > lines.size()) itemText.append("\n&8...and ").append(kitItems.size() - lines.size()).append(" more item entries.");
        }
        body.add(DialogBody.plainMessage(color(itemText.toString()), 340));

        boolean locked = isKitLocked(player, kit);
        boolean claimed = hasClaimedKit(player, kit);
        String statusKey = locked ? "ui.locked-button" : claimed ? "ui.claimed-button" : "ui.ready-button";
        String fallback = locked ? "&c%kit% &8| &cLocked"
                : claimed ? "&e%kit% &8| &eClaimed" : "&a%kit% &8| &aReady";
        ActionButton claimButton = button(format(getConfig().getString(statusKey, fallback), getKitDisplayName(kit), kit.name),
                locked ? "/dialogkits locked " + kit.name : "/dialogkits claim " + kit.name,
                color(locked ? "&cYou do not have permission to claim this kit."
                        : claimed ? "&eThis kit has already been claimed or is on cooldown." : "&7Claim this kit."));
        ActionButton backButton = button(color(getConfig().getString("ui.preview-back-button", "&7Back to Kits")),
                "/dialogkits open", color("&7Return to the kit list."));
        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(format(getConfig().getString("ui.preview-title", "&8Preview &7» &f%kit%"),
                                getKitDisplayName(kit), kit.name))
                        .canCloseWithEscape(true).pause(false).body(body).build())
                .type(DialogType.multiAction(List.of(claimButton, backButton), null, 2)));
        showDialogOrFallback(player, dialog);
    }

    private void openBedrockMain(Player player) {
        MenuHolder holder = new MenuHolder(MenuType.MAIN, null);
        Inventory inventory = Bukkit.createInventory(holder, 54, colored(getConfig().getString("bedrock.title", "&8Player Kits")));
        holder.inventory = inventory;
        fillInventory(inventory);
        for (KitDefinition kit : getManuallyAssignedKits()) {
            int slot = getAssignedSlot(kit.name) - 1;
            if (slot >= 0 && slot < 50) inventory.setItem(slot, bedrockKitItem(player, kit));
        }
        if (isOwnerRank(player)) {
            ItemStack admin = namedItem(Material.WRITABLE_BOOK, "&aDialogKits Admin",
                    List.of(colored("&7Create: &f/kit create <name>"), colored("&7Edit positions: &f/dialogkitsadmin edit")));
            inventory.setItem(53, admin);
        }
        player.openInventory(inventory);
    }

    private ItemStack bedrockKitItem(Player player, KitDefinition kit) {
        ItemStack item = getKitIcon(kit);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String status = isKitLocked(player, kit) ? "&cLocked" : hasClaimedKit(player, kit) ? "&eClaimed" : "&aReady";
            meta.setDisplayName(colored(getKitDisplayName(kit)));
            List<String> lore = new ArrayList<>(meta.hasLore() && meta.getLore() != null ? meta.getLore() : List.of());
            lore.add(colored(status));
            lore.add(colored("&7Left click: claim"));
            lore.add(colored("&7Right click: preview"));
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private void openBedrockPreview(Player player, KitDefinition kit) {
        MenuHolder holder = new MenuHolder(MenuType.PREVIEW, kit.name);
        Inventory inventory = Bukkit.createInventory(holder, 54,
                colored(formatString(getConfig().getString("bedrock.preview-title", "&8Preview: %kit%"), kit.name)));
        holder.inventory = inventory;
        fillInventory(inventory);
        List<ItemStack> items = getKitItems(kit);
        for (int i = 0; i < Math.min(45, items.size()); i++) inventory.setItem(i, items.get(i).clone());
        inventory.setItem(45, namedItem(Material.ARROW, "&7Back to Kits", List.of()));
        inventory.setItem(49, namedItem(Material.LIME_DYE, "&aClaim Kit",
                List.of(colored("&7Click to claim " + getKitDisplayName(kit) + "."))));
        player.openInventory(inventory);
    }

    private void fillInventory(Inventory inventory) {
        Material fillerMaterial = Material.matchMaterial(getConfig().getString("bedrock.filler", "BLACK_STAINED_GLASS_PANE"));
        if (fillerMaterial == null) fillerMaterial = Material.BLACK_STAINED_GLASS_PANE;
        ItemStack filler = namedItem(fillerMaterial, " ", List.of());
        for (int i = 0; i < inventory.getSize(); i++) inventory.setItem(i, filler);
    }

    private ItemStack namedItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(colored(name));
            if (lore != null && !lore.isEmpty()) meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof MenuHolder holder)) return;
        if (holder.type == MenuType.EDITOR) return;

        event.setCancelled(true);
        if (event.getRawSlot() < 0 || event.getRawSlot() >= top.getSize()) return;

        if (holder.type == MenuType.MAIN) {
            int slot = event.getRawSlot();
            if (slot == 53 && isOwnerRank(player)) {
                player.closeInventory();
                tell(player, "admin-help", "&7Create kits with &f/kit create <name>&7. Assign positions with &f/dialogkitsadmin edit&7.");
                return;
            }
            KitDefinition clicked = findKitForMenuSlot(slot + 1);
            if (clicked == null) return;
            if (event.isRightClick()) openBedrockPreview(player, clicked);
            else if (event.isLeftClick()) {
                player.closeInventory();
                claimKit(player, clicked.name, false, false);
            }
            return;
        }

        if (holder.type == MenuType.PREVIEW) {
            if (event.getRawSlot() == 45) openBedrockMain(player);
            else if (event.getRawSlot() == 49) {
                KitDefinition kit = findKit(holder.kitName);
                if (kit != null) {
                    player.closeInventory();
                    claimKit(player, kit.name, false, false);
                }
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Inventory inventory = event.getInventory();
        if (!(inventory.getHolder() instanceof MenuHolder holder) || holder.type != MenuType.EDITOR) return;
        KitDefinition kit = findKit(holder.kitName);
        if (kit == null) return;

        List<ItemStack> contents = new ArrayList<>();
        for (ItemStack item : inventory.getContents()) {
            if (item != null && !item.getType().isAir()) contents.add(item.clone());
        }
        saveKitItems(kit, contents);
        event.getPlayer().sendMessage(colored("&aSaved the edited contents for kit &f" + kit.name + "&a."));
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (event.getPlayer().hasPlayedBefore()) return;
        String firstKit = getConfig().getString("first-join-kit", "").trim();
        if (firstKit.isEmpty()) return;
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!event.getPlayer().isOnline()) return;
            KitDefinition kit = findKit(resolveKitName(firstKit));
            if (kit != null) claimKit(event.getPlayer(), kit.name, true, true);
            else getLogger().warning("Configured first-join kit '" + firstKit + "' does not exist.");
        }, 20L);
    }

    private void createKitFromInventory(Player player, String rawName) {
        String name = sanitizeKitName(rawName);
        if (name == null) {
            player.sendMessage(colored("&cKit names may contain only letters, numbers, dots, underscores, plus signs, and hyphens."));
            return;
        }
        if (findKit(name) != null) {
            player.sendMessage(colored("&cA kit with that name already exists."));
            return;
        }

        List<ItemStack> contents = new ArrayList<>();
        PlayerInventory inventory = player.getInventory();
        for (ItemStack item : inventory.getStorageContents()) {
            if (item != null && !item.getType().isAir()) contents.add(item.clone());
        }
        for (ItemStack item : inventory.getArmorContents()) {
            if (item != null && !item.getType().isAir()) contents.add(item.clone());
        }
        ItemStack offHand = inventory.getItemInOffHand();
        if (offHand != null && !offHand.getType().isAir()) contents.add(offHand.clone());
        if (contents.isEmpty()) {
            tell(player, "inventory-empty", "&cYou need at least one item in your inventory to create a kit.");
            return;
        }

        File file = new File(kitsFolder, name + ".yml");
        YamlConfiguration config = new YamlConfiguration();
        config.set("cooldown", getConfig().getInt("kits.new-kit-defaults.cooldown", 3600));
        config.set("one_time", false);
        config.set("auto_armor", true);
        config.set("permission_required", false);
        config.set("clear_inventory", false);
        config.set("save_original_items", true);
        config.set("allow_placeholders_on_original_items", false);
        config.set("display.default.id", inventory.getItemInMainHand().getType().name());
        config.set("display.default.name", "&f&l" + name.toUpperCase(Locale.ROOT));
        config.set("display.default.amount", 1);
        for (int i = 0; i < contents.size(); i++) config.set("items." + (i + 1) + ".original", contents.get(i));
        saveYaml(config, file);
        loadKits();
        KitDefinition created = findKit(name);
        if (created != null) {
            int freeSlot = findFirstFreeManualSlot();
            if (freeSlot > 0) {
                orderData.set("slots." + encodeKitId(name), freeSlot);
                saveAdminOrderData();
            }
        }
        tell(player, "kit-created", "&aKit &f%kit% &acreated from your inventory.", "%kit%", name);
        if (created != null && getAssignedSlot(name) > 0) {
            tell(player, "kit-added-to-menu", "&aKit &f%kit% &ahas been placed in menu slot &f%slot%&a.",
                    "%kit%", name, "%slot%", String.valueOf(getAssignedSlot(name)));
        }
    }

    private void replaceKitFromInventory(Player player, String rawKitName) {
        KitDefinition kit = findKit(resolveKitName(rawKitName));
        if (kit == null) {
            tell(player, "kit-not-found", "&cKit &f%kit% &cdoes not exist.", "%kit%", rawKitName);
            return;
        }

        // Capture storage slots, equipped armor, and off-hand separately so all
        // currently held kit items are saved with their full ItemStack metadata.
        List<ItemStack> contents = new ArrayList<>();
        PlayerInventory inventory = player.getInventory();
        for (ItemStack item : inventory.getStorageContents()) {
            if (item != null && !item.getType().isAir()) contents.add(item.clone());
        }
        for (ItemStack item : inventory.getArmorContents()) {
            if (item != null && !item.getType().isAir()) contents.add(item.clone());
        }
        ItemStack offHand = inventory.getItemInOffHand();
        if (offHand != null && !offHand.getType().isAir()) contents.add(offHand.clone());

        if (contents.isEmpty()) {
            tell(player, "inventory-empty", "&cYour inventory, armor, and off-hand are empty; the kit was not changed.");
            return;
        }

        // saveKitItems replaces only the item list. Cooldowns, permission rules,
        // display icon/name, prices, requirements, and actions remain unchanged.
        saveKitItems(kit, contents);
        tell(player, "kit-replaced",
                "&aKit &f%kit% &aitems replaced with your current inventory, armor, and off-hand.",
                "%kit%", kit.name);
    }

    private void openKitEditor(Player player, String kitName) {
        KitDefinition kit = findKit(resolveKitName(kitName));
        if (kit == null) {
            tell(player, "kit-not-found", "&cKit &f%kit% &cdoes not exist.", "%kit%", kitName);
            return;
        }
        MenuHolder holder = new MenuHolder(MenuType.EDITOR, kit.name);
        Inventory inventory = Bukkit.createInventory(holder, 54,
                colored(formatString(getConfig().getString("kits.editor-title", "&8Edit Kit: %kit%"), kit.name)));
        holder.inventory = inventory;
        List<ItemStack> items = getKitItems(kit);
        for (int i = 0; i < Math.min(inventory.getSize(), items.size()); i++) inventory.setItem(i, items.get(i).clone());
        if (items.size() > inventory.getSize()) {
            player.sendMessage(colored("&eThis editor displays the first 54 item entries. Extra item entries will be removed if you save this edit."));
        }
        player.openInventory(inventory);
    }

    private void saveKitItems(KitDefinition kit, List<ItemStack> items) {
        kit.config.set("items", null);
        kit.config.set("save_original_items", true);
        for (int i = 0; i < items.size(); i++) kit.config.set("items." + (i + 1) + ".original", items.get(i).clone());
        saveYaml(kit.config, kit.file);
        loadKits();
    }

    private void deleteKit(Player player, String kitName) {
        KitDefinition kit = findKit(kitName);
        if (kit == null) {
            tell(player, "kit-not-found", "&cKit &f%kit% &cdoes not exist.", "%kit%", kitName);
            return;
        }
        if (!kit.file.delete()) {
            player.sendMessage(colored("&cCould not delete " + kit.file.getName() + ". Check file permissions."));
            return;
        }
        orderData.set("slots." + encodeKitId(kit.name), null);
        saveAdminOrderData();
        ConfigurationSection players = playerData.getConfigurationSection("players");
        if (players != null) {
            for (String uuidText : players.getKeys(false)) {
                try {
                    playerData.set(playerKitPath(UUID.fromString(uuidText), kit.name), null);
                } catch (IllegalArgumentException ignored) {
                    // Ignore unrelated malformed player-data sections.
                }
            }
        }
        savePlayerData();
        loadKits();
        tell(player, "kit-deleted", "&aKit &f%kit% &adeleted.", "%kit%", kit.name);
    }

    private void claimKit(Player player, String kitName, boolean firstJoin, boolean ignorePrice) {
        KitDefinition kit = findKit(resolveKitName(kitName));
        if (kit == null) {
            tell(player, "kit-not-found", "&cKit &f%kit% &cdoes not exist.", "%kit%", kitName);
            if (!firstJoin) Bukkit.getScheduler().runTaskLater(this, () -> {
                if (player.isOnline()) openFor(player);
            }, 1L);
            return;
        }

        if (!firstJoin && isKitLocked(player, kit)) {
            tell(player, "kit-locked", "&cYou do not have permission to claim this kit.");
            runActions(player, kit, "error");
            refreshPlayerMenu(player, false);
            return;
        }

        String dataPath = playerKitPath(player.getUniqueId(), kit.name);
        long now = System.currentTimeMillis();
        boolean oneTimeClaimed = playerData.getBoolean(dataPath + ".one-time-claimed", false);
        long cooldownUntil = playerData.getLong(dataPath + ".cooldown-until", 0L);
        if (!firstJoin && kit.oneTime && oneTimeClaimed) {
            tell(player, "one-time-error", "&cYou cannot claim this kit again.", "%kit%", kit.name);
            runActions(player, kit, "error");
            refreshPlayerMenu(player, false);
            return;
        }
        if (!firstJoin && kit.cooldownSeconds > 0 && cooldownUntil > now) {
            tell(player, "cooldown-error", "&cYou need to wait %time% before claiming this kit again.",
                    "%time%", formatDuration(cooldownUntil - now), "%kit%", kit.name);
            runActions(player, kit, "error");
            refreshPlayerMenu(player, false);
            return;
        }

        if (!checkExtraRequirements(player, kit)) {
            sendRequirementMessages(player, kit);
            runActions(player, kit, "error");
            refreshPlayerMenu(player, false);
            return;
        }

        double price = getKitPrice(kit);
        boolean alreadyBought = playerData.getBoolean(dataPath + ".bought", false);
        boolean chargeThisClaim = !ignorePrice && price > 0
                && (!kit.config.getBoolean("requirements.one_time_requirements", false) || !alreadyBought);
        Object economy = null;
        if (chargeThisClaim) {
            economy = getEconomyProvider();
            if (economy == null) {
                tell(player, "economy-unavailable", "&cThis kit requires money but no Vault economy provider is available.");
                sendRequirementMessages(player, kit);
                runActions(player, kit, "error");
                refreshPlayerMenu(player, false);
                return;
            }
            double balance = getBalance(economy, player);
            if (balance + 0.000001d < price) {
                tell(player, "requirements-error", "&cYou do not have enough money for this kit. Price: &f%price%&c, balance: &f%balance%&c.",
                        "%price%", formatMoney(price), "%balance%", formatMoney(balance), "%kit%", kit.name);
                sendRequirementMessages(player, kit);
                runActions(player, kit, "error");
                refreshPlayerMenu(player, false);
                return;
            }
        }

        if (!getConfig().getBoolean("claim.drop-items-if-full-inventory", false) && !canFitKit(player, kit)) {
            tell(player, "no-space-error", "&cYou do not have enough inventory space for this kit.");
            runActions(player, kit, "error");
            refreshPlayerMenu(player, false);
            return;
        }
        if (chargeThisClaim && !withdraw(economy, player, price)) {
            tell(player, "requirements-error", "&cThe economy provider could not withdraw the kit price.");
            return;
        }
        if (!giveKitItems(player, kit)) {
            if (chargeThisClaim) deposit(economy, player, price);
            return;
        }

        if (chargeThisClaim && kit.config.getBoolean("requirements.one_time_requirements", false)) {
            playerData.set(dataPath + ".bought", true);
        }
        playerData.set(dataPath + ".claimed", true);
        playerData.set(dataPath + ".last-claim-at", now);
        playerData.set(dataPath + ".cooldown-until", kit.cooldownSeconds > 0 ? now + kit.cooldownSeconds * 1000L : 0L);
        if (kit.oneTime) playerData.set(dataPath + ".one-time-claimed", true);
        savePlayerData();
        tell(player, "kit-received", "&aKit &f%kit% &areceived.", "%kit%", kit.name);
        runActions(player, kit, "claim");
        if (getConfig().getBoolean("claim.close-menu-after-claim", false)) player.closeInventory();
        refreshPlayerMenu(player, firstJoin);
    }

    private boolean giveKitItems(Player player, KitDefinition kit) {
        List<ItemStack> items = getKitItemsForPlayer(player, kit);
        if (items.isEmpty()) {
            tell(player, "kit-empty", "&cThis kit has no readable items.");
            return false;
        }
        if (kit.config.getBoolean("clear_inventory", false)) {
            player.getInventory().clear();
            player.getInventory().setArmorContents(new ItemStack[4]);
            player.getInventory().setItemInOffHand(null);
        }

        boolean autoArmor = kit.config.getBoolean("auto_armor", true);
        for (ItemStack original : items) {
            if (original == null || original.getType().isAir()) continue;
            ItemStack item = original.clone();
            int armorSlot = autoArmor ? armorSlot(item.getType()) : -1;
            if (armorSlot >= 0) {
                ItemStack[] armor = player.getInventory().getArmorContents();
                if (armor[armorSlot] == null || armor[armorSlot].getType().isAir()) {
                    armor[armorSlot] = item;
                    player.getInventory().setArmorContents(armor);
                    continue;
                }
            }
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
            if (!leftover.isEmpty()) {
                if (getConfig().getBoolean("claim.drop-items-if-full-inventory", false)) {
                    for (ItemStack extra : leftover.values()) player.getWorld().dropItemNaturally(player.getLocation(), extra);
                } else {
                    tell(player, "no-space-error", "&cYou do not have enough inventory space for this kit.");
                    return false;
                }
            }
        }
        return true;
    }

    private boolean canFitKit(Player player, KitDefinition kit) {
        Inventory simulated = Bukkit.createInventory(null, 36);
        boolean clear = kit.config.getBoolean("clear_inventory", false);
        if (!clear) {
            ItemStack[] storage = player.getInventory().getStorageContents();
            for (int i = 0; i < Math.min(storage.length, simulated.getSize()); i++) {
                if (storage[i] != null) simulated.setItem(i, storage[i].clone());
            }
        }
        ItemStack[] armor = clear ? new ItemStack[4] : player.getInventory().getArmorContents().clone();
        for (ItemStack original : getKitItems(kit)) {
            if (original == null || original.getType().isAir()) continue;
            ItemStack item = original.clone();
            int slot = kit.config.getBoolean("auto_armor", true) ? armorSlot(item.getType()) : -1;
            if (slot >= 0 && (armor[slot] == null || armor[slot].getType().isAir())) {
                armor[slot] = item;
                continue;
            }
            if (!simulated.addItem(item).isEmpty()) return false;
        }
        return true;
    }

    private int armorSlot(Material material) {
        String name = material.name();
        if (name.endsWith("_BOOTS")) return 0;
        if (name.endsWith("_LEGGINGS")) return 1;
        if (name.endsWith("_CHESTPLATE") || name.equals("ELYTRA")) return 2;
        if (name.endsWith("_HELMET") || name.equals("TURTLE_HELMET") || name.equals("CARVED_PUMPKIN")
                || name.equals("PLAYER_HEAD") || name.equals("SKELETON_SKULL") || name.equals("WITHER_SKELETON_SKULL")
                || name.equals("ZOMBIE_HEAD") || name.equals("CREEPER_HEAD") || name.equals("DRAGON_HEAD")
                || name.equals("PIGLIN_HEAD")) return 3;
        return -1;
    }

    private List<ItemStack> getKitItemsForPlayer(Player player, KitDefinition kit) {
        List<ItemStack> result = new ArrayList<>();
        for (ItemStack item : getKitItems(kit)) {
            ItemStack clone = item.clone();
            if (kit.config.getBoolean("allow_placeholders_on_original_items", false)) {
                ItemMeta meta = clone.getItemMeta();
                if (meta != null) {
                    if (meta.hasDisplayName()) meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', applyPlaceholders(player, meta.getDisplayName())));
                    if (meta.hasLore() && meta.getLore() != null) {
                        List<String> lore = new ArrayList<>();
                        for (String line : meta.getLore()) lore.add(ChatColor.translateAlternateColorCodes('&', applyPlaceholders(player, line)));
                        meta.setLore(lore);
                    }
                    clone.setItemMeta(meta);
                }
            }
            result.add(clone);
        }
        return result;
    }

    private void refreshPlayerMenu(Player player, boolean silentMenu) {
        if (silentMenu) return;
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (player.isOnline()) openFor(player);
        }, 1L);
    }

    private boolean hasClaimedKit(Player player, KitDefinition kit) {
        String path = playerKitPath(player.getUniqueId(), kit.name);
        if (kit.oneTime && playerData.getBoolean(path + ".one-time-claimed", false)) return true;
        if (kit.cooldownSeconds > 0) return playerData.getLong(path + ".cooldown-until", 0L) > System.currentTimeMillis();
        return playerData.getBoolean(path + ".claimed", false);
    }

    private boolean isKitLocked(Player player, KitDefinition kit) {
        if (!kit.permissionRequired) return false;
        return !player.hasPermission("playerkits.kit." + kit.name)
                && !player.hasPermission("dialogkits.kit." + kit.name)
                && !player.hasPermission("dialogkits.kit.*");
    }

    private void runActions(Player player, KitDefinition kit, String group) {
        ConfigurationSection actions = kit.config.getConfigurationSection("actions." + group);
        if (actions == null) return;
        List<String> keys = new ArrayList<>(actions.getKeys(false));
        keys.sort(Comparator.comparingInt(this::numericSortKey));
        for (String key : keys) {
            String action = actions.getString(key + ".action", "").trim();
            if (!action.isEmpty()) executeAction(player, kit, action);
        }
    }

    private void executeAction(Player player, KitDefinition kit, String raw) {
        String action = raw.trim();
        String lower = action.toLowerCase(Locale.ROOT);
        if (lower.startsWith("playsound:") || lower.startsWith("sound:")) {
            String value = action.substring(action.indexOf(':') + 1).trim();
            String[] parts = value.split(";");
            try {
                Sound sound = Sound.valueOf(parts[0].trim().toUpperCase(Locale.ROOT));
                float volume = parts.length > 1 ? Float.parseFloat(parts[1].trim()) : 1.0f;
                float pitch = parts.length > 2 ? Float.parseFloat(parts[2].trim()) : 1.0f;
                player.playSound(player.getLocation(), sound, volume, pitch);
            } catch (IllegalArgumentException ignored) {
                getLogger().fine("Ignoring unknown kit sound action: " + raw);
            }
            return;
        }
        if (lower.startsWith("message:")) {
            player.sendMessage(colored(expandActionText(player, kit, action.substring(action.indexOf(':') + 1).trim())));
            return;
        }
        if (lower.startsWith("playercommand:") || lower.startsWith("command:")) {
            player.performCommand(stripLeadingSlash(expandActionText(player, kit, action.substring(action.indexOf(':') + 1).trim())));
            return;
        }
        if (lower.startsWith("consolecommand:") || lower.startsWith("console:")) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), stripLeadingSlash(expandActionText(player, kit, action.substring(action.indexOf(':') + 1).trim())));
            return;
        }
        getLogger().fine("Ignoring unsupported kit action: " + raw);
    }

    private String expandActionText(Player player, KitDefinition kit, String text) {
        return applyPlaceholders(player, text.replace("%player%", player.getName()).replace("%kit%", kit.name));
    }

    private boolean checkExtraRequirements(Player player, KitDefinition kit) {
        Object raw = kit.config.get("requirements.extra_requirements");
        if (raw == null) return true;
        List<String> requirements = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object entry : list) {
                if (entry instanceof String string) requirements.add(string);
                else if (entry instanceof Map<?, ?> map) {
                    Object type = map.get("type");
                    Object value = map.get("value");
                    if (type != null && value != null) requirements.add(type + ":" + value);
                }
            }
        } else if (raw instanceof ConfigurationSection section) {
            for (String key : section.getKeys(false)) {
                String value = section.getString(key);
                if (value != null) requirements.add(value);
            }
        }
        for (String requirement : requirements) if (!evaluateRequirement(player, requirement)) return false;
        return true;
    }

    private boolean evaluateRequirement(Player player, String raw) {
        String req = raw.trim();
        String lower = req.toLowerCase(Locale.ROOT);
        if (lower.startsWith("permission:")) return player.hasPermission(req.substring(req.indexOf(':') + 1).trim());
        if (lower.startsWith("balance:")) {
            try {
                double needed = Double.parseDouble(req.substring(req.indexOf(':') + 1).trim());
                Object economy = getEconomyProvider();
                return economy != null && getBalance(economy, player) >= needed;
            } catch (NumberFormatException ignored) {
                return false;
            }
        }
        if (lower.startsWith("placeholder:")) {
            String expression = req.substring(req.indexOf(':') + 1).trim();
            String[] operators = {">=", "<=", "==", "!=", ">", "<"};
            for (String operator : operators) {
                int at = expression.indexOf(operator);
                if (at <= 0) continue;
                String left = applyPlaceholders(player, expression.substring(0, at).trim());
                String right = expression.substring(at + operator.length()).trim();
                try {
                    double a = Double.parseDouble(left);
                    double b = Double.parseDouble(right);
                    return switch (operator) {
                        case ">=" -> a >= b;
                        case "<=" -> a <= b;
                        case "==" -> Double.compare(a, b) == 0;
                        case "!=" -> Double.compare(a, b) != 0;
                        case ">" -> a > b;
                        case "<" -> a < b;
                        default -> false;
                    };
                } catch (NumberFormatException ignored) {
                    boolean equal = left.equalsIgnoreCase(right);
                    return operator.equals("==") ? equal : operator.equals("!=") && !equal;
                }
            }
            return !applyPlaceholders(player, expression).contains("%");
        }
        return false;
    }

    private void sendRequirementMessages(Player player, KitDefinition kit) {
        List<String> lines = kit.config.getStringList("requirements.message");
        if (lines.isEmpty()) {
            tell(player, "requirements-error", "&cYou do not meet the requirements for this kit.", "%kit%", kit.name);
            return;
        }
        for (String line : lines) player.sendMessage(colored(applyPlaceholders(player, line.replace("%kit%", kit.name))));
    }

    private double getKitPrice(KitDefinition kit) {
        return Math.max(0.0d, kit.config.getDouble("requirements.price", 0.0d));
    }

    private Object getEconomyProvider() {
        Plugin vault = Bukkit.getPluginManager().getPlugin("Vault");
        if (vault == null || !vault.isEnabled()) return null;
        try {
            Class<?> economyClass = Class.forName("net.milkbowl.vault.economy.Economy", true, vault.getClass().getClassLoader());
            Object registration = Bukkit.getServicesManager().getClass()
                    .getMethod("getRegistration", Class.class)
                    .invoke(Bukkit.getServicesManager(), economyClass);
            return invoke(registration, "getProvider");
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return null;
        }
    }

    private double getBalance(Object economy, Player player) {
        Object value = invokeCompatibleResult(economy, "getBalance", player);
        return value instanceof Number number ? number.doubleValue() : 0.0d;
    }

    private boolean withdraw(Object economy, Player player, double amount) {
        Object response = invokeCompatibleResult(economy, "withdrawPlayer", player, amount);
        if (response == null) return false;
        Object result = invoke(response, "transactionSuccess");
        return result instanceof Boolean value && value;
    }

    private void deposit(Object economy, Player player, double amount) {
        if (economy != null) invokeCompatibleResult(economy, "depositPlayer", player, amount);
    }

    private String formatMoney(double value) {
        return String.format(Locale.US, "%,.2f", value).replaceAll("\\.00$", "");
    }

    private String applyPlaceholders(Player player, String text) {
        Plugin papi = Bukkit.getPluginManager().getPlugin("PlaceholderAPI");
        if (papi == null || !papi.isEnabled()) return text;
        try {
            Class<?> api = Class.forName("me.clip.placeholderapi.PlaceholderAPI", true, papi.getClass().getClassLoader());
            Method method = api.getMethod("setPlaceholders", Player.class, String.class);
            Object result = method.invoke(null, player, text);
            return result instanceof String s ? s : text;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return text;
        }
    }

    private List<ItemStack> getKitItems(KitDefinition kit) {
        List<ItemStack> result = new ArrayList<>();
        ConfigurationSection items = kit.config.getConfigurationSection("items");
        if (items == null) return result;
        List<String> keys = new ArrayList<>(items.getKeys(false));
        keys.sort(Comparator.comparingInt(this::numericSortKey));
        boolean originalMode = kit.config.getBoolean("save_original_items", true);
        for (String key : keys) {
            ConfigurationSection entry = items.getConfigurationSection(key);
            if (entry == null) continue;
            ItemStack item = originalMode ? readSerializedItem(entry.get("original")) : null;
            if (item == null) {
                ConfigurationSection configurable = entry.getConfigurationSection("configurable");
                if (configurable == null && (entry.contains("id") || entry.contains("material"))) configurable = entry;
                item = readConfigurableItem(configurable);
            }
            if (item == null) item = readSerializedItem(entry.get("original"));
            if (item != null && !item.getType().isAir()) result.add(item);
        }
        return result;
    }

    private ItemStack readSerializedItem(Object raw) {
        if (raw instanceof ItemStack item) return item.clone();
        if (raw instanceof Map<?, ?> map) {
            try {
                Map<String, Object> values = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) values.put(String.valueOf(entry.getKey()), entry.getValue());
                Object parsed = ConfigurationSerialization.deserializeObject(values);
                if (parsed instanceof ItemStack item) return item.clone();
                return ItemStack.deserialize(values);
            } catch (IllegalArgumentException | ClassCastException ignored) {
                return null;
            }
        }
        return null;
    }

    private ItemStack readConfigurableItem(ConfigurationSection section) {
        if (section == null) return null;
        String materialName = section.getString("id", section.getString("material", ""));
        Material material = Material.matchMaterial(materialName);
        if (material == null && materialName.toLowerCase(Locale.ROOT).startsWith("minecraft:")) {
            material = Material.matchMaterial(materialName.substring("minecraft:".length()));
        }
        if (material == null || material.isAir()) return null;
        ItemStack item = new ItemStack(material, Math.max(1, Math.min(material.getMaxStackSize(), section.getInt("amount", 1))));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            String name = section.getString("name");
            if (name != null) meta.setDisplayName(colored(name));
            List<String> lore = section.getStringList("lore");
            if (!lore.isEmpty()) {
                List<String> lines = new ArrayList<>();
                for (String line : lore) lines.add(colored(line));
                meta.setLore(lines);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack getKitIcon(KitDefinition kit) {
        ConfigurationSection display = kit.config.getConfigurationSection("display.default");
        Material material = Material.PAPER;
        if (display != null) {
            String id = display.getString("id", "PAPER");
            Material parsed = Material.matchMaterial(id);
            if (parsed == null && id.toLowerCase(Locale.ROOT).startsWith("minecraft:")) {
                parsed = Material.matchMaterial(id.substring("minecraft:".length()));
            }
            if (parsed != null && !parsed.isAir()) material = parsed;
        }
        ItemStack icon = new ItemStack(material, Math.max(1, Math.min(material.getMaxStackSize(), display == null ? 1 : display.getInt("amount", 1))));
        ItemMeta meta = icon.getItemMeta();
        if (meta != null && display != null) {
            String name = display.getString("name", "");
            if (!name.isBlank()) meta.setDisplayName(colored(formatString(name, kit.name)));
            List<String> lore = display.getStringList("lore");
            if (!lore.isEmpty()) {
                List<String> coloredLore = new ArrayList<>();
                for (String line : lore) coloredLore.add(colored(formatString(line, kit.name)));
                meta.setLore(coloredLore);
            }
            icon.setItemMeta(meta);
        }
        return icon;
    }

    private String getKitDisplayName(KitDefinition kit) {
        if (getConfig().getBoolean("ui.use-kit-display-name", true)) {
            ConfigurationSection display = kit.config.getConfigurationSection("display.default");
            if (display != null) {
                String name = display.getString("name", "");
                if (!name.isBlank()) return formatString(name, kit.name);
            }
        }
        return titleCase(kit.name);
    }

    private String formatString(String template, String kitName) {
        return Objects.requireNonNullElse(template, "%kit_name%")
                .replace("%kit_name%", titleCase(kitName))
                .replace("%kit_name_upper%", kitName.toUpperCase(Locale.ROOT))
                .replace("%kit%", kitName).replace("%kit_id%", kitName);
    }

    private Component format(String template, String displayName, String kitId) {
        return color(Objects.requireNonNullElse(template, "%kit%")
                .replace("%kit%", displayName).replace("%kit_id%", kitId).replace("%kit_name%", displayName));
    }

    private Component color(String text) {
        return LEGACY.deserialize(Objects.requireNonNullElse(text, ""));
    }

    private ActionButton button(Component label, String command, Component tooltip) {
        return ActionButton.builder(label).width(220).tooltip(tooltip)
                .action(DialogAction.staticAction(ClickEvent.runCommand(command))).build();
    }

    private void showNotice(Player player, String title, String message) {
        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(color(title)).canCloseWithEscape(true).pause(false)
                        .body(List.of(DialogBody.plainMessage(color(message), 340))).build())
                .type(DialogType.notice()));
        showDialogOrFallback(player, dialog);
    }

    private void showDialogOrFallback(Player player, Dialog dialog) {
        try {
            player.showDialog(dialog);
        } catch (Throwable error) {
            getLogger().warning("Could not show a native DialogKits dialog to " + player.getName() + ": " + error.getMessage());
            tell(player, "dialog-failed", "&cCould not open the native kit dialog. Check the server console for details.");
        }
    }

    private boolean isOwnerRank(Player player) {
        Plugin luckPerms = Bukkit.getPluginManager().getPlugin("LuckPerms");
        if (luckPerms == null || !luckPerms.isEnabled()) return false;
        try {
            Class<?> provider = Class.forName("net.luckperms.api.LuckPermsProvider", true, luckPerms.getClass().getClassLoader());
            Object api = provider.getMethod("get").invoke(null);
            Object users = invoke(api, "getUserManager");
            Object user = invokeCompatibleResult(users, "getUser", player.getUniqueId());
            String primary = stringValue(invoke(user, "getPrimaryGroup"));
            String expected = getConfig().getString("admin.owner-group", "owner");
            return primary != null && expected != null && primary.equalsIgnoreCase(expected.trim());
        } catch (ReflectiveOperationException | LinkageError exception) {
            getLogger().fine("Could not check LuckPerms primary group: " + exception.getMessage());
            return false;
        }
    }

    private boolean requireOwner(Player player) {
        if (isOwnerRank(player)) return true;
        tell(player, "no-permission", "&cOnly the configured LuckPerms owner group can manage kits.");
        return false;
    }

    private boolean handleAdminCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command opens a native dialog and must be used in-game.");
            return true;
        }
        if (!requireOwner(player)) return true;
        if (args.length == 0 || args[0].equalsIgnoreCase("edit")) {
            openAdminKitSelection(player);
            return true;
        }
        if (args[0].equalsIgnoreCase("choose") && args.length >= 2) {
            String kitName = decodeKitId(args[1]);
            if (kitName == null || findKit(kitName) == null) {
                tell(player, "kit-not-found", "&cKit &f%kit% &cdoes not exist.", "%kit%", String.valueOf(kitName));
                openAdminKitSelection(player);
                return true;
            }
            openAdminSlotSelection(player, kitName);
            return true;
        }
        if (args[0].equalsIgnoreCase("assign") && args.length >= 3) {
            String kitName = decodeKitId(args[1]);
            int slot;
            try {
                slot = Integer.parseInt(args[2]);
            } catch (NumberFormatException exception) {
                player.sendMessage(colored("&cThat slot number is invalid."));
                return true;
            }
            assignKitToSlot(player, kitName, slot);
            return true;
        }
        if (args[0].equalsIgnoreCase("help")) {
            tell(player, "admin-help", "&7Create kits with &f/kit create <name>&7; edit with &f/kit edit <name>&7.");
            return true;
        }
        player.sendMessage(colored("&7Usage: &f/dialogkitsadmin edit"));
        return true;
    }

    private void openAdminKitSelection(Player player) {
        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(button(color("&aCreate Kit from Inventory"), "/dialogkitsadmin help",
                color("&7Use /kit create <name> while holding the items to save.")));
        for (KitDefinition kit : getKits()) {
            int slot = getAssignedSlot(kit.name);
            String label = "&f" + getKitDisplayName(kit) + (slot > 0 ? " &8(Slot " + slot + ")" : " &8(Unassigned)");
            buttons.add(button(color(label), "/dialogkitsadmin choose " + encodeKitId(kit.name),
                    color("&7Choose a manual slot for " + kit.name + ".")));
        }
        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(color("&8DialogKits Admin")).canCloseWithEscape(true).pause(false)
                        .body(List.of(DialogBody.plainMessage(color("&7Select a kit to assign or change its manual dialog position."), 360))).build())
                .type(DialogType.multiAction(buttons, null, 2)));
        showDialogOrFallback(player, dialog);
    }

    private void openAdminSlotSelection(Player player, String kitName) {
        KitDefinition kit = findKit(kitName);
        if (kit == null) {
            openAdminKitSelection(player);
            return;
        }
        int current = getAssignedSlot(kit.name);
        List<ActionButton> buttons = new ArrayList<>(50);
        for (int slot = 1; slot <= 50; slot++) {
            String occupant = getKitAssignedToSlot(slot);
            String label;
            if (slot == current) label = "&aSlot " + slot + " &8(Current)";
            else if (occupant != null) {
                KitDefinition occupyingKit = findKit(occupant);
                label = "&eSlot " + slot + " &8(" + (occupyingKit == null ? occupant : getKitDisplayName(occupyingKit)) + ")";
            } else label = "&fSlot " + slot;

            buttons.add(ActionButton.builder(color(label)).width(120)
                    .tooltip(color("&7Place " + getKitDisplayName(kit) + " at position " + slot + "."))
                    .action(DialogAction.staticAction(ClickEvent.runCommand("/dialogkitsadmin assign " + encodeKitId(kit.name) + " " + slot)))
                    .build());
        }
        String text = "&7Choose a slot between 1 and 50 for &f" + getKitDisplayName(kit) + "&7.\n&8If a slot is occupied, positions are swapped when possible.";
        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(color("&8Edit Kit Position")).canCloseWithEscape(true).pause(false)
                        .body(List.of(DialogBody.plainMessage(color(text), 380))).build())
                .type(DialogType.multiAction(buttons, null, 3)));
        showDialogOrFallback(player, dialog);
    }

    private void assignKitToSlot(Player player, String kitName, int slot) {
        KitDefinition kit = findKit(kitName);
        if (kit == null) {
            tell(player, "kit-not-found", "&cKit &f%kit% &cdoes not exist.", "%kit%", String.valueOf(kitName));
            openAdminKitSelection(player);
            return;
        }
        if (slot < 1 || slot > 50) {
            player.sendMessage(colored("&cChoose a slot between 1 and 50."));
            openAdminSlotSelection(player, kitName);
            return;
        }
        String selectedKey = encodeKitId(kit.name);
        int oldSlot = getAssignedSlot(kit.name);
        String occupantKey = findOrderKeyForSlot(slot);
        if (occupantKey != null && !occupantKey.equals(selectedKey)) {
            if (oldSlot > 0) orderData.set("slots." + occupantKey, oldSlot);
            else orderData.set("slots." + occupantKey, null);
        }
        orderData.set("slots." + selectedKey, slot);
        saveAdminOrderData();
        player.sendMessage(colored("&aSet &f" + kit.name + " &ato slot &f" + slot + "&a."));
        openAdminSlotSelection(player, kit.name);
    }

    private List<KitDefinition> getManuallyAssignedKits() {
        List<ManualKitOrderEntry> entries = new ArrayList<>();
        int index = 0;
        for (KitDefinition kit : getKits()) {
            int slot = getAssignedSlot(kit.name);
            if (slot >= 1 && slot <= 50) entries.add(new ManualKitOrderEntry(kit, slot, index));
            index++;
        }
        entries.sort(Comparator.comparingInt(ManualKitOrderEntry::slot).thenComparingInt(ManualKitOrderEntry::originalIndex));
        List<KitDefinition> result = new ArrayList<>();
        for (ManualKitOrderEntry entry : entries) result.add(entry.kit());
        return result;
    }

    private int getAssignedSlot(String kitName) {
        return orderData == null || kitName == null ? 0 : orderData.getInt("slots." + encodeKitId(kitName), 0);
    }

    private KitDefinition findKitForMenuSlot(int slot) {
        for (KitDefinition kit : getManuallyAssignedKits()) if (getAssignedSlot(kit.name) == slot) return kit;
        return null;
    }

    private String getKitAssignedToSlot(int slot) {
        String key = findOrderKeyForSlot(slot);
        return key == null ? null : decodeKitId(key);
    }

    private String findOrderKeyForSlot(int slot) {
        if (orderData == null || orderData.getConfigurationSection("slots") == null) return null;
        for (String key : orderData.getConfigurationSection("slots").getKeys(false)) {
            if (orderData.getInt("slots." + key, 0) == slot) return key;
        }
        return null;
    }

    private int findFirstFreeManualSlot() {
        for (int slot = 1; slot <= 50; slot++) if (getKitAssignedToSlot(slot) == null) return slot;
        return 0;
    }

    private KitDefinition findKit(String name) {
        if (name == null) return null;
        KitDefinition direct = kits.get(name);
        if (direct != null) return direct;
        for (KitDefinition kit : kits.values()) if (kit.name.equalsIgnoreCase(name)) return kit;
        return null;
    }

    private String resolveKitName(String name) {
        KitDefinition direct = findKit(name);
        if (direct != null) return direct.name;
        ConfigurationSection aliases = getConfig().getConfigurationSection("migration.kit-name-aliases");
        if (aliases != null) {
            for (String oldName : aliases.getKeys(false)) {
                String target = aliases.getString(oldName, "");
                if (oldName.equalsIgnoreCase(name) && !target.isBlank()) {
                    KitDefinition byAlias = findKit(target);
                    return byAlias == null ? target : byAlias.name;
                }
            }
        }
        String normalized = normalizeKitName(name);
        for (KitDefinition kit : kits.values()) {
            String candidate = normalizeKitName(kit.name);
            if (candidate.equals(normalized) || (normalized.endsWith("kit") && normalized.substring(0, normalized.length() - 3).equals(candidate))) {
                return kit.name;
            }
        }
        return name;
    }

    private String normalizeKitName(String name) {
        return name == null ? "" : name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private String sanitizeKitName(String input) {
        if (input == null || !input.matches("[A-Za-z0-9._+-]{1,48}") || input.equals(".") || input.equals("..")) return null;
        return input;
    }

    private List<KitDefinition> getKits() {
        return new ArrayList<>(kits.values());
    }

    private void loadKits() {
        kits.clear();
        if (kitsFolder == null) kitsFolder = new File(getDataFolder(), "kits");
        File[] files = kitsFolder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null) return;
        List<File> sorted = new ArrayList<>(List.of(files));
        sorted.sort(Comparator.comparing(file -> file.getName().toLowerCase(Locale.ROOT)));
        for (File file : sorted) {
            String name = file.getName().substring(0, file.getName().length() - 4);
            YamlConfiguration config = YamlConfiguration.loadConfiguration(file);
            kits.put(name, new KitDefinition(name, file, config));
        }
    }

    private void loadMigrationData() {
        migrationFile = new File(getDataFolder(), "migration.yml");
        migrationData = YamlConfiguration.loadConfiguration(migrationFile);
    }

    private void migrateLegacyKitFilesAndSettings() {
        if (!getConfig().getBoolean("migration.import-playerkits2-on-first-start", true)) return;
        File legacyFolder = new File(getDataFolder().getParentFile(), "PlayerKits2");
        if (!legacyFolder.isDirectory()) return;

        File oldConfigFile = new File(legacyFolder, "config.yml");
        if (!migrationData.getBoolean("settings-imported", false) && oldConfigFile.isFile()) {
            YamlConfiguration old = YamlConfiguration.loadConfiguration(oldConfigFile);
            getConfig().set("claim.drop-items-if-full-inventory", old.getBoolean("drop_items_if_full_inventory", false));
            getConfig().set("commands.short-claim", old.getBoolean("claim_kit_short_command", false));
            getConfig().set("claim.close-menu-after-claim", old.getBoolean("close_inventory_on_claim", false));
            getConfig().set("preview.enabled", old.getBoolean("kit_preview", true));
            getConfig().set("preview.requires-permission", old.getBoolean("kit_preview_requires_kit_permission", false));
            String firstJoinKit = old.getString("first_join_kit", "");
            if (firstJoinKit != null && !firstJoinKit.isBlank()) getConfig().set("first-join-kit", resolveKitName(firstJoinKit));
            migrationData.set("settings-imported", true);
            saveConfig();
            saveMigrationData();
            getLogger().info("Imported supported settings from PlayerKits2/config.yml. The original file was not modified.");
        }

        File oldKits = new File(legacyFolder, "kits");
        if (oldKits.isDirectory()) {
            File[] files = oldKits.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
            if (files != null) {
                int copied = 0;
                for (File oldFile : files) {
                    File newFile = new File(kitsFolder, oldFile.getName());
                    if (newFile.exists()) continue;
                    try {
                        Files.copy(oldFile.toPath(), newFile.toPath());
                        copied++;
                    } catch (IOException exception) {
                        getLogger().warning("Could not import kit file " + oldFile.getName() + ": " + exception.getMessage());
                    }
                }
                if (copied > 0) getLogger().info("Imported " + copied + " kit file(s) from PlayerKits2/kits.");
            }
        }
    }

    private void migrateLegacyInventoryLayout() {
        if (migrationData.getBoolean("layout-imported", false)) return;
        ConfigurationSection slots = orderData.getConfigurationSection("slots");
        if (slots != null && !slots.getKeys(false).isEmpty()) return;
        File legacyInventoryFile = new File(getDataFolder().getParentFile(), "PlayerKits2/inventory.yml");
        if (!legacyInventoryFile.isFile()) return;
        YamlConfiguration old = YamlConfiguration.loadConfiguration(legacyInventoryFile);
        ConfigurationSection main = old.getConfigurationSection("inventories.main_inventory");
        if (main == null) return;

        List<LegacyMenuEntry> entries = new ArrayList<>();
        for (String key : main.getKeys(false)) {
            ConfigurationSection item = main.getConfigurationSection(key);
            if (item == null) continue;
            String type = item.getString("type", "");
            if (!type.toLowerCase(Locale.ROOT).startsWith("kit:")) continue;
            int guiSlot;
            try {
                guiSlot = Integer.parseInt(key);
            } catch (NumberFormatException ignored) {
                continue;
            }
            String legacyName = type.substring(type.indexOf(':') + 1).trim();
            String currentName = resolveKitName(legacyName);
            if (findKit(currentName) != null) entries.add(new LegacyMenuEntry(currentName, guiSlot));
        }
        entries.sort(Comparator.comparingInt(LegacyMenuEntry::oldSlot));
        int position = 1;
        for (LegacyMenuEntry entry : entries) {
            if (position > 50) break;
            orderData.set("slots." + encodeKitId(entry.kitName()), position++);
        }
        if (!entries.isEmpty()) {
            saveAdminOrderData();
            getLogger().info("Imported " + entries.size() + " kit position(s) from PlayerKits2/inventory.yml.");
        }
        migrationData.set("layout-imported", true);
        saveMigrationData();
    }

    private void migrateLegacyPlayerData() {
        if (migrationData.getBoolean("player-data-imported", false)) return;
        File oldPlayers = new File(getDataFolder().getParentFile(), "PlayerKits2/players");
        if (!oldPlayers.isDirectory()) return;
        File[] files = oldPlayers.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null) return;
        int imported = 0;

        for (File file : files) {
            String filename = file.getName();
            UUID uuid;
            try {
                uuid = UUID.fromString(filename.substring(0, filename.length() - 4));
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            YamlConfiguration old = YamlConfiguration.loadConfiguration(file);
            ConfigurationSection oldKits = old.getConfigurationSection("kits");
            if (oldKits == null) continue;

            for (String oldKitName : oldKits.getKeys(false)) {
                ConfigurationSection oldState = oldKits.getConfigurationSection(oldKitName);
                if (oldState == null) continue;
                KitDefinition kit = findKit(resolveKitName(oldKitName));
                if (kit == null) continue;
                String path = playerKitPath(uuid, kit.name);
                if (playerData.contains(path + ".last-claim-at")) continue;

                long cooldownUntil = oldState.getLong("cooldown", 0L);
                boolean oldOneTime = oldState.getBoolean("one_time", false);
                boolean bought = oldState.getBoolean("bought", false);
                playerData.set(path + ".cooldown-until", Math.max(0L, cooldownUntil));
                playerData.set(path + ".bought", bought);
                if (kit.oneTime && oldOneTime) playerData.set(path + ".one-time-claimed", true);
                playerData.set(path + ".claimed", cooldownUntil > 0L || bought || oldOneTime);
                if (cooldownUntil > 0L) playerData.set(path + ".last-claim-at", Math.max(0L, cooldownUntil - kit.cooldownSeconds * 1000L));
                imported++;
            }
        }

        savePlayerData();
        migrationData.set("player-data-imported", true);
        migrationData.set("imported-player-kit-states", imported);
        saveMigrationData();
        getLogger().info("Imported " + imported + " claim/cooldown state(s) from PlayerKits2/players where kit names could be matched. Original files were not changed.");
    }


    private void migratePreviousDialogKitsClaimData() {
        if (migrationData.getBoolean("dialogkits-claim-history-imported", false)) return;
        File oldFile = new File(getDataFolder(), "claimed.yml");
        if (!oldFile.isFile()) return;

        YamlConfiguration old = YamlConfiguration.loadConfiguration(oldFile);
        int imported = 0;
        for (String uuidText : old.getKeys(false)) {
            UUID uuid;
            try {
                uuid = UUID.fromString(uuidText);
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            ConfigurationSection playerClaims = old.getConfigurationSection(uuidText);
            if (playerClaims == null) continue;
            for (String encodedName : playerClaims.getKeys(false)) {
                if (!playerClaims.getBoolean(encodedName, false)) continue;
                String kitName = decodeKitId(encodedName);
                KitDefinition kit = findKit(kitName);
                if (kit == null) continue;
                String path = playerKitPath(uuid, kit.name);
                if (playerData.contains(path + ".claimed")) continue;
                playerData.set(path + ".claimed", true);
                imported++;
            }
        }

        savePlayerData();
        migrationData.set("dialogkits-claim-history-imported", true);
        migrationData.set("imported-dialogkits-claim-states", imported);
        saveMigrationData();
        getLogger().info("Imported " + imported + " legacy DialogKits claim marker(s) from claimed.yml. The old file was not changed.");
    }

    private void loadAdminOrderData() {
        orderFile = new File(getDataFolder(), "kit-order.yml");
        orderData = YamlConfiguration.loadConfiguration(orderFile);
    }

    private void saveAdminOrderData() {
        if (orderData != null && orderFile != null) saveYaml(orderData, orderFile);
    }

    private void loadPlayerData() {
        playerDataFile = new File(getDataFolder(), "player-data.yml");
        playerData = YamlConfiguration.loadConfiguration(playerDataFile);
    }

    private void savePlayerData() {
        if (playerData != null && playerDataFile != null) saveYaml(playerData, playerDataFile);
    }

    private void loadMessages() {
        messagesFile = new File(getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) saveResource("messages.yml", false);
        messages = YamlConfiguration.loadConfiguration(messagesFile);
    }

    private void saveMigrationData() {
        if (migrationData != null && migrationFile != null) saveYaml(migrationData, migrationFile);
    }

    private void saveYaml(YamlConfiguration yaml, File file) {
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            yaml.save(file);
        } catch (IOException exception) {
            getLogger().warning("Could not save " + file.getName() + ": " + exception.getMessage());
        }
    }

    private String playerKitPath(UUID uuid, String kitName) {
        return "players." + uuid + ".kits." + encodeKitId(kitName);
    }

    private String encodeKitId(String name) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(name.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private String decodeKitId(String encoded) {
        try {
            return new String(Base64.getUrlDecoder().decode(encoded), java.nio.charset.StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private String getMessage(String key, String fallback) {
        return messages == null ? fallback : messages.getString(key, fallback);
    }

    private void tell(Player player, String key, String fallback, String... replacements) {
        String message = getMessage(key, fallback);
        for (int i = 0; i + 1 < replacements.length; i += 2) message = message.replace(replacements[i], replacements[i + 1]);
        player.sendMessage(colored(getMessage("prefix", "&8[&bDialogKits&8] ") + message));
    }

    private String colored(String input) {
        return ChatColor.translateAlternateColorCodes('&', Objects.requireNonNullElse(input, ""));
    }

    private String stringValue(Object value) {
        return value instanceof String string ? string : null;
    }

    private Object invoke(Object target, String name) {
        if (target == null) return null;
        try {
            return target.getClass().getMethod(name).invoke(target);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private Object invokeCompatibleResult(Object target, String name, Object... arguments) {
        if (target == null) return null;
        for (Method method : target.getClass().getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != arguments.length) continue;
            Class<?>[] types = method.getParameterTypes();
            boolean compatible = true;
            for (int i = 0; i < types.length; i++) {
                if (arguments[i] == null) continue;
                if (!wrapPrimitive(types[i]).isAssignableFrom(arguments[i].getClass())) {
                    compatible = false;
                    break;
                }
            }
            if (!compatible) continue;
            try {
                return method.invoke(target, arguments);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return null;
            }
        }
        return null;
    }

    private Class<?> wrapPrimitive(Class<?> type) {
        if (!type.isPrimitive()) return type;
        if (type == boolean.class) return Boolean.class;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == double.class) return Double.class;
        if (type == float.class) return Float.class;
        if (type == short.class) return Short.class;
        if (type == byte.class) return Byte.class;
        if (type == char.class) return Character.class;
        return type;
    }

    private String itemLabel(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            String stripped = ChatColor.stripColor(meta.getDisplayName());
            if (stripped != null && !stripped.isBlank()) return stripped;
        }
        return titleCase(item.getType().name());
    }

    private String titleCase(String input) {
        String normalised = Objects.requireNonNullElse(input, "").replace('_', ' ').replace('-', ' ').trim();
        StringBuilder result = new StringBuilder();
        boolean upper = true;
        for (char character : normalised.toCharArray()) {
            if (Character.isWhitespace(character)) {
                result.append(character);
                upper = true;
            } else {
                result.append(upper ? Character.toUpperCase(character) : Character.toLowerCase(character));
                upper = false;
            }
        }
        return result.toString();
    }

    private int numericSortKey(String value) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return Integer.MAX_VALUE;
        }
    }

    private String formatDuration(long millis) {
        long seconds = Math.max(0, millis / 1000);
        long days = seconds / 86400;
        long hours = (seconds % 86400) / 3600;
        long minutes = (seconds % 3600) / 60;
        long remainder = seconds % 60;
        List<String> parts = new ArrayList<>();
        if (days > 0) parts.add(days + "d");
        if (hours > 0) parts.add(hours + "h");
        if (minutes > 0) parts.add(minutes + "m");
        if (remainder > 0 || parts.isEmpty()) parts.add(remainder + "s");
        return String.join(" ", parts);
    }

    private String stripLeadingSlash(String command) {
        return command.startsWith("/") ? command.substring(1) : command;
    }

    private boolean isBedrockPlayer(Player player) {
        Plugin floodgate = Bukkit.getPluginManager().getPlugin("floodgate");
        if (floodgate == null || !floodgate.isEnabled()) return false;
        try {
            Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi", true, floodgate.getClass().getClassLoader());
            Object api = apiClass.getMethod("getInstance").invoke(null);
            Object result = invokeCompatibleResult(api, "isFloodgatePlayer", player.getUniqueId());
            return result instanceof Boolean value && value;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }

    private enum MenuType { MAIN, PREVIEW, EDITOR }

    private final class MenuHolder implements InventoryHolder {
        private final MenuType type;
        private final String kitName;
        private Inventory inventory;

        private MenuHolder(MenuType type, String kitName) {
            this.type = type;
            this.kitName = kitName;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final class KitDefinition {
        private final String name;
        private final File file;
        private final YamlConfiguration config;
        private final int cooldownSeconds;
        private final boolean oneTime;
        private final boolean permissionRequired;

        private KitDefinition(String name, File file, YamlConfiguration config) {
            this.name = name;
            this.file = file;
            this.config = config;
            this.cooldownSeconds = Math.max(0, config.getInt("cooldown", 0));
            this.oneTime = config.getBoolean("one_time", false);
            this.permissionRequired = config.getBoolean("permission_required", false);
        }
    }

    private record ManualKitOrderEntry(KitDefinition kit, int slot, int originalIndex) {}
    private record LegacyMenuEntry(String kitName, int oldSlot) {}
}
