package cjs.DF_Plugin.upgrade.profile.type;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.item.CustomItemFactory;
import cjs.DF_Plugin.upgrade.UpgradeManager;
import cjs.DF_Plugin.upgrade.profile.IUpgradeableProfile;
import cjs.DF_Plugin.upgrade.specialability.ISpecialAbility;
import cjs.DF_Plugin.upgrade.specialability.impl.*;
import org.bukkit.ChatColor;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

public class TridentProfile implements IUpgradeableProfile {

    private static final ISpecialAbility BACKFLOW_ABILITY = new BackflowAbility();
    private static final UUID ATTACK_DAMAGE_MODIFIER_UUID = UUID.fromString("CB3F55D3-645C-4F38-A497-9C13A33DB5CF");
    private static final UUID ATTACK_SPEED_MODIFIER_UUID = UUID.fromString("FA233E1C-4180-4865-B01B-BCCE9785ACA2");

    @Override
    public void applyAttributes(ItemStack item, ItemMeta meta, int level) {
        // 1. 기존 공격 관련 속성을 모두 제거합니다.
        meta.removeAttributeModifier(Attribute.ATTACK_DAMAGE);
        meta.removeAttributeModifier(Attribute.ATTACK_SPEED);

        // '용의 심장'으로 부여된 추가 공격력을 가져옵니다.
        double bonusDamage = meta.getPersistentDataContainer().getOrDefault(CustomItemFactory.BONUS_DAMAGE_KEY, PersistentDataType.DOUBLE, 0.0);
        double totalDamage = 8.0 + bonusDamage;

        // 2. 삼지창의 기본 스탯을 적용합니다. (바닐라 기준: 공격력 9, 공격 속도 1.1)
        meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, new AttributeModifier(ATTACK_DAMAGE_MODIFIER_UUID, "weapon.damage", totalDamage, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlot.HAND)); // 기본 1 + 8 + bonus = 9 + bonus
        meta.addAttributeModifier(Attribute.ATTACK_SPEED, new AttributeModifier(ATTACK_SPEED_MODIFIER_UUID, "weapon.attack_speed", -2.9, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlot.HAND)); // 4.0 - 2.9 = 1.1

        // 모드에 따른 능력, 이름, 인챈트 설정
        String mode = meta.getPersistentDataContainer().getOrDefault(UpgradeManager.TRIDENT_MODE_KEY, PersistentDataType.STRING, "backflow");
        ISpecialAbility currentAbility = DF_Main.getInstance().getSpecialAbilityManager().getRegisteredAbility(mode);

        if (currentAbility != null) {
            // 모드에 따른 인챈트 설정
            // 먼저 기존의 충성/급류 인챈트를 제거합니다.
            meta.removeEnchant(Enchantment.LOYALTY);
            meta.removeEnchant(Enchantment.RIPTIDE);
            meta.removeEnchant(Enchantment.CHANNELING);

            // 모드에 맞는 인챈트를 부여합니다.
            if (mode.equals("backflow")) {
                meta.addEnchant(Enchantment.RIPTIDE, 3, true);
            } else if (mode.equals("lightning_spear")) {
                meta.addEnchant(Enchantment.LOYALTY, 3, true);
                meta.addEnchant(Enchantment.CHANNELING, 1, true);
            }
            // 이름에 모드 접미사 추가
            String currentName = meta.hasDisplayName() ? meta.getDisplayName() : "삼지창";

            int modeIndex = currentName.lastIndexOf(" [");
            if (modeIndex != -1) {
                currentName = currentName.substring(0, modeIndex).trim();
            }

            String displayName = currentAbility.getDisplayName(); // e.g., "§3역류"
            String colorCode = "§7"; // 기본 회색
            if (displayName.length() >= 2 && displayName.charAt(0) == '§') {
                colorCode = displayName.substring(0, 2);
            }
            String abilityName = ChatColor.stripColor(displayName); // "역류"
            meta.setDisplayName(currentName + " " + colorCode + "[" + abilityName + "]");
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
    }

    public List<String> getPassiveBonusLore(ItemStack item, int level, double bonusDamage) {
        return Collections.emptyList();
    }

    @Override
    public List<String> getBaseStatsLore(ItemStack item, int level, double bonusDamage) {
        List<String> lore = new ArrayList<>();
        lore.add("§7주 손에 있을 때:");
        lore.add(String.format(" §2%.1f 공격력", 9.0 + bonusDamage));
        lore.add(" §21.10 공격 속도");
        return lore;
    }

    @Override
    public Optional<ISpecialAbility> getSpecialAbility() {
        // 기본 능력은 폭류로 설정. 실제 능력은 applyAttributes에서 모드에 따라 결정됨.
        return Optional.of(BACKFLOW_ABILITY);
    }

    public Collection<ISpecialAbility> getAdditionalAbilities() {
        // 두 능력을 모두 등록해야 SpecialAbilityManager에서 찾을 수 있음.
        return List.of(new LightningSpearAbility());
    }
}