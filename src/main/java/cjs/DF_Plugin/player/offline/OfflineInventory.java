package cjs.DF_Plugin.player.offline;

import org.bukkit.inventory.ItemStack;

public class OfflineInventory {
    private final ItemStack[] main;
    private final ItemStack[] armor;
    private final ItemStack offHand;
    private final ItemStack playerHead;

    public OfflineInventory(ItemStack[] main, ItemStack[] armor, ItemStack offHand, ItemStack playerHead) {
        this.main = main;
        this.armor = armor;
        this.offHand = offHand;
        this.playerHead = playerHead;
    }

    public ItemStack[] getMain() { return main; }
    public ItemStack[] getArmor() { return armor; }
    public ItemStack getOffHand() { return offHand; }
    public ItemStack getPlayerHead() { return playerHead; }
}