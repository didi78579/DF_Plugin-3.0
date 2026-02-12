package cjs.DF_Plugin.upgrade.profile.passive;

import cjs.DF_Plugin.DF_Main;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;

public class BowPassiveListener implements Listener {

    private final DF_Main plugin;
    // 활의 강화 레벨을 화살에 저장하기 위한 키
    public static final String BOW_PASSIVE_LEVEL_KEY = "bow_passive_level";

    public BowPassiveListener(DF_Main plugin) {
        this.plugin = plugin;
    }

    /**
     * 플레이어가 활을 쏠 때, 발사된 화살에 활의 강화 레벨을 기록합니다.
     */
    @EventHandler
    public void onEntityShootBow(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player) || !(event.getProjectile() instanceof Arrow arrow)) {
            return;
        }

        ItemStack bow = event.getBow();
        if (bow == null || bow.getType() != Material.BOW) return; // 오직 활에만 적용

        int level = plugin.getUpgradeManager().getUpgradeLevel(bow);
        if (level > 0) {
            arrow.setMetadata(BOW_PASSIVE_LEVEL_KEY, new FixedMetadataValue(plugin, level));
        }
    }

    /**
     * 엔티티가 활에서 발사된 화살에 맞았을 때, 강화 레벨에 따른 추가 고정 피해(현재 체력 비례)를 적용합니다.
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onDamageByBowArrow(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Arrow arrow) || !(event.getEntity() instanceof LivingEntity target) || !arrow.hasMetadata(BOW_PASSIVE_LEVEL_KEY)) {
            return;
        }

        // 보스 몬스터(위더, 엔더 드래곤)에게는 체력 비례 피해를 적용하지 않습니다.
        EntityType targetType = target.getType();
        if (targetType == EntityType.WITHER || targetType == EntityType.ENDER_DRAGON) {
            return;
        }

        // --- 기본 패시브 (체력 비례 데미지) ---
        int level = arrow.getMetadata(BOW_PASSIVE_LEVEL_KEY).getFirst().asInt();
        if (level <= 0) return;

        double percentPerLevel = plugin.getGameConfigManager().getConfig().getDouble("upgrade.generic-bonuses.bow.passive-current-health-damage-percent-per-level", 1.5) / 100.0;
        double maxPercent = plugin.getGameConfigManager().getConfig().getDouble("upgrade.generic-bonuses.bow.passive-current-health-damage-max-percent", 15.0) / 100.0;

        double healthPercentage = Math.min(percentPerLevel * level, maxPercent);
        // [수정] 최대 체력이 아닌 '현재 체력'에 비례하여 추가 피해를 계산합니다.
        final double additionalDamage = target.getHealth() * healthPercentage;

        // 갑옷을 무시하는 고정 피해를 주기 위해, 이벤트 처리 후 체력을 직접 감소시킵니다.
        new BukkitRunnable() {
            @Override
            public void run() {
                if (target.isDead()) return;

                // 플레이어의 경우 서바이벌 모드일 때만 피해를 적용합니다.
                if (target instanceof Player player && player.getGameMode() != GameMode.SURVIVAL) {
                    return;
                }

                // 실제 피해처럼 보이도록 처리합니다.
                // 참고: damage() 메서드는 방어력과 인챈트의 영향을 받으므로,
                // 고정 피해를 주기 위해 setHealth()를 사용하고 피격 효과를 수동으로 재생합니다.
                target.setHealth(Math.max(0, target.getHealth() - additionalDamage));
                target.playHurtAnimation(0);
            }
        }.runTaskLater(plugin, 1L);
    }
}