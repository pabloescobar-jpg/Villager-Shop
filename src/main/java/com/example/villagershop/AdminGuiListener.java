package com.example.villagershop;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Handles click and close events for both admin GUIs.
 *
 * ── Creator GUI (/vs admin) ───────────────────────────────────────────────────
 * Identified by AdminGuiHolder.  Interactive slots: 9, 11, 13.
 * All other top-inventory slots are locked.  Items are returned on any close.
 *
 * ── Delete GUI (/vs admindelete) ─────────────────────────────────────────────
 * Identified by AdminDeleteGuiHolder.  Display-only — all clicks are cancelled.
 * Delete buttons (column 8, rows 0–4) remove the corresponding trade and
 * refresh the GUI in-place.  Navigation buttons (slots 45 / 53) page forward
 * and backward.  No items to return on close (nothing placed by the player).
 */
public class AdminGuiListener implements Listener {

    private final TradeManager tradeManager;

    public AdminGuiListener(TradeManager tradeManager) {
        this.tradeManager = tradeManager;
    }

    // -------------------------------------------------------------------------
    // InventoryClickEvent — dispatch by holder type
    // -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        Inventory inv = event.getInventory();

        if (inv.getHolder() instanceof AdminGuiHolder) {
            handleCreatorClick(event, player);
        } else if (inv.getHolder() instanceof AdminDeleteGuiHolder deleteHolder) {
            handleDeleteClick(event, player, deleteHolder);
        }
    }

    // -------------------------------------------------------------------------
    // Creator GUI click handling (unchanged logic from original)
    // -------------------------------------------------------------------------

    private void handleCreatorClick(InventoryClickEvent event, Player player) {
        int slot = event.getRawSlot();

        // Block all shift-clicks.
        if (event.isShiftClick()) {
            event.setCancelled(true);
            return;
        }

        // Clicking in the player's own bottom inventory — allow freely.
        if (slot < 0 || slot >= ShopAdminCommand.GUI_SIZE) {
            return;
        }

        // ---- Confirm (slot 6) ----
        if (slot == ShopAdminCommand.SLOT_CONFIRM) {
            event.setCancelled(true);
            handleConfirm(player, event.getInventory());
            return;
        }

        // ---- Exit (slot 8) ----
        if (slot == ShopAdminCommand.SLOT_EXIT) {
            event.setCancelled(true);
            player.closeInventory();
            return;
        }

        // ---- Interactive item slots: 9, 11, 13 ----
        if (slot == ShopAdminCommand.SLOT_INPUT
                || slot == ShopAdminCommand.SLOT_INPUT2
                || slot == ShopAdminCommand.SLOT_OUTPUT) {
            handleInteractiveSlot(event, player, slot);
            return;
        }

        // ---- Everything else: locked ----
        event.setCancelled(true);
    }

    /**
     * Manually handles clicks on the three open item slots (9, 11, 13).
     *
     * Supported operations:
     *   cursor has item, slot empty → place (left = whole stack, right = one)
     *   cursor empty, slot has item → pick up (left = whole stack, right = one)
     *   both occupied               → swap
     */
    private void handleInteractiveSlot(InventoryClickEvent event, Player player, int slot) {
        event.setCancelled(true);

        Inventory inv      = event.getInventory();
        ItemStack slotItem = inv.getItem(slot);
        ItemStack cursor   = player.getItemOnCursor();

        boolean slotEmpty   = slotItem == null || slotItem.getType().isAir();
        boolean cursorEmpty = cursor   == null  || cursor.getType().isAir();

        if (slotEmpty && cursorEmpty) return;

        boolean rightClick = event.getClick() == ClickType.RIGHT;

        if (slotEmpty) {
            if (rightClick) {
                ItemStack place = cursor.clone();
                place.setAmount(1);
                inv.setItem(slot, place);
                if (cursor.getAmount() > 1) {
                    ItemStack rem = cursor.clone();
                    rem.setAmount(cursor.getAmount() - 1);
                    player.setItemOnCursor(rem);
                } else {
                    player.setItemOnCursor(null);
                }
            } else {
                inv.setItem(slot, cursor.clone());
                player.setItemOnCursor(null);
            }
            return;
        }

        if (cursorEmpty) {
            if (rightClick) {
                ItemStack pickedUp = slotItem.clone();
                pickedUp.setAmount(1);
                player.setItemOnCursor(pickedUp);
                if (slotItem.getAmount() > 1) {
                    ItemStack rem = slotItem.clone();
                    rem.setAmount(slotItem.getAmount() - 1);
                    inv.setItem(slot, rem);
                } else {
                    inv.setItem(slot, null);
                }
            } else {
                player.setItemOnCursor(slotItem.clone());
                inv.setItem(slot, null);
            }
            return;
        }

        // Both occupied — swap.
        inv.setItem(slot, cursor.clone());
        player.setItemOnCursor(slotItem.clone());
    }

    // -------------------------------------------------------------------------
    // Creator confirm handler
    // -------------------------------------------------------------------------

    private void handleConfirm(Player player, Inventory inv) {
        ItemStack raw1   = inv.getItem(ShopAdminCommand.SLOT_INPUT);
        ItemStack raw2   = inv.getItem(ShopAdminCommand.SLOT_INPUT2);
        ItemStack rawOut = inv.getItem(ShopAdminCommand.SLOT_OUTPUT);

        boolean has1   = raw1   != null && !raw1.getType().isAir();
        boolean has2   = raw2   != null && !raw2.getType().isAir();
        boolean hasOut = rawOut != null && !rawOut.getType().isAir();

        if (!has1 && !has2) {
            player.sendActionBar(Component.text("Place an item in the input slot.", NamedTextColor.RED));
            return;
        }
        if (!hasOut) {
            player.sendActionBar(Component.text("Place an item in the output slot.", NamedTextColor.RED));
            return;
        }

        final ItemStack ingredient1;
        final ItemStack ingredient2;

        if (!has1 && has2) {
            ingredient1 = raw2.clone();
            ingredient2 = null;
            inv.setItem(ShopAdminCommand.SLOT_INPUT2, null);
        } else {
            ingredient1 = raw1.clone();
            ingredient2 = has2 ? raw2.clone() : null;
            inv.setItem(ShopAdminCommand.SLOT_INPUT2, null);
        }

        ItemStack output = rawOut.clone();

        // Clear before close so InventoryCloseEvent doesn't double-return.
        inv.setItem(ShopAdminCommand.SLOT_INPUT,  null);
        inv.setItem(ShopAdminCommand.SLOT_OUTPUT, null);

        tradeManager.addTrade(ingredient1, ingredient2, output);

        giveOrDrop(player, ingredient1);
        if (ingredient2 != null) giveOrDrop(player, ingredient2);
        giveOrDrop(player, output);

        player.closeInventory();

        String desc = ingredient2 != null
                ? formatItem(ingredient1) + " + " + formatItem(ingredient2) + " → " + formatItem(output)
                : formatItem(ingredient1) + " → " + formatItem(output);
        player.sendMessage(Component.text("Trade added: " + desc, NamedTextColor.GREEN));
    }

    // -------------------------------------------------------------------------
    // Delete GUI click handling
    // -------------------------------------------------------------------------

    private void handleDeleteClick(InventoryClickEvent event,
                                   Player player,
                                   AdminDeleteGuiHolder holder) {
        event.setCancelled(true); // display-only — cancel everything

        if (event.isShiftClick()) return;

        int slot = event.getRawSlot();
        // Ignore clicks in the player's bottom inventory.
        if (slot < 0 || slot >= ShopAdminCommand.DELETE_GUI_SIZE) return;

        int page = holder.getPage();

        // ---- Delete button: column 8 in trade rows (slots 8, 17, 26, 35, 44) ----
        if (slot % 9 == 8 && slot < 45) {
            int row        = slot / 9;
            int tradeIndex = page * ShopAdminCommand.DELETE_TRADES_PER_PAGE + row; // 0-based

            List<TradeManager.TradePair> trades = tradeManager.getTrades();
            if (tradeIndex >= trades.size()) return; // stale display slot

            tradeManager.removeTrade(tradeIndex + 1); // 1-based
            player.sendMessage(Component.text("Removed trade #" + (tradeIndex + 1) + ".", NamedTextColor.GREEN));

            // Refresh GUI in-place.
            List<TradeManager.TradePair> updated = tradeManager.getTrades();
            int totalPages = ShopAdminCommand.computeTotalPages(updated.size());
            int newPage    = Math.min(page, Math.max(0, totalPages - 1));
            holder.setPage(newPage);
            ShopAdminCommand.fillDeleteGui(event.getInventory(), updated, newPage, totalPages);
            return;
        }

        // ---- Previous page ----
        if (slot == ShopAdminCommand.DELETE_NAV_PREV && page > 0) {
            int newPage = page - 1;
            holder.setPage(newPage);
            List<TradeManager.TradePair> trades = tradeManager.getTrades();
            ShopAdminCommand.fillDeleteGui(event.getInventory(), trades, newPage,
                    ShopAdminCommand.computeTotalPages(trades.size()));
            return;
        }

        // ---- Next page ----
        if (slot == ShopAdminCommand.DELETE_NAV_NEXT) {
            List<TradeManager.TradePair> trades = tradeManager.getTrades();
            int totalPages = ShopAdminCommand.computeTotalPages(trades.size());
            if (page < totalPages - 1) {
                int newPage = page + 1;
                holder.setPage(newPage);
                ShopAdminCommand.fillDeleteGui(event.getInventory(), trades, newPage, totalPages);
            }
        }
    }

    // -------------------------------------------------------------------------
    // InventoryCloseEvent — return items (creator GUI only)
    // -------------------------------------------------------------------------

    /**
     * Returns real items from the three open creator slots on any close reason.
     * Slots are pre-cleared by handleConfirm() before closeInventory() on confirm,
     * so this handler finds nothing to return after a successful add.
     *
     * Delete GUI is display-only — nothing to return.
     */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;

        Inventory inv = event.getInventory();
        if (!(inv.getHolder() instanceof AdminGuiHolder)) return;

        returnSlotItem(player, inv, ShopAdminCommand.SLOT_INPUT);
        returnSlotItem(player, inv, ShopAdminCommand.SLOT_INPUT2);
        returnSlotItem(player, inv, ShopAdminCommand.SLOT_OUTPUT);
    }

    private void returnSlotItem(Player player, Inventory inv, int slot) {
        ItemStack item = inv.getItem(slot);
        if (item == null || item.getType().isAir()) return;
        inv.setItem(slot, null);
        giveOrDrop(player, item);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void giveOrDrop(Player player, ItemStack item) {
        player.getInventory().addItem(item).values().forEach(leftover ->
                player.getWorld().dropItemNaturally(player.getLocation(), leftover));
    }

    private String formatItem(ItemStack item) {
        return capitalize(item.getType().getKey().getKey().replace('_', ' ')) + " x" + item.getAmount();
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        StringBuilder sb = new StringBuilder();
        for (String word : s.split(" ")) {
            if (!word.isEmpty()) sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(' ');
        }
        return sb.toString().trim();
    }
}
