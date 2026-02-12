package cjs.DF_Plugin.upgrade.profile.type;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.item.CustomItemFactory;
import cjs.DF_Plugin.upgrade.UpgradeManager;
import cjs.DF_Plugin.upgrade.profile.IUpgradeableProfile;
import cjs.DF_Plugin.upgrade.specialability.ISpecialAbility;
import cjs.DF_Plugin.upgrade.specialability.impl.*;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

public class TridentProfile implements IUpgradeableProfile {

    private static final ISpecialAbility BACKFLOW_ABILITY = new BackflowAbility();
    private static final ISpecialAbility LIGHTNING_SPEAR_ABILITY = new LightningSpearAbility();
    private static final NamespacedKey ATTACK_DAMAGE_MODIFIER_KEY = new NamespacedKey(DF_Main.getInstance(), "trident_attack_damage");
    private static final NamespacedKey ATTACK_SPEED_MODIFIER_KEY = new NamespacedKey(DF_Main.getInstance(), "trident_attack_speed");

    @Override
    public void applyAttributes(ItemStack item, ItemMeta meta, int level) {
        meta.removeAttributeModifier(Attribute.ATTACK_DAMAGE);
        meta.removeAttributeModifier(Attribute.ATTACK_SPEED);

        double bonusDamage = meta.getPersistentDataContainer().getOrDefault(CustomItemFactory.BONUS_DAMAGE_KEY, PersistentDataType.DOUBLE, 0.0);
        double totalDamage = 8.0 + bonusDamage;

        meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, new AttributeModifier(ATTACK_DAMAGE_MODIFIER_KEY, totalDamage, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.HAND));
        meta.addAttributeModifier(Attribute.ATTACK_SPEED, new AttributeModifier(ATTACK_SPEED_MODIFIER_KEY, -2.9, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.HAND));

        String mode = meta.getPersistentDataContainer().getOrDefault(UpgradeManager.TRIDENT_MODE_KEY, PersistentDataType.STRING, "backflow");
        ISpecialAbility currentAbility = "lightning_spear".equals(mode) ? LIGHTNING_SPEAR_ABILITY : BACKFLOW_ABILITY;

        meta.removeEnchant(Enchantment.LOYALTY);
        meta.removeEnchant(Enchantment.RIPTIDE);

        if (mode.equals("backflow")) {
            meta.addEnchant(Enchantment.RIPTIDE, 3, true);
        } else if (mode.equals("lightning_spear")) {
            meta.addEnchant(Enchantment.LOYALTY, 3, true);
            meta.addEnchant(Enchantment.CHANNELING, 1, true);
        }
        String currentName = meta.hasDisplayName() ? meta.getDisplayName() : "삼지창";

        int modeIndex = currentName.lastIndexOf(" [");
        if (modeIndex != -1) {
            currentName = currentName.substring(0, modeIndex).trim();
        }

        String displayName = currentAbility.getDisplayName();
        String colorCode = "§7";
        if (displayName.length() >= 2 && displayName.charAt(0) == '§') {
            colorCode = displayName.substring(0, 2);
        }
        String abilityName = ChatColor.stripColor(displayName);
        meta.setDisplayName(currentName + " " + colorCode + "[" + abilityName + "]");
        
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
    }

    @Override
    public List<String> getPassiveBonusLore(ItemStack item, int level) {
        if (level > 0) {
            return List.of("§b추가 투사체: +" + level + "개");
        }
        return Collections.emptyList();
    }

    @Override
    public List<String> getBaseStatsLore(ItemStack item, int level, double bonusDamage) {
        List<String> baseLore = new ArrayList<>();
        baseLore.add("§7주로 사용하는 손에 있을 때:");

        double finalDamage = 9.0 + bonusDamage;
        baseLore.add("§2 " + String.format("%.1f", finalDamage) + " 공격 피해");
        baseLore.add("§2 1.10 공격 속도");

        return baseLore;
    }

    @Override
    public Optional<ISpecialAbility> getSpecialAbility() {
        return Optional.of(BACKFLOW_ABILITY);
    }

    @Override
    public Collection<ISpecialAbility> getAdditionalAbilities() {
        return List.of(LIGHTNING_SPEAR_ABILITY);
    }
}