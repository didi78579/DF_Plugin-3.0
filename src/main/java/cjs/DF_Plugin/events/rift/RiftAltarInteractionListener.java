package cjs.DF_Plugin.events.rift;

import cjs.DF_Plugin.DF_Main;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.player.PlayerInteractEvent;

public class RiftAltarInteractionListener implements Listener {

    private final DF_Main plugin;

    public RiftAltarInteractionListener(DF_Main plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        if (plugin.getRiftManager().isProtectedZone(event.getBlockPlaced().getLocation())) {
            event.getPlayer().sendMessage("§c[차원의 균열] §f제단 위에는 블록을 설치할 수 없습니다.");
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        if (plugin.getRiftManager().isAltarBlock(event.getBlock().getLocation())) {
            event.getPlayer().sendMessage("§c[차원의 균열] §f이 제단은 파괴할 수 없습니다.");
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.LEFT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null || event.getClickedBlock().getType() != Material.DRAGON_EGG) return;

        Location eggLoc = plugin.getRiftManager().getAltarLocation();
        if (eggLoc != null && eggLoc.equals(event.getClickedBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPistonExtend(BlockPistonExtendEvent event) {
        for (Block block : event.getBlocks()) {
            // 피스톤에 의해 밀리는 블록 중 하나라도 제단 블록이면 이벤트를 취소합니다.
            if (plugin.getRiftManager().isAltarBlock(block.getLocation())) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (Block block : event.getBlocks()) {
            // 피스톤에 의해 당겨지는 블록 중 하나라도 제단 블록이면 이벤트를 취소합니다.
            if (plugin.getRiftManager().isAltarBlock(block.getLocation())) {
                event.setCancelled(true);
                return;
            }
        }
    }
}