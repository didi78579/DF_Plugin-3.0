package cjs.DF_Plugin.upgrade.profile.type;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.item.CustomItemFactory;
import cjs.DF_Plugin.upgrade.profile.IUpgradeableProfile;
import cjs.DF_Plugin.upgrade.specialability.ISpecialAbility;
import cjs.DF_Plugin.upgrade.specialability.impl.ToolBeltAbility;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

public class LeggingsProfile implements IUpgradeableProfile {

    private static final ISpecialAbility TOOL_BELT_ABILITY = new ToolBeltAbility();
    private static final NamespacedKey ARMOR_MODIFIER_KEY = new NamespacedKey(DF_Main.getInstance(), "leggings_armor");
    private static final NamespacedKey ARMOR_TOUGHNESS_MODIFIER_KEY = new NamespacedKey(DF_Main.getInstance(), "leggings_armor_toughness");
    private static final NamespacedKey KNOCKBACK_RESISTANCE_MODIFIER_KEY = new NamespacedKey(DF_Main.getInstance(), "leggings_knockback_resistance");
    private static final NamespacedKey HEALTH_MODIFIER_KEY = new NamespacedKey(DF_Main.getInstance(), "leggings_upgrade_health");

    /**
     * Applies attributes to the leggings based on their upgrade level and bonus stats.
     * @param item The ItemStack of the leggings.
     * @param meta The ItemMeta of the leggings.
     * @param level The upgrade level of the leggings.
     */
    @Override
    public void applyAttributes(ItemStack item, ItemMeta meta, int level) {
        meta.removeAttributeModifier(Attribute.ARMOR);
        meta.removeAttributeModifier(Attribute.ARMOR_TOUGHNESS);
        meta.removeAttributeModifier(Attribute.KNOCKBACK_RESISTANCE);
        meta.removeAttributeModifier(Attribute.MAX_HEALTH);

        applyBaseArmorAttributes(item.getType(), meta);

        // [수정] 악마의 영혼으로 부여된 추가 체력과 강화 레벨에 따른 체력을 합산합니다.
        double levelHealthBonus = (level >= 10) ? 10.0 : level; // 10강부터 기본 +10
        double soulHealthBonus = 0.0; // 악마의 영혼 보너스
        if (meta.getPersistentDataContainer().has(CustomItemFactory.BONUS_HEALTH_KEY)) {
            soulHealthBonus = meta.getPersistentDataContainer().getOrDefault(CustomItemFactory.BONUS_HEALTH_KEY, PersistentDataType.DOUBLE, 0.0);
        }
        double healthBonus = levelHealthBonus + soulHealthBonus;
        if (healthBonus > 0) {
            AttributeModifier healthModifier = new AttributeModifier(HEALTH_MODIFIER_KEY, healthBonus, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.LEGS);
            meta.addAttributeModifier(Attribute.MAX_HEALTH, healthModifier);
        }

        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
    }

    /**
     * Generates the lore for passive bonuses based on the upgrade level.
     * @param item The ItemStack of the leggings.
     * @param level The upgrade level of the leggings.
     * @return A list of strings representing the passive bonus lore.
     */
    @Override
    public List<String> getPassiveBonusLore(ItemStack item, int level) {
        if (level <= 0) return Collections.emptyList();
        List<String> lore = new ArrayList<>();

        // [수정] 10강부터 기본 스탯 +10을 표시합니다.
        if (level >= 10) {
            lore.add("§b추가 체력: +10");
            lore.add("§b스킬 피해 감소: +10%");
        }

        // [수정] 악마의 영혼으로 추가된 보너스 스탯을 함께 표시합니다.
        if (item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            double bonusHealth = meta.getPersistentDataContainer().getOrDefault(CustomItemFactory.BONUS_HEALTH_KEY, PersistentDataType.DOUBLE, 0.0);
            double bonusSkillReduction = meta.getPersistentDataContainer().getOrDefault(CustomItemFactory.BONUS_SKILL_REDUCTION_KEY, PersistentDataType.DOUBLE, 0.0);

            if (bonusHealth > 0) {
                lore.add("§d추가 체력: +" + String.format("%.0f", bonusHealth));
            }
            if (bonusSkillReduction > 0) {
                lore.add("§d스킬 피해 감소: +" + String.format("%.0f", bonusSkillReduction * 100) + "%");
            }
        }

        return lore;
    }

    /**
     * Generates the base stats lore for the leggings.
     * @param item The ItemStack of the leggings.
     * @param level The upgrade level of the leggings.
     * @param bonusDamage (Not used for leggings) Bonus damage value.
     * @return A list of strings representing the base stats lore.
     */
    @Override
    public List<String> getBaseStatsLore(ItemStack item, int level, double bonusDamage) {
        List<String> baseLore = new ArrayList<>();
        baseLore.add("§7다리에 있을 때:");

        double armor = getBaseArmorAttribute(item.getType(), "armor");
        double toughness = getBaseArmorAttribute(item.getType(), "toughness");
        double knockbackResistance = getBaseArmorAttribute(item.getType(), "knockbackResistance");

        if (armor > 0) baseLore.add("§2 " + String.format("%.0f", armor) + " 방어");
        if (toughness > 0) baseLore.add("§2 " + String.format("%.0f", toughness) + " 방어 강도");
        if (knockbackResistance > 0) baseLore.add("§2 " + String.format("%.1f", knockbackResistance) + " 밀치기 저항");

        return baseLore;
    }

    /**
     * Applies the base armor attributes to the leggings based on their material.
     * @param material The material of the leggings.
     * @param meta The ItemMeta to apply the attributes to.
     */
    private void applyBaseArmorAttributes(Material material, ItemMeta meta) {
        double armor = getBaseArmorAttribute(material, "armor");
        double toughness = getBaseArmorAttribute(material, "toughness");
        double knockbackResistance = getBaseArmorAttribute(material, "knockbackResistance");

        if (armor > 0) {
            meta.addAttributeModifier(Attribute.ARMOR, new AttributeModifier(ARMOR_MODIFIER_KEY, armor, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.LEGS));
        }
        if (toughness > 0) {
            meta.addAttributeModifier(Attribute.ARMOR_TOUGHNESS, new AttributeModifier(ARMOR_TOUGHNESS_MODIFIER_KEY, toughness, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.LEGS));
        }
        if (knockbackResistance > 0) {
            meta.addAttributeModifier(Attribute.KNOCKBACK_RESISTANCE, new AttributeModifier(KNOCKBACK_RESISTANCE_MODIFIER_KEY, knockbackResistance, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.LEGS));
        }
    }

    /**
     * Gets the base attribute value for a given legging material and attribute type.
     * @param material The material of the leggings.
     * @param type The type of attribute ("armor", "toughness", "knockbackResistance").
     * @return The base value of the attribute.
     */
    private double getBaseArmorAttribute(Material material, String type) {
        return switch (material) {
            case LEATHER_LEGGINGS -> "armor".equals(type) ? 2 : 0;
            case CHAINMAIL_LEGGINGS -> "armor".equals(type) ? 4 : 0;
            case GOLDEN_LEGGINGS -> "armor".equals(type) ? 3 : 0;
            case IRON_LEGGINGS -> "armor".equals(type) ? 5 : 0;
            case DIAMOND_LEGGINGS -> switch (type) { case "armor" -> 6; case "toughness" -> 2; default -> 0; };
            case NETHERITE_LEGGINGS -> switch (type) { case "armor" -> 6; case "toughness" -> 3; case "knockbackResistance" -> 0.1; default -> 0; };
            default -> 0;
        };
    }

    @Override
    public Optional<ISpecialAbility> getSpecialAbility() {
        return Optional.of(TOOL_BELT_ABILITY);
    }
}