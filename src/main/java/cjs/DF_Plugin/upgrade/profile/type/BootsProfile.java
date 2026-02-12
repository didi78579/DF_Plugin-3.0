package cjs.DF_Plugin.upgrade.profile.type;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.item.CustomItemFactory;
import cjs.DF_Plugin.upgrade.profile.IUpgradeableProfile;
import cjs.DF_Plugin.upgrade.specialability.ISpecialAbility;
import cjs.DF_Plugin.upgrade.specialability.impl.DoubleJumpAbility;
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

public class BootsProfile implements IUpgradeableProfile {

    private static final ISpecialAbility DOUBLE_JUMP_ABILITY = new DoubleJumpAbility();
    private static final NamespacedKey ARMOR_MODIFIER_KEY = new NamespacedKey(DF_Main.getInstance(), "boots_armor");
    private static final NamespacedKey ARMOR_TOUGHNESS_MODIFIER_KEY = new NamespacedKey(DF_Main.getInstance(), "boots_armor_toughness");
    private static final NamespacedKey KNOCKBACK_RESISTANCE_MODIFIER_KEY = new NamespacedKey(DF_Main.getInstance(), "boots_knockback_resistance");
    private static final NamespacedKey SPEED_MODIFIER_KEY = new NamespacedKey(DF_Main.getInstance(), "boots_upgrade_movement_speed");
    private static final NamespacedKey HEALTH_MODIFIER_KEY = new NamespacedKey(DF_Main.getInstance(), "boots_upgrade_health");

    @Override
    public void applyAttributes(org.bukkit.inventory.ItemStack item, ItemMeta meta, int level) {
        meta.removeAttributeModifier(Attribute.ARMOR);
        meta.removeAttributeModifier(Attribute.ARMOR_TOUGHNESS);
        meta.removeAttributeModifier(Attribute.KNOCKBACK_RESISTANCE);
        meta.removeAttributeModifier(Attribute.MOVEMENT_SPEED);
        meta.removeAttributeModifier(Attribute.MAX_HEALTH);

        applyBaseArmorAttributes(item.getType(), meta);

        double healthBonus = 1.0 * level;
        if (healthBonus > 0) {
            AttributeModifier healthModifier = new AttributeModifier(HEALTH_MODIFIER_KEY, healthBonus, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.FEET);
            meta.addAttributeModifier(Attribute.MAX_HEALTH, healthModifier);
        }

        double levelSpeedBonus = 0.01 * level;
        double soulSpeedBonus = meta.getPersistentDataContainer().getOrDefault(CustomItemFactory.BONUS_SPEED_KEY, PersistentDataType.DOUBLE, 0.0);
        double totalSpeedBonus = levelSpeedBonus + soulSpeedBonus;

        if (totalSpeedBonus > 0) {
            AttributeModifier speedModifier = new AttributeModifier(SPEED_MODIFIER_KEY, totalSpeedBonus, AttributeModifier.Operation.MULTIPLY_SCALAR_1, EquipmentSlotGroup.FEET);
            meta.addAttributeModifier(Attribute.MOVEMENT_SPEED, speedModifier);
        }

        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
    }

    @Override
    public List<String> getPassiveBonusLore(org.bukkit.inventory.ItemStack item, int level) {
        if (level <= 0) return Collections.emptyList();
        List<String> lore = new ArrayList<>();
        double healthBonus = 1.0 * level;
        lore.add("§b추가 체력: +" + String.format("%.0f", healthBonus));

        if (item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            double bonusCdr = meta.getPersistentDataContainer().getOrDefault(CustomItemFactory.BONUS_CDR_KEY, PersistentDataType.DOUBLE, 0.0);
            double bonusGenericReduction = meta.getPersistentDataContainer().getOrDefault(CustomItemFactory.BONUS_GENERIC_REDUCTION_KEY, PersistentDataType.DOUBLE, 0.0);
            double bonusSkillReduction = meta.getPersistentDataContainer().getOrDefault(CustomItemFactory.BONUS_SKILL_REDUCTION_KEY, PersistentDataType.DOUBLE, 0.0);
            double bonusSpeed = meta.getPersistentDataContainer().getOrDefault(CustomItemFactory.BONUS_SPEED_KEY, PersistentDataType.DOUBLE, 0.0);

            if (bonusCdr > 0) lore.add("§b쿨타임 감소: +" + String.format("%.0f", bonusCdr * 100) + "%");
            if (bonusGenericReduction > 0) lore.add("§b일반 피해 감소: +" + String.format("%.0f", bonusGenericReduction * 100) + "%");
            if (bonusSkillReduction > 0) lore.add("§b스킬 피해 감소: +" + String.format("%.0f", bonusSkillReduction * 100) + "%");

            double totalSpeedBonus = (0.01 * level) + bonusSpeed;
            lore.add("§b이동 속도: +" + String.format("%.0f", totalSpeedBonus * 100) + "%");
        } else {
            double speedBonus = 0.01 * level;
            lore.add("§b이동 속도: +" + String.format("%.0f", speedBonus * 100) + "%");
        }

        return lore;
    }

    @Override
    public List<String> getBaseStatsLore(org.bukkit.inventory.ItemStack item, int level, double bonusDamage) {
        List<String> baseLore = new ArrayList<>();
        baseLore.add("§7발에 있을 때:");

        double armor = getBaseArmorAttribute(item.getType(), "armor");
        double toughness = getBaseArmorAttribute(item.getType(), "toughness");
        double knockbackResistance = getBaseArmorAttribute(item.getType(), "knockbackResistance");

        if (armor > 0) baseLore.add("§2 " + String.format("%.0f", armor) + " 방어");
        if (toughness > 0) baseLore.add("§2 " + String.format("%.0f", toughness) + " 방어 강도");
        if (knockbackResistance > 0) baseLore.add("§2 " + String.format("%.1f", knockbackResistance) + " 밀치기 저항");

        return baseLore;
    }

    private void applyBaseArmorAttributes(Material material, ItemMeta meta) {
        double armor = getBaseArmorAttribute(material, "armor");
        double toughness = getBaseArmorAttribute(material, "toughness");
        double knockbackResistance = getBaseArmorAttribute(material, "knockbackResistance");

        if (armor > 0) {
            AttributeModifier armorModifier = new AttributeModifier(ARMOR_MODIFIER_KEY, armor, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.FEET);
            meta.addAttributeModifier(Attribute.ARMOR, armorModifier);
        }
        if (toughness > 0) {
            AttributeModifier toughnessModifier = new AttributeModifier(ARMOR_TOUGHNESS_MODIFIER_KEY, toughness, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.FEET);
            meta.addAttributeModifier(Attribute.ARMOR_TOUGHNESS, toughnessModifier);
        }
        if (knockbackResistance > 0) {
            AttributeModifier knockbackModifier = new AttributeModifier(KNOCKBACK_RESISTANCE_MODIFIER_KEY, knockbackResistance, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.FEET);
            meta.addAttributeModifier(Attribute.KNOCKBACK_RESISTANCE, knockbackModifier);
        }
    }

    private double getBaseArmorAttribute(Material material, String type) {
        return switch (material) {
            case LEATHER_BOOTS, CHAINMAIL_BOOTS, GOLDEN_BOOTS -> "armor".equals(type) ? 1 : 0;
            case IRON_BOOTS -> "armor".equals(type) ? 2 : 0;
            case DIAMOND_BOOTS -> switch (type) {
                case "armor" -> 3; case "toughness" -> 2; default -> 0;
            };
            case NETHERITE_BOOTS -> switch (type) {
                case "armor" -> 3; case "toughness" -> 3; case "knockbackResistance" -> 0.1; default -> 0;
            };
            default -> 0;
        };
    }

    @Override
    public Optional<ISpecialAbility> getSpecialAbility() {
        return Optional.of(DOUBLE_JUMP_ABILITY);
    }
}