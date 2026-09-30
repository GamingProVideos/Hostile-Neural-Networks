package dev.shadowsoffire.hostilenetworks.datagen;

import java.util.concurrent.CompletableFuture;

import dev.shadowsoffire.hostilenetworks.Hostile;
import dev.shadowsoffire.hostilenetworks.HostileNetworks;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.data.BlockTagsProvider;

public class BlockTagProvider extends BlockTagsProvider {

    public BlockTagProvider(PackOutput output, CompletableFuture<HolderLookup.Provider> lookup) {
        super(output, lookup, HostileNetworks.MODID);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        this.tag(Hostile.Tags.DATA_CENTER_FLOOR).addTag(Tags.Blocks.OBSIDIANS);
        this.tag(Hostile.Tags.DATA_CENTER_WALL)
            .addOptional(ResourceKey.create(Registries.BLOCK, Identifier.withDefaultNamespace("black_stained_glass")))
            .add(Hostile.Blocks.DATA_CENTER_IO_PORT.getKey())
            .addOptionalTag(TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("chipped", "black_stained_glass")))
            .addOptional(ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("connectedglass", "borderless_glass_black")))
            .addOptional(ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("connectedglass", "scratched_glass_black")))
            .addOptional(ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("connectedglass", "clear_glass_black")))
            .addOptional(ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("connectedglass", "tinted_borderless_glass_black")))
            .addOptional(ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("securitycraft", "reinforced_black_stained_glass")));
    }
}
