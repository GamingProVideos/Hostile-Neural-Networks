package dev.shadowsoffire.hostilenetworks.tile.proxy;

import dev.shadowsoffire.hostilenetworks.tile.DataCenterTileEntity.DataCenterItemHandler;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/** A transactional view of just the selected Data Center slots. */
public record RangedItemPort(DataCenterItemHandler delegate, int offset, int count, boolean allowModelExtraction)
        implements ResourceHandler<ItemResource> {
    private int translate(int slot) {
        if (slot < 0 || slot >= count) throw new IndexOutOfBoundsException(slot);
        return offset + slot;
    }

    @Override public int size() { return count; }
    @Override public ItemResource getResource(int slot) { return delegate.getResource(translate(slot)); }
    @Override public long getAmountAsLong(int slot) { return delegate.getAmountAsLong(translate(slot)); }
    @Override public long getCapacityAsLong(int slot, ItemResource resource) { return delegate.getCapacityAsLong(translate(slot), resource); }
    @Override public boolean isValid(int slot, ItemResource resource) { return delegate.isValid(translate(slot), resource); }
    @Override public int insert(int slot, ItemResource resource, int amount, TransactionContext tx) {
        return delegate.insert(translate(slot), resource, amount, tx);
    }
    @Override public int extract(int slot, ItemResource resource, int amount, TransactionContext tx) {
        int index = translate(slot);
        return allowModelExtraction ? delegate.extractInternal(index, resource, amount, tx) : delegate.extract(index, resource, amount, tx);
    }
}
