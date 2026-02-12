package cjs.DF_Plugin.upgrade.item;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.item.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public class UpgradeItems {

    private static final NamespacedKey UPGRADE_STONE_KEY = new NamespacedKey(DF_Main.getInstance(), "upgrade_stone");

    public static ItemStack createUpgradeStone(int amount) {
        ItemStack stone = new ItemBuilder(Material.ECHO_SHARD)
                .withName("§d강화석")
                .withLore("§7장비를 강화는 신비한 광물입니다.")
                .withPDCString(UPGRADE_STONE_KEY, "true")
                .build();
        stone.setAmount(amount);
        return stone;
    }

    public static boolean isUpgradeStone(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        return meta.getPersistentDataContainer().has(UPGRADE_STONE_KEY, PersistentDataType.STRING);
    }
}