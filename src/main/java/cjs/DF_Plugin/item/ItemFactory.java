package cjs.DF_Plugin.item;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.UUID;

public class ItemFactory {

    /**
     * 플레이어의 UUID를 사용하여 온라인/오프라인 여부에 관계없이
     * 실제 스킨이 적용된 머리 아이템을 생성합니다.
     *
     * @param playerUUID 스킨을 가져올 플레이어의 UUID
     * @return 플레이어의 머리 아이템
     */
    public static ItemStack createPlayerHead(UUID playerUUID) {
        ItemStack playerHead = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta skullMeta = (SkullMeta) playerHead.getItemMeta();

        // OfflinePlayer 객체를 사용하면 오프라인 플레이어의 스킨도 불러올 수 있습니다.
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerUUID);
        skullMeta.setOwningPlayer(offlinePlayer);
        playerHead.setItemMeta(skullMeta);

        return playerHead;
    }
}