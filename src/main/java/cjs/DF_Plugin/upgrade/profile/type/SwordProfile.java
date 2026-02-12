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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

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
            // 10강일 때 '무한' 공격 속도 적용
            speedModifier = new AttributeModifier(BASE_ATTACK_SPEED_MODIFIER_KEY, 1024, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.HAND);
        } else {
            // 1~9강일 때는 레벨 비례 보너스 적용
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
        return Collections.emptyList();
    }

    @Override
    public List<String> getBaseStatsLore(ItemStack item, int level, double bonusDamage) {
        List<String> lore = new ArrayList<>();
        lore.add("§7주 손에 있을 때:");
        lore.add(String.format(" §2%.1f 공격력", getBaseDamageModifier(item.getType()) + 1.0 + bonusDamage)); // 아이템 기본 공격력 1을 더함

        // 10강 미만일 때만 공격 속도 로어를 표시
        if (level < 10) {
            double baseAttackSpeed = 1.6; // 기본 공격 속도
            double speedBonusPerLevel = DF_Main.getInstance().getGameConfigManager().getConfig()
                    .getDouble("upgrade.generic-bonuses.sword.attack-speed-per-level", 0.3);
            double finalAttackSpeed = baseAttackSpeed + (speedBonusPerLevel * level);
            lore.add(String.format(" §2%.2f 공격 속도", finalAttackSpeed));
        }

        return lore;
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