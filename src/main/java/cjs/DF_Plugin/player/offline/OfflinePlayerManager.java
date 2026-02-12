package cjs.DF_Plugin.player.offline;

import cjs.DF_Plugin.DF_Main;
import cjs.DF_Plugin.data.InventoryDataManager;
import cjs.DF_Plugin.player.death.PlayerDeathManager;
import cjs.DF_Plugin.pylon.clan.Clan;
import cjs.DF_Plugin.pylon.clan.ClanManager;
import cjs.DF_Plugin.util.item.ItemBuilder;
import cjs.DF_Plugin.util.item.ItemFactory;
import org.bukkit.*;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 오프라인 플레이어의 '수면 캐릭터'와 인벤토리를 관리하는 클래스.
 */
public class OfflinePlayerManager implements Listener {

    private final DF_Main plugin;
    public static final NamespacedKey OFFLINE_BODY_KEY = new NamespacedKey(DF_Main.getInstance(), "offline_body_uuid");
    private final PlayerDeathManager playerDeathManager;
    private final Map<Inventory, UUID> openInventories = new ConcurrentHashMap<>();
    private final Map<UUID, Player> viewingPlayers = new ConcurrentHashMap<>(); // 오프라인 플레이어 UUID -> 현재 GUI를 보고 있는 플레이어

    public OfflinePlayerManager(DF_Main plugin) {
        this.plugin = plugin;
        this.playerDeathManager = plugin.getPlayerDeathManager();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /**
     * 서버 시작 시 월드에 남아있는 모든 오프라인 아바타를 로드하고 유효성을 검사합니다.
     * 주인이 이미 온라인인 아바타(비정상 종료로 남은 경우)는 제거합니다.
     */
    public void loadAndVerifyOfflineStands() {
        plugin.getLogger().info("[오프라인 관리] 오프라인 플레이어 아바타를 불러오는 중...");
        int verifiedCount = 0;
        int removedCount = 0;

        for (World world : Bukkit.getWorlds()) {
            for (ArmorStand stand : world.getEntitiesByClass(ArmorStand.class)) {
                PersistentDataContainer container = stand.getPersistentDataContainer();
                if (container.has(OFFLINE_BODY_KEY, PersistentDataType.STRING)) {
                    String uuidString = container.get(OFFLINE_BODY_KEY, PersistentDataType.STRING);
                    if (uuidString == null) continue;

                    try {
                        UUID ownerUUID = UUID.fromString(uuidString);
                        OfflinePlayer owner = Bukkit.getOfflinePlayer(ownerUUID);

                        // 주인이 온라인 상태라면, 이 아바타는 비정상 종료로 남은 것이므로 제거합니다.
                        if (owner.isOnline()) {
                            stand.remove();
                            removedCount++;
                        } else {
                            verifiedCount++;
                        }
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("[오프라인 관리] 오프라인 아바타에서 잘못된 UUID 태그를 발견하여 제거합니다: " + uuidString);
                        stand.remove();
                    }
                }
            }
        }
        plugin.getLogger().info("[오프라인 관리] " + verifiedCount + "개의 오프라인 플레이어 아바타를 확인했으며, " + removedCount + "개의 오래된 아바타를 제거했습니다.");
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // 사망으로 인해 밴(킥)된 플레이어인지 확인합니다.
        if (playerDeathManager.isDeathBanned(player.getUniqueId())) {
            return; // 사망 밴 상태의 플레이어는 아바타를 남기지 않습니다.
        }

        // 관전자 모드인 플레이어는 아바타를 남기지 않습니다.
        if (player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }

        // 플레이어의 머리 아이템을 생성하고 playerdata.yml에 저장합니다.
        ItemStack playerHead = getPlayerHead(player);
        plugin.getPlayerDataManager().setPlayerHead(player.getUniqueId(), playerHead);

        saveInventory(player, playerHead);
        spawnBody(player, playerHead);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerUUID = player.getUniqueId();

        // 만약 다른 플레이어가 이 플레이어의 인벤토리를 보고 있었다면, 강제로 닫습니다.
        if (viewingPlayers.containsKey(playerUUID)) {
            Player viewer = viewingPlayers.get(playerUUID);
            if (viewer != null && viewer.isOnline()) {
                viewer.closeInventory(); // onInventoryClose가 호출되어 인벤토리가 저장됩니다.
                viewer.sendMessage("§e[인벤토리] §e" + player.getName() + "님이 접속하여 인벤토리가 닫혔습니다.");
            }
        }

        OfflineInventory offlineInventory = loadOfflineInventory(playerUUID);
        if (offlineInventory != null) {
            // Restore player's inventory
            player.getInventory().setStorageContents(offlineInventory.getMain());
            player.getInventory().setArmorContents(offlineInventory.getArmor());
            player.getInventory().setItemInOffHand(offlineInventory.getOffHand());

            // Delete the data after loading
            plugin.getInventoryDataManager().getInventoriesSection("player_inventory").set(playerUUID.toString(), null);
            plugin.getInventoryDataManager().saveConfig();
        }
        removeBody(player);
    }

    @EventHandler
    public void onPlayerInteractAtEntity(PlayerInteractAtEntityEvent event) {
        if (!(event.getRightClicked() instanceof ArmorStand armorStand)) return;

        PersistentDataContainer container = armorStand.getPersistentDataContainer();
        if (!container.has(OFFLINE_BODY_KEY, PersistentDataType.STRING)) return;

        event.setCancelled(true);
        UUID offlinePlayerUUID = UUID.fromString(container.get(OFFLINE_BODY_KEY, PersistentDataType.STRING));
        openOfflinePlayerGUI(event.getPlayer(), offlinePlayerUUID);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Inventory closedInventory = event.getInventory();
        if (!openInventories.containsKey(closedInventory)) return;

        UUID offlinePlayerUUID = openInventories.remove(closedInventory);
        viewingPlayers.remove(offlinePlayerUUID); // 뷰어 정보 제거
        OfflineInventory updatedOfflineInventory = InventoryGUI.fromGui(closedInventory);
        saveOfflineInventory(offlinePlayerUUID, updatedOfflineInventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        // Check if the open inventory is one of our offline GUIs
        if (!openInventories.containsKey(event.getView().getTopInventory())) return;

        // 유리판과의 상호작용을 막습니다.
        if (InventoryGUI.FILLER_PANE.isSimilar(event.getCurrentItem())) {
            event.setCancelled(true);
            return;
        }

        // 플레이어 머리(슬롯 0)와의 상호작용을 막습니다.
        if (event.getClickedInventory() != null && event.getClickedInventory().equals(event.getView().getTopInventory()) && event.getSlot() == 0) {
            event.setCancelled(true);
            return;
        }

        // 귀속 저주가 걸린 갑옷을 빼는 것을 막습니다.
        if (event.getClickedInventory() != null && event.getClickedInventory().equals(event.getView().getTopInventory())) {
            int slot = event.getSlot();
            // 갑옷 슬롯 (2:헬멧, 3:흉갑, 4:레깅스, 5:부츠)
            if (slot >= 2 && slot <= 5) {
                ItemStack clickedItem = event.getCurrentItem();
                if (clickedItem != null && clickedItem.hasItemMeta() && clickedItem.getEnchantments().containsKey(Enchantment.BINDING_CURSE)) {
                    event.setCancelled(true);
                    return; // 이벤트를 취소하고 더 이상 진행하지 않음
                }
            }
        }

        Inventory topInventory = event.getView().getTopInventory();
        Inventory clickedInventory = event.getClickedInventory();

        // 플레이어 인벤토리에서 GUI로 아이템을 쉬프트-클릭하는 것을 막습니다.
        if (event.isShiftClick() && clickedInventory != null && !clickedInventory.equals(topInventory)) {
            event.setCancelled(true);
            return;
        }

        // GUI 내부에서 아이템을 놓을 때 슬롯 종류를 확인합니다.
        if (clickedInventory != null && clickedInventory.equals(topInventory)) {
            int slot = event.getSlot();
            ItemStack cursorItem = event.getCursor();

            if (cursorItem != null && cursorItem.getType() != Material.AIR) {
                if (!isPlacementAllowed(slot, cursorItem)) {
                    event.setCancelled(true);
                    if (event.getWhoClicked() instanceof Player p) {
                        p.sendMessage("§c[인벤토리] §c이 슬롯에는 해당 종류의 아이템만 놓을 수 있습니다.");
                    }
                }
            }
        }
    }

    private void spawnBody(Player player, ItemStack playerHead) {
        Location spawnLocation = player.getLocation().getBlock().getLocation().add(0.5, 0.0, 0.5);
        spawnLocation.setYaw(player.getLocation().getYaw());

        ArmorStand as = spawnLocation.getWorld().spawn(spawnLocation, ArmorStand.class);

        as.setInvulnerable(true);
        as.setPersistent(true);
        as.setGravity(false);
        as.setSmall(true);
        as.setBasePlate(false);
        as.setVisible(false);

        as.getEquipment().setHelmet(playerHead);

        as.getPersistentDataContainer().set(OFFLINE_BODY_KEY, PersistentDataType.STRING, player.getUniqueId().toString());
        ClanManager clanManager = plugin.getClanManager();
        if (clanManager != null) {
            Clan clan = clanManager.getClanByPlayer(player.getUniqueId());
            if (clan != null) {
                Color clanColor = convertChatColorToColor(clan.getColor());
                if (clanColor != null) {
                    as.getEquipment().setChestplate(createDyedArmor(Material.LEATHER_CHESTPLATE, clanColor));
                    as.getEquipment().setLeggings(createDyedArmor(Material.LEATHER_LEGGINGS, clanColor));
                    as.getEquipment().setBoots(createDyedArmor(Material.LEATHER_BOOTS, clanColor));
                }
            }
        }
    }

    private boolean isPlacementAllowed(int slot, ItemStack item) {
        if (item == null) return true;
        return switch (slot) {
            case 2 -> isHelmet(item);
            case 3 -> isChestplate(item);
            case 4 -> isLeggings(item);
            case 5 -> isBoots(item);
            default -> true; // 다른 모든 슬롯은 허용
        };
    }

    private boolean isHelmet(ItemStack item) {
        return item.getType().name().endsWith("_HELMET") || item.getType() == Material.CARVED_PUMPKIN;
    }

    private boolean isChestplate(ItemStack item) {
        return item.getType().name().endsWith("_CHESTPLATE") || item.getType() == Material.ELYTRA;
    }

    private boolean isLeggings(ItemStack item) {
        return item.getType().name().endsWith("_LEGGINGS");
    }

    private boolean isBoots(ItemStack item) {
        return item.getType().name().endsWith("_BOOTS");
    }

    private ItemStack createDyedArmor(Material leatherArmor, Color color) {
        ItemStack item = new ItemStack(leatherArmor);
        LeatherArmorMeta meta = (LeatherArmorMeta) item.getItemMeta();
        if (meta != null) {
            meta.setColor(color);
            item.setItemMeta(meta);
        }
        return item;
    }

    private Color convertChatColorToColor(ChatColor chatColor) {
        if (chatColor == null) return null;
        return switch (chatColor) {
            case BLACK -> Color.BLACK;
            case DARK_BLUE, BLUE -> Color.BLUE;
            case DARK_GREEN -> Color.GREEN;
            case DARK_AQUA -> Color.TEAL;
            case DARK_RED -> Color.MAROON;
            case DARK_PURPLE -> Color.PURPLE;
            case GOLD -> Color.ORANGE;
            case GRAY, DARK_GRAY -> Color.GRAY;
            case GREEN -> Color.LIME;
            case AQUA -> Color.AQUA;
            case RED -> Color.RED;
            case LIGHT_PURPLE -> Color.FUCHSIA;
            case YELLOW -> Color.YELLOW;
            case WHITE -> Color.WHITE;
            default -> null;
        };
    }

    private void removeBody(Player player) {
        for (Entity entity : player.getWorld().getEntitiesByClass(ArmorStand.class)) {
            PersistentDataContainer container = entity.getPersistentDataContainer();
            if (container.has(OFFLINE_BODY_KEY, PersistentDataType.STRING)) {
                if (container.get(OFFLINE_BODY_KEY, PersistentDataType.STRING).equals(player.getUniqueId().toString())) {
                    entity.remove();
                }
            }
        }
    }

    private void saveInventory(Player player, ItemStack playerHead) {
        OfflineInventory offlineInventory = new OfflineInventory(
                player.getInventory().getStorageContents(),
                player.getInventory().getArmorContents(),
                player.getInventory().getItemInOffHand(),
                playerHead
        );
        saveOfflineInventory(player.getUniqueId(), offlineInventory);
    }

    private void saveOfflineInventory(UUID playerUUID, OfflineInventory offlineInventory) {
        Inventory gui = InventoryGUI.create(offlineInventory);
        plugin.getInventoryDataManager().saveInventory(gui, "player_inventory", playerUUID.toString());
    }

    private OfflineInventory loadOfflineInventory(UUID playerUUID) {
        InventoryDataManager idm = plugin.getInventoryDataManager();
        // Check if data exists for this player
        if (!idm.getInventoriesSection("player_inventory").contains(playerUUID.toString())) {
            return null;
        }

        // Create a temporary GUI inventory to load data into
        Inventory gui = Bukkit.createInventory(null, 54, "Offline Load");
        idm.loadInventory(gui, "player_inventory", playerUUID.toString());

        // Convert the GUI inventory back to our DTO
        return InventoryGUI.fromGui(gui);
    }

    public void openOfflinePlayerGUI(Player viewer, UUID offlinePlayerUUID) {
        OfflineInventory offlineInventory = loadOfflineInventory(offlinePlayerUUID);

        if (viewingPlayers.containsKey(offlinePlayerUUID)) {
            viewer.sendMessage("§c[인벤토리] §c다른 플레이어가 이미 해당 플레이어의 인벤토리를 보고 있습니다.");
            return;
        }

        if (offlineInventory == null) {
            viewer.sendMessage("§c[인벤토리] §c오프라인 플레이어 정보를 찾을 수 없습니다.");
            return;
        }

        Inventory gui = InventoryGUI.create(offlineInventory);
        viewingPlayers.put(offlinePlayerUUID, viewer);
        openInventories.put(gui, offlinePlayerUUID);
        viewer.openInventory(gui);
    }

    private ItemStack getPlayerHead(Player player) {
        // ItemFactory와 ItemBuilder를 사용하여 일관성 있게 플레이어 머리를 생성합니다.
        return new ItemBuilder(ItemFactory.createPlayerHead(player.getUniqueId()))
                .withName("§f" + player.getName() + "의 정보")
                .build();
    }
}