package cjs.DF_Plugin.command.clan;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.pylon.clan.Clan;
import cjs.DF_Plugin.pylon.clan.ClanManager;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public class ClanCommand {

    private final ClanManager clanManager;
    private final CreateClanCommand createClanCommand;
    private final DeleteClanCommand deleteClanCommand;

    public ClanCommand(DF_Main plugin) {
        this.clanManager = plugin.getClanManager();
        // ClanCommand가 create, delete 명령어를 내부적으로 처리합니다.
        this.createClanCommand = new CreateClanCommand(plugin);
        this.deleteClanCommand = new DeleteClanCommand(plugin);
    }

    public boolean handle(@NotNull Player player, @NotNull String[] args) {
        if (args.length == 0) {
            Clan clan = clanManager.getClanByPlayer(player.getUniqueId());
            if (clan == null) {
                player.sendMessage("§c[가문] §c소속된 가문이 없습니다. 가문을 생성하려면 /df clan create 를 입력하세요.");
            } else {
                // TODO: 가문 정보 UI를 열거나 정보를 표시하는 로직 추가
                player.sendMessage("§a[가문] §a당신은 " + clan.getDisplayName() + "§a 가문에 소속되어 있습니다.");
                player.sendMessage("§7[가문] §7사용 가능한 명령어: /df clan <create|delete>");
            }
            return true;
        }

        String action = args[0].toLowerCase();
        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);

        switch (action) {
            case "create":
                return createClanCommand.handle(player, subArgs);
            case "delete":
                return deleteClanCommand.handle(player, subArgs);
            // 향후 'invite', 'leave', 'kick' 등의 명령어가 여기에 추가될 수 있습니다.
            default:
                player.sendMessage("§c[가문] §c알 수 없는 가문 명령어입니다. 사용 가능한 명령어: create, delete");
                return true;
        }
    }
}