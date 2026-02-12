package cjs.DF_Plugin.util;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;

public class InventoryUtils {

    /**
     * 플레이어의 인벤토리에 아이템을 추가하고, 공간이 부족할 경우 바닥에 드롭합니다.
     * @param player 대상 플레이어
     * @param items 추가할 아이템들
     */
    public static void giveOrDropItems(Player player, ItemStack... items) {
        HashMap<Integer, ItemStack> leftovers = player.getInventory().addItem(items);
        if (!leftovers.isEmpty()) {
            player.sendMessage(PluginUtils.colorize("&c인벤토리에 공간이 부족하여 일부 아이템을 바닥에 드롭했습니다."));
            for (ItemStack leftover : leftovers.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }
    }
}