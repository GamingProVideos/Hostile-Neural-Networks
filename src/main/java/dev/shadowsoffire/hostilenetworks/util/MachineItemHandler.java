package dev.shadowsoffire.hostilenetworks.util;

import dev.shadowsoffire.placebo.cap.InternalItemHandler;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/** Keeps the machine's inventory operations on top of the 26.2 transactional handler. */
public class MachineItemHandler extends InternalItemHandler {
    public MachineItemHandler(int size) {
        super(size);
    }

    public ItemStack getStackInSlot(int slot) {
        return this.stacks.get(slot);
    }

    public void setStackInSlot(int slot, ItemStack stack) {
        this.set(slot, ItemResource.of(stack), stack.getCount());
    }

    public int getSlotLimit(int slot) {
        return this.getCapacityAsInt(slot, ItemResource.EMPTY);
    }

    public boolean isItemValid(int slot, ItemStack stack) {
        return true;
    }

    @Override
    public boolean isValid(int slot, ItemResource resource) {
        return this.isItemValid(slot, resource.toStack(1));
    }

    protected boolean canAutomationInsert(int slot) { return true; }
    protected boolean canAutomationExtract(int slot) { return true; }

    @Override
    public int insert(int slot, ItemResource resource, int amount, TransactionContext tx) {
        return this.canAutomationInsert(slot) ? super.insert(slot, resource, amount, tx) : 0;
    }

    @Override
    public int extract(int slot, ItemResource resource, int amount, TransactionContext tx) {
        return this.canAutomationExtract(slot) ? super.extract(slot, resource, amount, tx) : 0;
    }

    @Override
    protected void onContentsChanged(int slot, ItemStack previous) {
        this.onContentsChanged(slot);
    }

    protected void onContentsChanged(int slot) {}

    public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        try (Transaction tx = Transaction.openRoot()) {
            int inserted = this.insert(slot, ItemResource.of(stack), stack.getCount(), tx);
            if (!simulate) tx.commit();
            return stack.copyWithCount(stack.getCount() - inserted);
        }
    }

    public ItemStack insertItemInternal(int slot, ItemStack stack, boolean simulate) {
        if (stack.isEmpty()) return ItemStack.EMPTY;
        try (Transaction tx = Transaction.openRoot()) {
            int inserted = this.insertInternal(slot, ItemResource.of(stack), stack.getCount(), tx);
            if (!simulate) tx.commit();
            return stack.copyWithCount(stack.getCount() - inserted);
        }
    }

    public ItemStack extractItem(int slot, int amount, boolean simulate) {
        return this.extractItemInternal(slot, amount, simulate);
    }

    public ItemStack extractItemInternal(int slot, int amount, boolean simulate) {
        ItemResource resource = this.getResource(slot);
        if (resource.isEmpty() || amount <= 0) return ItemStack.EMPTY;
        try (Transaction tx = Transaction.openRoot()) {
            int extracted = this.extractInternal(slot, resource, amount, tx);
            if (!simulate) tx.commit();
            return resource.toStack(extracted);
        }
    }
}
