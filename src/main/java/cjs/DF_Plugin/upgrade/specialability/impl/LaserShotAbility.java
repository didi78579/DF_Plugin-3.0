package cjs.DF_Plugin.upgrade.specialability.impl;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.events.game.settings.GameConfigManager;
import cjs.DF_Plugin.upgrade.UpgradeManager;
import cjs.DF_Plugin.upgrade.specialability.ISpecialAbility;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

public class LaserShotAbility implements ISpecialAbility {

    @Override
    public String getInternalName() {
        return "laser_shot";
    }

    @Override
    public String getDisplayName() {
        return "§b레이저 샷";
    }

    @Override
    public String getDescription() {
        return "§7쇠뇌에서 레이저를 발사합니다.";
    }

    @Override
    public double getCooldown() {
        return 0; // 이 능력은 10강 전용 패시브이므로, 별도의 쿨다운이 없습니다.
    }

    @Override
    public void onEntityShootBow(EntityShootBowEvent event, Player player, ItemStack item) {
        UpgradeManager upgradeManager = DF_Main.getInstance().getUpgradeManager();
        GameConfigManager configManager = DF_Main.getInstance().getGameConfigManager();
        int level = upgradeManager.getUpgradeLevel(item);
        int requiredLevel = configManager.getConfig().getInt("upgrade.special-abilities.laser_shot.details.required-level", 10);

        if (level >= requiredLevel) {
            // 1. 화살 발사를 취소하고 즉발 레이저로 대체합니다.
            event.setCancelled(true);

            // 2. 레이저 속성 설정
            double damage = configManager.getConfig().getDouble("upgrade.special-abilities.laser_shot.details.damage", 20.0);
            double maxRange = configManager.getConfig().getDouble("upgrade.special-abilities.laser_shot.details.range", 100.0);
            int glowDuration = configManager.getConfig().getInt("upgrade.special-abilities.laser_shot.details.glow-duration-seconds", 10);

            // 3. 레이저 발사 위치 및 방향 설정
            Location startPoint = player.getEyeLocation();
            Vector direction = startPoint.getDirection();

            // 4. 시각 효과를 먼저 생성합니다.
            // 이렇게 하면 적중 여부와 관계없이 항상 동일한 위치에 파티클이 표시됩니다.
            Location particleLocation = startPoint.clone().add(direction.clone().multiply(1.5));
            player.getWorld().spawnParticle(Particle.SWEEP_ATTACK, particleLocation, 1, 0, 0, 0, 0);

            // 5. 레이캐스트로 타겟 탐지
            RayTraceResult result = player.getWorld().rayTrace(
                    startPoint,
                    direction,
                    maxRange,
                    FluidCollisionMode.NEVER,
                    true,
                    0.2,
                    e -> e instanceof LivingEntity && !e.getUniqueId().equals(player.getUniqueId())
            );

            // 6. 타겟이 있으면 피해 및 효과 적용
            if (result != null && result.getHitEntity() instanceof LivingEntity target) {
                target.damage(damage, player);
                target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, glowDuration * 20, 0, true, false));
            }
        }
    }
}