package cjs.DF_Plugin.command.etc.item;

import cjs.DF_Plugin.DF_Main;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;

public class ItemNameCommand implements CommandExecutor {

    private final ItemNameManager itemNameManager;

    public ItemNameCommand(DF_Main plugin) {
        this.itemNameManager = plugin.getItemNameManager();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§c[아이템] §c플레이어만 사용할 수 있는 명령어입니다.");
            return true;
        }

        if (args.length == 0) {
            itemNameManager.startOrOpenUI(player);
            return true;
        }

        String action = args[0].toLowerCase();
        switch (action) {
            case "set":
                if (args.length < 2) {
                    player.sendMessage("§c[아이템] §c사용법: /itemname set <이름>");
                    return true;
                }
                String name = String.join(" ", Arrays.copyOfRange(args, 1, args.length));
                itemNameManager.setSessionName(player, name);
                break;
            case "color":
                if (args.length < 2) {
                    player.sendMessage("§c[아이템] §c사용법: /itemname color <prev|next>");
                    return true;
                }
                if (args[1].equalsIgnoreCase("next")) {
                    itemNameManager.cycleColor(player, true);
                } else if (args[1].equalsIgnoreCase("prev")) {
                    itemNameManager.cycleColor(player, false);
                }
                break;
            case "reset":
                itemNameManager.resetName(player);
                break;
            case "confirm":
                itemNameManager.confirmChanges(player);
                break;
            default:
                player.sendMessage("§c[아이템] §c잘못된 명령어입니다. 이름 변경을 시작하려면 /itemname 을 입력하세요.");
                break;
        }
        return true;
    }
}