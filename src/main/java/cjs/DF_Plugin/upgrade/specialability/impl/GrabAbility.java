package cjs.DF_Plugin.upgrade.specialability.impl;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.events.game.settings.GameConfigManager;
import cjs.DF_Plugin.upgrade.specialability.ISpecialAbility;
import cjs.DF_Plugin.upgrade.specialability.SpecialAbilityManager;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

public class GrabAbility implements ISpecialAbility {

    @Override
    public String getInternalName() {
        return "grab";
    }

    @Override
    public String getDisplayName() {
        return "§d끌어오기";
    }

    @Override
    public String getDescription() {
        return "§7낚시찌에 걸린 대상을 자신에게로 끌어옵니다.";
    }

    @Override
    public double getCooldown() {
        return DF_Main.getInstance().getGameConfigManager().getConfig().getDouble("upgrade.special-abilities.grab.cooldown", 60.0);
    }
    @Override
    public void onPlayerFish(PlayerFishEvent event, Player player, ItemStack item) {
        SpecialAbilityManager manager = DF_Main.getInstance().getSpecialAbilityManager();
        GameConfigManager config = DF_Main.getInstance().getGameConfigManager();

        // 엔티티를 낚았을 때 (CAUGHT_ENTITY state)
        if (event.getState() == PlayerFishEvent.State.CAUGHT_ENTITY) {
            if (DF_Main.getInstance().getUpgradeManager().getUpgradeLevel(item) < 10) return;

            // 능력 사용을 시도합니다. 쿨다운 중이거나 다른 이유로 사용할 수 없다면, 여기서 중단됩니다.
            if (!manager.tryUseAbility(player, this, item)) return;

            // tryUseAbility가 성공적으로 호출되면, 쿨다운은 이미 적용된 상태입니다.
            if (!(event.getCaught() instanceof LivingEntity livingTarget)) return;

            // 2단계 끌어오기 로직
            double upwardForceValue = config.getConfig().getDouble("upgrade.special-abilities.grab.details.upward-force", 1.2);
            double pullStrength = config.getConfig().getDouble("upgrade.special-abilities.grab.details.pull-strength", 2.5);
            double maxPullStrength = config.getConfig().getDouble("upgrade.special-abilities.grab.details.max-pull-strength", 10.0);

            // 1. 대상 위로 띄우기
            livingTarget.setVelocity(new Vector(0, upwardForceValue, 0));

            // 2. 잠시 후 대상 끌어오기
            Location playerLocation = player.getLocation();
            DF_Main.getInstance().getServer().getScheduler().runTaskLater(DF_Main.getInstance(), () -> {
                if (!livingTarget.isValid() || livingTarget.isDead()) return;

                Location targetLocation = livingTarget.getLocation();
                Vector pullDirection = playerLocation.toVector().subtract(targetLocation.toVector()).normalize();

                Vector pullForce = pullDirection.multiply(pullStrength);
                if (pullForce.length() > maxPullStrength) {
                    pullForce = pullForce.normalize().multiply(maxPullStrength);
                }
                livingTarget.setVelocity(pullForce);
                livingTarget.getWorld().playSound(player.getLocation(), Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 1.5f, 1.0f);
            }, 10L); // 0.5초 지연
        }
    }
}