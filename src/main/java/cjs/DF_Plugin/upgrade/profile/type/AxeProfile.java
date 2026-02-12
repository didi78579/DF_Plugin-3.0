package cjs.DF_Plugin.upgrade.profile.type;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.upgrade.UpgradeManager;
import cjs.DF_Plugin.upgrade.profile.IUpgradeableProfile;
import cjs.DF_Plugin.upgrade.specialability.ISpecialAbility;
import cjs.DF_Plugin.upgrade.specialability.impl.CleansingAbility;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class AxeProfile implements IUpgradeableProfile {

    private static final ISpecialAbility CLEANSING_ABILITY = new CleansingAbility();

    @Override
    public void applyAttributes(org.bukkit.inventory.ItemStack item, ItemMeta meta, int level) {
        // 1. 기존 인챈트 로직을 적용합니다.
        Map<Enchantment, Double> enchantBonuses = new LinkedHashMap<>();
        enchantBonuses.put(Enchantment.EFFICIENCY, 0.5);
        enchantBonuses.put(Enchantment.UNBREAKING, 0.5);
        UpgradeManager.applyCyclingEnchantments(meta, level, enchantBonuses);
    }

    @Override
    public Optional<ISpecialAbility> getSpecialAbility() {
        return Optional.of(CLEANSING_ABILITY);
    }

    @Override
    public List<String> getBaseStatsLore(org.bukkit.inventory.ItemStack item, int level, double baseValue) {
        return new ArrayList<>(); // 도끼는 기본 스탯 로어를 표시하지 않음
    }
}