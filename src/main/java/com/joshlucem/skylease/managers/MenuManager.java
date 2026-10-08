/*
 * Skylease - Copyright 2026 joshlucem
 * SPDX-License-Identifier: Apache-2.0 AND LicenseRef-Skylease-Credits
 */
package com.joshlucem.skylease.managers;

import com.joshlucem.skylease.Skylease;
import com.joshlucem.skylease.menus.SkyleaseMenu;
import com.joshlucem.skylease.models.ParticleEffect;
import com.joshlucem.skylease.models.PlayerData;
import com.joshlucem.skylease.utils.ItemBuilder;
import com.joshlucem.skylease.utils.TimeUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Builds the three menus. Layout (slots, materials) comes from menus.yml and text from the lang file.
 * What a click does is decided in {@link com.joshlucem.skylease.listeners.MenuListener}.
 */
public class MenuManager {

    private static final String SETTINGS_TEXT = "menus.settings";
    private static final String ALERTS_TEXT = "menus.alerts";
    private static final String EFFECTS_TEXT = "menus.effects";

    private final Skylease plugin;
    private FileConfiguration menus;
    private final Map<UUID, Integer> effectsPage = new ConcurrentHashMap<>();

    public MenuManager(Skylease plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        menus = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "menus.yml"));
        for (int threshold : plugin.getConfigManager().getAvailableAlertThresholds()) {
            if (menus.getConfigurationSection("alerts-menu.items.alert-" + threshold) == null) {
                plugin.log().warn("menus.yml: no alert-" + threshold + " slot, so that alert will not appear in the menu.");
            }
        }
    }

    public FileConfiguration getMenusConfig() {
        return menus;
    }

    public int getEffectsPage(UUID uuid) {
        return effectsPage.getOrDefault(uuid, 0);
    }

    public void forget(UUID uuid) {
        effectsPage.remove(uuid);
    }

    private MessageManager messages() {
        return plugin.getMessageManager();
    }

    // ------------------------------------------------------------------

    public void openSettingsMenu(Player player) {
        ConfigurationSection root = menus.getConfigurationSection("settings-menu");
        PlayerData data = plugin.getDatabaseManager().getCachedPlayerData(player.getUniqueId());
        if (root == null || data == null) {
            return;
        }
        Inventory inventory = create(root, SkyleaseMenu.Type.SETTINGS, SETTINGS_TEXT);
        ConfigurationSection items = root.getConfigurationSection("items");
        if (items == null) {
            return;
        }

        ConfigurationSection head = items.getConfigurationSection("player-head");
        if (head != null) {
            ItemStack skull = item(head, SETTINGS_TEXT + ".player-head", true,
                MessageManager.text("player", player.getName()),
                MessageManager.text("time", messages().displayTime(player, data.getFlyTime())),
                MessageManager.mini("effect", effectDisplay(data.getParticleEffect())));
            if (skull.getItemMeta() instanceof SkullMeta meta) {
                meta.setOwningPlayer(player);
                skull.setItemMeta(meta);
            }
            inventory.setItem(head.getInt("slot", 13), skull);
        }
        place(inventory, items, "alerts-button", SETTINGS_TEXT + ".alerts-button");
        place(inventory, items, "effects-button", SETTINGS_TEXT + ".effects-button");
        fill(inventory, items);

        player.openInventory(inventory);
    }

    public void openAlertsMenu(Player player) {
        ConfigurationSection root = menus.getConfigurationSection("alerts-menu");
        PlayerData data = plugin.getDatabaseManager().getCachedPlayerData(player.getUniqueId());
        if (root == null || data == null) {
            return;
        }
        Inventory inventory = create(root, SkyleaseMenu.Type.ALERTS, ALERTS_TEXT);
        ConfigurationSection items = root.getConfigurationSection("items");
        if (items == null) {
            return;
        }

        String alertText = ALERTS_TEXT + ".alert";
        for (int threshold : plugin.getConfigManager().getAvailableAlertThresholds()) {
            ConfigurationSection alert = items.getConfigurationSection("alert-" + threshold);
            if (alert == null) {
                continue;
            }
            boolean enabled = data.isAlertEnabled(threshold);
            String state = enabled ? "enabled" : "disabled";
            inventory.setItem(alert.getInt("slot", 10), item(alert, alertText, enabled,
                MessageManager.text("time", TimeUtil.formatTime(threshold)),
                MessageManager.mini("status", messages().raw(alertText + "." + state + "-status")),
                MessageManager.mini("action", messages().raw(alertText + "." + state + "-action"))));
        }
        place(inventory, items, "back-button", ALERTS_TEXT + ".back-button");
        fill(inventory, items);

        player.openInventory(inventory);
    }

    public void openEffectsMenu(Player player) {
        openEffectsMenu(player, getEffectsPage(player.getUniqueId()));
    }

    public void openEffectsMenu(Player player, int requestedPage) {
        ConfigurationSection root = menus.getConfigurationSection("effects-menu");
        PlayerData data = plugin.getDatabaseManager().getCachedPlayerData(player.getUniqueId());
        if (root == null || data == null) {
            return;
        }
        ConfigurationSection items = root.getConfigurationSection("items");
        ConfigurationSection layout = root.getConfigurationSection("layout");
        if (items == null || layout == null) {
            return;
        }
        Inventory inventory = create(root, SkyleaseMenu.Type.EFFECTS, EFFECTS_TEXT);

        List<Integer> slots = layout.getIntegerList("effect-slots");
        List<ParticleEffect> effects = new ArrayList<>(plugin.getParticleManager().getAllEffects().values());
        int perPage = effectsPerPage(root, slots.size());
        int pages = Math.max(1, (int) Math.ceil(effects.size() / (double) perPage));
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        effectsPage.put(player.getUniqueId(), page);

        TagResolver[] pageTags = {
            MessageManager.text("current_page", page + 1),
            MessageManager.text("total_pages", pages)
        };

        ConfigurationSection current = items.getConfigurationSection("current-effect");
        if (current != null) {
            inventory.setItem(current.getInt("slot", 49), item(current, EFFECTS_TEXT + ".current-effect", true,
                MessageManager.mini("current_effect", effectDisplay(data.getParticleEffect()))));
        }

        for (int i = 0; i < slots.size(); i++) {
            int index = page * perPage + i;
            if (index >= effects.size()) {
                break;
            }
            ParticleEffect effect = effects.get(index);
            boolean allowed = plugin.getParticleManager().hasPermission(player, effect.id());
            boolean selected = effect.id().equals(data.getParticleEffect());
            inventory.setItem(slots.get(i), effectItem(effect, root, allowed, selected));
        }

        // at either end the page button is swapped for its greyed-out version
        place(inventory, items, page > 0 ? "previous-page" : "no-previous-page", EFFECTS_TEXT + (page > 0 ? ".previous-page" : ".no-previous-page"), pageTags);
        place(inventory, items, page < pages - 1 ? "next-page" : "no-next-page", EFFECTS_TEXT + (page < pages - 1 ? ".next-page" : ".no-next-page"), pageTags);
        place(inventory, items, "reset-effect", EFFECTS_TEXT + ".reset-effect");
        place(inventory, items, "back-button", EFFECTS_TEXT + ".back-button");
        fill(inventory, items);

        player.openInventory(inventory);
    }

    /** Effects per page: the configured value, or the number of slots in the layout. */
    public int effectsPerPage(ConfigurationSection effectsMenu, int slotCount) {
        int fallback = slotCount > 0 ? slotCount : 21;
        ConfigurationSection pagination = effectsMenu.getConfigurationSection("pagination");
        int configured = pagination != null ? pagination.getInt("effects-per-page", fallback) : fallback;
        // never more than the slots, or effects would repeat or be skipped between pages
        return configured > 0 ? Math.min(configured, fallback) : fallback;
    }

    // ------------------------------------------------------------------

    private Inventory create(ConfigurationSection root, SkyleaseMenu.Type type, String textPath) {
        SkyleaseMenu holder = new SkyleaseMenu(type);
        Component title = messages().parse(messages().raw(textPath + ".title")).decoration(TextDecoration.ITALIC, false);
        Inventory inventory = Bukkit.createInventory(holder, root.getInt("size", 27), title);
        holder.setInventory(inventory);
        return inventory;
    }

    private void place(Inventory inventory, ConfigurationSection items, String key, String textPath, TagResolver... resolvers) {
        ConfigurationSection section = items.getConfigurationSection(key);
        if (section != null) {
            inventory.setItem(section.getInt("slot", 0), item(section, textPath, true, resolvers));
        }
    }

    private void fill(Inventory inventory, ConfigurationSection items) {
        ConfigurationSection filler = items.getConfigurationSection("filler");
        if (filler == null || !filler.getBoolean("enabled", true)) {
            return;
        }
        ItemStack pane = new ItemBuilder(material(filler.getString("material"))).setName(Component.text(" ")).build();
        for (int slot : filler.getIntegerList("slots")) {
            inventory.setItem(slot, pane);
        }
    }

    /**
     * An item from its layout section (material) and its text path in the lang file (name and lore).
     *
     * @param active when false, "material-disabled" is used if the section defines it
     */
    private ItemStack item(ConfigurationSection layout, String textPath, boolean active, TagResolver... resolvers) {
        String materialName = !active && layout.contains("material-disabled")
            ? layout.getString("material-disabled")
            : layout.getString("material");

        ItemBuilder builder = new ItemBuilder(material(materialName));
        builder.setName(line(messages().raw(textPath + ".name"), resolvers));
        for (String lore : messages().rawList(textPath + ".lore")) {
            builder.addLore(line(lore, resolvers));
        }
        return builder.build();
    }

    private ItemStack effectItem(ParticleEffect effect, ConfigurationSection root, boolean allowed, boolean selected) {
        ConfigurationSection style = root.getConfigurationSection("effect-item");
        String lockedMaterial = style != null ? style.getString("locked-material", "BARRIER") : "BARRIER";

        ItemBuilder builder = new ItemBuilder(material(allowed ? effect.material() : lockedMaterial));
        builder.setName(line(plugin.getParticleManager().displayName(effect)));
        plugin.getParticleManager().lore(effect).forEach(lore -> builder.addLore(line(lore)));

        String extra = !allowed ? "locked-lore" : selected ? "selected-lore" : "available-lore";
        messages().rawList(EFFECTS_TEXT + "." + extra).forEach(lore -> builder.addLore(line(lore)));
        return builder.build();
    }

    private Component line(String miniMessage, TagResolver... resolvers) {
        return messages().parse(miniMessage, resolvers).decoration(TextDecoration.ITALIC, false);
    }

    private Material material(String name) {
        Material material = name == null ? null : Material.matchMaterial(name);
        return material != null ? material : Material.STONE;
    }

    /** Name of an effect in MiniMessage, or the "none" text. */
    private String effectDisplay(String id) {
        ParticleEffect effect = id == null ? null : plugin.getParticleManager().getEffect(id);
        return effect != null ? plugin.getParticleManager().displayName(effect) : messages().raw(EFFECTS_TEXT + ".none");
    }
}
