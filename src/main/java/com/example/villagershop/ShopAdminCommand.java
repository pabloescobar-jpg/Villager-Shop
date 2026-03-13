package com.example.villagershop;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Handles /vs (and alias /villagershop) [create|delete|info].
 *
 * /vs           — shows command info (same as /vs info)
 * /vs create    — opens the trade-creator GUI (admin only)
 * /vs delete    — opens the trade-deletion GUI (admin only)
 * /vs info      — shows command and GUI explanations
 *
 * ── Creator GUI (/vs create) ─────────────────────────────────────────────────
 * 27-slot chest (3 rows):
 *
 *   Row 1:  [0=Input label]  [1=filler] [2=Input2 label] [3=filler] [4=Output label]
 *           [5=filler]       [6=Confirm ✔]               [7=filler] [8=Exit ✗]
 *   Row 2:  [9=INPUT open]  [10=filler] [11=INPUT2 open] [12=filler] [13=OUTPUT open] [14-17=filler]
 *   Row 3:  [18-26=filler]
 *
 * ── Delete GUI (/vs delete) ──────────────────────────────────────────────────
 * 54-slot chest (6 rows), paginated at 5 trades per page:
 *
 *   Rows 0-4 (slots 0-44): one trade per row
 *     col 0: input1 display    col 1: input2 display (or filler)
 *     col 2: filler            col 3: arrow indicator
 *     col 4: output display    cols 5-7: filler
 *     col 8: ✗ Delete button
 *
 *   Row 5 (slots 45-53): navigation
 *     slot 45: ← Previous Page (or filler on page 0)
 *     slot 49: Page N / M info
 *     slot 53: Next Page → (or filler on last page)
 *     all others: filler
 */
public class ShopAdminCommand implements CommandExecutor, TabCompleter {

    // ── Creator GUI slot constants (referenced by AdminGuiListener) ───────────
    static final int SLOT_INPUT_LABEL  = 0;
    static final int SLOT_INPUT2_LABEL = 2;
    static final int SLOT_OUTPUT_LABEL = 4;
    static final int SLOT_CONFIRM      = 6;
    static final int SLOT_EXIT         = 8;
    static final int SLOT_INPUT        = 9;
    static final int SLOT_INPUT2       = 11;
    static final int SLOT_OUTPUT       = 13;
    static final int GUI_SIZE          = 27;

    // ── Delete GUI constants (referenced by AdminGuiListener) ─────────────────
    static final int DELETE_GUI_SIZE        = 54;
    static final int DELETE_TRADES_PER_PAGE = 5;
    static final int DELETE_NAV_PREV        = 45;
    static final int DELETE_NAV_INFO        = 49;
    static final int DELETE_NAV_NEXT        = 53;

    private final VillagerShop plugin;
    private final TradeManager tradeManager;

    public ShopAdminCommand(VillagerShop plugin, TradeManager tradeManager) {
        this.plugin = plugin;
        this.tradeManager = tradeManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use /vs."));
            return true;
        }

        if (!hasAdminPerm(player)) {
            player.sendMessage(Component.text("You do not have permission to use /vs.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            handleInfo(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "create" -> openAdminGui(player);
            case "delete" -> {
                if (tradeManager.getTrades().isEmpty()) {
                    player.sendMessage(Component.text("No trades to delete.", NamedTextColor.YELLOW));
                    return true;
                }
                openDeleteGui(player, 0);
            }
            case "info" -> handleInfo(player);
            default -> player.sendMessage(Component.text("Usage: /vs [create|delete|info]", NamedTextColor.YELLOW));
        }
        return true;
    }

    private boolean hasAdminPerm(Player player) {
        String permNode = plugin.getConfig().getString("admin-permission", "villagershop.admin");
        return player.hasPermission(permNode) || player.isOp();
    }

    // -------------------------------------------------------------------------
    // Tab completion — admin-only; returns empty list for non-admins so
    // Paper shows nothing (no player-name suggestions).
    // -------------------------------------------------------------------------

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender,
                                                @NotNull Command command,
                                                @NotNull String label,
                                                @NotNull String[] args) {
        if (!(sender instanceof Player player)) return Collections.emptyList();
        if (!hasAdminPerm(player)) return Collections.emptyList();

        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            List<String> options = List.of("create", "delete", "info");
            List<String> matches = new ArrayList<>();
            for (String opt : options) {
                if (opt.startsWith(partial)) matches.add(opt);
            }
            return matches;
        }
        return Collections.emptyList();
    }

    // -------------------------------------------------------------------------
    // /vs info
    // -------------------------------------------------------------------------

    private void handleInfo(Player player) {
        player.sendMessage(Component.text("=== VillagerShop ===", NamedTextColor.GOLD));
        player.sendMessage(Component.text("/shop", NamedTextColor.YELLOW)
                .append(Component.text(" — Browse and perform trades.", NamedTextColor.WHITE)));
        player.sendMessage(Component.text("/vs info", NamedTextColor.YELLOW)
                .append(Component.text(" — Show this help.", NamedTextColor.WHITE)));
        player.sendMessage(Component.text("/vs create", NamedTextColor.YELLOW)
                .append(Component.text(" — Open the trade creator GUI. [Admin]", NamedTextColor.GRAY)));
        player.sendMessage(Component.text("/vs delete", NamedTextColor.YELLOW)
                .append(Component.text(" — Open the trade deletion GUI. [Admin]", NamedTextColor.GRAY)));
    }

    // -------------------------------------------------------------------------
    // /vs create — trade creator GUI
    // -------------------------------------------------------------------------

    private void openAdminGui(Player player) {
        Component title = Component.text("VillagerShop Admin");

        AdminGuiHolder holder = new AdminGuiHolder();
        Inventory inv = plugin.getServer().createInventory(holder, GUI_SIZE, title);
        holder.setInventory(inv);

        ItemStack filler = makeFiller();
        for (int i = 0; i < GUI_SIZE; i++) {
            inv.setItem(i, filler);
        }

        inv.setItem(SLOT_INPUT_LABEL,  makeInputLabel());
        inv.setItem(SLOT_INPUT2_LABEL, makeInput2Label());
        inv.setItem(SLOT_OUTPUT_LABEL, makeOutputLabel());
        inv.setItem(SLOT_CONFIRM,      makeConfirmButton());
        inv.setItem(SLOT_EXIT,         makeExitButton());

        inv.setItem(SLOT_INPUT,  null);
        inv.setItem(SLOT_INPUT2, null);
        inv.setItem(SLOT_OUTPUT, null);

        player.openInventory(inv);
    }

    // -------------------------------------------------------------------------
    // /vs delete — trade deletion GUI
    // -------------------------------------------------------------------------

    void openDeleteGui(Player player, int page) {
        List<TradeManager.TradePair> trades = tradeManager.getTrades();
        int totalPages = computeTotalPages(trades.size());
        page = Math.min(page, Math.max(0, totalPages - 1));

        AdminDeleteGuiHolder holder = new AdminDeleteGuiHolder(page);
        Inventory inv = plugin.getServer().createInventory(holder, DELETE_GUI_SIZE,
                Component.text("Delete Trades"));
        holder.setInventory(inv);

        fillDeleteGui(inv, trades, page, totalPages);
        player.openInventory(inv);
    }

    /**
     * Fills (or refreshes in-place) all slots of the delete GUI.
     * Static so AdminGuiListener can call it without holding a command instance.
     */
    static void fillDeleteGui(Inventory inv,
                              List<TradeManager.TradePair> trades,
                              int page,
                              int totalPages) {
        // Blank slate
        ItemStack filler = makeFiller();
        for (int i = 0; i < DELETE_GUI_SIZE; i++) inv.setItem(i, filler);

        // Trade rows (rows 0–4, slots 0–44)
        for (int row = 0; row < DELETE_TRADES_PER_PAGE; row++) {
            int tradeIndex = page * DELETE_TRADES_PER_PAGE + row;
            if (tradeIndex >= trades.size()) break;

            TradeManager.TradePair pair = trades.get(tradeIndex);
            int base = row * 9;

            inv.setItem(base,     pair.input().clone());
            if (pair.input2() != null) {
                inv.setItem(base + 1, pair.input2().clone());
            }
            // base+2: filler (already set)
            inv.setItem(base + 3, makeArrowItem());
            inv.setItem(base + 4, pair.output().clone());
            // base+5, 6, 7: filler (already set)
            inv.setItem(base + 8, makeDeleteButton(tradeIndex + 1));
        }

        // Navigation row (row 5, slots 45–53)
        if (page > 0) {
            inv.setItem(DELETE_NAV_PREV, makePrevButton());
        }
        inv.setItem(DELETE_NAV_INFO, makePageInfo(page + 1, totalPages, trades.size()));
        if (page < totalPages - 1) {
            inv.setItem(DELETE_NAV_NEXT, makeNextButton());
        }
    }

    static int computeTotalPages(int tradeCount) {
        return Math.max(1, (int) Math.ceil((double) tradeCount / DELETE_TRADES_PER_PAGE));
    }

    // -------------------------------------------------------------------------
    // Item helpers — creator GUI
    // -------------------------------------------------------------------------

    /** Black stained glass pane — silent filler. */
    static ItemStack makeFiller() {
        ItemStack item = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.customName(Component.empty());
        item.setItemMeta(meta);
        return item;
    }

    static ItemStack makeInputLabel() {
        ItemStack item = new ItemStack(Material.BLUE_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.customName(Component.text("Input Item", NamedTextColor.AQUA));
        item.setItemMeta(meta);
        return item;
    }

    static ItemStack makeInput2Label() {
        ItemStack item = new ItemStack(Material.BLUE_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.customName(Component.text("Input Item 2", NamedTextColor.AQUA));
        meta.lore(List.of(Component.text("Optional second ingredient", NamedTextColor.GRAY)));
        item.setItemMeta(meta);
        return item;
    }

    static ItemStack makeOutputLabel() {
        ItemStack item = new ItemStack(Material.BLUE_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.customName(Component.text("Output Item", NamedTextColor.AQUA));
        item.setItemMeta(meta);
        return item;
    }

    static ItemStack makeConfirmButton() {
        ItemStack item = new ItemStack(Material.GREEN_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.customName(Component.text("✔ Add Trade", NamedTextColor.GREEN));
        meta.lore(List.of(Component.text("Click to save this trade", NamedTextColor.GRAY)));
        item.setItemMeta(meta);
        return item;
    }

    static ItemStack makeExitButton() {
        ItemStack item = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.customName(Component.text("✗ Cancel", NamedTextColor.RED));
        meta.lore(List.of(Component.text("Close without saving", NamedTextColor.GRAY)));
        item.setItemMeta(meta);
        return item;
    }

    // -------------------------------------------------------------------------
    // Item helpers — delete GUI
    // -------------------------------------------------------------------------

    private static ItemStack makeArrowItem() {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        meta.customName(Component.text("→", NamedTextColor.WHITE));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack makeDeleteButton(int tradeNumber) {
        ItemStack item = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.customName(Component.text("✗ Delete Trade #" + tradeNumber, NamedTextColor.RED));
        meta.lore(List.of(Component.text("Click to remove this trade", NamedTextColor.GRAY)));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack makePrevButton() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.customName(Component.text("← Previous Page", NamedTextColor.YELLOW));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack makeNextButton() {
        ItemStack item = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.customName(Component.text("Next Page →", NamedTextColor.YELLOW));
        item.setItemMeta(meta);
        return item;
    }

    private static ItemStack makePageInfo(int page, int totalPages, int totalTrades) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.customName(Component.text("Page " + page + " / " + totalPages, NamedTextColor.WHITE));
        meta.lore(List.of(Component.text(totalTrades + " trade(s) total", NamedTextColor.GRAY)));
        item.setItemMeta(meta);
        return item;
    }
}
