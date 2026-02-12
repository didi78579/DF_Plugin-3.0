package cjs.DF_Plugin.command.admin;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.events.game.settings.GameConfigManager;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.entity.Player;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class SettingsEditor {

    private final GameConfigManager configManager;

    public SettingsEditor(DF_Main plugin) {
        this.configManager = plugin.getGameConfigManager();
    }

    /**
     * 메인 설정 카테고리 메뉴를 엽니다.
     */
    public void openMainMenu(Player player) {
        player.sendMessage("§e======== §f[DarkForest 설정] §e========");
        player.sendMessage("§7수정할 설정 카테고리를 선택하세요.");

        String[] colors = {"§a", "§b"}; // Green, Aqua
        int colorIndex = 0;

        sendCategoryButton(player, "사망 타이머", "death", "§e사망 시 부활 대기 관련 설정을 엽니다.", colors[colorIndex++ % 2]);
        sendCategoryButton(player, "파일런 설정", "pylon", "§e파일런 관련 세부 기능 설정을 엽니다.", colors[colorIndex++ % 2]);
        sendCategoryButton(player, "월드 보더", "worldborder", "§e월드 보더 크기 및 활성화 설정을 엽니다.", colors[colorIndex++ % 2]);
        sendCategoryButton(player, "유틸리티", "utility", "§e게임 편의성 관련 설정을 엽니다.", colors[colorIndex++ % 2]);
        sendCategoryButton(player, "OP 인챈트", "openchant", "§e밸런스에 영향을 주는 인챈트 활성화 여부를 설정합니다.", colors[colorIndex++ % 2]);
        sendCategoryButton(player, "보스몹 강화", "bossmobstrength", "§e보스 몬스터의 능력치 배율을 설정합니다.", colors[colorIndex++ % 2]);

        player.sendMessage(""); // 설정 항목과 구분

        sendCategoryButton(player, "세부 정보", "detailsettings", "§e설정 파일 직접 수정 등 세부 정보를 안내합니다.", "§e");
        sendCategoryButton(player, "§c설정 초기화", "resetsettings", "§c모든 설정을 기본값으로 되돌립니다.", "§c");

        player.sendMessage("§e===================================");
    }

    /**
     * 사망 타이머 설정 UI를 엽니다.
     */
    public void openDeathTimerSettings(Player player) {
        player.sendMessage("§e======== §f[사망 타이머 설정] §e========");
        sendBooleanSetting(player, "사망 타이머 활성화", "death-timer.enabled");
        sendTimeSettingInMinutes(player, "부활 대기시간(분)", "death-timer.time", 1);
        player.sendMessage("§e===================================");
        sendBackButton(player);
    }

    /**
     * 파일런 설정 UI를 엽니다.
     */
    public void openPylonSettings(Player player) {
        player.sendMessage("§e======== §f[파일런 설정] §e========");
        sendNumberSetting(player, "가문 최대 인원", "pylon.recruitment.max-members", 1);
        sendStringToggleSetting(player, "팀원 모집 방식", "pylon.recruitment.mode", "select", "roulette", "§b선택", "§d룰렛");
        sendBooleanSetting(player, "기본 스탯 플레이어 모집", "pylon.recruitment.include-default-stats-players");
        sendBooleanSetting(player, "파일런 창고", "pylon.features.storage");
        sendBooleanSetting(player, "귀환 주문서", "pylon.features.return-scroll");
        sendBooleanSetting(player, "보조 코어", "pylon.features.multi-core");
        sendBooleanSetting(player, "클랜 지옥", "pylon.features.clan-nether");
        player.sendMessage("§e===================================");
        sendBackButton(player);
    }

    /**
     * 월드보더 설정 UI를 엽니다.
     */
    public void openWorldBorderSettings(Player player) {
        player.sendMessage("§e======== §f[월드 보더 설정] §e========");
        sendNumberSetting(player, "오버월드 크기", "world.border.overworld-size", 1000);
        sendBooleanSetting(player, "엔드 월드보더", "world.border.end-enabled");
        sendNumberSetting(player, "엔드 크기", "world.border.end-size", 500);
        player.sendMessage("§e===================================");
        sendBackButton(player);
    }

    /**
     * 유틸리티 설정 UI를 엽니다.
     */
    public void openUtilitySettings(Player player) {
        player.sendMessage("§e======== §f[유틸리티 설정] §e========");
        sendBooleanSetting(player, "인벤토리 유지", "world.rules.keep-inventory");
        sendInvertedBooleanSetting(player, "좌표 표시", "world.rules.location-info-disabled");
        sendInvertedBooleanSetting(player, "팬텀 생성", "world.rules.phantom-disabled");
        sendInvertedBooleanSetting(player, "좌표 막대 표시", "world.rules.locator-bar-disabled");
        sendBooleanSetting(player, "마법 황금사과 조합", "items.notched-apple-recipe");
        sendCooldownSettingInSeconds(player, "불사의 토템 쿨타임(분)", "upgrade.special-abilities.totem_of_undying.cooldown", 1);
        sendInvertedBooleanSetting(player, "엔더 상자 사용", "world.rules.enderchest-disabled");
        sendNumberSetting(player, "포션 소지 제한", "world.rules.potion-limit", 1);
        sendInvertedBooleanSetting(player, "전체 채팅 사용", "utility.chat-disabled");
        sendBooleanSetting(player, "차원의 균열 활성화", "events.rift.enabled");
        player.sendMessage("§e===================================");
        sendBackButton(player);
    }

    /**
     * OP 인챈트 설정 UI를 엽니다.
     */
    public void openOpEnchantSettings(Player player) {
        player.sendMessage("§e======== §f[OP 인챈트 설정] §e========");
        sendInvertedBooleanSetting(player, "격파(Breach) 부여", "items.op-enchant.breach-disabled");
        sendInvertedBooleanSetting(player, "가시(Thorns) 부여", "items.op-enchant.thorns-disabled");
        player.sendMessage("§e===================================");
        sendBackButton(player);
    }

    /**
     * 보스몹 강화 설정 UI를 엽니다.
     */
    public void openBossMobStrengthSettings(Player player) {
        player.sendMessage("§e======== §f[보스몹 강화 설정] §e========");
        sendDecimalSetting(player, "엔더 드래곤 배율", "boss-mob-strength.ender_dragon", 0.5);
        sendDecimalSetting(player, "위더 배율", "boss-mob-strength.wither", 0.5);
        player.sendMessage("§e===================================");
        sendBackButton(player);
    }

    /**
     * 세부 설정 안내 메시지를 표시합니다.
     */
    public void openDetailSettingsInfo(Player player) {
        player.sendMessage("§e======== §f[세부 설정 정보] §e========");
        player.sendMessage("§7강화 확률, 아이템 능력치 등 세부 설정은");
        player.sendMessage("§7플러그인 폴더 내의 §econfig.yml§7 파일에서 직접 수정해야 합니다.");
        player.sendMessage("§7잘못된 수정은 오류를 유발할 수 있으니 주의하세요.");
        player.sendMessage("§e===================================");
        sendBackButton(player);
    }

    /**
     * 설정 초기화 확인 메시지를 표시합니다. (1단계)
     */
    public void openInitialResetConfirmation(Player player) {
        player.sendMessage("§e======== §f[설정 초기화 확인] §e========");
        player.sendMessage("§c경고! 정말로 모든 설정을 기본값으로 초기화하시겠습니까?");
        player.sendMessage("§c이 작업은 되돌릴 수 없습니다.");
        player.sendMessage("");

        TextComponent confirmButton = new TextComponent("  §a[네, 초기화를 진행합니다]");
        confirmButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/df admin settings confirmreset_step2"));
        confirmButton.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("§a초기화를 계속 진행합니다.").create()));

        TextComponent cancelButton = new TextComponent("  §c[아니요, 취소합니다]");
        cancelButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/df admin settings"));
        cancelButton.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("§c초기화를 취소하고 이전 메뉴로 돌아갑니다.").create()));

        player.spigot().sendMessage(confirmButton);
        player.spigot().sendMessage(cancelButton);
        player.sendMessage("§e===================================");
    }

    /**
     * 설정 초기화 확인 메시지를 표시합니다. (2단계 - 최종)
     */
    public void openFinalResetConfirmation(Player player) {
        player.sendMessage("§e======== §f[설정 초기화] §e========");
        player.sendMessage("§4§l최종 확인: §c'config.yml' 파일이 삭제되고 기본 설정으로 대체됩니다.");

        TextComponent finalResetButton = new TextComponent("§4§l[정말로 초기화합니다]");
        finalResetButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/df admin settings confirmreset"));
        finalResetButton.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("§4모든 것을 이해했으며, 설정을 초기화합니다.").create()));
        player.spigot().sendMessage(finalResetButton);
        player.sendMessage("§e===================================");
    }

    // --- UI 컴포넌트 생성 메소드 ---

    /**
     * 초 단위로 저장된 쿨다운 설정을 분 단위로 표시하고 조작하는 UI를 생성합니다.
     * @param player 대상 플레이어
     * @param name 설정 항목 이름
     * @param key config.yml의 설정 키
     * @param incrementInMinutes 증감 단위 (분)
     */
    private void sendCooldownSettingInSeconds(Player player, String name, String key, int incrementInMinutes) {
        double valueInSeconds = configManager.getConfig().getDouble(key);
        int valueInMinutes = (int) Math.round(valueInSeconds / 60.0);

        TextComponent message = new TextComponent("§7- " + name + ": ");

        TextComponent minusButton = new TextComponent("§c[-]");
        int newMinusMinutes = Math.max(1, valueInMinutes - incrementInMinutes);
        minusButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/df admin set " + key + " " + (newMinusMinutes * 60.0)));
        minusButton.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("§c-" + incrementInMinutes + "분 감소").create()));
        message.addExtra(minusButton);

        message.addExtra(" ");

        TextComponent valueComponent = new TextComponent("§b[" + valueInMinutes + "]");
        valueComponent.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/df admin set " + key + " " + valueInSeconds));
        valueComponent.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("§e클릭하여 직접 초 단위로 값 입력").create()));
        message.addExtra(valueComponent);

        message.addExtra(" ");

        TextComponent plusButton = new TextComponent("§a[+]");
        int newPlusMinutes = valueInMinutes + incrementInMinutes;
        plusButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/df admin set " + key + " " + (newPlusMinutes * 60.0)));
        plusButton.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("§a+" + incrementInMinutes + "분 증가").create()));
        message.addExtra(plusButton);

        player.spigot().sendMessage(message);
    }

    private void sendTimeSettingInMinutes(Player player, String name, String key, int incrementInMinutes) {
        int valueInMinutes = configManager.getConfig().getInt(key);

        TextComponent message = new TextComponent("§7- " + name + ": ");

        TextComponent minusButton = new TextComponent("§c[-]");
        int newMinusValue = Math.max(1, valueInMinutes - incrementInMinutes);
        minusButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/df admin set " + key + " " + newMinusValue));
        minusButton.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("§c-" + incrementInMinutes + "분 감소").create()));
        message.addExtra(minusButton);

        message.addExtra(" ");

        TextComponent valueComponent = new TextComponent("§b[" + valueInMinutes + "]");
        valueComponent.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/df admin set " + key + " " + valueInMinutes));
        valueComponent.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("§e클릭하여 직접 분 단위로 값 입력").create()));
        message.addExtra(valueComponent);

        message.addExtra(" ");

        TextComponent plusButton = new TextComponent("§a[+]");
        int newPlusValue = valueInMinutes + incrementInMinutes;
        plusButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/df admin set " + key + " " + newPlusValue));
        plusButton.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("§a+" + incrementInMinutes + "분 증가").create()));
        message.addExtra(plusButton);

        player.spigot().sendMessage(message);
    }

    private void sendCategoryButton(Player player, String name, String command, String hoverText, String arrowColor) {
        TextComponent button = new TextComponent("  " + arrowColor + "▶ " + name);
        button.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/df admin settings " + command));
        button.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder(hoverText).create()));
        player.spigot().sendMessage(button);
    }

    private void sendStringToggleSetting(Player player, String name, String key, String option1, String option2, String display1, String display2) {
        String value = configManager.getConfig().getString(key, option1);
        String displayValue = value.equalsIgnoreCase(option1) ? display1 : display2;
        String nextValue = value.equalsIgnoreCase(option1) ? option2 : option1;
        String command = "/df admin set " + key + " " + nextValue;

        TextComponent message = new TextComponent("§7- " + name + ": ");
        TextComponent valueComponent = new TextComponent("[" + displayValue + "§r]");
        valueComponent.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command));
        valueComponent.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("§e클릭하여 토글").create()));
        message.addExtra(valueComponent);
        player.spigot().sendMessage(message);
    }

    private void sendNumberSetting(Player player, String name, String key, int increment) {
        int value = configManager.getConfig().getInt(key);
        TextComponent message = new TextComponent("§7- " + name + ": ");

        TextComponent minusButton = new TextComponent("§c[-]");
        minusButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/df admin set " + key + " " + (value - increment)));
        minusButton.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("§c-" + increment + " 감소").create()));
        message.addExtra(minusButton);

        message.addExtra(" ");

        TextComponent valueComponent = new TextComponent("§b[" + value + "]");
        valueComponent.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/df admin set " + key + " "));
        valueComponent.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("§e클릭하여 직접 값 입력").create()));
        message.addExtra(valueComponent);

        message.addExtra(" ");

        TextComponent plusButton = new TextComponent("§a[+]");
        plusButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/df admin set " + key + " " + (value + increment)));
        plusButton.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("§a+" + increment + " 증가").create()));
        message.addExtra(plusButton);

        player.spigot().sendMessage(message);
    }

    private void sendDecimalSetting(Player player, String name, String key, double increment) {
        double value = configManager.getConfig().getDouble(key);
        BigDecimal bdValue = BigDecimal.valueOf(value);
        BigDecimal bdIncrement = BigDecimal.valueOf(increment);

        String displayValue = bdValue.setScale(2, RoundingMode.HALF_UP).toPlainString();
        String minusValue = bdValue.subtract(bdIncrement).setScale(2, RoundingMode.HALF_UP).toPlainString();
        String plusValue = bdValue.add(bdIncrement).setScale(2, RoundingMode.HALF_UP).toPlainString();

        TextComponent message = new TextComponent("§7- " + name + ": ");

        TextComponent minusButton = new TextComponent("§c[-]");
        minusButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/df admin set " + key + " " + minusValue));
        minusButton.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("§c-" + increment + " 감소").create()));
        message.addExtra(minusButton);

        message.addExtra(" ");

        TextComponent valueComponent = new TextComponent("§b[" + displayValue + "]");
        valueComponent.setClickEvent(new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/df admin set " + key + " "));
        valueComponent.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("§e클릭하여 직접 값 입력").create()));
        message.addExtra(valueComponent);

        message.addExtra(" ");

        TextComponent plusButton = new TextComponent("§a[+]");
        plusButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/df admin set " + key + " " + plusValue));
        plusButton.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("§a+" + increment + " 증가").create()));
        message.addExtra(plusButton);

        player.spigot().sendMessage(message);
    }

    private void sendBooleanSetting(Player player, String name, String key) {
        boolean value = configManager.getConfig().getBoolean(key);
        String displayValue = value ? "§a활성화" : "§c비활성화";
        String command = "/df admin set " + key + " " + !value;

        TextComponent message = new TextComponent("§7- " + name + ": ");
        TextComponent valueComponent = new TextComponent("[" + displayValue + "§r]");
        valueComponent.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command));
        valueComponent.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("§e클릭하여 토글").create()));
        message.addExtra(valueComponent);
        player.spigot().sendMessage(message);
    }

    private void sendInvertedBooleanSetting(Player player, String name, String key) {
        boolean value = configManager.getConfig().getBoolean(key);
        // 값이 true이면 '비활성화'로, false이면 '활성화'로 표시 (논리 반전)
        String displayValue = value ? "§c비활성화" : "§a활성화";
        String command = "/df admin set " + key + " " + !value;

        TextComponent message = new TextComponent("§7- " + name + ": ");
        TextComponent valueComponent = new TextComponent("[" + displayValue + "§r]");
        valueComponent.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command));
        valueComponent.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("§e클릭하여 토글").create()));
        message.addExtra(valueComponent);
        player.spigot().sendMessage(message);
    }

    private void sendBackButton(Player player) {
        TextComponent backButton = new TextComponent("§7« 뒤로가기");
        backButton.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/df admin settings"));
        backButton.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("§7설정 카테고리 선택으로 돌아갑니다.").create()));
        player.spigot().sendMessage(backButton);
    }
}