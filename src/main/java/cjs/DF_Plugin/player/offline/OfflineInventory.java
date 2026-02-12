package cjs.DF_Plugin.player.offline;

import org.bukkit.inventory.ItemStack;

public record OfflineInventory(ItemStack[] main, ItemStack[] armor, ItemStack offHand, ItemStack playerHead) {
}