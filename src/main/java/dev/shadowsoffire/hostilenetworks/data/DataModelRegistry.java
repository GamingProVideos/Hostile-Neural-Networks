package dev.shadowsoffire.hostilenetworks.data;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import javax.annotation.Nullable;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.google.gson.JsonElement;

import dev.shadowsoffire.hostilenetworks.HostileNetworks;
import dev.shadowsoffire.hostilenetworks.Hostile;
import dev.shadowsoffire.hostilenetworks.HostileConfig;
import dev.shadowsoffire.hostilenetworks.util.DataGained;
import dev.shadowsoffire.hostilenetworks.util.DisplayData;
import dev.shadowsoffire.hostilenetworks.util.DisplayableBlock;
import dev.shadowsoffire.hostilenetworks.util.RequiredData;
import dev.shadowsoffire.placebo.dynreg.DynamicRegistry;
import dev.shadowsoffire.placebo.dynreg.RegistrySerializer;
import dev.shadowsoffire.placebo.json.JsonUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.common.conditions.ConditionalOps;
import net.neoforged.neoforge.event.DefaultDataComponentsBoundEvent;

public class DataModelRegistry extends DynamicRegistry<DataModel> {

    public static final DataModelRegistry INSTANCE = new DataModelRegistry();

    private Multimap<EntityType<?>, EntityDataModel> modelsByType = HashMultimap.create();
    private Multimap<Block, BlockDataModel> modelsByBlock = HashMultimap.create();
    private volatile Map<Identifier, JsonElement> pendingFiles;
    private ConditionalOps<JsonElement> pendingOps;

    public DataModelRegistry() {
        super(HostileNetworks.LOGGER, HostileNetworks.loc("data_models"),
            RegistrySerializer.<DataModel>subtypedSynced("data_models")
                .registerDefault(HostileNetworks.loc("entity_data_model"), EntityDataModel.CODEC)
                .register(HostileNetworks.loc("block_data_model"), BlockDataModel.CODEC));
    }

    @Override
    protected void beginReload(ReloadType type) {
        super.beginReload(type);
        this.modelsByType = HashMultimap.create();
        this.modelsByBlock = HashMultimap.create();
    }

    @Override
    protected void onReload(ReloadType type) {
        if (type == ReloadType.SERVER && this.pendingFiles != null) {
            // Minecraft 26.2 binds item default components after all reload listeners finish.
            // Both the datapack codecs and generated models create ItemStacks, so they must
            // be decoded in DefaultDataComponentsBoundEvent instead of this apply phase.
            this.pendingOps = this.makeConditionalOps();
            super.onReload(type);
            this.modelsByType = ImmutableMultimap.copyOf(this.modelsByType);
            this.modelsByBlock = ImmutableMultimap.copyOf(this.modelsByBlock);
            return;
        }
        // Only the server generates fallback entries. Placebo syncs them like ordinary models;
        // generating them again on the client would collide with the synced entries.
        if (type == ReloadType.SERVER) generateFallbackModels();
        super.onReload(type);
        this.modelsByType = ImmutableMultimap.copyOf(this.modelsByType);
        this.modelsByBlock = ImmutableMultimap.copyOf(this.modelsByBlock);
    }

    public void onComponentsBound(DefaultDataComponentsBoundEvent event) {
        if (event.getUpdateCause() != DefaultDataComponentsBoundEvent.UpdateCause.SERVER_DATA_LOAD) return;
        Map<Identifier, JsonElement> files = this.pendingFiles;
        if (files == null) return;
        this.pendingFiles = null;
        ConditionalOps<JsonElement> ops = this.pendingOps;
        this.pendingOps = null;

        // The first apply already cleared the registry. Keep the tag manager's bindings,
        // which have been loaded by this point, and replace only the empty entry map.
        this.registry = HashBiMap.create();
        this.modelsByType = HashMultimap.create();
        this.modelsByBlock = HashMultimap.create();
        files.forEach((key, json) -> {
            try {
                if (JsonUtil.checkAndLogEmpty(json, key, this.id, this.logger)
                    && JsonUtil.checkConditions(json, key, this.id, this.logger, ops)) {
                    DataModel model = this.elementCodec().parse(ops, json.getAsJsonObject()).getOrThrow();
                    this.register(key, model);
                }
            }
            catch (Exception ex) {
                this.logger.error("Failed parsing {} file {} after item components were bound.", this.id, key, ex);
            }
        });
        this.onReload(ReloadType.SERVER);
    }

    private void generateFallbackModels() {
        int entities = 0;
        int blocks = 0;
        for (EntityType<?> entity : BuiltInRegistries.ENTITY_TYPE) {
            Identifier id = EntityType.getKey(entity);
            // Vanilla miscellaneous entities are mostly projectiles, minecarts, and display entities.
            // Modded types may put living mobs in MISC, so include those; the interaction
            // handler only allows living targets to use the generated entries.
            if (!HostileConfig.autoGenerateMobModels || !this.modelsByType.get(entity).isEmpty()
                || ("minecraft".equals(id.getNamespace()) && entity.getCategory() == MobCategory.MISC)) continue;
            EntityDataModel model = new EntityDataModel(entity, List.of(), Optional.empty(), TextColor.fromRgb(0x66CCFF),
                DisplayData.DEFAULT, 256, Ingredient.of(Hostile.Items.PREDICTION_MATRIX.value()),
                new ItemStack(Hostile.Items.OVERWORLD_PREDICTION.value()), "hostilenetworks.trivia.auto_generated",
                List.of(new ItemStack(Items.EXPERIENCE_BOTTLE)), RequiredData.EMPTY, DataGained.EMPTY, Optional.empty());
            try {
                register(HostileNetworks.loc("generated/entity/" + id.getNamespace() + "/" + id.getPath()), model);
                entities++;
            }
            catch (RuntimeException ex) {
                HostileNetworks.LOGGER.warn("Unable to create fallback data model for entity {}", id, ex);
            }
        }
        if (HostileConfig.autoGenerateBlockModels) {
            for (Block block : BuiltInRegistries.BLOCK) {
                Identifier id = BuiltInRegistries.BLOCK.getKey(block);
                if ("minecraft".equals(id.getNamespace()) || "hostilenetworks".equals(id.getNamespace())
                    || !this.modelsByBlock.get(block).isEmpty()) continue;
                if (block.defaultBlockState().isAir()) continue;
                ItemStack item = new ItemStack(block);
                // Blocks without an item can still be attuned. The barrier is only a GUI icon;
                // those blocks have no meaningful generic fabricator output.
                ItemStack display = item.isEmpty() ? new ItemStack(Items.BARRIER) : item;
                BlockDataModel model = new BlockDataModel(new DisplayableBlock(BuiltInRegistries.BLOCK.wrapAsHolder(block), display),
                    List.of(), Optional.empty(), TextColor.fromRgb(0x66CCFF), DisplayData.DEFAULT, 256,
                    Ingredient.of(Hostile.Items.PREDICTION_MATRIX.value()), new ItemStack(Hostile.Items.OVERWORLD_PREDICTION.value()),
                    "hostilenetworks.trivia.auto_generated", item.isEmpty() ? List.of() : List.of(item), RequiredData.EMPTY, DataGained.EMPTY,
                    Optional.empty(), List.of());
                try {
                    register(HostileNetworks.loc("generated/block/" + id.getNamespace() + "/" + id.getPath()), model);
                    blocks++;
                }
                catch (RuntimeException ex) {
                    HostileNetworks.LOGGER.warn("Unable to create fallback data model for block {}", id, ex);
                }
            }
        }
        HostileNetworks.LOGGER.info("Generated {} fallback mob models and {} fallback block models.", entities, blocks);
    }

    /**
     * Validates that the data model is legal in context of the other registered models.
     * <p>
     * A model is legal if it is the only model for its target (entity type or block), or all models for that target specify an attunement.
     * <p>
     * This method places the model into the appropriate by-target lookup if it is valid.
     */
    @Override
    protected void validateItem(Identifier key, DataModel model) {
        switch (model) {
            case EntityDataModel entityModel -> validateEntity(key, entityModel);
            case BlockDataModel blockModel -> validateBlock(key, blockModel);
            default -> {}
        }
    }

    private void validateEntity(Identifier key, EntityDataModel entityModel) {
        entityModel.entityAndVariants().forEach(type -> {
            Collection<EntityDataModel> existingModels = this.modelsByType.get(type);
            if (existingModels.isEmpty()) {
                // If there are no models, we just take the new one.
                this.modelsByType.put(type, entityModel);
            }
            else if (existingModels.size() == 1) {
                // If there's only one model, it might not have an attunement, so validate that they both do.
                EntityDataModel existing = existingModels.iterator().next();
                if (!existing.hasAttunement() || !entityModel.hasAttunement()) {
                    throwAttunementError(key, "Entity Type", EntityType.getKey(type), existingModels);
                }
                this.modelsByType.put(type, entityModel);
            }
            else {
                // If there's more than one model, we know all the existing ones do, so we only need to check the new one.
                if (!entityModel.hasAttunement()) {
                    throwAttunementError(key, "Entity Type", EntityType.getKey(type), existingModels);
                }
                this.modelsByType.put(type, entityModel);
            }
        });
    }

    private void validateBlock(Identifier key, BlockDataModel blockModel) {
        Stream<Block> blocks = Stream.concat(
            Stream.of(blockModel.block().block()),
            blockModel.variants().stream().map(DisplayableBlock::block));
        blocks.forEach(block -> {
            Collection<BlockDataModel> existingModels = this.modelsByBlock.get(block);
            if (existingModels.isEmpty()) {
                this.modelsByBlock.put(block, blockModel);
            }
            else if (existingModels.size() == 1) {
                BlockDataModel existing = existingModels.iterator().next();
                if (!existing.hasAttunement() || !blockModel.hasAttunement()) {
                    throwAttunementError(key, "Block", BuiltInRegistries.BLOCK.getKey(block), existingModels);
                }
                this.modelsByBlock.put(block, blockModel);
            }
            else {
                if (!blockModel.hasAttunement()) {
                    throwAttunementError(key, "Block", BuiltInRegistries.BLOCK.getKey(block), existingModels);
                }
                this.modelsByBlock.put(block, blockModel);
            }
        });
    }

    @Nullable
    public Collection<EntityDataModel> getForEntity(EntityType<?> type) {
        return this.modelsByType.get(type);
    }

    @Nullable
    public Collection<BlockDataModel> getForBlock(Block block) {
        return this.modelsByBlock.get(block);
    }

    @Override
    public Map<Identifier, JsonElement> prepare(ResourceManager pResourceManager, ProfilerFiller pProfiler) {
        Map<Identifier, JsonElement> files = scanFiles(pResourceManager, pProfiler);
        this.pendingFiles = files;
        return Map.of();
    }

    /** Returns raw model definitions without scheduling a registry reload (used by datafix_all). */
    public Map<Identifier, JsonElement> scanFiles(ResourceManager pResourceManager, ProfilerFiller pProfiler) {
        Map<Identifier, JsonElement> files = super.prepare(pResourceManager, pProfiler);
        HNNRegistryResources.addLegacyFiles(files, pResourceManager, "data_models");
        HostileNetworks.LOGGER.info("Discovered {} data model files before decoding.", files.size());
        return files;
    }

    private void throwAttunementError(Identifier key, String targetKind, Identifier targetId, Collection<? extends DataModel> existingModels) {
        String msg = "Attempted to register multiple models for %s %s without specifying an attunement. When registering multiple models, ALL models must specify an attunement!";
        msg += " Existing models: " + existingModels.stream().map(this::getKey).toList();
        msg += " New model: " + key;
        throw new UnsupportedOperationException(String.format(msg, targetKind, targetId));
    }
}
