package dev.shadowsoffire.hostilenetworks.gui;

import dev.shadowsoffire.placebo.menu.SimpleDataSlots.IDataAutoRegister;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.level.block.entity.BlockEntity;

/** Resolves a machine menu's block entity and registers its synced data slots. */
public abstract class HostileBlockEntityMenu<T extends BlockEntity> extends HostileMenu {

    protected final BlockPos pos;
    protected final T tile;

    @SuppressWarnings("unchecked")
    protected HostileBlockEntityMenu(MenuType<?> type, int id, Inventory inventory, BlockPos pos) {
        super(type, id, inventory);
        this.pos = pos;
        BlockEntity blockEntity = this.level.getBlockEntity(pos);
        if (blockEntity == null) throw new IllegalStateException("Missing block entity for menu at " + pos);
        this.tile = (T) blockEntity;
        if (blockEntity instanceof IDataAutoRegister data) data.registerSlots(this::addDataSlot);
    }

    @Override
    public boolean stillValid(Player player) {
        return this.tile.getType().isValid(this.level.getBlockState(this.pos))
            && player.distanceToSqr(this.pos.getX() + 0.5, this.pos.getY() + 0.5, this.pos.getZ() + 0.5) <= 64;
    }
}
