/*
 * Skylease - Copyright 2026 joshlucem
 * SPDX-License-Identifier: Apache-2.0 AND LicenseRef-Skylease-Credits
 */
package com.joshlucem.skylease.managers;

import com.joshlucem.skylease.Skylease;
import com.joshlucem.skylease.models.ParticleEffect;
import com.joshlucem.skylease.models.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Loads effects.yml and draws the particles. Each effect hangs from skylease.effects.* */
public class ParticleManager {

    private static final double SPREAD = 0.3;
    private static final double SPEED = 0.01;

    private final Skylease plugin;
    private final Map<String, ParticleEffect> effects = new LinkedHashMap<>();
    private BukkitTask particleTask;

    public ParticleManager(Skylease plugin) {
        this.plugin = plugin;
        loadEffects();
    }

    public void reload() {
        loadEffects();
    }

    private void loadEffects() {
        effects.clear();
        FileConfiguration file = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "effects.yml"));

        for (String id : file.getKeys(false)) {
            if (!file.isConfigurationSection(id)) {
                continue; // config-version and other loose keys
            }

            String particleName = file.getString(id + ".particle", "NONE");
            Particle particle = null;
            if (!particleName.equalsIgnoreCase("NONE")) {
                particle = resolveParticle(particleName);
                if (particle == null) {
                    plugin.log().warn("effects.yml: effect \"" + id + "\" uses the particle \"" + particleName
                        + "\", which does not exist on this server version. Skipped.");
                    continue;
                }
                if (!isSupported(particle)) {
                    plugin.log().warn("effects.yml: effect \"" + id + "\" uses " + particle.name()
                        + ", which needs extra data Skylease cannot supply. Skipped.");
                    continue;
                }
            }

            effects.put(id, new ParticleEffect(
                id,
                file.getString(id + ".material", "BARRIER"),
                file.getString(id + ".permission", "skylease.effects." + id),
                particle));
        }

        registerPermissions();
        if (effects.isEmpty()) {
            plugin.log().warn("effects.yml defines no valid effects.");
        }
    }

    @SuppressWarnings("deprecation") // Particle is no longer an enum
    private Particle resolveParticle(String name) {
        try {
            return Particle.valueOf(name.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // each effect hangs from skylease.effects.*
    private void registerPermissions() {
        Permission all = Bukkit.getPluginManager().getPermission("skylease.effects.*");
        if (all == null) {
            all = new Permission("skylease.effects.*", "Use every particle effect", PermissionDefault.FALSE);
            Bukkit.getPluginManager().addPermission(all);
        }

        for (ParticleEffect effect : effects.values()) {
            Permission permission = Bukkit.getPluginManager().getPermission(effect.permission());
            if (permission == null) {
                permission = new Permission(effect.permission(), "Use the " + effect.id() + " effect", PermissionDefault.FALSE);
                Bukkit.getPluginManager().addPermission(permission);
            }
            all.getChildren().put(permission.getName(), true);
        }
        all.recalculatePermissibles();
    }

    public ParticleEffect getEffect(String id) {
        return effects.get(id);
    }

    public Map<String, ParticleEffect> getAllEffects() {
        return new LinkedHashMap<>(effects);
    }

    /** Name in the chosen language, as MiniMessage. Falls back to the key. */
    public String displayName(ParticleEffect effect) {
        String name = plugin.getMessageManager().raw("effects-text." + effect.id() + ".name");
        return name.isBlank() ? effect.id() : name;
    }

    public List<String> lore(ParticleEffect effect) {
        return plugin.getMessageManager().rawList("effects-text." + effect.id() + ".lore");
    }

    public boolean hasPermission(Player player, String effectId) {
        ParticleEffect effect = effects.get(effectId);
        return effect != null && player.hasPermission(effect.permission());
    }

    // ------------------------------------------------------------------

    public void startParticleTask() {
        int interval = plugin.getConfigManager().getParticleSpawnInterval();
        particleTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!player.isFlying()) {
                    continue;
                }
                PlayerData data = plugin.getDatabaseManager().getCachedPlayerData(player.getUniqueId());
                if (data != null && !data.getParticleEffect().equals("none")) {
                    spawnParticles(player, data.getParticleEffect());
                }
            }
        }, interval, interval);
    }

    public void restartTask() {
        stopParticleTask();
        startParticleTask();
    }

    public void stopParticleTask() {
        if (particleTask != null) {
            particleTask.cancel();
            particleTask = null;
        }
    }

    private void spawnParticles(Player player, String effectId) {
        ParticleEffect effect = effects.get(effectId);
        if (effect == null || effect.particle() == null || !hasPermission(player, effectId)) {
            return;
        }
        if (player.getGameMode() == GameMode.SPECTATOR || isVanished(player)) {
            return;
        }

        Location center = player.getLocation().add(0, 1, 0);
        int count = plugin.getConfigManager().getParticlesPerSpawn();
        Particle particle = effect.particle();

        try {
            Object data = dataFor(particle);
            if (data == null) {
                player.getWorld().spawnParticle(particle, center, count, SPREAD, SPREAD, SPREAD, SPEED);
            } else {
                player.getWorld().spawnParticle(particle, center, count, SPREAD, SPREAD, SPREAD, SPEED, data);
            }
        } catch (IllegalArgumentException e) {
            plugin.log().debug("Could not draw " + particle.name() + ": " + e.getMessage());
        }
    }

    /** Particles that need no extra data, or data we can supply (see dataFor). */
    private boolean isSupported(Particle particle) {
        Class<?> type = particle.getDataType();
        return type == Void.class || type == Particle.DustOptions.class || type == Particle.DustTransition.class
            || type == Float.class || type == Integer.class || type == ItemStack.class || type == BlockData.class;
    }

    // some particles need extra data (colour, item, block); they get a fixed one
    private Object dataFor(Particle particle) {
        Class<?> type = particle.getDataType();
        if (type == Particle.DustOptions.class) {
            return new Particle.DustOptions(Color.RED, 1.0f);
        }
        if (type == Particle.DustTransition.class) {
            return new Particle.DustTransition(Color.RED, Color.AQUA, 1.0f);
        }
        if (type == Float.class) {
            return 0.0f;
        }
        if (type == Integer.class) {
            return 0;
        }
        if (type == ItemStack.class) {
            return new ItemStack(Material.SLIME_BALL);
        }
        if (type == BlockData.class) {
            return Material.GRASS_BLOCK.createBlockData();
        }
        return null;
    }

    // Essentials, SuperVanish and PremiumVanish set this metadata
    private boolean isVanished(Player player) {
        return player.getMetadata("vanished").stream().anyMatch(value -> value.asBoolean());
    }
}
