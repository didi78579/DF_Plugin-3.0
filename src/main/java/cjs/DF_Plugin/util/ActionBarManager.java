package cjs.DF_Plugin.util;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.upgrade.specialability.ISpecialAbility;
import cjs.DF_Plugin.upgrade.specialability.SpecialAbilityManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class ActionBarManager {

    private final SpecialAbilityManager specialAbilityManager;

    public ActionBarManager(DF_Main plugin, SpecialAbilityManager specialAbilityManager) {
        this.specialAbilityManager = specialAbilityManager;
        startUpdater(plugin);
    }

    private void startUpdater(DF_Main plugin) {
        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    updateActionBar(player);
                }
            }
        }.runTaskTimer(plugin, 0L, 2L); // 0.1초마다 액션바 업데이트
    }

    private void updateActionBar(Player player) {
        UUID playerUUID = player.getUniqueId();
        Optional<SpecialAbilityManager.LungeGaugeInfo> lungeInfoOpt = specialAbilityManager.getLungeGaugeInfo(player);

        // 1. 창을 들고 있을 경우, 기력 게이지 바만 표시합니다.
        if (isHoldingLungeWeapon(player)) {
            if (lungeInfoOpt.isPresent() && lungeInfoOpt.get().maxGauge() > 0) {
                player.sendActionBar(formatLungeGaugeAsBar(lungeInfoOpt.get()));
                return; // 다른 정보는 표시하지 않고 종료
            }
        }

        // 2. 창을 들고 있지 않을 경우, 다른 정보들과 함께 기력을 %로 표시합니다.
        List<Component> otherParts = new ArrayList<>();

        // 쿨다운 정보 추가
        Map<String, SpecialAbilityManager.CooldownInfo> cooldowns = specialAbilityManager.getPlayerCooldowns(playerUUID);
        if (cooldowns != null) {
            cooldowns.forEach((key, info) -> {
                long remainingMillis = info.endTime() - System.currentTimeMillis();
                if (remainingMillis > 0) {
                    ISpecialAbility ability = specialAbilityManager.getRegisteredAbility(key);
                    if (ability != null && !ability.showInActionBar()) {
                        return;
                    }
                    int remainingSeconds = (int) Math.ceil(remainingMillis / 1000.0);
                    otherParts.add(Component.text(String.format("%s: %d초", info.displayName(), remainingSeconds)));
                }
            });
        }

        // 충전량 정보 추가
        Map<String, SpecialAbilityManager.ChargeInfo> charges = specialAbilityManager.getPlayerCharges(playerUUID);
        if (charges != null) {
            charges.forEach((key, info) -> {
                // '윈드차지'의 경우, 철퇴를 들고 있거나 충전량이 최대가 아닐 때만 표시
                if ("wind_charge".equals(key)) {
                    if (isHoldingMace(player) || info.current() < info.max()) {
                        otherParts.add(formatCharges(info));
                    }
                } else if (info.visible()) { // 다른 능력들은 기존 로직 따름
                    otherParts.add(formatCharges(info));
                }
            });
        }

        // 기력이 최대치가 아니고, 0보다 클 경우 %로 표시하여 추가합니다.
        if (lungeInfoOpt.isPresent() && lungeInfoOpt.get().currentGauge() < lungeInfoOpt.get().maxGauge() && lungeInfoOpt.get().maxGauge() > 0) {
            otherParts.add(formatLungeGaugeAsPercentage(lungeInfoOpt.get()));
        }

        if (otherParts.isEmpty()) {
            return;
        }

        // 모든 정보를 한 줄로 조합하여 표시합니다.
        Component finalMessage = Component.join(JoinConfiguration.separator(Component.text("  ")), otherParts);
        player.sendActionBar(finalMessage);
    }

    private Component formatCharges(SpecialAbilityManager.ChargeInfo info) {
        // 능력에 설정된 표시 유형에 따라 포맷을 결정합니다.
        if (info.displayType() == ISpecialAbility.ChargeDisplayType.FRACTION) {
            return Component.text(String.format("%s %d/%d", info.displayName(), info.current(), info.max()));
        }

        // 점(dot)으로 표시합니다.
        String displayName = info.displayName();
        String colorCode = "§e"; // 기본값: 노랑

        // 능력의 표시 이름에서 색상 코드를 추출합니다.
        if (displayName.length() >= 2 && displayName.charAt(0) == '§') {
            colorCode = displayName.substring(0, 2);
        }

        Component component = Component.text(displayName + " ");
        for (int i = 0; i < info.max(); i++) {
            if (i < info.current()) {
                component = component.append(Component.text("●").color(NamedTextColor.NAMES.value(colorCode.substring(1))));
            } else {
                component = component.append(Component.text("○").color(NamedTextColor.DARK_GRAY));
            }
        }
        return component;
    }

    private Component formatLungeGaugeAsBar(SpecialAbilityManager.LungeGaugeInfo info) {
        int maxBars = 25;
        int filledBars = (int) ((info.currentGauge() / info.maxGauge()) * maxBars);

        Component component = Component.empty();
        NamedTextColor color;

        double percentage = info.currentGauge() / info.maxGauge();
        if (percentage <= 0.2) {
            color = NamedTextColor.RED;
        } else if (percentage <= 0.5) {
            color = NamedTextColor.YELLOW;
        } else {
            color = NamedTextColor.GREEN;
        }

        component = component.append(Component.text("[").color(color));
        for (int i = 0; i < maxBars; i++) {
            component = component.append(Component.text(i < filledBars ? "=" : "-").color(i < filledBars ? color : NamedTextColor.DARK_GRAY));
        }
        component = component.append(Component.text("]").color(color));
        return component;
    }

    private Component formatLungeGaugeAsPercentage(SpecialAbilityManager.LungeGaugeInfo info) {
        double percentage = info.currentGauge() / info.maxGauge();
        int percentageInt = (int) (percentage * 100);

        NamedTextColor color;

        if (percentage <= 0.2) {
            color = NamedTextColor.RED;
        } else if (percentage <= 0.5) {
            color = NamedTextColor.YELLOW;
        } else {
            color = NamedTextColor.GREEN;
        }

        return Component.text(String.format("기력: %d%%", percentageInt)).color(color);
    }

    private boolean isHoldingLungeWeapon(Player player) {
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (isLungeWeapon(mainHand)) return true;

        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (isLungeWeapon(offHand)) return true;

        return false;
    }

    private boolean isLungeWeapon(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasLore()) {
            return false;
        }
        List<Component> lore = meta.lore();
        if (lore == null) return false;

        return lore.stream()
                .anyMatch(line -> PlainTextComponentSerializer.plainText().serialize(line).contains("최대 기력"));
    }

    private boolean isHoldingMace(Player player) {
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (isMace(mainHand)) return true;

        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (isMace(offHand)) return true;

        return false;
    }

    private boolean isMace(ItemStack item) {
        if (item != null && item.getType() == Material.MACE) {
            return DF_Main.getInstance().getUpgradeManager().getUpgradeLevel(item) >= 10;
        }
        return false;
    }
}