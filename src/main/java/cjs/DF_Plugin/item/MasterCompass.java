package cjs.DF_Plugin.item;

import cjs.DF_Plugin.DF_Main;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;

public class MasterCompass {

    private static final NamespacedKey MASTER_COMPASS_KEY = new NamespacedKey(DF_Main.getInstance(), "master_compass");

    public static ItemStack createMasterCompass(int amount) {
        ItemStack compass = new ItemStack(Material.COMPASS, amount);
        ItemMeta meta = compass.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§5마스터 컴퍼스");
            meta.setLore(Arrays.asList(
                    "§7우클릭 시 가장 가까운 §c적 가문§7의",
                    "§7주 파일런 방향으로 기운을 발산합니다.",
                    "§c(1회용)"
            ));
            meta.addEnchant(Enchantment.LURE, 1, false);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            meta.getPersistentDataContainer().set(MASTER_COMPASS_KEY, PersistentDataType.BYTE, (byte) 1);
            compass.setItemMeta(meta);
        }
        return compass;
    }

    public static ItemStack createMasterCompass() {
        return createMasterCompass(1);
    }

    public static boolean isMasterCompass(ItemStack item) {
        if (item == null || item.getType() != Material.COMPASS || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(MASTER_COMPASS_KEY, PersistentDataType.BYTE);
    }
}