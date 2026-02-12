package cjs.DF_Plugin.item;

import cjs.DF_Plugin.DF_Main;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Arrays;
import java.util.List;

/**
 * 모든 커스텀 아이템의 생성과 식별을 담당하는 유틸리티 클래스입니다.
 */
public final class CustomItemFactory {

    // --- Item Identification Keys ---
    public static final NamespacedKey MAIN_PYLON_CORE_KEY = new NamespacedKey(DF_Main.getInstance(), "main_pylon_core");
    public static final NamespacedKey AUXILIARY_PYLON_CORE_KEY = new NamespacedKey(DF_Main.getInstance(), "auxiliary_pylon_core");
    public static final NamespacedKey RETURN_SCROLL_KEY = new NamespacedKey(DF_Main.getInstance(), "return_scroll");
    public static final NamespacedKey DEMON_SOUL_KEY = new NamespacedKey(DF_Main.getInstance(), "demon_soul");
    public static final NamespacedKey DRAGONS_HEART_KEY = new NamespacedKey(DF_Main.getInstance(), "dragons_heart");
    public static final NamespacedKey OBSIDIAN_POTION_KEY = new NamespacedKey(DF_Main.getInstance(), "obsidian_potion");

    // --- Item Stat Keys (for SpecialItemListener) ---
    public static final NamespacedKey BONUS_CDR_KEY = new NamespacedKey(DF_Main.getInstance(), "bonus_cdr");
    public static final NamespacedKey BONUS_GENERIC_REDUCTION_KEY = new NamespacedKey(DF_Main.getInstance(), "bonus_generic_reduction");
    public static final NamespacedKey BONUS_SKILL_REDUCTION_KEY = new NamespacedKey(DF_Main.getInstance(), "bonus_skill_reduction");
    public static final NamespacedKey BONUS_SPEED_KEY = new NamespacedKey(DF_Main.getInstance(), "bonus_speed");
    public static final NamespacedKey DEMON_SOUL_USES_KEY = new NamespacedKey(DF_Main.getInstance(), "demon_soul_uses");
    public static final NamespacedKey BONUS_DAMAGE_KEY = new NamespacedKey(DF_Main.getInstance(), "bonus_damage");

    private CustomItemFactory() {
        // 유틸리티 클래스는 인스턴스화할 수 없습니다.
    }

    // --- Item Creation Methods ---

    public static ItemStack createMainPylonCore() {
        return createItem(Material.BEACON, "§b§l파일런 코어", MAIN_PYLON_CORE_KEY, "§7가문의 중심이 되는 강력한 코어입니다.", "§7설치 시 가문의 영역이 생성됩니다.");
    }

    public static ItemStack createAuxiliaryPylonCore() {
        return createItem(Material.NETHER_STAR, "§b보조 파일런 코어", AUXILIARY_PYLON_CORE_KEY, "§7마계의 주인을 처치한 자에게 주어지는", "§7강력한 에너지의 집합체.");
    }

    public static ItemStack createReturnScroll() {
        return createItem(Material.GLOBE_BANNER_PATTERN, "§b귀환 주문서", RETURN_SCROLL_KEY, "§7가문 파일런 영역 내의 안전한 곳으로", "§7자신을 소환합니다.", "", "§e[사용법] §f손에 들고 우클릭하여 사용합니다.", "§c(시전 중 이동하거나 피격 시 취소)");
    }

    public static ItemStack createDemonSoul() {
        return createItem(Material.GHAST_TEAR, "§4악마의 영혼", DEMON_SOUL_KEY, "§7마계에서 타락한 영혼의 파편.", "§7음산한 기운을 내뿜는다.");
    }

    public static ItemStack createDragonsHeart() {
        return createItem(Material.DRAGON_EGG, "§d용의 심장", DRAGONS_HEART_KEY, "§7드래곤의 힘이 응축된 심장.", "§7커서에 들고 10강 이상의 무기를 클릭하면", "§7공격력을 영구적으로 1 증가시킵니다.");
    }

    public static ItemStack createObsidianPotion() {
        ItemStack potion = new ItemStack(Material.POTION);
        PotionMeta meta = (PotionMeta) potion.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§5흑요석 포션");
            meta.addCustomEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 216000, 0, false, true), true); // 3 hours
            meta.setColor(Color.fromRGB(75, 0, 130)); // Indigo
            meta.setLore(Arrays.asList("§7워든의 힘이 담긴 포션입니다.", "§7마시면 3시간 동안 지옥의 불길을 견딜 수 있습니다."));
            meta.getPersistentDataContainer().set(OBSIDIAN_POTION_KEY, PersistentDataType.BYTE, (byte) 1);
            potion.setItemMeta(meta);
        }
        return potion;
    }

    // --- Item Identification Methods ---

    public static boolean isMainPylonCore(ItemStack item) {
        return hasKey(item, MAIN_PYLON_CORE_KEY);
    }

    public static boolean isAuxiliaryPylonCore(ItemStack item) {
        return hasKey(item, AUXILIARY_PYLON_CORE_KEY);
    }

    public static boolean isReturnScroll(ItemStack item) {
        return hasKey(item, RETURN_SCROLL_KEY);
    }

    public static boolean isDemonSoul(ItemStack item) {
        return hasKey(item, DEMON_SOUL_KEY);
    }

    public static boolean isDragonsHeart(ItemStack item) {
        return hasKey(item, DRAGONS_HEART_KEY);
    }
    
    public static boolean isObsidianPotion(ItemStack item) {
        return hasKey(item, OBSIDIAN_POTION_KEY);
    }

    // --- Helper Methods ---

    private static ItemStack createItem(Material material, String name, NamespacedKey key, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(Arrays.asList(lore));
            meta.addEnchant(Enchantment.LURE, 1, false);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static boolean hasKey(ItemStack item, NamespacedKey key) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }

    public static boolean isArmor(Material material) {
        String name = material.name();
        return name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE") || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS");
    }

    public static boolean isWeapon(Material material) {
        String name = material.name();
        return name.endsWith("_SWORD") || name.endsWith("_AXE") || material == Material.TRIDENT || material == Material.MACE;
    }
}