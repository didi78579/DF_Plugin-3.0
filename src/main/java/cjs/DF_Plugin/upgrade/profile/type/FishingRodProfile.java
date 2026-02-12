// C:/Users/CJS/IdeaProjects/DF_Plugin-2.0/src/main/java/cjs/DF_Plugin/upgrade/profile/type/FishingRodProfile.java
package cjs.DF_Plugin.upgrade.profile.type;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.upgrade.profile.IUpgradeableProfile;
import cjs.DF_Plugin.upgrade.specialability.ISpecialAbility;
import cjs.DF_Plugin.upgrade.specialability.impl.GrabAbility;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class FishingRodProfile implements IUpgradeableProfile {

    private static final ISpecialAbility GRAB_ABILITY = new GrabAbility();

    @Override
    public void applyAttributes(ItemStack item, ItemMeta meta, int level) {
        // 낚싯대는 현재 특별한 Attribute나 Enchantment 보너스가 없습니다.
    }

    @Override
    public List<String> getPassiveBonusLore(ItemStack item, int level) {
        if (level > 0) {
            double velocityBonusPerLevel = DF_Main.getInstance().getGameConfigManager().getConfig()
                    .getDouble("upgrade.generic-bonuses.fishing_rod.velocity-bonus-per-level", 0.4);
            double totalBonus = velocityBonusPerLevel * level * 100; // 퍼센트로 표시

            DecimalFormat df = new DecimalFormat("#.#");
            return List.of("§b추가 낚시찌 속도: +" + df.format(totalBonus) + "%");
        }
        return Collections.emptyList();
    }


    @Override
    public List<String> getBaseStatsLore(org.bukkit.inventory.ItemStack item, int level, double baseValue) {
        return new ArrayList<>(); // 도끼는 기본 스탯 로어를 표시하지 않음
    }
    @Override
    public Optional<ISpecialAbility> getSpecialAbility() {
        return Optional.of(GRAB_ABILITY);
    }
}