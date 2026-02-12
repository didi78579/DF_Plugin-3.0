package cjs.DF_Plugin.upgrade.profile.type;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.item.CustomItemFactory;
import cjs.DF_Plugin.upgrade.profile.IUpgradeableProfile;
import cjs.DF_Plugin.upgrade.specialability.ISpecialAbility;
import cjs.DF_Plugin.upgrade.specialability.impl.SwordDanceAbility;
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

public class SwordProfile implements IUpgradeableProfile {

    private static final ISpecialAbility SWORD_DANCE_ABILITY = new SwordDanceAbility();
    private static final NamespacedKey ATTACK_DAMAGE_MODIFIER_KEY = new NamespacedKey(DF_Main.getInstance(), "sword_attack_damage");
    private static final NamespacedKey BASE_ATTACK_SPEED_MODIFIER_KEY = new NamespacedKey(DF_Main.getInstance(), "sword_attack_speed");

    @Override
    public void applyAttributes(ItemStack item, ItemMeta meta, int level) {
        meta.removeAttributeModifier(Attribute.ATTACK_DAMAGE);
        meta.removeAttributeModifier(Attribute.ATTACK_SPEED);

        double baseDamage = getBaseDamageModifier(item.getType());
        double bonusDamage = meta.getPersistentDataContainer().getOrDefault(CustomItemFactory.BONUS_DAMAGE_KEY, PersistentDataType.DOUBLE, 0.0);
        double totalDamage = baseDamage + bonusDamage;

        AttributeModifier damageModifier = new AttributeModifier(ATTACK_DAMAGE_MODIFIER_KEY, totalDamage, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.HAND);
        meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, damageModifier);

        AttributeModifier speedModifier;
        if (level >= 10) {
            speedModifier = new AttributeModifier(BASE_ATTACK_SPEED_MODIFIER_KEY, 1020, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.HAND);
        } else {
            final double baseAttackSpeedAttribute = -2.4;
            double speedBonusPerLevel = DF_Main.getInstance().getGameConfigManager().getConfig()
                    .getDouble("upgrade.generic-bonuses.sword.attack-speed-per-level", 0.3);
            double totalBonus = speedBonusPerLevel * level;
            double finalAttackSpeedModifierValue = baseAttackSpeedAttribute + totalBonus;
            speedModifier = new AttributeModifier(BASE_ATTACK_SPEED_MODIFIER_KEY, finalAttackSpeedModifierValue, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.HAND);
        }
        meta.addAttributeModifier(Attribute.ATTACK_SPEED, speedModifier);

        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
    }

    @Override
    public List<String> getPassiveBonusLore(ItemStack item, int level) {
        if (level <= 0) {
            return Collections.emptyList();
        }
        if (level >= 10) {
            return List.of("§b공격 속도: 최대");
        }
        double speedBonusPerLevel = DF_Main.getInstance().getGameConfigManager().getConfig()
                .getDouble("upgrade.generic-bonuses.sword.attack-speed-per-level", 0.3);
        double totalBonus = speedBonusPerLevel * level;
        return List.of("§b추가 공격속도: +" + String.format("%.1f", totalBonus));
    }

    @Override
    public List<String> getBaseStatsLore(ItemStack item, int level, double bonusDamage) {
        List<String> baseLore = new ArrayList<>();
        baseLore.add("§7주로 사용하는 손에 있을 때:");

        double damageModifierValue = getBaseDamageModifier(item.getType());
        double finalDamage = 1.0 + damageModifierValue + bonusDamage;
        baseLore.add("§2 " + String.format("%.1f", finalDamage) + " 공격 피해");

        if (level > 0) {
            final double baseAttackSpeedAttribute = -2.4;
            double speedBonusPerLevel = DF_Main.getInstance().getGameConfigManager().getConfig()
                    .getDouble("upgrade.generic-bonuses.sword.attack-speed-per-level", 0.3);
            double totalBonus = speedBonusPerLevel * level;
            double finalAttackSpeedModifierValue = baseAttackSpeedAttribute + totalBonus;
            double finalSpeed = 4.0 + finalAttackSpeedModifierValue;
            baseLore.add("§2 " + String.format("%.1f", finalSpeed) + " 공격 속도");
        }

        return baseLore;
    }

    private double getBaseDamageModifier(Material material) {
        return switch (material) {
            case WOODEN_SWORD, GOLDEN_SWORD -> 3.0;
            case STONE_SWORD -> 4.0;
            case IRON_SWORD -> 5.0;
            case DIAMOND_SWORD -> 6.0;
            case NETHERITE_SWORD -> 7.0;
            default -> 0.0;
        };
    }

    @Override
    public Optional<ISpecialAbility> getSpecialAbility() {
        return Optional.of(SWORD_DANCE_ABILITY);
    }
}