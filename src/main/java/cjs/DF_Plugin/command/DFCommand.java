package cjs.DF_Plugin.command;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.command.admin.DFAdminCommand;
import cjs.DF_Plugin.command.clan.ClanCommand;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public class DFCommand implements CommandExecutor {

    private final DFAdminCommand adminCommand;
    private final ClanCommand clanCommand;

    public DFCommand(DF_Main plugin) {
        this.adminCommand = new DFAdminCommand(plugin);
        this.clanCommand = new ClanCommand(plugin);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§c[DF] §c사용법: /df <명령어> [옵션...]");
            sender.sendMessage("§7[DF] §7사용 가능한 명령어: clan");
            if (sender.hasPermission("df.admin")) {
                sender.sendMessage("§c[DF] §c관리자 명령어: admin");
            }
            return true;
        }

        String subCommand = args[0].toLowerCase();
        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);

        switch (subCommand) {
            // --- 일반 플레이어 명령어 ---
            case "clan" -> {
                if (!(sender instanceof Player)) {
                    sender.sendMessage("§c[DF] §c플레이어만 사용할 수 있는 명령어입니다.");
                    return true;
                }
                return clanCommand.handle((Player) sender, subArgs);
            }

            // --- 관리자 전용 명령어 ---
            case "admin" -> {
                if (!sender.hasPermission("df.admin")) {
                    sender.sendMessage("§c[DF] §c이 명령어를 사용할 권한이 없습니다.");
                    return true;
                }
                // DFAdminCommand는 이제 admin 하위 명령어들을 처리합니다.
                return adminCommand.onCommand(sender, command, label, subArgs);
            }

            default -> {
                sender.sendMessage("§c[DF] §c알 수 없는 명령어입니다. 도움말을 보려면 /df 를 입력하세요.");
                return true;
            }
        }
    }
}