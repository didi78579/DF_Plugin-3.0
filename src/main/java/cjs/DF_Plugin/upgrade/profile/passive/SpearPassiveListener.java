package cjs.DF_Plugin.upgrade.profile.passive;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.upgrade.specialability.SpecialAbilityManager;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SpearPassiveListener implements Listener {

    private final DF_Main plugin;
    private final SpecialAbilityManager specialAbilityManager;
    private final cjs.DF_Plugin.upgrade.UpgradeManager upgradeManager;
    private final Map<UUID, BukkitRunnable> activeLungeTasks = new ConcurrentHashMap<>();

    // LungeAbility에서 가져온 상수들
    public static final double GAUGE_PER_LEVEL = 100.0;
    public static final double GAUGE_CONSUMPTION_PER_SECOND = 125.0;
    public static final double GAUGE_REGEN_PER_SECOND = 40.0; // 2배 증가

    private static final UUID LUNGE_SPEED_MODIFIER_UUID = UUID.fromString("E4A2B1C3-D5E6-F7A8-B9C0-D1E2F3A4B5C6");

    public SpearPassiveListener(DF_Main plugin) {
        this.plugin = plugin;
        this.specialAbilityManager = plugin.getSpecialAbilityManager();
        this.upgradeManager = plugin.getUpgradeManager();
        startPassiveLungeTicker();
    }

    private void startPassiveLungeTicker() { // 리스너 생성 시점에 타이머를 시작합니다.
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) { // 모든 온라인 플레이어 순회
                    ItemStack lungeWeapon = findLungeWeaponInHands(player);
                    int level = (lungeWeapon != null) ? upgradeManager.getUpgradeLevel(lungeWeapon) : specialAbilityManager.getLastLungeLevel(player.getUniqueId());

                    if (level <= 0) continue; // 기력을 가진 무기를 사용한 적이 없는 플레이어는 건너뜁니다.

                    double maxGauge = (double) level * GAUGE_PER_LEVEL;
                    double currentGauge = specialAbilityManager.getAbilityGauge(player, "lunge");

                    boolean isUsingSpear = lungeWeapon != null && player.isHandRaised() &&
                            (lungeWeapon.equals(player.getInventory().getItemInMainHand()) || lungeWeapon.equals(player.getInventory().getItemInOffHand()));

                    if (isUsingSpear && currentGauge > 0) {
                        // 무기를 들고 우클릭 중일 때: 기력 소모
                        if (!specialAbilityManager.isPlayerUsingAbility(player, "lunge")) {
                            specialAbilityManager.setPlayerUsingAbility(player, "lunge", true);
                        }
                        handleLungeCharging(player, currentGauge);
                    } else {
                        // 그 외 모든 경우 (무기를 안 들었거나, 우클릭을 안 하거나, 기력이 없거나): 기력 회복
                        handleLungeRegeneration(player, specialAbilityManager.getAbilityGauge(player, "lunge"), maxGauge);
                        specialAbilityManager.setPlayerUsingAbility(player, "lunge", false);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    public void stopLungeTask(Player player) {
        // 이제 타이머는 하나만 실행되므로, 플레이어의 상태만 정리합니다.
        removeLungeSpeedModifier(player);
        specialAbilityManager.setPlayerUsingAbility(player, "lunge", false);
        activeLungeTasks.remove(player.getUniqueId());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        stopLungeTask(event.getPlayer());
    }

    // --- Lunge Logic (이전 LungeAbilityTask에서 이동) ---

    private void handleLungeCharging(Player player, double currentGauge) {
        double newGauge = Math.max(0, currentGauge - (GAUGE_CONSUMPTION_PER_SECOND / 20.0));
        specialAbilityManager.setAbilityGauge(player, "lunge", newGauge);
        // '돌진' 시작 즉시 이동 속도를 2배로 만듭니다. (1.0 = 100% 증가)
        applyLungeSpeedModifier(player, 1.0);
    }

    private void handleLungeRegeneration(Player player, double currentGauge, double maxGauge) {
        removeLungeSpeedModifier(player);

        if (currentGauge < maxGauge) {
            double newGauge = Math.min(maxGauge, currentGauge + (GAUGE_REGEN_PER_SECOND / 20.0));
            specialAbilityManager.setAbilityGauge(player, "lunge", newGauge);
        }
    }

    private void applyLungeSpeedModifier(Player player, double multiplier) {
        AttributeInstance attribute = player.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (attribute == null) return;

        removeLungeSpeedModifier(player);

        AttributeModifier modifier = new AttributeModifier(LUNGE_SPEED_MODIFIER_UUID, "lunge_speed_boost", multiplier, AttributeModifier.Operation.MULTIPLY_SCALAR_1);
        attribute.addModifier(modifier);
    }

    private void removeLungeSpeedModifier(Player player) {
        AttributeInstance attribute = player.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (attribute != null) {
            attribute.getModifiers().stream()
                    .filter(m -> m.getUniqueId().equals(LUNGE_SPEED_MODIFIER_UUID))
                    .findFirst()
                    .ifPresent(attribute::removeModifier);
        }
    }

    private boolean isLungeWeapon(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        List<String> lore = item.getItemMeta().getLore();
        if (lore == null) {
            return false;
        }
        for (String line : lore) {
            if (line.contains("최대 기력")) {
                return true;
            }
        }
        return false;
    }

    private ItemStack findLungeWeaponInHands(Player player) {
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (isLungeWeapon(mainHand)) return mainHand;
        return null; // 현재는 주 손만 확인, 필요시 부 손도 추가 가능
    }
}