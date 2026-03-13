package com.example;

import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class ViewInventoryMenu extends AbstractContainerMenu {

    private final SimpleContainer buffer;
    private final Inventory targetInventory;

    /**
     * Buffer layout (slots 0-44, GENERIC_9x5 top section):
     *   Row 0  ( 0- 8): Hotbar        → inv 0-8
     *   Rows 1-3 ( 9-35): Main storage  → inv 9-35
     *   Row 4 col 0 (36): Boots         → inv 36
     *   Row 4 col 1 (37): Leggings      → inv 37
     *   Row 4 col 2 (38): Chestplate    → inv 38
     *   Row 4 col 3 (39): Helmet        → inv 39
     *   Row 4 col 4 (40): Offhand       → inv 40
     *   Row 4 col 5-8 (41-44): locked filler
     *
     * Slots 45-80: viewer's own inventory (so the client bottom-half works correctly)
     */
    public ViewInventoryMenu(int containerId, Inventory viewerInventory, Inventory targetInventory) {
        super(MenuType.GENERIC_9x5, containerId);
        this.targetInventory = targetInventory;
        this.buffer = new SimpleContainer(45);

        // Copy target's inventory into the buffer
        for (int i = 0; i < 41; i++) {
            buffer.setItem(i, targetInventory.getItem(i).copy());
        }

        // ── Target inventory slots (0-44) ────────────────────────────────────
        for (int i = 0; i < 45; i++) {
            int row = i / 9, col = i % 9;
            int x = 8 + col * 18, y = 18 + row * 18;
            if (i >= 41) {
                // Locked filler — cannot place or pick up
                addSlot(new Slot(buffer, i, x, y) {
                    @Override public boolean mayPlace(ItemStack stack) { return false; }
                    @Override public boolean mayPickup(Player player) { return false; }
                });
            } else {
                addSlot(new Slot(buffer, i, x, y));
            }
        }

        // ── Viewer's own inventory slots (45-80) ─────────────────────────────
        // Without these the client sends clicks to undefined slots → duplication/weirdness.
        // For GENERIC_9x5: player storage starts at y=121, hotbar at y=179.
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(viewerInventory, col + row * 9 + 9, 8 + col * 18, 121 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(viewerInventory, col, 8 + col * 18, 179));
        }
    }

    /**
     * Called every server tick while the menu is open.
     * Syncs the buffer back to the target's live inventory in real time.
     */
    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        for (int i = 0; i < 41; i++) {
            targetInventory.setItem(i, buffer.getItem(i).copy());
        }
        targetInventory.setChanged();
    }

    /** Disable shift-click to keep things simple. */
    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    /** Final sync when the menu is closed. */
    @Override
    public void removed(Player player) {
        super.removed(player);
        for (int i = 0; i < 41; i++) {
            targetInventory.setItem(i, buffer.getItem(i).copy());
        }
        targetInventory.setChanged();
    }
}
