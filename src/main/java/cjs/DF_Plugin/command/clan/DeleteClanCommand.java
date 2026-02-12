package cjs.DF_Plugin.command.clan;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.pylon.clan.Clan;
import cjs.DF_Plugin.pylon.clan.ClanManager;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class DeleteClanCommand {

    private final DF_Main plugin;
    private final ClanManager clanManager;
    private final ClanUIManager uiManager;

    public DeleteClanCommand(DF_Main plugin) {
        this.plugin = plugin;
        this.clanManager = plugin.getClanManager();
        this.uiManager = plugin.getClanManager().getUiManager();
    }

    public boolean handle(@NotNull Player player, @NotNull String[] args) {
        Clan clan = clanManager.getClanByPlayer(player.getUniqueId());
        if (clan == null) {
            player.sendMessage("§c[가문] §c소속된 가문이 없습니다.");
            return true;
        }

        if (!clan.getLeader().equals(player.getUniqueId())) {
            player.sendMessage("§c[가문] §c가문 삭제는 리더만 가능합니다.");
            return true;
        }

        if (args.length == 0) {
            clanManager.requestDeletion(player, clan);
            uiManager.openDeletionConfirmation(player, clan.getDisplayName());
            return true;
        }

        String action = args[0].toLowerCase();
        String confirmation = clanManager.getDeletionConfirmation(player);

        if (confirmation == null || !confirmation.equals(clan.getName())) {
            player.sendMessage("§c[가문] §c삭제 절차를 먼저 시작해주세요. (/df clan delete)");
            return true;
        }

        switch (action) {
            case "confirm":
                uiManager.openFinalDeletionConfirmation(player, clan.getDisplayName());
                break;
            case "finalconfirm":
                clanManager.disbandClan(clan);
                player.sendMessage("§a[가문] §a가문이 성공적으로 삭제되었습니다.");
                clanManager.clearDeletionConfirmation(player);
                break;
            case "cancel":
                clanManager.clearDeletionConfirmation(player);
                player.sendMessage("§a[가문] §a가문 삭제를 취소했습니다.");
                break;
            default:
                player.sendMessage("§c[가문] §c알 수 없는 명령어입니다.");
                clanManager.clearDeletionConfirmation(player);
                break;
        }
        return true;
    }
}