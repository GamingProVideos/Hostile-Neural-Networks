package dev.shadowsoffire.hostilenetworks.client;

import java.util.Random;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import java.util.ArrayList;
import java.util.List;

import dev.shadowsoffire.hostilenetworks.data.DataModel;
import dev.shadowsoffire.hostilenetworks.item.DataModelItem;
import dev.shadowsoffire.hostilenetworks.multiblock.DataCenterShell;
import dev.shadowsoffire.hostilenetworks.tile.DataCenterTileEntity;
import dev.shadowsoffire.hostilenetworks.util.ClientEntityCache;
import dev.shadowsoffire.hostilenetworks.util.DisplayEntity;
import dev.shadowsoffire.placebo.dynreg.DynamicHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import org.jspecify.annotations.Nullable;

public class DataCenterRenderer implements BlockEntityRenderer<DataCenterTileEntity, DataCenterRenderer.State> {

    private static final float WORLD_SCALE_FACTOR = 1.5f;
    private static final float DISPLAY_SCALE_FACTOR = 0.55f;
    private static final float CYCLE_TICKS = 100f;
    private static final float RAMP_FRACTION = 0.15f;
    private static final float HOLD_END_FRACTION = 0.45f;
    private static final float ALIVE_END_FRACTION = 0.60f;

    private static final long SEED_MIX_SLOT = 0x9E3779B97F4A7C15L;
    private static final long SEED_MIX_CYCLE = 0xC6BC279692B5C323L;

    public DataCenterRenderer(BlockEntityRendererProvider.Context ctx) {}

    public static final class State extends BlockEntityRenderState {
        AABB shellBounds;
        final List<DisplaySnapshot> displays = new ArrayList<>();
    }

    private record DisplaySnapshot(EntityRenderer<Entity, EntityRenderState> renderer, EntityRenderState state,
                                   float x, float y, float z, float scale, float spin,
                                   double xOffset, double yOffset, double zOffset) {}

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public int getViewDistance() {
        return 96;
    }

    @Override
    public AABB getRenderBoundingBox(DataCenterTileEntity tile) {
        DataCenterShell.Layout layout = tile.getCachedLayout();
        if (layout == null) return new AABB(tile.getBlockPos());
        BlockPos min = layout.shellMin();
        BlockPos max = layout.shellMax();
        return new AABB(min.getX(), min.getY(), min.getZ(), max.getX() + 1, max.getY() + 1, max.getZ() + 1);
    }

    @Override
    public void extractRenderState(DataCenterTileEntity tile, State state, float partialTick, Vec3 cameraPos,
                                   ModelFeatureRenderer.@Nullable CrumblingOverlay crumblingOverlay) {
        BlockEntityRenderer.super.extractRenderState(tile, state, partialTick, cameraPos, crumblingOverlay);
        state.shellBounds = null;
        state.displays.clear();
        if (!tile.isShellValid()) return;
        DataCenterShell.Layout layout = tile.getCachedLayout();
        if (layout == null) return;
        BlockPos here = tile.getBlockPos();
        double minX = layout.shellMin().getX() - here.getX();
        double minY = layout.shellMin().getY() - here.getY();
        double minZ = layout.shellMin().getZ() - here.getZ();
        double maxX = layout.shellMax().getX() - here.getX() + 1;
        double maxY = layout.shellMax().getY() - here.getY() + 1;
        double maxZ = layout.shellMax().getZ() - here.getZ() + 1;

        state.shellBounds = new AABB(minX, minY, minZ, maxX, maxY, maxZ);
        captureDisplaySlots(tile, layout, partialTick, state);
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.shellBounds == null) return;
        collector.submitShapeOutline(pose, Shapes.create(state.shellBounds), RenderTypes.lines(),
            0xFF00FFFF, 1.0f, false);
        for (DisplaySnapshot display : state.displays) {
            pose.pushPose();
            pose.translate(display.x, display.y, display.z);
            pose.scale(display.scale, display.scale, display.scale);
            pose.mulPose(Axis.YP.rotationDegrees(display.spin));
            pose.translate(display.xOffset, display.yOffset, display.zOffset);
            display.renderer.submit(display.state, pose, collector, camera);
            pose.popPose();
        }
    }

    private void captureDisplaySlots(DataCenterTileEntity tile, DataCenterShell.Layout layout, float partialTick, State state) {
        if (tile.displaySlotPositions == null) generateSlotLayout(tile);

        int activeMask = tile.getActiveSlotsMask();
        if (activeMask == 0) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        BlockPos here = tile.getBlockPos();
        BlockPos center = layout.centerPos();
        float cx = (center.getX() - here.getX()) + 0.5f;
        float cy = (center.getY() - here.getY()) + 0.5f;
        float cz = (center.getZ() - here.getZ()) + 0.5f;

        float baseSpin = ((mc.player != null ? mc.player.tickCount : 0) + partialTick) * 2f;
        float now = mc.level.getGameTime() + partialTick;

        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        for (int i = 0; i < DataCenterTileEntity.DISPLAY_SLOT_COUNT; i++) {
            captureOneDisplaySlot(tile, i, activeMask, now, baseSpin, cx, cy, cz, partialTick, dispatcher, mc, state);
        }
    }

    private void captureOneDisplaySlot(DataCenterTileEntity tile, int slotIdx, int activeMask, float now, float baseSpin,
        float cx, float cy, float cz, float partialTick, EntityRenderDispatcher dispatcher, Minecraft mc, State state) {

        float localTime = now - tile.displaySlotPhaseOffsets[slotIdx];
        int cycleIdx = Mth.floor(localTime / CYCLE_TICKS);
        float t = (localTime - cycleIdx * CYCLE_TICKS) / CYCLE_TICKS;
        float envelope = envelopeAt(t);
        if (envelope <= 0.001f) return;

        // Picked entity index is locked for the full cycle; if the slot empties mid-cycle we just stop rendering it.
        if (tile.displaySlotCycleIdx[slotIdx] != cycleIdx) {
            tile.displaySlotCycleIdx[slotIdx] = cycleIdx;
            tile.displaySlotEntityIdx[slotIdx] = pickEntityForCycle(activeMask, tile.getBlockPos(), slotIdx, cycleIdx);
        }
        int entityIdx = tile.displaySlotEntityIdx[slotIdx];
        if (entityIdx < 0 || entityIdx >= DataCenterTileEntity.MODEL_SLOTS) return;

        ItemStack stack = tile.getInventory().getStackInSlot(entityIdx);
        DynamicHolder<DataModel> model = DataModelItem.getStoredModel(stack);
        if (!model.isBound()) return;
        DisplayEntity display = model.get().displayEntity(mc.level);
        Entity ent = ClientEntityCache.computeIfAbsent(display, mc.level);
        if (ent == null) return;

        if (mc.player != null) ent.tickCount = mc.player.tickCount;

        ent.setYRot(0);
        if (ent instanceof LivingEntity living) {
            living.yBodyRot = 0;
            living.yBodyRotO = 0;
            living.yHeadRot = 0;
            living.yHeadRotO = 0;
        }

        float[] off = tile.displaySlotPositions[slotIdx];
        float scale = WORLD_SCALE_FACTOR * DISPLAY_SCALE_FACTOR * display.scale() * envelope;
        float spin = baseSpin + (slotIdx * 360f / DataCenterTileEntity.DISPLAY_SLOT_COUNT);

        @SuppressWarnings("unchecked")
        EntityRenderer<Entity, EntityRenderState> renderer =
            (EntityRenderer<Entity, EntityRenderState>) dispatcher.getRenderer(ent);
        EntityRenderState entityState = renderer.createRenderState();
        renderer.extractRenderState(ent, entityState, partialTick);
        state.displays.add(new DisplaySnapshot(renderer, entityState, cx + off[0], cy + off[1], cz + off[2],
            scale, spin, display.xOffset(), display.yOffset(), display.zOffset()));
    }

    private static float envelopeAt(float t) {
        if (t < RAMP_FRACTION) return t / RAMP_FRACTION;
        if (t < HOLD_END_FRACTION) return 1f;
        if (t < ALIVE_END_FRACTION) return (ALIVE_END_FRACTION - t) / RAMP_FRACTION;
        return 0f;
    }

    private static int pickEntityForCycle(int activeMask, BlockPos pos, int slotIdx, int cycleIdx) {
        if (activeMask == 0) return -1;
        int count = Integer.bitCount(activeMask);
        long seed = pos.asLong() ^ (slotIdx * SEED_MIX_SLOT) ^ (cycleIdx * SEED_MIX_CYCLE);
        int pick = new Random(seed).nextInt(count);
        int seen = 0;
        for (int i = 0; i < DataCenterTileEntity.MODEL_SLOTS; i++) {
            if ((activeMask & (1 << i)) == 0) continue;
            if (seen == pick) return i;
            seen++;
        }
        return -1;
    }

    private static void generateSlotLayout(DataCenterTileEntity tile) {
        BlockPos pos = tile.getBlockPos();
        Random rng = new Random(pos.asLong());
        float[][] positions = new float[DataCenterTileEntity.DISPLAY_SLOT_COUNT][3];
        float[] phases = new float[DataCenterTileEntity.DISPLAY_SLOT_COUNT];
        int idx = 0;
        for (int xi = 0; xi < 3; xi++) {
            for (int yi = 0; yi < 2; yi++) {
                for (int zi = 0; zi < 2; zi++) {
                    float cellCx = -1.33f + xi * 1.33f;
                    float cellCy = -1f + yi * 2f;
                    float cellCz = -1f + zi * 2f;
                    positions[idx][0] = cellCx + (rng.nextFloat() - 0.5f) * 0.8f;
                    positions[idx][1] = cellCy + (rng.nextFloat() - 0.5f) * 1.4f;
                    positions[idx][2] = cellCz + (rng.nextFloat() - 0.5f) * 1.4f;
                    phases[idx] = rng.nextFloat() * CYCLE_TICKS;
                    idx++;
                }
            }
        }
        tile.displaySlotPositions = positions;
        tile.displaySlotPhaseOffsets = phases;
    }
}
