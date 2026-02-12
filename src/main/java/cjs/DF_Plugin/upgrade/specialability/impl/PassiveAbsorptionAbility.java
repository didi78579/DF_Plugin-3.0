package cjs.DF_Plugin.upgrade.specialability.impl;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.upgrade.specialability.ISpecialAbility;
import cjs.DF_Plugin.upgrade.specialability.SpecialAbilityManager;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PassiveAbsorptionAbility implements ISpecialAbility, Listener {

    private final Map<UUID, BukkitTask> activeTasks = new ConcurrentHashMap<>();
    private final DF_Main plugin = DF_Main.getInstance();

    @Override
    public String getInternalName() {
        return "passive_absorption";
    }

    @Override
    public String getDisplayName() {
        return "§6보호막";
    }

    @Override
    public String getDescription() {
        return "§7피해를 흡수하는 보호막을 최대 4개까지 얻습니다.";
    }

    @Override
    public double getCooldown() {
        // 이 능력은 패시브이므로, 전통적인 의미의 쿨다운은 없습니다.
        return 0.0;
    }

    @Override
    public int getMaxCharges() {
        // 보호막의 최대 스택 수를 설정에서 가져옵니다.
        return plugin.getGameConfigManager().getConfig().getInt("upgrade.special-abilities.passive_absorption.details.max-shields", 4);
    }

    @Override
    public boolean showInActionBar() {
        return false; // 액션바에 쿨다운은 표시하지 않고, 충전량(보호막 스택)만 표시합니다.
    }

    @Override
    public ChargeDisplayType getChargeDisplayType() {
        return ChargeDisplayType.DOTS;
    }

    @Override
    public void onEquip(Player player, ItemStack item) {
        UUID playerUUID = player.getUniqueId();
        if (activeTasks.containsKey(playerUUID)) return;

        SpecialAbilityManager manager = plugin.getSpecialAbilityManager();
        final int maxShields = getMaxCharges();

        // 장착 시 충전량 정보를 0으로 초기화하고, 액션바에 표시되도록 설정합니다.
        if (manager.getChargeInfo(player, this) == null) {
            manager.setChargeInfo(player, this, 0, maxShields);
        }
        manager.setChargeVisibility(player, this, true);

        long intervalTicks = plugin.getGameConfigManager().getConfig().getLong("upgrade.special-abilities.passive_absorption.details.regen-interval-seconds", 4) * 20L;

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                Player currentPlayer = Bukkit.getPlayer(playerUUID);
                if (currentPlayer == null || !currentPlayer.isOnline()) {
                    cancel();
                    activeTasks.remove(playerUUID);
                    return;
                }

                // 능력이 쿨다운 상태(최근에 피해를 받음)인지 확인합니다.
                if (manager.isOnCooldown(currentPlayer, PassiveAbsorptionAbility.this)) {
                    return; // 아직 쿨다운이므로 재생성하지 않습니다.
                }

                SpecialAbilityManager.ChargeInfo currentInfo = manager.getChargeInfo(currentPlayer, PassiveAbsorptionAbility.this);
                int currentShields = (currentInfo != null) ? currentInfo.current() : 0;

                if (currentShields < maxShields) {
                    manager.setChargeInfo(currentPlayer, PassiveAbsorptionAbility.this, currentShields + 1, maxShields);
                    currentPlayer.getWorld().playSound(currentPlayer.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5f, 1.5f);
                }
            }
        }.runTaskTimer(DF_Main.getInstance(), 0L, intervalTicks);
        activeTasks.put(playerUUID, task);
    }

    @Override
    public void onCleanup(Player player) {
        BukkitTask task = activeTasks.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
        }
        // 액션바에서 충전량 표시를 숨깁니다.
        plugin.getSpecialAbilityManager().setChargeVisibility(player, this, false);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageEvent event) {
        // ignoreCancelled = true 옵션으로 인해, 이 이벤트는 다른 능력에 의해 피해가
        // 이미 취소된 경우에는 호출되지 않습니다.
        // HIGHEST 우선순위는 다른 대부분의 피해 처리 로직이 실행된 후에 이 코드가 실행되도록 보장합니다.

        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        SpecialAbilityManager manager = plugin.getSpecialAbilityManager();
        if (!manager.isAbilityActive(player, this.getInternalName())) {
            return;
        }

        SpecialAbilityManager.ChargeInfo chargeInfo = manager.getChargeInfo(player, this);
        if (chargeInfo != null && chargeInfo.current() > 0) {
            event.setCancelled(true);
            manager.setChargeInfo(player, this, chargeInfo.current() - 1, getMaxCharges());

            // 보호막이 피해를 흡수하면, 재생성 대기 시간(쿨다운)을 설정합니다.
            double noDamageDelaySeconds = plugin.getGameConfigManager().getConfig().getDouble("upgrade.special-abilities.passive_absorption.details.no-damage-delay-seconds", 20.0);
            manager.setCooldown(player, this, noDamageDelaySeconds);

            player.getWorld().playSound(player.getLocation(), Sound.ITEM_TRIDENT_RETURN, 1.0f, 0.8f);
            player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 20, 0, false, false));
            player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.1);
        }
    }
}