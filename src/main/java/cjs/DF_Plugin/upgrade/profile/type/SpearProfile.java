package cjs.DF_Plugin.upgrade.profile.type;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.item.CustomItemFactory;
import cjs.DF_Plugin.upgrade.profile.IUpgradeableProfile;
import cjs.DF_Plugin.upgrade.specialability.ISpecialAbility;
import cjs.DF_Plugin.upgrade.specialability.SpecialAbilityManager;
import cjs.DF_Plugin.upgrade.specialability.impl.HarpoonAbility;
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

public class SpearProfile implements IUpgradeableProfile {

    private static final ISpecialAbility HARPOON_ABILITY = new HarpoonAbility();
    private static final NamespacedKey ATTACK_DAMAGE_MODIFIER_KEY = new NamespacedKey(DF_Main.getInstance(), "spear_attack_damage");
    private static final NamespacedKey ATTACK_SPEED_MODIFIER_KEY = new NamespacedKey(DF_Main.getInstance(), "spear_attack_speed");

    @Override
    public void applyAttributes(ItemStack item, ItemMeta meta, int level) {
        meta.removeAttributeModifier(Attribute.ATTACK_DAMAGE);
        meta.removeAttributeModifier(Attribute.ATTACK_SPEED);

        double baseDamage = getBaseDamageModifier(item.getType());
        double bonusDamage = meta.getPersistentDataContainer().getOrDefault(CustomItemFactory.BONUS_DAMAGE_KEY, PersistentDataType.DOUBLE, 0.0);
        double totalDamage = baseDamage + bonusDamage;

        AttributeModifier damageModifier = new AttributeModifier(ATTACK_DAMAGE_MODIFIER_KEY, totalDamage, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.HAND);
        meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, damageModifier);

        final double baseAttackSpeedAttribute = getBaseAttackSpeedAttribute(item.getType());
        AttributeModifier speedModifier = new AttributeModifier(ATTACK_SPEED_MODIFIER_KEY, baseAttackSpeedAttribute, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.HAND);
        meta.addAttributeModifier(Attribute.ATTACK_SPEED, speedModifier);

        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
    }

    @Override
    public List<String> getBaseStatsLore(ItemStack item, int level, double bonusDamage) {
        List<String> lore = new ArrayList<>();
        lore.add("§7주 손에 있을 때:");
        lore.add(String.format(" §2%.1f 공격력", getBaseDamageModifier(item.getType()) + 1.0 + bonusDamage));
        lore.add(String.format(" §2%.2f 공격 속도", 4.0 + getBaseAttackSpeedAttribute(item.getType())));
        return lore;
    }

    @Override
    public List<String> getPassiveBonusLore(ItemStack item, int level) {
        if (level <= 0) {
            return Collections.emptyList();
        }
        List<String> lore = new ArrayList<>();
        double maxGauge = level * SpecialAbilityManager.GAUGE_PER_LEVEL;
        lore.add("§b최대 기력: " + String.format("%.0f", maxGauge));
        return lore;
    }

    private double getBaseDamageModifier(Material material) {
        String prefix = getMaterialPrefix(material);
        return switch (prefix) {
            case "WOODEN", "GOLDEN" -> 0.0;
            case "STONE", "COPPER" -> 1.0;
            case "IRON" -> 2.0;
            case "DIAMOND" -> 3.0;
            case "NETHERITE", "TRIDENT" -> 4.0;
            default -> 0.0;
        };
    }

    private double getBaseAttackSpeedAttribute(Material material) {
        String prefix = getMaterialPrefix(material);
        return switch (prefix) {
            case "WOODEN" -> -2.46;
            case "STONE" -> -2.67;
            case "COPPER" -> -2.82;
            case "IRON", "GOLDEN" -> -2.95;
            case "DIAMOND" -> -3.05;
            case "NETHERITE" -> -3.15;
            default -> -3.13;
        };
    }

    private String getMaterialPrefix(Material material) {
        String name = material.name();
        int underscoreIndex = name.indexOf('_');
        if (underscoreIndex > 0) {
            return name.substring(0, underscoreIndex);
        }
        return name;
    }

    @Override
    public Optional<ISpecialAbility> getSpecialAbility() {
        return Optional.of(HARPOON_ABILITY);
    }
}