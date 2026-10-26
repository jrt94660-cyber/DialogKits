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
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.UUID;

public final class DialogKitsPlugin extends JavaPlugin implements Listener, CommandExecutor {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();
    private File claimedDataFile;
    private YamlConfiguration claimedData;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadClaimedData();
        PluginCommand command = getCommand("dialogkits");
        if (command != null) {
            command.setExecutor(this);
        }
        Bukkit.getPluginManager().registerEvents(this, this);

        if (getPlayerKitsPlugin() == null) {
            getLogger().warning("PlayerKits2 was not detected. DialogKits will wait for it, but kit dialogs cannot open until it is installed and enabled.");
        } else {
            getLogger().info("PlayerKits2 detected. Java players will use native kit dialogs.");
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                reloadConfig();
                sender.sendMessage("DialogKits configuration reloaded.");
            } else {
                sender.sendMessage("Usage: /dialogkits reload");
            }
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("open")) {
            if (!player.hasPermission("dialogkits.open")) {
                player.sendMessage(color(getConfig().getString("messages.no-permission", "&cYou do not have permission to open kits.")));
                return true;
            }
            openFor(player);
            return true;
        }

        if (args[0].equalsIgnoreCase("claim")) {
            if (args.length < 2) {
                player.sendMessage(color("&cUsage: /dialogkits claim <kit>"));
                return true;
            }
            claimKit(player, args[1]);
            return true;
        }

        if (args[0].equalsIgnoreCase("locked")) {
            player.sendMessage(color(getConfig().getString(
                    "messages.kit-locked",
                    "&cLocked: you do not have permission to claim that kit."
            )));
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (player.isOnline()) {
                    openNativeKitDialog(player);
                }
            }, 1L);
            return true;
        }

        if (args[0].equalsIgnoreCase("preview")) {
            if (args.length < 2) {
                player.sendMessage(color(getConfig().getString("messages.preview-usage", "&cUsage: /dialogkits preview <kit>")));
                return true;
            }
            openPreview(player, args[1]);
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!player.hasPermission("dialogkits.reload")) {
                player.sendMessage(color(getConfig().getString("messages.no-permission", "&cYou do not have permission to do that.")));
                return true;
            }
            reloadConfig();
            player.sendMessage(color(getConfig().getString("messages.reloaded", "&aDialogKits configuration reloaded.")));
            return true;
        }

        player.sendMessage(color("&7Usage: &f/dialogkits [open|claim <kit>|preview <kit>|reload]"));
        return true;
    }

    /**
     * Replaces only the bare /kit command. Commands such as /kit claim, /kit create,
     * /kit edit and /kit preview are left to PlayerKits2.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        if (!getConfig().getBoolean("intercept-kit-command", true)) {
            return;
        }

        Player player = event.getPlayer();
        if (isBedrockPlayer(player) && getConfig().getBoolean("bedrock-use-playerkits-gui", true)) {
            return;
        }

        String message = event.getMessage().trim();
        if (message.matches("(?i)^/(kit|kits|playerkits)$")) {
            event.setCancelled(true);
            openNativeKitDialog(player);
            return;
        }

        // Route ordinary claims through this plugin as well, so the Ready/Claimed
        // indicator stays accurate even when a player uses PlayerKits2's command.
        java.util.regex.Matcher explicitClaim = java.util.regex.Pattern
                .compile("(?i)^/(kit|kits|playerkits)\\s+claim\\s+([^\\s]+)$")
                .matcher(message);
        if (explicitClaim.matches()) {
            event.setCancelled(true);
            claimKit(player, explicitClaim.group(2));
            return;
        }

        if (isClaimShortCommandEnabled()) {
            java.util.regex.Matcher shortClaim = java.util.regex.Pattern
                    .compile("(?i)^/(kit|kits|playerkits)\\s+([^\\s]+)$")
                    .matcher(message);
            if (shortClaim.matches() && findKit(shortClaim.group(2)) != null) {
                event.setCancelled(true);
                claimKit(player, shortClaim.group(2));
            }
        }
    }

    private void openFor(Player player) {
        if (isBedrockPlayer(player) && getConfig().getBoolean("bedrock-use-playerkits-gui", true)) {
            player.performCommand("kit");
            return;
        }
        openNativeKitDialog(player);
    }

    private void openNativeKitDialog(Player player) {
        List<?> kits = getKits();
        if (kits != null && getConfig().getBoolean("rank-order.enabled", true)) {
            kits = sortKitsByLuckPermsWeight(kits);
        }
        if (kits == null) {
            showNotice(
                    player,
                    getConfig().getString("ui.title", "&8Kits"),
                    getConfig().getString("messages.playerkits-missing", "&cPlayerKits2 is not installed or is not ready.")
            );
            return;
        }

        List<ActionButton> buttons = new ArrayList<>();
        for (Object kit : kits) {
            String kitId = stringValue(invoke(kit, "getName"));
            if (kitId == null || kitId.isBlank()) {
                continue;
            }
            String displayName = getKitDisplayName(kit, kitId);

            boolean locked = isKitLocked(player, kit);
            boolean claimed = hasClaimedKit(player, kit);
            String statusKey = locked ? "ui.locked-button" : claimed ? "ui.claimed-button" : "ui.ready-button";
            String statusFallback = locked
                    ? "&c%kit% &8| &cLocked"
                    : claimed ? "&e%kit% &8| &eClaimed" : "&a%kit% &8| &aReady";
            String statusLabel = getConfig().getString(statusKey, statusFallback);
            String statusCommand = locked ? "/dialogkits locked " + kitId : "/dialogkits claim " + kitId;
            String statusTooltip = locked
                    ? "&cYou do not have permission to claim " + displayName + "."
                    : claimed ? "&eYou have already claimed " + displayName + "."
                    : "&aReady to claim " + displayName + ".";
            buttons.add(button(
                    format(statusLabel, displayName, kitId),
                    statusCommand,
                    color(statusTooltip)
            ));
            buttons.add(button(
                    color(getConfig().getString("ui.preview-button", "&bPreview")),
                    "/dialogkits preview " + kitId,
                    color("&7View the items in &f" + displayName + "&7.")
            ));
        }

        if (buttons.isEmpty()) {
            showNotice(
                    player,
                    getConfig().getString("ui.title", "&8Kits"),
                    getConfig().getString("messages.no-kits", "&7There are no kits configured in PlayerKits2.")
            );
            return;
        }

        List<DialogBody> body = List.of(DialogBody.plainMessage(
                color(getConfig().getString("ui.description", "&7Choose an action for each kit. Ready is green, Claimed is yellow, Locked is red.")),
                340
        ));

        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(color(getConfig().getString("ui.title", "&8Kits")))
                        .canCloseWithEscape(true)
                        .pause(false)
                        .body(body)
                        .build())
                .type(DialogType.multiAction(buttons, null, 2))
        );

        showDialogOrFallback(player, dialog);
    }

    private void openPreview(Player player, String kitId) {
        if (isBedrockPlayer(player) && getConfig().getBoolean("bedrock-use-playerkits-gui", true)) {
            player.performCommand("kit preview " + kitId);
            return;
        }

        Object kit = findKit(kitId);
        if (kit == null) {
            player.sendMessage(color(getConfig().getString("messages.kit-not-found", "&cThat kit does not exist in PlayerKits2.")));
            openNativeKitDialog(player);
            return;
        }

        String displayName = getKitDisplayName(kit, kitId);
        List<DialogBody> body = new ArrayList<>();

        Object displayItem = invoke(kit, "getDisplayItemDefault");
        ItemStack icon = asItemStack(displayItem);
        if (icon != null) {
            body.add(DialogBody.item(icon, null, true, true, 32, 32));
        }

        List<?> kitItems = listValue(invoke(kit, "getItems"));
        List<String> lines = new ArrayList<>();
        int limit = Math.max(1, getConfig().getInt("ui.preview-list-limit", 14));
        int total = 0;

        if (kitItems != null) {
            total = kitItems.size();
            for (Object kitItem : kitItems) {
                ItemStack item = asItemStack(kitItem);
                if (item == null || item.getType().isAir()) {
                    continue;
                }
                if (lines.size() >= limit) {
                    continue;
                }
                lines.add("&f" + itemAmount(item) + " &7× &f" + itemLabel(item));
            }
        }

        StringBuilder itemText = new StringBuilder();
        itemText.append("&7Items included: &f").append(total);
        if (lines.isEmpty()) {
            itemText.append("\n&8No item preview data was available.");
        } else {
            for (String line : lines) {
                itemText.append("\n").append(line);
            }
            long omitted = Math.max(0, total - lines.size());
            if (omitted > 0) {
                itemText.append("\n&8...and ").append(omitted).append(" more item entries.");
            }
        }
        body.add(DialogBody.plainMessage(color(itemText.toString()), 340));

        boolean locked = isKitLocked(player, kit);
        boolean claimed = hasClaimedKit(player, kit);
        String statusKey = locked ? "ui.locked-button" : claimed ? "ui.claimed-button" : "ui.ready-button";
        String statusFallback = locked
                ? "&c%kit% &8| &cLocked"
                : claimed ? "&e%kit% &8| &eClaimed" : "&a%kit% &8| &aReady";
        ActionButton claimButton = button(
                format(getConfig().getString(statusKey, statusFallback), displayName, kitId),
                locked ? "/dialogkits locked " + kitId : "/dialogkits claim " + kitId,
                color(locked
                        ? "&cYou do not have permission to claim this kit."
                        : claimed ? "&eThis kit has already been claimed." : "&7Claim this kit using PlayerKits2.")
        );
        ActionButton backButton = button(
                color(getConfig().getString("ui.preview-back-button", "&7Back to Kits")),
                "/dialogkits open",
                color("&7Return to the kit list.")
        );

        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(format(getConfig().getString("ui.preview-title", "&8Preview &7» &f%kit%"), displayName, kitId))
                        .canCloseWithEscape(true)
                        .pause(false)
                        .body(body)
                        .build())
                .type(DialogType.multiAction(List.of(claimButton, backButton), null, 2))
        );

        showDialogOrFallback(player, dialog);
    }

    private void loadClaimedData() {
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            getLogger().warning("Could not create DialogKits data folder; claimed-state persistence may not work.");
        }
        claimedDataFile = new File(getDataFolder(), "claimed.yml");
        if (!claimedDataFile.exists()) {
            try {
                if (!claimedDataFile.createNewFile()) {
                    getLogger().warning("Could not create claimed.yml.");
                }
            } catch (IOException exception) {
                getLogger().warning("Could not create claimed.yml: " + exception.getMessage());
            }
        }
        claimedData = YamlConfiguration.loadConfiguration(claimedDataFile);
    }

    private void saveClaimedData() {
        if (claimedData == null || claimedDataFile == null) {
            return;
        }
        try {
            claimedData.save(claimedDataFile);
        } catch (IOException exception) {
            getLogger().warning("Could not save claimed.yml: " + exception.getMessage());
        }
    }

    private boolean hasClaimedKit(Player player, Object kit) {
        String kitId = stringValue(invoke(kit, "getName"));
        if (kitId == null) {
            return false;
        }

        int cooldownSeconds = numberValue(invoke(kit, "getCooldown"));
        boolean oneTime = Boolean.TRUE.equals(invoke(kit, "isOneTime"));
        Plugin playerKits = getPlayerKitsPlugin();
        Object dataManager = invoke(playerKits, "getPlayerDataManager");

        if (oneTime) {
            Object oneTimeClaimed = invokeWithArguments(dataManager, "isKitOneTime", player, kitId);
            if (Boolean.TRUE.equals(oneTimeClaimed)) {
                return true;
            }
        }

        if (cooldownSeconds > 0) {
            Object cooldown = invokeWithArguments(dataManager, "getKitCooldown", player, kitId);
            if (cooldown instanceof Number number && number.longValue() > System.currentTimeMillis()) {
                return true;
            }
            // Cooldown kits become Ready again once the PlayerKits2 cooldown expires.
            return false;
        }

        String key = claimedKey(player, kitId);
        return claimedData != null && claimedData.getBoolean(key, false);
    }

    private boolean isKitLocked(Player player, Object kit) {
        Object permissionResult = invokeWithArguments(kit, "playerHasPermission", player);
        return permissionResult instanceof Boolean allowed && !allowed;
    }

    private void markClaimed(Player player, Object kit) {
        int cooldownSeconds = numberValue(invoke(kit, "getCooldown"));
        boolean oneTime = Boolean.TRUE.equals(invoke(kit, "isOneTime"));
        // PlayerKits2 owns one-time and cooldown state. DialogKits persists the
        // claim marker for unlimited kits, which otherwise have no status to read.
        if (!oneTime && cooldownSeconds <= 0 && claimedData != null) {
            claimedData.set(claimedKey(player, stringValue(invoke(kit, "getName"))), true);
            saveClaimedData();
        }
    }

    private String claimedKey(Player player, String kitId) {
        String encoded = java.util.Base64.getUrlEncoder().withoutPadding()
                .encodeToString(kitId.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return player.getUniqueId() + "." + encoded;
    }

    private void claimKit(Player player, String kitId) {
        Plugin playerKits = getPlayerKitsPlugin();
        Object kit = findKit(kitId);
        if (playerKits == null || kit == null) {
            player.sendMessage(color(getConfig().getString("messages.kit-not-found", "&cThat kit does not exist in PlayerKits2.")));
            openNativeKitDialog(player);
            return;
        }

        try {
            ClassLoader loader = playerKits.getClass().getClassLoader();
            Class<?> instructionsClass = Class.forName("pk.ajneb97.model.internal.GiveKitInstructions", true, loader);
            Constructor<?> constructor = instructionsClass.getConstructor();
            Object instructions = constructor.newInstance();
            Object kitsManager = invoke(playerKits, "getKitsManager");
            Method giveKit = null;
            for (Method method : kitsManager.getClass().getMethods()) {
                if (method.getName().equals("giveKit") && method.getParameterCount() == 3
                        && method.getParameterTypes()[0].isAssignableFrom(player.getClass())
                        && method.getParameterTypes()[1] == String.class
                        && method.getParameterTypes()[2].isAssignableFrom(instructionsClass)) {
                    giveKit = method;
                    break;
                }
            }
            if (giveKit == null) {
                throw new NoSuchMethodException("PlayerKits2 KitsManager.giveKit");
            }

            Object result = giveKit.invoke(kitsManager, player, kitId, instructions);
            if (Boolean.TRUE.equals(invoke(result, "isError"))) {
                sendPlayerKitsMessage(player, stringValue(invoke(result, "getMessage")));
                openNativeKitDialog(player);
                return;
            }

            if (Boolean.TRUE.equals(invoke(result, "isProceedToBuy"))) {
                openPlayerKitsPurchaseRequirements(player, kitId, playerKits);
                return;
            }

            markClaimed(player, kit);
            sendKitReceivedMessage(player, kitId, playerKits);
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (player.isOnline()) {
                    openNativeKitDialog(player);
                }
            }, 1L);
        } catch (ReflectiveOperationException | RuntimeException exception) {
            getLogger().warning("Could not call PlayerKits2 claim API for kit '" + kitId + "': " + exception.getMessage());
            player.sendMessage(color("&cCould not claim that kit through the integration. Trying PlayerKits2's command instead."));
            player.performCommand(isClaimShortCommandEnabled() ? "kit " + kitId : "kit claim " + kitId);
            Bukkit.getScheduler().runTaskLater(this, () -> {
                if (player.isOnline()) {
                    openNativeKitDialog(player);
                }
            }, 1L);
        }
    }

    private void sendPlayerKitsMessage(Player player, String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        Object messagesManager = invoke(getPlayerKitsPlugin(), "getMessagesManager");
        if (!invokeCompatible(messagesManager, "sendMessage", player, message, true)) {
            player.sendMessage(color(message));
        }
    }

    private void sendKitReceivedMessage(Player player, String kitId, Plugin playerKits) {
        Object configs = invoke(playerKits, "getConfigsManager");
        Object messagesConfigManager = invoke(configs, "getMessagesConfigManager");
        Object messagesConfig = invoke(messagesConfigManager, "getConfig");
        String template = stringValue(invokeWithArgument(messagesConfig, "getString", "kitReceived"));
        if (template == null) {
            template = "&aKit &f%kit% &aclaimed.";
        }
        String message = template.replace("%kit%", kitId);
        Object messagesManager = invoke(playerKits, "getMessagesManager");
        if (!invokeCompatible(messagesManager, "sendMessage", player, message, true)) {
            player.sendMessage(color(message));
        }
    }

    private void openPlayerKitsPurchaseRequirements(Player player, String kitId, Plugin playerKits) {
        try {
            ClassLoader loader = playerKits.getClass().getClassLoader();
            Class<?> inventoryPlayerClass = Class.forName("pk.ajneb97.model.inventory.InventoryPlayer", true, loader);
            Object inventoryPlayer = inventoryPlayerClass.getConstructor(Player.class, String.class)
                    .newInstance(player, "buy_requirements_inventory");
            invokeWithArgument(inventoryPlayer, "setKitName", kitId);
            invokeWithArgument(inventoryPlayer, "setPreviousInventoryName", "main_inventory");
            Object inventoryManager = invoke(playerKits, "getInventoryManager");
            if (!invokeCompatible(inventoryManager, "openInventory", inventoryPlayer)) {
                throw new NoSuchMethodException("PlayerKits2 InventoryManager.openInventory");
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            getLogger().warning("Could not open PlayerKits2 requirement purchase screen: " + exception.getMessage());
            player.sendMessage(color("&cThis kit has purchase requirements; please use PlayerKits2's /kit preview or GUI to complete them."));
            openNativeKitDialog(player);
        }
    }

    private boolean invokeCompatible(Object target, String methodName, Object... arguments) {
        if (target == null) {
            return false;
        }
        for (Method method : target.getClass().getMethods()) {
            if (!method.getName().equals(methodName) || method.getParameterCount() != arguments.length) {
                continue;
            }
            Class<?>[] parameterTypes = method.getParameterTypes();
            boolean compatible = true;
            for (int i = 0; i < parameterTypes.length; i++) {
                Object argument = arguments[i];
                if (argument == null) {
                    continue;
                }
                Class<?> parameter = parameterTypes[i];
                if (parameter.isPrimitive()) {
                    compatible &= (parameter == boolean.class && argument instanceof Boolean)
                            || (parameter == int.class && argument instanceof Integer)
                            || (parameter == long.class && argument instanceof Long)
                            || (parameter == double.class && argument instanceof Double);
                } else {
                    compatible &= parameter.isAssignableFrom(argument.getClass());
                }
            }
            if (!compatible) {
                continue;
            }
            try {
                method.invoke(target, arguments);
                return true;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return false;
            }
        }
        return false;
    }

    private Object invokeWithArguments(Object target, String methodName, Object... arguments) {
        if (target == null) {
            return null;
        }
        for (Method method : target.getClass().getMethods()) {
            if (!method.getName().equals(methodName) || method.getParameterCount() != arguments.length) {
                continue;
            }
            Class<?>[] parameterTypes = method.getParameterTypes();
            boolean compatible = true;
            for (int i = 0; i < parameterTypes.length; i++) {
                if (arguments[i] != null && !parameterTypes[i].isAssignableFrom(arguments[i].getClass())) {
                    compatible = false;
                    break;
                }
            }
            if (!compatible) {
                continue;
            }
            try {
                return method.invoke(target, arguments);
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                return null;
            }
        }
        return null;
    }

    private int numberValue(Object value) {
        return value instanceof Number number ? number.intValue() : 0;
    }

    private void showNotice(Player player, String title, String message) {
        Dialog dialog = Dialog.create(builder -> builder.empty()
                .base(DialogBase.builder(color(title))
                        .canCloseWithEscape(true)
                        .pause(false)
                        .body(List.of(DialogBody.plainMessage(color(message), 340)))
                        .build())
                .type(DialogType.notice())
        );
        showDialogOrFallback(player, dialog);
    }

    private void showDialogOrFallback(Player player, Dialog dialog) {
        try {
            player.showDialog(dialog);
        } catch (Throwable error) {
            getLogger().warning("Could not show a native dialog to " + player.getName() + ": " + error.getMessage());
            player.performCommand("kit");
        }
    }

    private ActionButton button(Component label, String command, Component tooltip) {
        return ActionButton.builder(label)
                .width(220)
                .tooltip(tooltip)
                .action(DialogAction.staticAction(ClickEvent.runCommand(command)))
                .build();
    }

    private Component format(String template, String displayName, String kitId) {
        String text = Objects.requireNonNullElse(template, "%kit%")
                .replace("%kit%", displayName)
                .replace("%kit_id%", kitId)
                .replace("%kit_name%", displayName);
        return color(text);
    }

    private Component color(String text) {
        return LEGACY.deserialize(Objects.requireNonNullElse(text, ""));
    }

    private String getKitDisplayName(Object kit, String kitId) {
        if (getConfig().getBoolean("ui.use-kit-display-name", true)) {
            Object displayItem = invoke(kit, "getDisplayItemDefault");
            String configuredName = stringValue(invoke(displayItem, "getName"));
            if (configuredName != null && !configuredName.isBlank()) {
                String replacement = titleCase(kitId);
                return configuredName
                        .replace("%kit_name%", replacement)
                        .replace("%kit%", kitId)
                        .replace("%kit_name_upper%", kitId.toUpperCase(Locale.ROOT));
            }
        }
        return titleCase(kitId);
    }

    private boolean isClaimShortCommandEnabled() {
        Plugin plugin = getPlayerKitsPlugin();
        Object configs = invoke(plugin, "getConfigsManager");
        Object mainConfig = invoke(configs, "getMainConfigManager");
        Object config = invoke(mainConfig, "getConfig");
        Object value = invokeWithArgument(config, "getBoolean", "claim_kit_short_command");
        return value instanceof Boolean bool && bool;
    }

    private List<?> getKits() {
        Plugin plugin = getPlayerKitsPlugin();
        if (plugin == null) {
            return null;
        }
        Object kitsManager = invoke(plugin, "getKitsManager");
        List<?> kits = listValue(invoke(kitsManager, "getKits"));
        return kits == null ? Collections.emptyList() : kits;
    }

    /**
     * Sort the native kit dialog by LuckPerms group weights.
     *
     * Lower-weight rank kits appear first, and higher-weight rank kits appear
     * farther down. A kit is associated with a rank first by matching its ID to
     * a LuckPerms group name, then by matching the kit's required permission to
     * a permission node assigned directly to a weighted group.
     *
     * LuckPerms is optional and accessed reflectively so DialogKits does not
     * require LuckPerms at compile time. If the API is unavailable, original
     * PlayerKits2 order is preserved.
     */
    private List<?> sortKitsByLuckPermsWeight(List<?> kits) {
        if (kits == null || kits.size() < 2) {
            return kits;
        }

        Plugin luckPermsPlugin = Bukkit.getPluginManager().getPlugin("LuckPerms");
        if (luckPermsPlugin == null || !luckPermsPlugin.isEnabled()) {
            return kits;
        }

        Object luckPerms = getLuckPermsApi(luckPermsPlugin);
        Object groupManager = invoke(luckPerms, "getGroupManager");
        Object loadedGroupsValue = invoke(groupManager, "getLoadedGroups");
        if (!(loadedGroupsValue instanceof Collection<?> groups) || groups.isEmpty()) {
            return kits;
        }

        Map<String, Integer> weightsByGroupName = new HashMap<>();
        Map<String, Integer> weightsByPermission = new HashMap<>();

        for (Object group : groups) {
            String groupName = stringValue(invoke(group, "getName"));
            Integer weight = luckPermsWeight(group);
            if (groupName == null || weight == null) {
                continue;
            }

            weightsByGroupName.merge(normalizeRankName(groupName), weight, Math::min);
            Object nodesValue = invoke(group, "getNodes");
            if (!(nodesValue instanceof Collection<?> nodes)) {
                continue;
            }

            for (Object node : nodes) {
                String permission = stringValue(invoke(node, "getKey"));
                Object value = invoke(node, "getValue");
                if (permission == null || !Boolean.TRUE.equals(value)) {
                    continue;
                }
                weightsByPermission.merge(permission.toLowerCase(Locale.ROOT), weight, Math::min);
            }
        }

        if (weightsByGroupName.isEmpty()) {
            return kits;
        }

        List<KitOrderEntry> ordered = new ArrayList<>(kits.size());
        int originalIndex = 0;
        for (Object kit : kits) {
            String kitName = stringValue(invoke(kit, "getName"));
            boolean permissionRequired = Boolean.TRUE.equals(invoke(kit, "isPermissionRequired"));
            Integer weight = kitName == null
                    ? null
                    : weightsByGroupName.get(normalizeRankName(kitName));

            if (weight == null && kitName != null && permissionRequired) {
                String customPermission = stringValue(invoke(kit, "getCustomPermission"));
                String requiredPermission = customPermission != null && !customPermission.isBlank()
                        ? customPermission
                        : "playerkits.kit." + kitName;

                String expected = requiredPermission.toLowerCase(Locale.ROOT);
                for (Map.Entry<String, Integer> entry : weightsByPermission.entrySet()) {
                    if (permissionNodeMatches(expected, entry.getKey())
                            && (weight == null || entry.getValue() < weight)) {
                        weight = entry.getValue();
                    }
                }
            }

            ordered.add(new KitOrderEntry(kit, weight, permissionRequired, originalIndex++));
        }

        boolean unmatchedLast = getConfig().getBoolean("rank-order.unmatched-kits-last", true);
        Comparator<KitOrderEntry> comparator = Comparator
                // Kits without a permission requirement always come first.
                .comparingInt((KitOrderEntry entry) -> entry.permissionRequired() ? 1 : 0)
                // Keep non-permission kits together in their original order; sort
                // permission-based kits by ascending LuckPerms group weight.
                .thenComparingInt(entry -> !entry.permissionRequired()
                        ? 0
                        : entry.weight() == null
                        ? (unmatchedLast ? Integer.MAX_VALUE : Integer.MIN_VALUE)
                        : entry.weight())
                .thenComparingInt(KitOrderEntry::originalIndex);
        ordered.sort(comparator);

        List<Object> result = new ArrayList<>(ordered.size());
        for (KitOrderEntry entry : ordered) {
            result.add(entry.kit());
        }
        return result;
    }

    private Object getLuckPermsApi(Plugin luckPermsPlugin) {
        try {
            Class<?> providerClass = Class.forName(
                    "net.luckperms.api.LuckPermsProvider",
                    true,
                    luckPermsPlugin.getClass().getClassLoader()
            );
            return providerClass.getMethod("get").invoke(null);
        } catch (ReflectiveOperationException | LinkageError exception) {
            getLogger().fine("LuckPerms API could not be accessed for kit sorting: " + exception.getMessage());
            return null;
        }
    }

    private Integer luckPermsWeight(Object group) {
        Object value = invoke(group, "getWeight");
        if (value instanceof OptionalInt optionalInt) {
            return optionalInt.isPresent() ? optionalInt.getAsInt() : null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return null;
    }

    private boolean permissionNodeMatches(String requiredPermission, String assignedNode) {
        if (assignedNode.equals("*") || assignedNode.equalsIgnoreCase(requiredPermission)) {
            return true;
        }
        if (assignedNode.endsWith(".*")) {
            String prefix = assignedNode.substring(0, assignedNode.length() - 1);
            return requiredPermission.startsWith(prefix);
        }
        return false;
    }

    private String normalizeRankName(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    private record KitOrderEntry(Object kit, Integer weight, boolean permissionRequired, int originalIndex) {
    }

    private Object findKit(String kitId) {
        List<?> kits = getKits();
        if (kits == null) {
            return null;
        }
        for (Object kit : kits) {
            String name = stringValue(invoke(kit, "getName"));
            if (kitId.equals(name)) {
                return kit;
            }
        }
        return null;
    }

    private Plugin getPlayerKitsPlugin() {
        Plugin plugin = Bukkit.getPluginManager().getPlugin("PlayerKits2");
        return plugin != null && plugin.isEnabled() ? plugin : null;
    }

    private boolean isBedrockPlayer(Player player) {
        try {
            Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Object api = apiClass.getMethod("getInstance").invoke(null);
            Method method = apiClass.getMethod("isFloodgatePlayer", UUID.class);
            Object result = method.invoke(api, player.getUniqueId());
            return result instanceof Boolean value && value;
        } catch (ReflectiveOperationException | LinkageError ignored) {
            return false;
        }
    }

    private ItemStack asItemStack(Object kitItem) {
        if (kitItem == null) {
            return null;
        }

        Object original = invoke(kitItem, "getOriginalItem");
        if (original instanceof ItemStack itemStack) {
            return itemStack.clone();
        }

        String id = stringValue(invoke(kitItem, "getId"));
        if (id == null || id.isBlank()) {
            return null;
        }

        Material material = Material.matchMaterial(id);
        if (material == null && id.startsWith("minecraft:")) {
            material = Material.matchMaterial(id.substring("minecraft:".length()));
        }
        if (material == null || material.isAir()) {
            return null;
        }

        Object rawAmount = invoke(kitItem, "getAmount");
        int amount = rawAmount instanceof Number number ? number.intValue() : 1;
        ItemStack result = new ItemStack(material, Math.max(1, Math.min(Math.max(1, amount), 99)));

        String customName = stringValue(invoke(kitItem, "getName"));
        if (customName != null && !customName.isBlank()) {
            ItemMeta meta = result.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', customName));
                result.setItemMeta(meta);
            }
        }
        return result;
    }

    private String itemLabel(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) {
            String label = ChatColor.stripColor(meta.getDisplayName());
            if (label != null && !label.isBlank()) {
                return label;
            }
        }
        String name = item.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
        StringBuilder result = new StringBuilder();
        for (String part : name.split(" ")) {
            if (part.isBlank()) {
                continue;
            }
            if (!result.isEmpty()) {
                result.append(' ');
            }
            result.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return result.toString();
    }

    private int itemAmount(ItemStack item) {
        return Math.max(1, item.getAmount());
    }

    private String titleCase(String input) {
        String normalised = input.replace('_', ' ').replace('-', ' ').trim();
        StringBuilder result = new StringBuilder();
        boolean makeUpper = true;
        for (char character : normalised.toCharArray()) {
            if (Character.isWhitespace(character)) {
                result.append(character);
                makeUpper = true;
            } else {
                result.append(makeUpper ? Character.toUpperCase(character) : character);
                makeUpper = false;
            }
        }
        return result.toString();
    }

    private Object invoke(Object target, String methodName) {
        if (target == null) {
            return null;
        }
        try {
            return target.getClass().getMethod(methodName).invoke(target);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private Object invokeWithArgument(Object target, String methodName, Object argument) {
        if (target == null || argument == null) {
            return null;
        }
        for (Method method : target.getClass().getMethods()) {
            if (method.getName().equals(methodName) && method.getParameterCount() == 1
                    && method.getParameterTypes()[0].isAssignableFrom(argument.getClass())) {
                try {
                    return method.invoke(target, argument);
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    private List<?> listValue(Object value) {
        return value instanceof List<?> list ? list : null;
    }

    private String stringValue(Object value) {
        return value instanceof String string ? string : null;
    }
}
