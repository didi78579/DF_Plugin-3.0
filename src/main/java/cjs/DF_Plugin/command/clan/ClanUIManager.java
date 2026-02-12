package cjs.DF_Plugin.command.clan;

import cjs.DF_Plugin.pylon.clan.ClanManager;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.entity.Player;

public class ClanUIManager {

    /**
     * 가문 생성 UI를 플레이어에게 표시합니다.
     */
    public void openCreationUI(Player player, ClanManager.CreationSession session) {
        player.sendMessage("§e======== §f[가문 생성] §e========");

        // 가문 이름 설정
        TextComponent nameLine = new TextComponent("  §f가문 이름: ");
        TextComponent nameComponent = new TextComponent("§b[" + (session.name == null ? "이름 입력" : session.name) + "]");
        nameComponent.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/df clan create setname "));
        nameComponent.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("§e클릭하여 가문 이름을 입력하세요.").create()));
        nameLine.addExtra(nameComponent);
        player.spigot().sendMessage(nameLine);

        // 가문 색상 설정
        TextComponent colorLine = new TextComponent("  §f가문 색상: ");
        TextComponent prevButton = new TextComponent("§c« ");
        prevButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/df clan create color prev"));
        colorLine.addExtra(prevButton);

        TextComponent colorComponent = new TextComponent(session.color + "[" + session.color.name() + "]");
        colorComponent.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("§e좌우 화살표를 클릭해 색상을 변경하세요.").create()));
        colorLine.addExtra(colorComponent);

        TextComponent nextButton = new TextComponent(" §a»");
        nextButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/df clan create color next"));
        colorLine.addExtra(nextButton);
        player.spigot().sendMessage(colorLine);

        player.sendMessage("");

        // 생성 버튼
        TextComponent createButton = new TextComponent("§a[생성하기]");
        createButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/df clan create confirm"));
        createButton.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("§a현재 설정으로 가문을 생성합니다.").create()));
        player.spigot().sendMessage(createButton);

        player.sendMessage("§e=============================");
    }

    /**
     * 가문 삭제 확인 UI (1단계)
     */
    public void openDeletionConfirmation(Player player, String clanName) {
        player.sendMessage("§e======== §f[가문 삭제 확인] §e========");
        player.sendMessage("§c[가문] §c정말로 '" + clanName + "' 가문을 삭제하시겠습니까?");
        player.sendMessage("§7[가문] §7이 작업은 되돌릴 수 없습니다.");
        player.sendMessage("");

        TextComponent confirmButton = new TextComponent("  §a[네, 삭제를 진행합니다]");
        confirmButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/df clan delete confirm"));
        confirmButton.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("§a클릭하여 가문 삭제를 계속 진행합니다.").create()));

        TextComponent cancelButton = new TextComponent("  §c[아니요, 취소합니다]");
        cancelButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/df clan delete cancel"));
        cancelButton.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("§c가문 삭제를 취소하고 돌아갑니다.").create()));

        player.spigot().sendMessage(confirmButton);
        player.spigot().sendMessage(cancelButton);
        player.sendMessage("§e=============================");
    }

    /**
     * 가문 삭제 확인 UI (2단계)
     */
    public void openFinalDeletionConfirmation(Player player, String clanName) {
        player.sendMessage("§e======== §f[가문 삭제] §e========");
        player.sendMessage("§4§l[가문] §4§l최종 확인: §c'" + clanName + "' 가문의 모든 정보가 영구적으로 사라집니다.");

        TextComponent finalDeleteButton = new TextComponent("§4§l[정말로 삭제합니다]");
        finalDeleteButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/df clan delete finalconfirm"));
        finalDeleteButton.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("§4모든 것을 이해했으며, 가문을 삭제합니다.").create()));
        player.spigot().sendMessage(finalDeleteButton);
        player.sendMessage("§e=============================");
    }
}