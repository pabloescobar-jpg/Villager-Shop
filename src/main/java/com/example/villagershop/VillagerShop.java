package com.example.villagershop;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * VillagerShop — Paper 1.21.11 plugin.
 *
 * API notes (confirmed against paper-api 1.21.11-R0.1-20260228.115254-85):
 * - Player#openMerchant deprecated since 1.21.4; we use MenuType.MERCHANT.builder() instead.
 * - Bukkit.createMerchant(String/Component) deprecated since 1.21.4; we use Bukkit.createMerchant().
 * - ItemMeta#displayName(Component) obsolete since 1.21.4; we use ItemMeta#customName(Component).
 * - ItemMeta#lore(List<Component>) is the non-deprecated lore API.
 * - PlayerPurchaseEvent fires when a player completes a trade with a virtual (plugin) merchant.
 *   Vanilla handles item consumption and output delivery automatically; no interception required.
 * - MerchantRecipe full constructor: (result, uses, maxUses, experienceReward,
 *   villagerExperience, priceMultiplier, demand, specialPrice, ignoreDiscounts).
 * - Integer.MAX_VALUE for maxUses → trade never locks (infinite supply).
 * - api-version "1.21" in plugin.yml.
 */
public class VillagerShop extends JavaPlugin {

    private TradeManager tradeManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        tradeManager = new TradeManager(this);
        tradeManager.load();

        ShopCommand shopCmd = new ShopCommand(this, tradeManager);
        ShopAdminCommand adminCmd = new ShopAdminCommand(this, tradeManager);

        getCommand("shop").setExecutor(shopCmd);
        getCommand("vs").setExecutor(adminCmd);
        getCommand("vs").setTabCompleter(adminCmd);

        getServer().getPluginManager().registerEvents(new AdminGuiListener(tradeManager), this);
        // MerchantListener is registered for documentation — vanilla handles trade exchange.
        getServer().getPluginManager().registerEvents(new MerchantListener(), this);

        getLogger().info("VillagerShop enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("VillagerShop disabled.");
    }

    public TradeManager getTradeManager() {
        return tradeManager;
    }
}
