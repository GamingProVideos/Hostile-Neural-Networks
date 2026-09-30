package dev.shadowsoffire.hostilenetworks.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Shared player inventory and shift-click behavior for Hostile Neural Networks menus. */
public abstract class HostileMenu extends AbstractContainerMenu {

    protected final Level level;
    protected final QuickMover mover = new QuickMover();
    protected int playerInvStart = -1;
    protected int hotbarStart = -1;

    protected HostileMenu(MenuType<?> type, int id, Inventory playerInventory) {
        super(type, id);
        this.level = playerInventory.player.level();
    }

    protected void addPlayerSlots(Inventory inventory, int x, int y) {
        this.playerInvStart = this.slots.size();
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(inventory, 9 + row * 9 + col, x + col * 18, y + row * 18));
            }
        }
        this.hotbarStart = this.slots.size();
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(inventory, col, x + col * 18, y + 58));
        }
    }

    protected void registerInvShuffleRules() {
        if (this.playerInvStart < 0 || this.hotbarStart < 0) {
            throw new IllegalStateException("Player inventory slots have not been added");
        }
        this.mover.registerRule((stack, slot) -> slot >= this.hotbarStart, this.playerInvStart, this.hotbarStart);
        this.mover.registerRule((stack, slot) -> slot >= this.playerInvStart, this.hotbarStart, this.slots.size());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= this.slots.size()) return ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (!slot.hasItem() || !slot.mayPickup(player)) return ItemStack.EMPTY;

        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        for (MoveRule rule : this.mover.rules) {
            if (!rule.matches.test(stack, index)) continue;
            if (!this.moveItemStackTo(stack, rule.start, rule.end, rule.reverse)) return ItemStack.EMPTY;
            if (stack.isEmpty()) slot.set(ItemStack.EMPTY);
            else slot.setChanged();
            slot.onTake(player, stack);
            return original;
        }
        return ItemStack.EMPTY;
    }

    protected static final class QuickMover {
        private final List<MoveRule> rules = new ArrayList<>();

        public void registerRule(BiPredicate<ItemStack, Integer> matches, int start, int end) {
            this.rules.add(new MoveRule(matches, start, end, false));
        }

        public void registerRule(BiPredicate<ItemStack, Integer> matches, int start, int end, boolean reverse) {
            this.rules.add(new MoveRule(matches, start, end, reverse));
        }
    }

    private record MoveRule(BiPredicate<ItemStack, Integer> matches, int start, int end, boolean reverse) {}
}
