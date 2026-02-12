package cjs.DF_Plugin.upgrade.specialability.impl;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.upgrade.specialability.ISpecialAbility;
import cjs.DF_Plugin.upgrade.specialability.SpecialAbilityManager;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class RegenerationAbility implements ISpecialAbility {
    @Override
    public String getInternalName() {
        return "regeneration";
    }

    @Override
    public String getDisplayName() {
        return "§b재생";
    }

    @Override
    public String getDescription() {
        return "§7체력이 서서히 회복됩니다.";
    }

    @Override
    public double getCooldown() {
        // 이 쿨다운은 효과가 다시 적용되기까지의 시간, 즉 '주기'를 의미합니다.
        return DF_Main.getInstance().getGameConfigManager().getConfig().getDouble("upgrade.special-abilities.regeneration.cooldown", 1.0);
    }

    @Override
    public boolean showInActionBar() {
        return false; // 이 능력은 패시브이므로 액션바에 표시하지 않습니다.
    }

    @Override
    public void onPlayerMove(PlayerMoveEvent event, Player player, ItemStack item) {
        // PlayerMoveEvent는 매우 자주 발생하므로, 쿨다운 시스템을 통해 효과 적용 주기를 제어합니다.
        SpecialAbilityManager manager = DF_Main.getInstance().getSpecialAbilityManager();

        // 1. 능력이 쿨다운 상태인지 확인합니다. 쿨다운 중이면 아무것도 하지 않습니다.
        if (manager.isOnCooldown(player, this, item)) {
            return;
        }

        // 2. 쿨다운이 아니라면, 설정된 값에 따라 재생 효과를 부여합니다.
        double durationSeconds = DF_Main.getInstance().getGameConfigManager().getConfig().getDouble("upgrade.special-abilities.regeneration.details.duration-seconds", 5.0);
        int amplifier = DF_Main.getInstance().getGameConfigManager().getConfig().getInt("upgrade.special-abilities.regeneration.details.amplifier", 0);
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, (int) (durationSeconds * 20), amplifier, false, false));

        // 3. 효과를 부여한 후, 즉시 쿨다운을 설정하여 다음 주기가 시작되도록 합니다.
        manager.setCooldown(player, this, item, getCooldown());
    }
}