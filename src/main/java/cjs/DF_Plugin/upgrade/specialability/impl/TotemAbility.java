package cjs.DF_Plugin.upgrade.specialability.impl;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.upgrade.specialability.ISpecialAbility;
import org.bukkit.configuration.ConfigurationSection;

public class TotemAbility implements ISpecialAbility {
    @Override
    public String getInternalName() {
        return "totem_of_undying";
    }

    @Override
    public String getDisplayName() {
        return "§6불사의 토템";
    }

    @Override
    public String getDescription() {
        return "죽음의 위기에서 생명을 구합니다.";
    }

    @Override
    public double getCooldown() {
        ConfigurationSection abilityConfig = DF_Main.getInstance().getGameConfigManager().getConfig()
                .getConfigurationSection("upgrade.special-abilities." + getInternalName());
        return (abilityConfig != null) ? abilityConfig.getDouble("cooldown", 300.0) : 300.0;
    }

    @Override
    public boolean showInActionBar() {
        return true; // 이 능력의 쿨다운을 액션바에 표시합니다.
    }
}