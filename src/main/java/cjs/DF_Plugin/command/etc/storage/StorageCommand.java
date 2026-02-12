package cjs.DF_Plugin.command.etc.storage;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.pylon.clan.Clan;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class StorageCommand implements CommandExecutor {

    private final DF_Main plugin;

    public StorageCommand(DF_Main plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c[파일런] §c이 명령어는 플레이어만 사용할 수 있습니다.");
            return true;
        }

        Clan clan = plugin.getClanManager().getClanByPlayer(player.getUniqueId());
        if (clan == null) {
            player.sendMessage("§c[파일런] §c당신은 가문에 소속되어 있지 않습니다.");
            return true;
        }

        // GUI에서 창고를 여는 것과 동일한 메소드를 호출하여 일관성을 보장합니다.
        plugin.getClanManager().openPylonStorage(player);
        return true;
    }
}