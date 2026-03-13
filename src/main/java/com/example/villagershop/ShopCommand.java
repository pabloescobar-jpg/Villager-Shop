package com.example.villagershop;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Merchant;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.MenuType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles the /shop command.
 *
 * Opens the vanilla merchant GUI populated from trades.yml.
 *
 * API confirmed (paper-api 1.21.11-R0.1-20260228.115254-85):
 * - Bukkit.createMerchant() (no args) — non-deprecated path since 1.21.4.
 * - MenuType.MERCHANT is Typed<MerchantView, MerchantInventoryViewBuilder<MerchantView>>.
 * - MerchantInventoryViewBuilder has .title(Component), .merchant(Merchant), .build(HumanEntity).
 * - Player.openInventory(InventoryView) opens the built view.
 * - MerchantRecipe(result, uses, maxUses, expReward, villagerExp, priceMultiplier,
 *   demand, specialPrice, ignoreDiscounts).
 * - recipe.addIngredient(ItemStack) accepts exactly one ingredient; second slot is vanilla default.
 * - Integer.MAX_VALUE for maxUses → never locks (infinite supply).
 * - openMerchant must be called on the main server thread (all Bukkit inventory ops are main-thread).
 */
public class ShopCommand implements CommandExecutor {

    private final VillagerShop plugin;
    private final TradeManager tradeManager;

    public ShopCommand(VillagerShop plugin, TradeManager tradeManager) {
        this.plugin = plugin;
        this.tradeManager = tradeManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender,
                             @NotNull Command command,
                             @NotNull String label,
                             @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Only players can use /shop."));
            return true;
        }

        // All Bukkit inventory operations must be on the main thread.
        // Commands are always dispatched on the main thread by Paper, so no scheduler guard needed.
        openShop(player);
        return true;
    }

    private void openShop(Player player) {
        String titleStr = plugin.getConfig().getString("shop-title", "Shop");
        Component title = MiniMessage.miniMessage().deserialize(titleStr);

        // Confirmed: Bukkit.createMerchant() is the non-deprecated API since 1.21.4.
        // The title is now set on the MenuType builder, not on the Merchant object.
        Merchant merchant = Bukkit.createMerchant();

        List<TradeManager.TradePair> tradeList = tradeManager.getTrades();
        List<MerchantRecipe> recipes = new ArrayList<>();

        if (tradeList.isEmpty()) {
            // Guard: ensure an empty merchant doesn't produce a blank/broken GUI.
            // We add one placeholder trade so the GUI has at least one entry.
            recipes.add(makePlaceholderRecipe());
        } else {
            for (TradeManager.TradePair pair : tradeList) {
                recipes.add(makeRecipe(pair));
            }
        }

        merchant.setRecipes(recipes);

        // Confirmed: MenuType.MERCHANT is Typed<MerchantView, MerchantInventoryViewBuilder<MerchantView>>.
        // builder() returns MerchantInventoryViewBuilder; .merchant() sets the Merchant;
        // .title() sets the title Component; .build(player) creates the view.
        // player.openInventory(view) opens it for the player.
        var view = MenuType.MERCHANT.builder()
                .title(title)
                .merchant(merchant)
                .build(player);
        player.openInventory(view);
    }

    /**
     * Creates a MerchantRecipe for one trade pair.
     *
     * Full constructor (confirmed from MerchantRecipe.java):
     *   MerchantRecipe(result, uses, maxUses, experienceReward,
     *                  villagerExperience, priceMultiplier,
     *                  demand, specialPrice, ignoreDiscounts)
     *
     * - uses = 0 (fresh)
     * - maxUses = Integer.MAX_VALUE (infinite supply)
     * - experienceReward = false (no XP on trade)
     * - villagerExperience = 0 (irrelevant for virtual merchant)
     * - priceMultiplier = 0.0f (no dynamic pricing)
     * - demand = 0 (no demand scaling)
     * - specialPrice = 0 (no reputation pricing)
     * - ignoreDiscounts = true (Paper-added field; no discount mechanics)
     */
    private MerchantRecipe makeRecipe(TradeManager.TradePair pair) {
        // Confirmed: MerchantRecipe result must not be empty (Preconditions check in source).
        MerchantRecipe recipe = new MerchantRecipe(
                pair.output().clone(),
                0,
                Integer.MAX_VALUE,
                false,
                0,
                0.0f,
                0,
                0,
                true
        );
        // Confirmed: addIngredient() accepts up to 2 ingredients.
        // input2 is null for single-ingredient trades; add it only when present.
        recipe.addIngredient(pair.input().clone());
        if (pair.input2() != null) {
            recipe.addIngredient(pair.input2().clone());
        }
        return recipe;
    }

    /**
     * Placeholder trade shown when no trades are configured.
     * Uses PAPER as both input and output with a descriptive name.
     *
     * Guards: MerchantRecipe result must not be empty — PAPER is always valid.
     * Confirmed: Material.PAPER exists in 1.21.11 (standard material since early versions).
     */
    private MerchantRecipe makePlaceholderRecipe() {
        ItemStack placeholderItem = new ItemStack(Material.PAPER);
        ItemMeta meta = placeholderItem.getItemMeta();
        // Confirmed: ItemMeta.customName(Component) is the non-deprecated API since 1.21.4.
        meta.customName(Component.text("No trades configured"));
        placeholderItem.setItemMeta(meta);

        MerchantRecipe recipe = new MerchantRecipe(
                placeholderItem,
                0,
                Integer.MAX_VALUE,
                false,
                0,
                0.0f,
                0,
                0,
                true
        );
        recipe.addIngredient(placeholderItem.clone());
        return recipe;
    }
}
