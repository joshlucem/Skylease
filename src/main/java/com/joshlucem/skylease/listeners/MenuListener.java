/*
 * Skylease - Copyright 2026 joshlucem
 * SPDX-License-Identifier: Apache-2.0 AND LicenseRef-Skylease-Credits
 */
package com.joshlucem.skylease.listeners;

import com.joshlucem.skylease.Skylease;
import com.joshlucem.skylease.managers.MessageManager;
import com.joshlucem.skylease.managers.MenuManager;
import com.joshlucem.skylease.menus.SkyleaseMenu;
import com.joshlucem.skylease.models.ParticleEffect;
import com.joshlucem.skylease.models.PlayerData;
import com.joshlucem.skylease.utils.TimeUtil;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class MenuListener implements Listener {

    private final Skylease plugin;

    public MenuListener(Skylease plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    // dragging would leave items inside the menu
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof SkyleaseMenu) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof SkyleaseMenu menu) || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        event.setCancelled(true);

        // ignore clicks outside the menu
        int slot = event.getRawSlot();
        ItemStack clicked = event.getCurrentItem();
        if (slot < 0 || slot >= event.getInventory().getSize() || clicked == null || clicked.getType() == Material.AIR) {
            return;
        }

        switch (menu.getType()) {
            case SETTINGS -> onSettingsClick(player, slot);
            case ALERTS -> onAlertsClick(player, slot);
            case EFFECTS -> onEffectsClick(player, slot);
        }
    }

    private void onSettingsClick(Player player, int slot) {
        ConfigurationSection items = section("settings-menu.items");
        if (items == null) {
            return;
        }
        if (slot == slotOf(items, "alerts-button")) {
            plugin.getMenuManager().openAlertsMenu(player);
        } else if (slot == slotOf(items, "effects-button")) {
            plugin.getMenuManager().openEffectsMenu(player);
        }
    }

    private void onAlertsClick(Player player, int slot) {
        ConfigurationSection items = section("alerts-menu.items");
        PlayerData data = plugin.getDatabaseManager().getCachedPlayerData(player.getUniqueId());
        if (items == null || data == null) {
            return;
        }
        if (slot == slotOf(items, "back-button")) {
            plugin.getMenuManager().openSettingsMenu(player);
            return;
        }

        for (int threshold : plugin.getConfigManager().getAvailableAlertThresholds()) {
            if (slot != slotOf(items, "alert-" + threshold)) {
                continue;
            }
            boolean enabled = data.toggleAlert(threshold);
            plugin.getDatabaseManager().markDirty(player.getUniqueId());

            plugin.getMessageManager().send(player, enabled ? "menu.alert-enabled" : "menu.alert-disabled",
                MessageManager.text("threshold", TimeUtil.formatTime(threshold)));
            // reopen to refresh it, unless the player has already left the menu
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.getOpenInventory().getTopInventory().getHolder() instanceof SkyleaseMenu open
                    && open.getType() == SkyleaseMenu.Type.ALERTS) {
                    plugin.getMenuManager().openAlertsMenu(player);
                }
            }, 1L);
            return;
        }
    }

    private void onEffectsClick(Player player, int slot) {
        ConfigurationSection root = section("effects-menu");
        PlayerData data = plugin.getDatabaseManager().getCachedPlayerData(player.getUniqueId());
        if (root == null || data == null) {
            return;
        }
        ConfigurationSection items = root.getConfigurationSection("items");
        ConfigurationSection layout = root.getConfigurationSection("layout");
        if (items == null || layout == null) {
            return;
        }
        MenuManager menus = plugin.getMenuManager();
        int page = menus.getEffectsPage(player.getUniqueId());

        if (slot == slotOf(items, "back-button")) {
            menus.openSettingsMenu(player);
        } else if (slot == slotOf(items, "reset-effect")) {
            data.setParticleEffect("none");
            plugin.getDatabaseManager().markDirty(player.getUniqueId());
            plugin.getMessageManager().send(player, "effects.disabled");
            menus.openEffectsMenu(player);
        } else if (slot == pageButtonSlot(items, "previous-page", "no-previous-page")) {
            if (page > 0) {
                menus.openEffectsMenu(player, page - 1);
            }
        } else if (slot == pageButtonSlot(items, "next-page", "no-next-page")) {
            menus.openEffectsMenu(player, page + 1);
        } else {
            selectEffect(player, data, effectAt(slot, layout, root, page));
        }
    }

    private void selectEffect(Player player, PlayerData data, ParticleEffect effect) {
        if (effect == null) {
            return;
        }
        if (!plugin.getParticleManager().hasPermission(player, effect.id())) {
            plugin.getMessageManager().send(player, "effects.no-permission");
            return;
        }

        data.setParticleEffect(effect.id());
        plugin.getDatabaseManager().markDirty(player.getUniqueId());

        String plainName = PlainTextComponentSerializer.plainText()
            .serialize(plugin.getMessageManager().parse(plugin.getParticleManager().displayName(effect)));
        plugin.getMessageManager().send(player, "effects.selected", MessageManager.text("effect", plainName));
        plugin.getMenuManager().openEffectsMenu(player);
    }

    private ParticleEffect effectAt(int slot, ConfigurationSection layout, ConfigurationSection root, int page) {
        List<Integer> slots = layout.getIntegerList("effect-slots");
        int position = slots.indexOf(slot);
        if (position < 0) {
            return null;
        }
        int perPage = plugin.getMenuManager().effectsPerPage(root, slots.size());
        List<ParticleEffect> effects = new ArrayList<>(plugin.getParticleManager().getAllEffects().values());
        int index = page * perPage + position;
        return index < effects.size() ? effects.get(index) : null;
    }

    private int pageButtonSlot(ConfigurationSection items, String key, String disabledKey) {
        int slot = slotOf(items, key);
        return slot != -1 ? slot : slotOf(items, disabledKey);
    }

    private ConfigurationSection section(String path) {
        return plugin.getMenuManager().getMenusConfig().getConfigurationSection(path);
    }

    private int slotOf(ConfigurationSection items, String key) {
        ConfigurationSection section = items.getConfigurationSection(key);
        return section == null ? -1 : section.getInt("slot", -1);
    }
}
