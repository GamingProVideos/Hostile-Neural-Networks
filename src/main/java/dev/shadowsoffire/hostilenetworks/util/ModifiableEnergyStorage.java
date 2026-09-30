package dev.shadowsoffire.hostilenetworks.util;

import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.transfer.energy.SimpleEnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * Transactional machine energy storage with legacy accessors for the UI.
 */
@SuppressWarnings("removal")
public class ModifiableEnergyStorage extends SimpleEnergyHandler implements IEnergyStorage {

    public ModifiableEnergyStorage(int capacity, int maxReceive) {
        super(capacity, maxReceive, 0);
    }

    public void setEnergy(int energy) {
        this.set(Math.max(0, Math.min(this.capacity, energy)));
    }

    public void setMaxExtract(int maxExtract) {
        this.maxExtract = Math.max(0, maxExtract);
    }

    @Override public int getEnergyStored() { return this.getAmountAsInt(); }
    @Override public int getMaxEnergyStored() { return this.getCapacityAsInt(); }
    @Override public boolean canExtract() { return this.maxExtract > 0; }
    @Override public boolean canReceive() { return this.maxInsert > 0; }

    @Override
    public int receiveEnergy(int amount, boolean simulate) {
        try (Transaction tx = Transaction.openRoot()) {
            int received = this.insert(amount, tx);
            if (!simulate) tx.commit();
            return received;
        }
    }

    @Override
    public int extractEnergy(int amount, boolean simulate) {
        try (Transaction tx = Transaction.openRoot()) {
            int extracted = this.extract(amount, tx);
            if (!simulate) tx.commit();
            return extracted;
        }
    }
}
