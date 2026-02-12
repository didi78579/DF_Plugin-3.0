package cjs.DF_Plugin.upgrade.specialability.impl;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.upgrade.specialability.ISpecialAbility;
import cjs.DF_Plugin.upgrade.specialability.SpecialAbilityManager;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class HarpoonAbility implements ISpecialAbility {

    private final Map<UUID, Long> divingPlayers = new ConcurrentHashMap<>();

    @Override
    public String getInternalName() {
        return "harpoon";
    }

    @Override
    public String getDisplayName() {
        return "§c작살";
    }

    @Override
    public String getDescription() {
        return "§7[Q]키로 지상/공중에서 다른 기술을 사용합니다.";
    }

    @Override
    public double getCooldown() {
        return 8.0;
    }

    @Override
    public void onPlayerDropItem(PlayerDropItemEvent event, Player player, ItemStack item) {
        event.setCancelled(true); // 아이템을 실제로 버리는 것을 방지

        SpecialAbilityManager manager = DF_Main.getInstance().getSpecialAbilityManager();
        if (!manager.tryUseAbility(player, this, item)) return;

        if (player.isOnGround()) {
            executeGroundThrust(player);
        } else {
            executeAerialDive(player);
        }
    }

    @Override
    public void onPlayerMove(PlayerMoveEvent event, Player player, ItemStack item) {
        if (!divingPlayers.containsKey(player.getUniqueId())) return;

        // 착지 감지
        if (player.isOnGround()) {
            float fallDistance = player.getFallDistance();
            divingPlayers.remove(player.getUniqueId());
            player.setFallDistance(0); // 낙하 데미지 무효화

            if (fallDistance > 3) { // 최소 낙하 높이
                Location impactLocation = player.getLocation();
                double power = Math.pow(fallDistance, 2);
                double damage = power + 10;
                double knockback = power;

                impactLocation.getWorld().createExplosion(impactLocation, 0F, false); // 폭발 효과만
                impactLocation.getWorld().playSound(impactLocation, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.5f);

                impactLocation.getWorld().getNearbyEntities(impactLocation, 5, 5, 5).forEach(entity -> {
                    if (entity instanceof LivingEntity && !entity.equals(player)) {
                        ((LivingEntity) entity).damage(damage, player);
                        Vector direction = entity.getLocation().toVector().subtract(impactLocation.toVector()).normalize();
                        direction.setY(0.5).multiply(Math.min(knockback / 20.0, 5.0)); // 넉백 힘 제한
                        entity.setVelocity(direction);
                    }
                });
            }
        } else {
            // 공중에서 이펙트 렌더링
            long startTime = divingPlayers.get(player.getUniqueId());
            long duration = System.currentTimeMillis() - startTime;
            float progress = Math.min(duration / 1500f, 1.0f); // 1.5초에 걸쳐 색상 변경

            // progress(0.0 ~ 1.0)에 따라 흰색(255,255,255)에서 빨간색(255,0,0)으로 색상을 보간합니다.
            int green = (int) (255 * (1 - progress));
            int blue = (int) (255 * (1 - progress));
            Color color = Color.fromRGB(255, green, blue);

            player.getWorld().spawnParticle(Particle.DUST, player.getLocation(), 50, 0.8, 1.2, 0.8, 0, new Particle.DustOptions(color, 2.0f));
        }
    }

    private void executeGroundThrust(Player player) {
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.2f);

        // 찌르기 이펙트
        Location eyeLoc = player.getEyeLocation();
        Vector direction = eyeLoc.getDirection();
        for (int i = 1; i <= 5; i++) {
            Location particleLoc = eyeLoc.clone().add(direction.clone().multiply(i * 0.8));
            player.getWorld().spawnParticle(Particle.DUST, particleLoc, 10, 0.1, 0.1, 0.1, 0, new Particle.DustOptions(Color.RED, 1.5f));
        }

        // 전방 4블록 내의 첫 번째 대상 찾기
        player.getWorld().getNearbyEntities(eyeLoc, 4, 4, 4).stream()
                .filter(e -> e instanceof LivingEntity && !e.equals(player))
                .filter(e -> {
                    Vector toEntity = e.getLocation().toVector().subtract(player.getLocation().toVector());
                    return toEntity.normalize().dot(direction) > 0.95; // 플레이어가 바라보는 방향에 있는지 확인
                })
                .min(java.util.Comparator.comparing(e -> e.getLocation().distanceSquared(player.getLocation())))
                .ifPresent(target -> {
                    LivingEntity victim = (LivingEntity) target;
                    double missingHealth = victim.getMaxHealth() - victim.getHealth();
                    double bonusDamage = missingHealth * 0.3; // 잃은 체력의 30% 추가 피해
                    double totalDamage = 5.0 + bonusDamage;

                    victim.damage(totalDamage, player);
                    victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1.0f, 0.8f);
                });
    }

    private void executeAerialDive(Player player) {
        divingPlayers.put(player.getUniqueId(), System.currentTimeMillis());
        player.setVelocity(new Vector(0, -2.5, 0)); // 아래로 빠르게 강하
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 1.5f, 0.5f);

        // 5초 후 강제 종료
        new BukkitRunnable() {
            @Override
            public void run() {
                if (divingPlayers.remove(player.getUniqueId()) != null) {
                    player.setFallDistance(0);
                }
            }
        }.runTaskLater(DF_Main.getInstance(), 100L);
    }

    @Override
    public void onCleanup(Player player) {
        divingPlayers.remove(player.getUniqueId());
    }
}