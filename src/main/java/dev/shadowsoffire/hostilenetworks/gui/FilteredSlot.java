package dev.shadowsoffire.hostilenetworks.gui;

import java.util.function.Predicate;

import dev.shadowsoffire.hostilenetworks.util.MachineItemHandler;
import net.minecraft.world.item.ItemStack;

/** Restricts player insertion and honors machine-internal extraction rules. */
public class FilteredSlot extends dev.shadowsoffire.placebo.menu.FilteredSlot {

    public FilteredSlot(MachineItemHandler handler, int index, int x, int y, Predicate<ItemStack> filter) {
        super(handler, index, x, y, filter);
    }
}
