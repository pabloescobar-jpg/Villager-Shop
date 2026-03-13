package com.example.villagershop;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/**
 * Custom InventoryHolder for the /vg admindelete GUI.
 * Stores the current page so AdminGuiListener can compute trade indices on click.
 * This is a display-only GUI — the player cannot place items in it.
 */
public class AdminDeleteGuiHolder implements InventoryHolder {

    private Inventory inventory;
    private int page;

    public AdminDeleteGuiHolder(int page) {
        this.page = page;
    }

    @Override
    @NotNull
    public Inventory getInventory() {
        return inventory;
    }

    void setInventory(Inventory inventory) {
        this.inventory = inventory;
    }

    int getPage() {
        return page;
    }

    void setPage(int page) {
        this.page = page;
    }
}
