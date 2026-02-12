package cjs.DF_Plugin.pylon.beacongui.giftbox;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.pylon.clan.Clan;
import cjs.DF_Plugin.util.PluginUtils;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;

public class GiftBoxManager {
    private final DF_Main plugin;
    private static final String PREFIX = PluginUtils.colorize("&e[선물상자] &f");
    private BukkitTask refillTask;

    public GiftBoxManager(DF_Main plugin) {
        this.plugin = plugin;
    }

    // --- Public Methods ---

    /**
     * 플레이어에게 선물상자 GUI를 엽니다.
     * @param player GUI를 열 플레이어
     */
    public void openGiftBox(Player player) {
        Clan clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
        if (clan == null) {
            // 버튼이 표시되지 않아야 하므로, 만약을 대비해 조용히 실패 처리
            return;
        }

        // 이 선물상자를 다른 가문원이 이미 사용하고 있는지 확인합니다.
        if (plugin.getClanManager().isGiftBoxInUse(clan)) {
            UUID viewerId = plugin.getClanManager().getGiftBoxViewer(clan);
            // 자기 자신이 이미 열고 있는 경우는 거의 없지만, 만약을 위해 체크합니다.
            if (viewerId != null && !viewerId.equals(player.getUniqueId())) {
                player.sendMessage(PREFIX + "§c다른 가문원이 이미 선물상자를 사용하고 있습니다.");
                return;
            }
        }

        Inventory giftBoxInventory = plugin.getClanManager().getGiftBoxInventory(clan);
        // 아무도 사용하고 있지 않으므로, 플레이어에게 선물상자를 엽니다.
        player.openInventory(giftBoxInventory);
    }

    /**
     * 선물상자 보충 작업을 시작하거나 재개합니다.
     */
    public void scheduleRefillTask() {
        // 기존 작업이 있다면 취소합니다.
        if (refillTask != null) {
            refillTask.cancel();
        }

        long cooldownMinutes = plugin.getGameConfigManager().getConfig().getLong("pylon.giftbox.cooldown-minutes", 5);
        long cooldownTicks = cooldownMinutes * 60 * 20;

        // 재시작 시 남은 시간을 계산합니다.
        long nextRefillTime = plugin.getGameStartManager().getNextGiftBoxRefillTime();
        long initialDelayTicks = (nextRefillTime > 0) ? Math.max(0, (nextRefillTime - System.currentTimeMillis()) / 50) : cooldownTicks;

        refillTask = new GiftBoxRefillTask().runTaskTimer(plugin, initialDelayTicks, cooldownTicks);
        plugin.getLogger().info("[선물상자] 보충 타이머를 " + (initialDelayTicks / 20) + "초 후에 시작합니다.");
    }

    /**
     * 선물상자 보충 작업을 중지합니다.
     */
    public void stopRefillTask() {
        if (refillTask != null) {
            refillTask.cancel();
            refillTask = null;
        }
    }

    // --- Inner Class for the Task ---

    private class GiftBoxRefillTask extends BukkitRunnable {
        @Override
        public void run() {
            plugin.getClanManager().refillAllGiftBoxes(System.currentTimeMillis());
        }
    }
}