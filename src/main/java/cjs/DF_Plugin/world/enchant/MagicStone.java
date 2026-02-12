package cjs.DF_Plugin.world.enchant;

import cjs.DF_Plugin.DF_Main;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;

public class MagicStone {

    private static final NamespacedKey MAGIC_STONE_KEY = new NamespacedKey(DF_Main.getInstance(), "magic_stone");

    /**
     * 마석 아이템을 생성합니다.
     * @param amount 생성할 개수
     * @return 생성된 마석 아이템
     */
    public static ItemStack createMagicStone(int amount) {
        ItemStack stone = new ItemStack(Material.NETHER_STAR, amount);
        ItemMeta meta = stone.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§6마석");
            meta.setLore(Arrays.asList("§7아이템에 무작위 마법을 부여합니다."));
            // 아이템을 빛나게 하는 효과 추가
            meta.addEnchant(Enchantment.LURE, 1, false);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

            meta.getPersistentDataContainer().set(MAGIC_STONE_KEY, PersistentDataType.BYTE, (byte) 1);
            stone.setItemMeta(meta);
        }
        return stone;
    }

    /**
     * 해당 아이템이 마석인지 확인합니다.
     */
    public static boolean isMagicStone(ItemStack item) {
        if (item == null || item.getType() != Material.NETHER_STAR || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(MAGIC_STONE_KEY, PersistentDataType.BYTE);
    }
}