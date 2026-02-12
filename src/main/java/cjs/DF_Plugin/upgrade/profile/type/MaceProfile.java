package cjs.DF_Plugin.upgrade.profile.type;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.item.CustomItemFactory;
import cjs.DF_Plugin.upgrade.profile.IUpgradeableProfile;
import cjs.DF_Plugin.upgrade.specialability.ISpecialAbility;
import cjs.DF_Plugin.upgrade.specialability.impl.WindChargeAbility;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class MaceProfile implements IUpgradeableProfile {

    private static final ISpecialAbility WIND_CHARGE_ABILITY = new WindChargeAbility();
    private static final NamespacedKey ATTACK_DAMAGE_MODIFIER_KEY = new NamespacedKey(DF_Main.getInstance(), "mace_attack_damage");
    private static final NamespacedKey ATTACK_SPEED_MODIFIER_KEY = new NamespacedKey(DF_Main.getInstance(), "mace_attack_speed");

    @Override
    public void applyAttributes(ItemStack item, ItemMeta meta, int level) {
        meta.removeAttributeModifier(Attribute.ATTACK_DAMAGE);
        meta.removeAttributeModifier(Attribute.ATTACK_SPEED);

        double bonusDamage = meta.getPersistentDataContainer().getOrDefault(CustomItemFactory.BONUS_DAMAGE_KEY, PersistentDataType.DOUBLE, 0.0);

        double totalDamage = 6.0 + bonusDamage;
        AttributeModifier damageModifier = new AttributeModifier(ATTACK_DAMAGE_MODIFIER_KEY, totalDamage, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.HAND);
        meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, damageModifier);

        AttributeModifier speedModifier = new AttributeModifier(ATTACK_SPEED_MODIFIER_KEY, -3.4, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.HAND);
        meta.addAttributeModifier(Attribute.ATTACK_SPEED, speedModifier);

        if (level >= 10) {
            meta.addEnchant(Enchantment.WIND_BURST, 3, true);
        } else {
            meta.removeEnchant(Enchantment.WIND_BURST);
        }

        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
    }

    @Override
    public List<String> getBaseStatsLore(ItemStack item, int level, double bonusDamage) {
        List<String> lore = new ArrayList<>();
        lore.add("§7주 손에 있을 때:");
        lore.add(String.format(" §2%.1f 공격력", 7.0 + bonusDamage));
        lore.add(" §20.60 공격 속도");
        return lore;
    }

    @Override
    public List<String> getPassiveBonusLore(ItemStack item, int level) {
        return Collections.emptyList();
    }

    @Override
    public Optional<ISpecialAbility> getSpecialAbility() {
        return Optional.of(WIND_CHARGE_ABILITY);
    }
}