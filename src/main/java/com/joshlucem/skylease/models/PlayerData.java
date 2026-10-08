/*
 * Skylease - Copyright 2026 joshlucem
 * SPDX-License-Identifier: Apache-2.0 AND LicenseRef-Skylease-Credits
 */
package com.joshlucem.skylease.models;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongUnaryOperator;

/** A player's data. The time is atomic because the main thread and the database thread both touch it. */
public class PlayerData {

    private final UUID uuid;
    private final AtomicLong flyTime = new AtomicLong();
    private final Set<Integer> enabledAlerts = ConcurrentHashMap.newKeySet();
    private volatile String username = "Desconocido";
    private volatile boolean flying;
    private volatile String particleEffect = "none";
    private volatile long lastSeen = System.currentTimeMillis();

    public PlayerData(UUID uuid) {
        this.uuid = uuid;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        if (username != null && !username.isBlank()) {
            this.username = username;
        }
    }

    public long getFlyTime() {
        return flyTime.get();
    }

    public void setFlyTime(long seconds) {
        flyTime.set(Math.max(0, seconds));
    }

    public long updateFlyTime(LongUnaryOperator operation) {
        // capped at Long.MAX_VALUE / 2 so it cannot overflow
        return flyTime.updateAndGet(current -> {
            long next = operation.applyAsLong(current);
            return Math.max(0, Math.min(next, Long.MAX_VALUE / 2));
        });
    }

    public boolean hasFlyTime() {
        return flyTime.get() > 0;
    }

    public boolean isFlying() {
        return flying;
    }

    public void setFlying(boolean flying) {
        this.flying = flying;
    }

    public String getParticleEffect() {
        return particleEffect;
    }

    public void setParticleEffect(String particleEffect) {
        this.particleEffect = particleEffect == null ? "none" : particleEffect;
    }

    public Set<Integer> getEnabledAlerts() {
        return enabledAlerts;
    }

    public boolean isAlertEnabled(int threshold) {
        return enabledAlerts.contains(threshold);
    }

    public boolean toggleAlert(int threshold) {
        if (!enabledAlerts.remove(threshold)) {
            enabledAlerts.add(threshold);
            return true;
        }
        return false;
    }

    public long getLastSeen() {
        return lastSeen;
    }

    public void setLastSeen(long lastSeen) {
        this.lastSeen = lastSeen;
    }

    public void updateLastSeen() {
        this.lastSeen = System.currentTimeMillis();
    }

    public PlayerData copy() {
        PlayerData clone = new PlayerData(uuid);
        clone.username = username;
        clone.flyTime.set(flyTime.get());
        clone.flying = flying;
        clone.particleEffect = particleEffect;
        clone.lastSeen = lastSeen;
        clone.enabledAlerts.addAll(enabledAlerts);
        return clone;
    }
}
