package cjs.DF_Plugin.command.etc.item;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.upgrade.UpgradeManager;
import cjs.DF_Plugin.upgrade.profile.IUpgradeableProfile;
import cjs.DF_Plugin.upgrade.profile.ProfileRegistry;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class ItemNameManager {

    private final DF_Main plugin;
    private final ItemNameUIManager uiManager;
    private final ConcurrentHashMap<UUID, NameChangeSession> sessions = new ConcurrentHashMap<>();
    public static NamespacedKey CUSTOM_NAME_KEY;
    public static NamespacedKey CUSTOM_COLOR_KEY;

    public ItemNameManager(DF_Main plugin) {
        this.plugin = plugin;
        this.uiManager = new ItemNameUIManager();
        CUSTOM_NAME_KEY = new NamespacedKey(plugin, "custom_item_name");
        CUSTOM_COLOR_KEY = new NamespacedKey(plugin, "custom_item_color_10");
    }

    public void startOrOpenUI(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType().isAir()) {
            player.sendMessage("§c[아이템] §c이름을 변경할 아이템을 손에 들어주세요.");
            return;
        }

        NameChangeSession session = sessions.computeIfAbsent(player.getUniqueId(), k -> new NameChangeSession(item));
        session.updateFromItem(item); // 매번 열 때마다 아이템의 현재 상태를 반영
        uiManager.openNameChangeUI(player, session);
    }

    public void setSessionName(Player player, String name) {
        NameChangeSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            player.sendMessage("§c[아이템] §c이름 변경 세션이 시작되지 않았습니다. /itemname 을 먼저 입력해주세요.");
            return;
        }
        session.setName(name);
        player.sendMessage("§a[아이템] §a이름이 '" + name + "' (으)로 설정되었습니다. §a[확인]§7을 눌러 적용하세요.");
        uiManager.openNameChangeUI(player, session);
    }

    public void cycleColor(Player player, boolean forward) {
        NameChangeSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            player.sendMessage("§c[아이템] §c이름 변경 세션이 시작되지 않았습니다. /itemname 을 먼저 입력해주세요.");
            return;
        }
        if (forward) {
            session.nextColor();
        } else {
            session.prevColor();
        }
        uiManager.openNameChangeUI(player, session);
    }

    public void confirmChanges(Player player) {
        NameChangeSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            player.sendMessage("§c[아이템] §c이름 변경 세션이 시작되지 않았습니다. /itemname 을 먼저 입력해주세요.");
            return;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType().isAir()) {
            player.sendMessage("§c[아이템] §c아이템을 찾을 수 없습니다.");
            sessions.remove(player.getUniqueId());
            return;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        meta.getPersistentDataContainer().set(CUSTOM_NAME_KEY, PersistentDataType.STRING, session.getName());
        meta.getPersistentDataContainer().set(CUSTOM_COLOR_KEY, PersistentDataType.STRING, String.valueOf(session.getColor().getChar()));
        item.setItemMeta(meta);

        // 변경된 이름/색상을 적용하고 아이템 로어를 새로고침합니다.
        UpgradeManager upgradeManager = plugin.getUpgradeManager();
        ProfileRegistry profileRegistry = upgradeManager.getProfileRegistry();
        IUpgradeableProfile profile = profileRegistry.getProfile(item.getType());
        if (profile != null) {
            int currentLevel = upgradeManager.getUpgradeLevel(item);
            upgradeManager.setUpgradeLevel(item, currentLevel);
        }

        player.sendMessage("§a[아이템] §a아이템 이름과 색상이 성공적으로 적용되었습니다.");
        sessions.remove(player.getUniqueId());
    }

    public void resetName(Player player) {
        sessions.remove(player.getUniqueId()); // 세션 종료

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType().isAir()) {
            player.sendMessage("§c[아이템] §c아이템을 찾을 수 없습니다.");
            return;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        meta.getPersistentDataContainer().remove(CUSTOM_NAME_KEY);
        meta.getPersistentDataContainer().remove(CUSTOM_COLOR_KEY);
        item.setItemMeta(meta);

        // 이름/색상 초기화 후 아이템 로어를 새로고침합니다.
        UpgradeManager upgradeManager = plugin.getUpgradeManager();
        ProfileRegistry profileRegistry = upgradeManager.getProfileRegistry();
        IUpgradeableProfile profile = profileRegistry.getProfile(item.getType());
        if (profile != null) {
            int currentLevel = upgradeManager.getUpgradeLevel(item);
            upgradeManager.setUpgradeLevel(item, currentLevel);
        }

        player.sendMessage("§a[아이템] §a아이템의 이름과 색상을 초기화했습니다.");
    }

    public static class NameChangeSession {
        private String name;
        private ChatColor color;
        private int colorIndex;
        private final List<ChatColor> availableColors;

        public NameChangeSession(ItemStack item) {
            this.availableColors = Arrays.stream(ChatColor.values())
                    .filter(ChatColor::isColor)
                    .filter(c -> c != ChatColor.BLACK)
                    .collect(Collectors.toList());
            updateFromItem(item);
        }

        public void updateFromItem(ItemStack item) {
            ItemMeta meta = item.getItemMeta();
            if (meta == null) {
                this.name = "이름 없음";
                this.color = ChatColor.GOLD;
                this.colorIndex = availableColors.indexOf(this.color);
                return;
            }

            this.name = meta.getPersistentDataContainer().getOrDefault(CUSTOM_NAME_KEY, PersistentDataType.STRING, "이름 입력");
            String colorChar = meta.getPersistentDataContainer().get(CUSTOM_COLOR_KEY, PersistentDataType.STRING);
            this.color = (colorChar != null) ? ChatColor.getByChar(colorChar.charAt(0)) : ChatColor.GOLD;
            this.colorIndex = availableColors.indexOf(this.color);
            if (this.colorIndex == -1) this.colorIndex = 0; // Fallback
        }

        public String getName() { return name; }
        public ChatColor getColor() { return color; }

        public void setName(String name) { this.name = name; }

        public void nextColor() {
            colorIndex = (colorIndex + 1) % availableColors.size();
            color = availableColors.get(colorIndex);
        }

        public void prevColor() {
            colorIndex = (colorIndex - 1 + availableColors.size()) % availableColors.size();
            color = availableColors.get(colorIndex);
        }
    }
}