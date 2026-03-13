package com.example.villagershop;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/**
 * Custom InventoryHolder used to tag admin trade-creator GUI inventories.
 * This lets AdminGuiListener identify admin GUIs without title-string comparison.
 *
 * The Inventory reference is set after creation (chicken-and-egg requirement of
 * Bukkit.createInventory, which needs the holder first).
 */
public class AdminGuiHolder implements InventoryHolder {

    private Inventory inventory;

    @Override
    @NotNull
    public Inventory getInventory() {
        return inventory;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }
}
