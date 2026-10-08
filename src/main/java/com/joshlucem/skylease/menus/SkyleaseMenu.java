/*
 * Skylease - Copyright 2026 joshlucem
 * SPDX-License-Identifier: Apache-2.0 AND LicenseRef-Skylease-Credits
 */
package com.joshlucem.skylease.menus;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

/** Marks the plugin's inventories. They are recognised by this and not by their title. */
public class SkyleaseMenu implements InventoryHolder {

    public enum Type { SETTINGS, ALERTS, EFFECTS }

    private final Type type;
    private Inventory inventory;

    public SkyleaseMenu(Type type) {
        this.type = type;
    }

    public Type getType() {
        return type;
    }

    public void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
