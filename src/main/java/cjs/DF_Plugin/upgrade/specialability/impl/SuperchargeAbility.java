package cjs.DF_Plugin.upgrade.specialability.impl;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.events.game.settings.GameConfigManager;
import cjs.DF_Plugin.player.offline.OfflinePlayerManager;
import cjs.DF_Plugin.upgrade.UpgradeManager;
import cjs.DF_Plugin.upgrade.specialability.ISpecialAbility;
import cjs.DF_Plugin.upgrade.specialability.SpecialAbilityManager;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class SuperchargeAbility implements ISpecialAbility {

    private final Map<UUID, BukkitTask> chargeTasks = new HashMap<>(); // 충전 시작 -> 완료까지의 작업
    private final Map<UUID, BukkitTask> chargedStateTasks = new HashMap<>(); // 충전 완료 후 발사 대기 상태의 작업
    public static final String CHARGED_STATE_KEY = "supercharge_charged_state"; // 플레이어 충전 완료 상태 메타데이터

    @Override
    public String getInternalName() {
        return "supercharge";
    }

    @Override
    public String getDisplayName() {
        return "§6슈퍼차지";
    }

    @Override
    public String getDescription() {
        return "§7활을 당겨 충전한 뒤, 강력한 화살을 발사합니다.";
    }

    @Override
    public double getCooldown() {
        return DF_Main.getInstance().getGameConfigManager().getConfig().getDouble("upgrade.special-abilities.supercharge.cooldown", 1.0);
    }

    @Override
    public void onPlayerInteract(PlayerInteractEvent event, Player player, ItemStack item) {
        // 활을 당기기 시작할 때(우클릭)만 발동하도록 제한합니다.
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        // 10강 이상일 때만 특수능력이 발동하도록 레벨을 확인합니다.
        int level = DF_Main.getInstance().getUpgradeManager().getUpgradeLevel(item);
        if (level < UpgradeManager.MAX_UPGRADE_LEVEL) {
            return;
        }

        SpecialAbilityManager manager = DF_Main.getInstance().getSpecialAbilityManager();
        // 쿨다운 중이면 충전을 시작하지 않음
        if (manager.isOnCooldown(player, this, item)) {
            // 액션바 매니저가 지속적으로 쿨타임을 표시해주므로, 별도 메시지 없이 리턴
            return;
        }

        // 이미 차징 중이면 중복 실행 방지
        if (chargeTasks.containsKey(player.getUniqueId())) {
            return;
        }

        GameConfigManager configManager = DF_Main.getInstance().getGameConfigManager();
        long chargeTicks = (long) (configManager.getConfig().getDouble("upgrade.special-abilities.supercharge.details.charge-time-seconds", 8.0) * 20L);

        BukkitTask chargeTask = new BukkitRunnable() {
            @Override
            public void run() {
                ItemStack currentItem = player.getInventory().getItemInMainHand();
                // 활을 계속 당기고 있고, 손에 든 아이템이 여전히 슈퍼차지 활인지 확인 (안정성 강화)
                boolean hasSupercharge = DF_Main.getInstance().getSpecialAbilityManager()
                        .getAbilityFromItem(currentItem)
                        .map(ability -> ability.getInternalName().equals(getInternalName()))
                        .orElse(false);

                if (player.isOnline() && player.isHandRaised() && hasSupercharge) {
                    enterChargedState(player, currentItem);
                }

                // 작업이 완료되거나 조건이 깨지면 맵에서 제거
                chargeTasks.remove(player.getUniqueId());
            }
        }.runTaskLater(DF_Main.getInstance(), chargeTicks);

        chargeTasks.put(player.getUniqueId(), chargeTask);
    }

    /**
     * 슈퍼차지 충전이 완료된 후, 발사 대기 상태로 전환하고 관련 효과를 재생합니다.
     * @param player 대상 플레이어
     * @param bow 사용 중인 활
     */
    private void enterChargedState(Player player, ItemStack bow) {
        // 이미 다른 대기 상태 작업이 있다면 취소
        if (chargedStateTasks.containsKey(player.getUniqueId())) {
            chargedStateTasks.get(player.getUniqueId()).cancel();
        }

        player.setMetadata(CHARGED_STATE_KEY, new FixedMetadataValue(DF_Main.getInstance(), true));
        // 워든이 소닉붐을 충전하는 소리로 변경
        player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_SONIC_CHARGE, 1.5f, 1.0f);

        GameConfigManager configManager = DF_Main.getInstance().getGameConfigManager();
        final long durationTicks = (long) (configManager.getConfig().getDouble("upgrade.special-abilities.supercharge.details.charged-duration-seconds", 5.0) * 20L);
        final long particleInterval = 2L;

        BukkitTask chargedTask = new BukkitRunnable() {
            long ticksPassed = 0;

            @Override
            public void run() {
                ItemStack currentItem = player.getInventory().getItemInMainHand();
                boolean hasSupercharge = DF_Main.getInstance().getSpecialAbilityManager()
                        .getAbilityFromItem(currentItem)
                        .map(ability -> ability.getInternalName().equals(getInternalName()))
                        .orElse(false);

                // 지속 시간이 다 되거나, 플레이어가 오프라인이거나, 활을 바꾸면 상태를 해제합니다.
                if (ticksPassed >= durationTicks || !player.isOnline() || !hasSupercharge) {
                    cleanupChargedState(player);
                    return;
                }

                // 지속 시간 동안 파티클 효과 재생
                player.getWorld().spawnParticle(Particle.SCULK_SOUL, player.getLocation().add(0, 1, 0), 2, 0.3, 0.5, 0.3, 0.01);
                ticksPassed += particleInterval;
            }
        }.runTaskTimer(DF_Main.getInstance(), 0L, particleInterval);

        chargedStateTasks.put(player.getUniqueId(), chargedTask);
    }

    @Override
    public void onEntityShootBow(EntityShootBowEvent event, Player player, ItemStack item) {
        // 발사 시, 진행 중이던 충전 작업이 있었다면 취소
        if (chargeTasks.containsKey(player.getUniqueId())) {
            chargeTasks.get(player.getUniqueId()).cancel();
            chargeTasks.remove(player.getUniqueId());
        }

        // 슈퍼차지 상태에서 발사했다면, 일반 화살 대신 소닉붐을 발사합니다.
        if (player.hasMetadata(CHARGED_STATE_KEY)) {
            SpecialAbilityManager abilityManager = DF_Main.getInstance().getSpecialAbilityManager();

            event.setCancelled(true); // 일반 화살 발사 취소
            cleanupChargedState(player); // 발사했으므로 대기 상태 정리
            abilityManager.setCooldown(player, this, item, getCooldown()); // 쿨다운 적용

            launchSonicBoom(player); // 새로운 음파 공격 발사

            // 발사 반동으로 시전자를 뒤로 밀어냅니다.
            double shooterKnockback = DF_Main.getInstance().getGameConfigManager().getConfig()
                    .getDouble("upgrade.special-abilities.supercharge.details.shooter-knockback-strength", 0.8);
            Vector knockbackVector = player.getLocation().getDirection().multiply(-1).setY(0.3);
            player.setVelocity(player.getVelocity().add(knockbackVector.multiply(shooterKnockback)));
        }
    }

    @Override
    public void onDamageByEntity(EntityDamageByEntityEvent event, Player player, ItemStack item) {
        // 데미지 로직이 소닉붐 발사로 변경되었으므로, 이 메서드는 더 이상 사용되지 않습니다.
    }

    @Override
    public void onCleanup(Player player) {
        // 플레이어가 서버를 나가거나 할 때, 모든 관련 작업을 정리하여 메모리 누수를 방지합니다.
        if (chargeTasks.containsKey(player.getUniqueId())) {
            chargeTasks.get(player.getUniqueId()).cancel();
            chargeTasks.remove(player.getUniqueId());
        }
        cleanupChargedState(player);
    }

    /**
     * 플레이어의 위치에서 전방으로 워든의 소닉붐 공격을 발사합니다.
     * @param player 공격을 발사할 플레이어
     */
    private void launchSonicBoom(Player player) {
        DF_Main plugin = DF_Main.getInstance();
        GameConfigManager configManager = plugin.getGameConfigManager();
        org.bukkit.Location startLoc = player.getEyeLocation();
        Vector direction = player.getEyeLocation().getDirection();
        double damage = configManager.getConfig().getDouble("upgrade.special-abilities.supercharge.details.damage", 20.0);
        double targetKnockback = configManager.getConfig().getDouble("upgrade.special-abilities.supercharge.details.target-knockback-strength", 1.5);

        player.getWorld().playSound(startLoc, Sound.ENTITY_WARDEN_SONIC_BOOM, 2.0f, 1.0f);

        new BukkitRunnable() {
            private final java.util.Set<UUID> hitEntities = new java.util.HashSet<>();
            private double distance = 0;
            private final double maxDistance = 90; // 음파 최대 사거리
            private final double step = 4.5; // 틱당 이동 거리 (기존 1.5에서 3배 증가)

            @Override
            public void run() {
                org.bukkit.Location currentLoc = startLoc.clone().add(direction.clone().multiply(distance));

                if (distance >= maxDistance || currentLoc.getBlock().isSolid()) {
                    this.cancel();
                    return;
                }

                player.getWorld().spawnParticle(Particle.SONIC_BOOM, currentLoc, 1, 0, 0, 0, 0);

                // 파티클 주변의 엔티티를 확인하여 피해를 줍니다.
                for (LivingEntity entity : currentLoc.getWorld().getNearbyLivingEntities(currentLoc, 1.5, 1.5, 1.5)) {
                    if (entity.equals(player) || hitEntities.contains(entity.getUniqueId())) {
                        continue;
                    }
                    // 오프라인 플레이어의 아바타는 피해를 입지 않도록 예외 처리합니다.
                    if (entity.getPersistentDataContainer().has(OfflinePlayerManager.OFFLINE_BODY_KEY, PersistentDataType.STRING)) {
                        continue;
                    }
                    // 1. 소량의 일반 피해를 주어 피격 효과(소리, 붉은색 점멸)를 유발합니다.
                    entity.damage(1.0, player);

                    // 2. 주된 피해는 방어력을 무시하는 고정 피해로 적용합니다.
                    //    일반 피해로 1.0이 이미 들어갔으므로, 전체 피해량에서 1.0을 뺍니다.
                    double fixedDamage = Math.max(0, damage - 1.0);
                    entity.setHealth(Math.max(0.0, entity.getHealth() - fixedDamage));

                    // 3. damage()에 의한 기본 넉백을 덮어쓰고, 커스텀 넉백을 적용합니다.
                    Vector knockbackDirection = entity.getLocation().toVector().subtract(player.getLocation().toVector()).normalize();
                    knockbackDirection.setY(Math.max(knockbackDirection.getY(), 0.4)); // 살짝 위로 띄웁니다.
                    entity.setVelocity(knockbackDirection.multiply(targetKnockback));
                    hitEntities.add(entity.getUniqueId());
                }
                distance += step;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    /**
     * 플레이어의 슈퍼차지 대기 상태와 관련된 모든 효과와 데이터를 정리합니다.
     * @param player 대상 플레이어
     */
    private void cleanupChargedState(Player player) {
        if (player.hasMetadata(CHARGED_STATE_KEY)) {
            player.removeMetadata(CHARGED_STATE_KEY, DF_Main.getInstance());
        }
        if (chargedStateTasks.containsKey(player.getUniqueId())) {
            chargedStateTasks.get(player.getUniqueId()).cancel();
            chargedStateTasks.remove(player.getUniqueId());
        }
    }
}