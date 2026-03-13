package com.example.villagershop;

import io.papermc.paper.event.player.PlayerPurchaseEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

/**
 * Listens to merchant trade events for the /shop virtual merchant.
 *
 * --- Trade completion event chain in Paper 1.21.11 ---
 *
 * Confirmed from API inspection (paper-api 1.21.11-R0.1-20260228.115254-85):
 *
 * - {@link PlayerPurchaseEvent} fires when a player completes a trade with a
 *   virtual merchant (one created by Bukkit.createMerchant(), NOT an actual
 *   Villager entity). This is the correct event for our shop.
 *
 * - {@link io.papermc.paper.event.player.PlayerTradeEvent} is the subclass for
 *   trades with actual villager/wandering-trader entities; it extends
 *   PlayerPurchaseEvent but carries an AbstractVillager reference. Our virtual
 *   merchant does NOT fire PlayerTradeEvent.
 *
 * - {@link org.bukkit.event.inventory.TradeSelectEvent} fires when a player
 *   clicks a trade in the sidebar (changing the selected recipe). It is NOT
 *   the "trade completed" event.
 *
 * - The vanilla merchant system handles item consumption (taking the input from
 *   the player) and output delivery (putting the result into the player's
 *   inventory) AUTOMATICALLY. This plugin does NOT need to manually move items.
 *
 * - Since maxUses = Integer.MAX_VALUE, the trade's internal `uses` counter will
 *   increment on each trade but will never reach maxUses, so the trade is never
 *   locked. PlayerPurchaseEvent#willIncreaseTradeUses() returns true by default;
 *   we do NOT override this because the increments are harmless against MAX_VALUE.
 *
 * - Guard: The plan asks us to confirm that a player cannot lose diamonds without
 *   receiving output. Because we do not cancel or interfere with PlayerPurchaseEvent,
 *   vanilla mechanics run normally. If we needed to cancel a trade (e.g., for a
 *   permission check), we would call event.setCancelled(true) here. In the current
 *   design we have no reason to cancel, so the event listener is present only for
 *   documentation and future extensibility.
 *
 * - openMerchant / MenuType.MERCHANT.builder().build() must be called on the main
 *   server thread. Commands are dispatched on the main thread by Paper, so
 *   ShopCommand#openShop runs on the correct thread without a scheduler guard.
 *   If any future async path calls openShop, a Bukkit.getScheduler().runTask()
 *   guard should be added there.
 */
public class MerchantListener implements Listener {

    /**
     * Fired when a player completes a trade with the virtual shop merchant.
     *
     * Currently no-op: vanilla handles item exchange automatically.
     * Logged at MONITOR priority (after other handlers) to observe final state.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerPurchase(PlayerPurchaseEvent event) {
        // No intervention required. Vanilla handles:
        //   1. Removing the input item from the merchant inventory.
        //   2. Placing the output item into the player's inventory (or dropping it).
        //   3. Incrementing recipe.uses (harmless against Integer.MAX_VALUE maxUses).
        //
        // If a future feature requires custom logic (e.g. logging, analytics,
        // permission checks per-trade), add it here.
    }
}
