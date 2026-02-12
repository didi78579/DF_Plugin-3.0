package cjs.DF_Plugin.upgrade.profile.type;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.upgrade.profile.IUpgradeableProfile;
import cjs.DF_Plugin.upgrade.specialability.ISpecialAbility;
import cjs.DF_Plugin.upgrade.specialability.impl.ShieldBashAbility;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class ShieldProfile implements IUpgradeableProfile {

    private static final ISpecialAbility SHIELD_BASH_ABILITY = new ShieldBashAbility();

    @Override
    public void applyAttributes(ItemStack item, ItemMeta meta, int level) {
        double unbreakingPerLevel = DF_Main.getInstance().getGameConfigManager().getConfig().getDouble("upgrade.generic-bonuses.shield.unbreaking-per-level", 1.0);
        int enchantLevel = (int) (level * unbreakingPerLevel);

        // 강화 레벨만큼 내구성 인챈트 적용
        if (enchantLevel > 0) {
            meta.addEnchant(Enchantment.UNBREAKING, enchantLevel, true);
        } else {
            meta.removeEnchant(Enchantment.UNBREAKING);
        }
    }

    @Override
    public List<String> getBaseStatsLore(org.bukkit.inventory.ItemStack item, int level, double baseValue) {
        return new ArrayList<>(); // 도끼는 기본 스탯 로어를 표시하지 않음
    }

    @Override
    public Optional<ISpecialAbility> getSpecialAbility() {
        return Optional.of(SHIELD_BASH_ABILITY);
    }
}