package dev.shadowsoffire.hostilenetworks.gui;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

/** Opens a machine menu and sends its position to the receiving client. */
public final class HostileMenuUtil {

    private HostileMenuUtil() {}

    @FunctionalInterface
    public interface PosFactory<M extends AbstractContainerMenu> {
        M create(int id, Inventory inventory, BlockPos pos);
    }

    public static <M extends AbstractContainerMenu> InteractionResult openGui(Player player, BlockPos pos, PosFactory<M> factory) {
        if (player.level().isClientSide()) return InteractionResult.SUCCESS;
        Component title = Component.translatable(player.level().getBlockState(pos).getBlock().getDescriptionId());
        player.openMenu(new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return title;
            }

            @Override
            public AbstractContainerMenu createMenu(int id, Inventory inventory, Player menuPlayer) {
                return factory.create(id, inventory, pos);
            }
        }, pos);
        return InteractionResult.CONSUME;
    }
}
