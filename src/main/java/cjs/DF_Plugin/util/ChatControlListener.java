package cjs.DF_Plugin.util;

import cjs.DF_Plugin.DF_Main;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;

public class ChatControlListener implements Listener {

    private final DF_Main plugin;

    public ChatControlListener(DF_Main plugin) {
        this.plugin = plugin;
    }

    /**
     * 'utility.chat-disabled' 설정이 true일 경우 전체 채팅을 막습니다.
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        boolean chatDisabled = plugin.getGameConfigManager().getConfig().getBoolean("utility.chat-disabled", true);
        if (chatDisabled) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§c[알림] §f현재 전체 채팅이 비활성화되어 있습니다.");
        }
    }

    /**
     * 'utility.chat-disabled' 설정이 true일 경우 도전과제 달성 메시지를 숨깁니다.
     */
    @EventHandler
    public void onAdvancementDone(PlayerAdvancementDoneEvent event) {
        boolean chatDisabled = plugin.getGameConfigManager().getConfig().getBoolean("utility.chat-disabled", true);
        // 도전과제 메시지도 전체 채팅의 일부로 간주하여 비활성화합니다.
        if (chatDisabled) {
            // getDisplay()가 null이 아닌 발전과제(예: 일반 과제, 목표, 도전)에 대해서만 메시지를 숨깁니다.
            // 레시피 잠금 해제와 같은 숨겨진 발전과제는 getDisplay()가 null을 반환하므로 영향을 받지 않습니다.
            // shouldAnnounceToChat() 메서드는 API 버전에 따라 존재하지 않을 수 있어, 더 안전한 방식으로 변경합니다.
            if (event.getAdvancement().getDisplay() != null) {
                event.message(null); // 메시지를 null로 설정하여 채팅에 표시되지 않도록 합니다.
            }
        }
    }
}