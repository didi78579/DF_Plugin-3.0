package cjs.DF_Plugin.upgrade.profile.type;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.item.CustomItemFactory;
import cjs.DF_Plugin.upgrade.profile.IUpgradeableProfile;
import cjs.DF_Plugin.upgrade.specialability.ISpecialAbility;
import cjs.DF_Plugin.upgrade.specialability.impl.PassiveAbsorptionAbility;
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

public class ChestplateProfile implements IUpgradeableProfile {
    private static final ISpecialAbility PASSIVE_ABSORPTION_ABILITY = new PassiveAbsorptionAbility();
    private static final NamespacedKey ARMOR_MODIFIER_KEY = new NamespacedKey(DF_Main.getInstance(), "chestplate_armor");
    private static final NamespacedKey ARMOR_TOUGHNESS_MODIFIER_KEY = new NamespacedKey(DF_Main.getInstance(), "chestplate_armor_toughness");
    private static final NamespacedKey KNOCKBACK_RESISTANCE_MODIFIER_KEY = new NamespacedKey(DF_Main.getInstance(), "chestplate_knockback_resistance");
    private static final NamespacedKey SPEED_MODIFIER_KEY = new NamespacedKey(DF_Main.getInstance(), "chestplate_upgrade_movement_speed");
    private static final NamespacedKey HEALTH_MODIFIER_KEY = new NamespacedKey(DF_Main.getInstance(), "chestplate_upgrade_health");

    @Override
    public void applyAttributes(ItemStack item, ItemMeta meta, int level) {
        meta.removeAttributeModifier(Attribute.ARMOR);
        meta.removeAttributeModifier(Attribute.ARMOR_TOUGHNESS);
        meta.removeAttributeModifier(Attribute.KNOCKBACK_RESISTANCE);
        meta.removeAttributeModifier(Attribute.MOVEMENT_SPEED);
        meta.removeAttributeModifier(Attribute.MAX_HEALTH);

        applyBaseArmorAttributes(item.getType(), meta);

        double healthBonus = 1.0 * level;
        if (healthBonus > 0) {
            AttributeModifier healthModifier = new AttributeModifier(HEALTH_MODIFIER_KEY, healthBonus, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.CHEST);
            meta.addAttributeModifier(Attribute.MAX_HEALTH, healthModifier);
        }

        double soulSpeedBonus = meta.getPersistentDataContainer().getOrDefault(CustomItemFactory.BONUS_SPEED_KEY, PersistentDataType.DOUBLE, 0.0);
        if (soulSpeedBonus > 0) {
            AttributeModifier speedModifier = new AttributeModifier(SPEED_MODIFIER_KEY, soulSpeedBonus, AttributeModifier.Operation.MULTIPLY_SCALAR_1, EquipmentSlotGroup.CHEST);
            meta.addAttributeModifier(Attribute.MOVEMENT_SPEED, speedModifier);
        }

        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
    }

    @Override
    public List<String> getPassiveBonusLore(ItemStack item, int level) {
        if (level <= 0) {
            return Collections.emptyList();
        }
        List<String> lore = new ArrayList<>();
        double healthBonus = 1.0 * level;
        lore.add("§b추가 체력: +" + String.format("%.0f", healthBonus));
        lore.add("§b일반 피해 감소: " + level + "%");
        return lore;
    }

    @Override
    public List<String> getBaseStatsLore(ItemStack item, int level, double bonusDamage) {
        List<String> baseLore = new ArrayList<>();
        baseLore.add("§7몸에 있을 때:");

        double armor = getBaseArmorAttribute(item.getType(), "armor");
        double toughness = getBaseArmorAttribute(item.getType(), "toughness");
        double knockbackResistance = getBaseArmorAttribute(item.getType(), "knockbackResistance");
        double healthBonus = 1.0 * level;

        if (armor > 0) baseLore.add("§2 " + String.format("%.0f", armor) + " 방어");
        if (toughness > 0) baseLore.add("§2 " + String.format("%.0f", toughness) + " 방어 강도");
        if (knockbackResistance > 0) baseLore.add("§2 " + String.format("%.1f", knockbackResistance) + " 밀치기 저항");
        if (healthBonus > 0) baseLore.add("§2 +" + String.format("%.0f", healthBonus) + " 최대 체력");

        if (item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            double bonusCdr = meta.getPersistentDataContainer().getOrDefault(CustomItemFactory.BONUS_CDR_KEY, PersistentDataType.DOUBLE, 0.0);
            double bonusGenericReduction = meta.getPersistentDataContainer().getOrDefault(CustomItemFactory.BONUS_GENERIC_REDUCTION_KEY, PersistentDataType.DOUBLE, 0.0);
            double bonusSkillReduction = meta.getPersistentDataContainer().getOrDefault(CustomItemFactory.BONUS_SKILL_REDUCTION_KEY, PersistentDataType.DOUBLE, 0.0);
            double bonusSpeed = meta.getPersistentDataContainer().getOrDefault(CustomItemFactory.BONUS_SPEED_KEY, PersistentDataType.DOUBLE, 0.0);

            if (bonusCdr > 0) baseLore.add("§2 +" + String.format("%.0f", bonusCdr * 100) + "% 쿨타임 감소");
            if (bonusGenericReduction > 0) baseLore.add("§2 +" + String.format("%.0f", bonusGenericReduction * 100) + "% 일반 피해 감소");
            if (bonusSkillReduction > 0) baseLore.add("§2 +" + String.format("%.0f", bonusSkillReduction * 100) + "% 스킬 피해 감소");
            if (bonusSpeed > 0) baseLore.add("§2 +" + String.format("%.0f", bonusSpeed * 100) + "% 이동 속도");
        }

        return baseLore;
    }

    private void applyBaseArmorAttributes(Material material, ItemMeta meta) {
        double armor = 0, toughness = 0, knockbackResistance = 0;

        switch (material) {
            case LEATHER_CHESTPLATE -> armor = 3;
            case CHAINMAIL_CHESTPLATE, GOLDEN_CHESTPLATE -> armor = 5;
            case IRON_CHESTPLATE -> armor = 6;
            case DIAMOND_CHESTPLATE -> { armor = 8; toughness = 2; }
            case NETHERITE_CHESTPLATE -> { armor = 8; toughness = 3; knockbackResistance = 0.1; }
        }

        if (armor > 0) {
            meta.addAttributeModifier(Attribute.ARMOR, new AttributeModifier(ARMOR_MODIFIER_KEY, armor, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.CHEST));
        }
        if (toughness > 0) {
            meta.addAttributeModifier(Attribute.ARMOR_TOUGHNESS, new AttributeModifier(ARMOR_TOUGHNESS_MODIFIER_KEY, toughness, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.CHEST));
        }
        if (knockbackResistance > 0) {
            meta.addAttributeModifier(Attribute.KNOCKBACK_RESISTANCE, new AttributeModifier(KNOCKBACK_RESISTANCE_MODIFIER_KEY, knockbackResistance, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.CHEST));
        }
    }

    private double getBaseArmorAttribute(Material material, String type) {
        return switch (material) {
            case LEATHER_CHESTPLATE -> "armor".equals(type) ? 3 : 0;
            case CHAINMAIL_CHESTPLATE, GOLDEN_CHESTPLATE -> "armor".equals(type) ? 5 : 0;
            case IRON_CHESTPLATE -> "armor".equals(type) ? 6 : 0;
            case DIAMOND_CHESTPLATE -> switch (type) {
                case "armor" -> 8; case "toughness" -> 2; default -> 0;
            };
            case NETHERITE_CHESTPLATE -> switch (type) {
                case "armor" -> 8; case "toughness" -> 3; case "knockbackResistance" -> 0.1; default -> 0;
            };
            default -> 0;
        };
    }

    @Override
    public Optional<ISpecialAbility> getSpecialAbility() {
        return Optional.of(PASSIVE_ABSORPTION_ABILITY);
    }
}