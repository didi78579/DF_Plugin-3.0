package cjs.DF_Plugin.util;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.pylon.clan.Clan;
import cjs.DF_Plugin.pylon.clan.ClanManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

public class PlayerTagManager {

    private final DF_Main plugin;
    private final ClanManager clanManager;

    public PlayerTagManager(DF_Main plugin, ClanManager clanManager) {
        this.plugin = plugin;
        this.clanManager = clanManager;
    }

    /**
     * 플레이어의 클랜 정보를 바탕으로 이름표와 탭리스트를 업데이트합니다.
     * @param player 업데이트할 플레이어
     */
    public void updatePlayerTag(Player player) {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        Clan clan = clanManager.getClanByPlayer(player.getUniqueId());

        // 먼저 플레이어를 기존의 모든 클랜 팀에서 제거하여 충돌을 방지합니다.
        Team oldTeam = scoreboard.getEntryTeam(player.getName());
        if (oldTeam != null && oldTeam.getName().startsWith("dfc_")) {
            oldTeam.removeEntry(player.getName());
        }

        // 클랜이 없으면, 탭리스트 이름을 초기화하고 함수를 종료합니다.
        if (clan == null) {
            player.setPlayerListName(player.getName());
            return;
        }

        // --- 새로운 클랜 태그 적용 ---
        String prefix = clan.getColor() + "[" + clan.getName() + "] ";
        // 가문 이름의 해시코드를 사용하여 고유하고 영구적인 팀 이름을 생성합니다.
        // 이는 한글 등 비-알파벳 문자를 포함한 가문 이름이 충돌하는 것을 방지합니다.
        String teamName = "dfc_" + Integer.toHexString(clan.getName().hashCode());

        player.setPlayerListName(prefix + "§r" + player.getName());

        Team team = scoreboard.getTeam(teamName);
        if (team == null) {
            team = scoreboard.registerNewTeam(teamName);
        }

        team.setPrefix(prefix);
        team.setColor(ChatColor.WHITE); // 이름 색상을 흰색으로 고정하여 접두사 색상만 적용되도록 함
        team.setCanSeeFriendlyInvisibles(true); // 투명화한 팀원을 볼 수 있게 설정

        // 플레이어를 새로운 팀에 추가합니다.
        team.addEntry(player.getName());
    }

    /**
     * 플레이어에게 적용된 모든 클랜 태그를 제거합니다.
     * @param player 태그를 제거할 플레이어
     */
    public void removePlayerTag(Player player) {
        // 이 메서드는 이제 updatePlayerTag를 호출하는 래퍼(wrapper)가 됩니다.
        // ClanManager에서 플레이어의 클랜 정보가 먼저 제거되므로,
        // updatePlayerTag를 호출하면 clan이 null로 감지되어 태그가 정상적으로 제거됩니다.
        updatePlayerTag(player);
    }

    /**
     * 클랜 해체 시 사용된 스코어보드 팀을 정리합니다.
     * @param clan 해체된 클랜
     */
    public void cleanupClanTeam(Clan clan) {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        String teamName = "dfc_" + Integer.toHexString(clan.getName().hashCode());
        Team team = scoreboard.getTeam(teamName);
        if (team != null) {
            team.unregister();
        }
    }
}