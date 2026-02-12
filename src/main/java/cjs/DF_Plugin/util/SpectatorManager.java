package cjs.DF_Plugin.util;

import cjs.DF_Plugin.DF_Main;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.Comparator;
import java.util.Optional;

public class SpectatorManager implements Listener {

    private final DF_Main plugin;
    private BukkitTask spectatorCheckTask;
    private static final String PREFIX = "§c[관전] §f";


    public SpectatorManager(DF_Main plugin) {
        this.plugin = plugin;
        startSpectatorCheckTask();
    }

    /**
     * 플레이어를 제한된 관전자 모드로 설정합니다.
     * - 게임 모드를 SPECTATOR로 변경합니다.
     * - 다른 플레이어를 관전하도록 시도합니다.
     * @param player 관전자 모드로 설정할 플레이어
     */
    public void setSpectator(Player player) {
        player.setGameMode(GameMode.SPECTATOR);

        // 즉시 다른 플레이어를 관전하도록 시도합니다.
        // 관전할 대상이 없을 경우, onPlayerMove 이벤트가 이동을 막아줍니다.
        trySetSpectatorTarget(player);
    }

    /**
     * 관전자 모드에서 자유로운 이동을 제한합니다.
     * 다른 플레이어를 관전하고 있지 않을 경우 움직임을 취소합니다.
     */
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.SPECTATOR && player.getSpectatorTarget() == null) {
            // 플레이어가 자유롭게 움직이는 관전자 모드일 경우 이동을 취소합니다.
            // 단, 블록 단위 이동이 아니면 취소하지 않아 부드러운 시점 전환을 방해하지 않습니다.
            if (event.getFrom().getBlockX() != event.getTo().getBlockX() ||
                event.getFrom().getBlockY() != event.getTo().getBlockY() ||
                event.getFrom().getBlockZ() != event.getTo().getBlockZ()) {
                event.setCancelled(true);
            }
        }
    }

    /**
     * 관전 중이던 플레이어가 로그아웃할 경우, 다른 플레이어를 관전하도록 시도합니다.
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player disconnectedPlayer = event.getPlayer();
        Bukkit.getOnlinePlayers().stream()
                .filter(p -> p.getGameMode() == GameMode.SPECTATOR && disconnectedPlayer.equals(p.getSpectatorTarget()))
                .forEach(this::trySetSpectatorTarget);
    }

    /**
     * 게임 모드가 SPECTATOR로 변경될 때, 자동으로 관전 대상을 지정합니다.
     */
    @EventHandler
    public void onPlayerGameModeChange(PlayerGameModeChangeEvent event) {
        if (event.getNewGameMode() == GameMode.SPECTATOR) {
            // 다음 틱에 관전 대상을 설정하여, 게임 모드 변경이 완전히 적용된 후 실행되도록 합니다.
            new BukkitRunnable() {
                @Override
                public void run() {
                    setSpectator(event.getPlayer());
                }
            }.runTaskLater(plugin, 1L);
        }
    }

    private boolean trySetSpectatorTarget(Player spectator) {
        // 먼저, 관전자와 같은 월드에 있는 가장 가까운 플레이어를 찾습니다.
        Optional<? extends Player> targetPlayer = Bukkit.getOnlinePlayers().stream()
                .filter(p -> !p.equals(spectator)) // 자기 자신은 제외
                .filter(p -> p.getGameMode() != GameMode.SPECTATOR) // 다른 관전자는 관전하지 않음
                .filter(p -> p.getWorld().equals(spectator.getWorld())) // 같은 월드에 있는 플레이어만
                .min(Comparator.comparingDouble(p -> p.getLocation().distanceSquared(spectator.getLocation())));

        // 만약 같은 월드에 관전할 대상이 없다면, 다른 월드의 플레이어라도 찾습니다.
        if (targetPlayer.isEmpty()) {
            targetPlayer = Bukkit.getOnlinePlayers().stream()
                    .filter(p -> !p.equals(spectator))
                    .filter(p -> p.getGameMode() != GameMode.SPECTATOR)
                    .findAny();
        }

        targetPlayer.ifPresent(spectator::setSpectatorTarget);
        return targetPlayer.isPresent();
    }

    /**
     * 주기적으로 관전자 모드 플레이어가 다른 플레이어를 관전하고 있는지 확인하고,
     * 그렇지 않다면 관전 대상을 지정합니다.
     */
    private void startSpectatorCheckTask() {
        if (spectatorCheckTask != null) {
            spectatorCheckTask.cancel();
        }
        spectatorCheckTask = new BukkitRunnable() {
            @Override
            public void run() {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (player.getGameMode() == GameMode.SPECTATOR && player.getSpectatorTarget() == null) {
                        trySetSpectatorTarget(player); // 주기적으로 관전할 플레이어를 찾아봅니다.
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L); // 1초마다 실행
    }


    public void stopSpectatorCheckTask() {
        if (spectatorCheckTask != null) {
            spectatorCheckTask.cancel();
            spectatorCheckTask = null;
        }
    }
}