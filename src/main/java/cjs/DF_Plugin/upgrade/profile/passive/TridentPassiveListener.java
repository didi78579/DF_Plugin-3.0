package cjs.DF_Plugin.upgrade.profile.passive;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.events.game.settings.GameConfigManager;
import org.bukkit.Particle;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Trident;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerRiptideEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class TridentPassiveListener implements Listener {

    private final DF_Main plugin;
    // 이 키는 패시브 효과(둔화)를 적용해야 하는 삼지창을 식별합니다.
    public static final String TRIDENT_PASSIVE_LEVEL_KEY = "trident_passive_level";
    // [FIX] 재귀 호출(무한 루프)을 방지하기 위해 현재 추가 투사체를 발사 중인 플레이어를 추적합니다.
    private final Set<UUID> launchingPlayers = ConcurrentHashMap.newKeySet();
    private final Set<UUID> riptideCooldown = ConcurrentHashMap.newKeySet();

    public TridentPassiveListener(DF_Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof Trident trident) || !(trident.getShooter() instanceof Player player))
            return;

        // [FIX] 플레이어가 이미 추가 투사체를 발사하는 중이라면, 이 이벤트는 재귀 호출이므로 무시합니다.
        if (launchingPlayers.contains(player.getUniqueId())) {
            return;
        }

        // '뇌창' 같은 특수 능력 사용 시에는 패시브가 발동하지 않도록 합니다.
        if (player.hasMetadata("df_is_firing_special")) {
            return;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType() != org.bukkit.Material.TRIDENT) {
            item = player.getInventory().getItemInOffHand();
            if (item.getType() != org.bukkit.Material.TRIDENT) return;
        }

        int level = plugin.getUpgradeManager().getUpgradeLevel(item);
        if (level > 0) {
            // [FIX] 추가 투사체 발사를 시작하기 전에 잠금을 설정합니다.
            launchingPlayers.add(player.getUniqueId());

            // 원본 삼지창에도 패시브 효과 키를 부여합니다.
            trident.setMetadata(TRIDENT_PASSIVE_LEVEL_KEY, new FixedMetadataValue(plugin, level));
            launchAdditionalTridents(player, level, trident.getVelocity());
        }
    }

    @EventHandler
    public void onPlayerRiptide(PlayerRiptideEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();

        if (item.getType() != org.bukkit.Material.TRIDENT) {
            return;
        }

        // [FIX] 급류 사용 시 투사체가 과도하게 생성되는 것을 막기 위해 쿨다운을 적용합니다.
        if (riptideCooldown.contains(player.getUniqueId())) {
            return;
        }

        // [FIX] 재귀 방지 잠금을 확인합니다.
        if (launchingPlayers.contains(player.getUniqueId())) {
            return;
        }

        int level = plugin.getUpgradeManager().getUpgradeLevel(item);
        if (level > 0) {
            // 쿨다운을 설정합니다.
            riptideCooldown.add(player.getUniqueId());
            new BukkitRunnable() {
                @Override
                public void run() {
                    riptideCooldown.remove(player.getUniqueId());
                }
            }.runTaskLater(plugin, 10L); // 0.5초 쿨다운

            // [FIX] 추가 투사체 발사를 시작하기 전에 잠금을 설정합니다.
            launchingPlayers.add(player.getUniqueId());

            // 플레이어가 바라보는 방향(눈 위치 기준)으로 발사합니다.
            Vector velocity = player.getEyeLocation().getDirection().multiply(2.0);
            launchAdditionalTridents(player, level, velocity);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onDamageByTrident(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Trident trident)) {
            return;
        }

        // 추가 삼지창인지 확인 (메타데이터 사용)
        if (!trident.hasMetadata(TRIDENT_PASSIVE_LEVEL_KEY)) {
            return;
        }

        // 1. 시전자 자신에게 피해를 주지 않도록 설정
        if (trident.getShooter() != null && trident.getShooter().equals(event.getEntity())) {
            event.setCancelled(true);
            return;
        }

        // 2. 다른 추가 삼지창에게 피해를 주지 않도록 설정
        if (event.getEntity() instanceof Trident && event.getEntity().hasMetadata(TRIDENT_PASSIVE_LEVEL_KEY)) {
            event.setCancelled(true);
            return;
        }

        if (!(event.getEntity() instanceof LivingEntity victim)) return;

        int level = trident.getMetadata(TRIDENT_PASSIVE_LEVEL_KEY).getFirst().asInt();
        if (level <= 0) return;

        GameConfigManager configManager = plugin.getGameConfigManager();
        int duration = (int) (configManager.getConfig().getDouble("upgrade.generic-bonuses.trident.slowness-duration-seconds", 3.0) * 20);
        int amplifier = configManager.getConfig().getInt("upgrade.generic-bonuses.trident.slowness-level", 2) - 1;
        victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, duration, amplifier));
    }

    private void launchAdditionalTridents(Player player, int level, Vector direction) {
        final Random random = new Random();

        GameConfigManager configManager = plugin.getGameConfigManager();
        // config.yml에 명시된 키를 사용하여 퍼지는 정도를 설정합니다.
        double spreadRadius = configManager.getConfig().getDouble("upgrade.generic-bonuses.trident.spread-radius", 3.0);
        double spreadVelocityMultiplier = configManager.getConfig().getDouble("upgrade.generic-bonuses.trident.spread-velocity-multiplier", 0.1);

        // 모든 추가 투사체를 즉시 발사합니다.
        try {
            for (int i = 0; i < level; i++) {
                // 속도에 무작위성을 더해 투사체를 퍼뜨립니다. 이 방식은 플레이어로부터 점점 퍼져나가는 효과를 만듭니다.
                double offsetX = (random.nextDouble() - 0.5) * spreadRadius;
                double offsetY = (random.nextDouble() - 0.5) * spreadRadius;
                double offsetZ = (random.nextDouble() - 0.5) * spreadRadius;

                Vector randomOffset = new Vector(offsetX, offsetY, offsetZ).normalize().multiply(spreadVelocityMultiplier);
                Vector finalVelocity = direction.clone().add(randomOffset);

                // 플레이어의 눈 위치에서, 계산된 최종 속도로 삼지창을 발사합니다.
                Trident additionalTrident = player.launchProjectile(Trident.class, finalVelocity);

                // 추가된 삼지창에도 패시브 효과를 적용할 수 있도록 레벨 정보를 저장합니다.
                additionalTrident.setMetadata(TRIDENT_PASSIVE_LEVEL_KEY, new FixedMetadataValue(plugin, level));
                additionalTrident.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
                scheduleTridentDespawn(additionalTrident);
            }
        } finally {
            // [FIX] 모든 투사체 발사가 끝나면 잠금을 해제하여, 다음번 발사가 가능하도록 합니다.
            // launchProjectile 이벤트는 동기적으로 처리되므로, 즉시 잠금을 해제해도 안전합니다.
            launchingPlayers.remove(player.getUniqueId());
        }
    }

    private void scheduleTridentDespawn(Trident trident) {
        new BukkitRunnable() {
            @Override
            public void run() {
                // 삼지창이 유효하지 않거나, 이미 사라졌거나, 아직 공중에 떠 있다면 작업을 계속합니다.
                if (!trident.isValid() || trident.isDead()) {
                    this.cancel();
                    return;
                }

                // 물 속에 있을 때 속도 저하를 상쇄하여 물을 통과하는 것처럼 보이게 합니다.
                // 이 값은 실험적으로 조정될 수 있습니다.
                if (trident.isInWater()) {
                    // [FIX] 삼지창 속도가 물 속에서 무한히 증폭되어 서버를 멈추게 하는 버그를 수정합니다.
                    // 기존 코드는 매 틱마다 속도를 1.2375배하여 기하급수적인 속도 증가를 유발했습니다.
                    // 이를 방지하기 위해 속도 상한선을 도입합니다.
                    Vector newVelocity = trident.getVelocity().multiply(1.2375);
                    final double maxSpeed = 60.0; // 초당 60블록의 상한 속도 (매우 빠름)
                    if (newVelocity.lengthSquared() > maxSpeed * maxSpeed) {
                        // 속도가 상한선을 초과하면, 방향은 유지하되 속도만 상한선으로 제한합니다.
                        newVelocity = newVelocity.normalize().multiply(maxSpeed);
                    }
                    trident.setVelocity(newVelocity);
                }

                // 삼지창이 땅에 박혔는지 확인합니다.
                if (trident.isOnGround()) {
                    // 땅에 박힌 후 잠시 후(예: 0.1초)에 사라지게 하여 시각적으로 자연스럽게 만듭니다.
                    trident.getWorld().spawnParticle(Particle.CRIT, trident.getLocation(), 10, 0.1, 0.1, 0.1, 0.1);
                    trident.remove();
                    this.cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L); // 1틱 후부터 매 틱마다 확인하여 즉각적인 반응
    }
}