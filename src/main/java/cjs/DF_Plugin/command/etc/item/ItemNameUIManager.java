package cjs.DF_Plugin.command.etc.item;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.entity.Player;

public class ItemNameUIManager {

    public void openNameChangeUI(Player player, ItemNameManager.NameChangeSession session) {
        player.sendMessage("§e======== §f[아이템 이름 변경] §e========");

        // 아이템 이름 설정
        TextComponent nameLine = new TextComponent("  §f아이템 이름: ");
        TextComponent nameComponent = new TextComponent("§b[" + session.getName() + "]");
        nameComponent.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/itemname set "));
        nameComponent.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("§e클릭하여 아이템 이름을 입력하세요.").create()));
        nameLine.addExtra(nameComponent);
        player.spigot().sendMessage(nameLine);

        // 10강 색상 설정
        TextComponent colorLine = new TextComponent("  §f10강 색상: ");
        TextComponent prevButton = new TextComponent("§c« ");
        prevButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/itemname color prev"));
        colorLine.addExtra(prevButton);

        TextComponent colorComponent = new TextComponent(session.getColor() + "[" + session.getColor().name() + "]");
        colorComponent.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("§e좌우 화살표를 클릭해 색상을 변경하세요.").create()));
        colorLine.addExtra(colorComponent);

        TextComponent nextButton = new TextComponent(" §a»");
        nextButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/itemname color next"));
        colorLine.addExtra(nextButton);
        player.spigot().sendMessage(colorLine);

        player.sendMessage("");

        // 확인 및 초기화 버튼
        TextComponent confirmButton = new TextComponent("§a[확인]");
        confirmButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/itemname confirm"));
        confirmButton.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("§a현재 설정으로 변경을 완료합니다.").create()));

        TextComponent resetButton = new TextComponent("  §c[초기화]");
        resetButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/itemname reset"));
        resetButton.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("§c이름과 색상을 모두 초기화합니다.").create()));

        TextComponent finalLine = new TextComponent();
        finalLine.addExtra(confirmButton);
        finalLine.addExtra(resetButton);
        player.spigot().sendMessage(finalLine);

        player.sendMessage("§e=============================");
    }
}