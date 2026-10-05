package net.alshanex.familiarslib.render;

import net.alshanex.familiarslib.block.ShrinkingStationBlock;
import net.alshanex.familiarslib.block.entity.ShrinkingStationBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import io.redspace.ironsspellbooks.registries.ParticleRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DiggerItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class ShrinkingStationBlockEntityRenderer implements BlockEntityRenderer<ShrinkingStationBlockEntity> {
    ItemRenderer itemRenderer;

    public ShrinkingStationBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        this.itemRenderer = context.getItemRenderer();
    }

    private static final Vec3 INPUT_POS = new Vec3(.5, 1.4, .5);
    private static final Vec3 OUTPUT_POS = new Vec3(.5, 1.4, .5);

    private static final Vec3 ANTENNA_NORTH_OFFSET = new Vec3(0, 1.5, -0.55);
    private static final Vec3 ANTENNA_SOUTH_OFFSET = new Vec3(0, 1.5, 0.55);

    @Override
    public void render(ShrinkingStationBlockEntity shrinkingStation, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        ItemStack inputItem = shrinkingStation.getInputItem();
        ItemStack outputItem = shrinkingStation.getOutputItem();

        Player player = Minecraft.getInstance().player;
        float rotation = player.tickCount * 2 + partialTick;

        // Render input item (if any)
        if (!inputItem.isEmpty()) {
            // Calculate shrinking scale based on counter progress
            float scale = calculateItemScale(shrinkingStation, partialTick);
            renderItem(inputItem, INPUT_POS, rotation, scale, shrinkingStation, partialTick, poseStack, bufferSource, packedLight, packedOverlay);

            if(shrinkingStation.isShrinkable()){
                BlockState state = shrinkingStation.getBlockState();

                // Check if the block actually has the property to avoid crashes if state is invalid
                if (state.hasProperty(ShrinkingStationBlock.FACING)) {
                    Direction facing = state.getValue(ShrinkingStationBlock.FACING);

                    // Calculate where the antennae are in the world based on rotation
                    float yRot = getRotationForFacing(facing);

                    Vec3 centerPos = new Vec3(0.5, 0, 0.5); // Pivot point for rotation

                    // Rotate offsets and add to center
                    Vec3 northAntennaPos = centerPos.add(ANTENNA_NORTH_OFFSET.yRot((float) Math.toRadians(-yRot)));
                    Vec3 southAntennaPos = centerPos.add(ANTENNA_SOUTH_OFFSET.yRot((float) Math.toRadians(-yRot)));

                    // Spawn particles from antenna to the item (INPUT_POS)
                    spawnElectricParticles(northAntennaPos, INPUT_POS, shrinkingStation, 1);
                    spawnElectricParticles(southAntennaPos, INPUT_POS, shrinkingStation, 1);

                    spawnParticleAt(northAntennaPos, shrinkingStation);
                    spawnParticleAt(southAntennaPos, shrinkingStation);
                }
            }
        }

        // Render output item (if any)
        if (!outputItem.isEmpty()) {
            // Output items are always miniature (50% scale)
            float outputScale = 0.5f * 0.65f;
            renderItem(outputItem, OUTPUT_POS, rotation * 0.5f, outputScale, shrinkingStation, partialTick, poseStack, bufferSource, packedLight, packedOverlay);
        }
    }

    /**
     * Calculates the scale for the item based on shrinking progress
     * Starts at 1.0 (100%) and goes down to 0.5 (50%) over 100 ticks
     */
    private float calculateItemScale(ShrinkingStationBlockEntity shrinkingStation, float partialTick) {
        ItemStack inputItem = shrinkingStation.getInputItem();

        // If the item is already a consumable (miniaturized), show it at 50% scale
        if (net.alshanex.familiarslib.util.consumables.FamiliarConsumableIntegration.isConsumableItem(inputItem)) {
            return 0.5f * 0.65f;
        }

        // If the item is not shrinkable, use default scale
        if (!shrinkingStation.isShrinkable()) {
            return 0.65f;
        }

        // During shrinking process: scale from 100% to 50%
        int counter = shrinkingStation.getCounter();
        float progress = (counter + partialTick) / 100.0f;
        progress = Math.min(progress, 1.0f);

        float baseScale = 1.0f - (progress * 0.5f);

        return baseScale * 0.65f;
    }

    private void renderItem(ItemStack itemStack, Vec3 offset, float yRot, float scale, ShrinkingStationBlockEntity shrinkingStation, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        poseStack.pushPose();
        //renderId seems to be some kind of uuid/salt
        int renderId = (int) shrinkingStation.getBlockPos().asLong();

        poseStack.translate(offset.x, offset.y, offset.z);
        poseStack.mulPose(Axis.YP.rotationDegrees(yRot));
        if (itemStack.getItem() instanceof SwordItem || itemStack.getItem() instanceof DiggerItem) {
            poseStack.mulPose(Axis.ZP.rotationDegrees(-45));
        }

        poseStack.scale(scale, scale, scale);

        int brightLight = 0xF000F0;

        itemRenderer.renderStatic(itemStack, ItemDisplayContext.FIXED, brightLight, packedOverlay, poseStack, bufferSource, shrinkingStation.getLevel(), renderId);
        poseStack.popPose();
    }

    private float getRotationForFacing(Direction facing) {
        return switch (facing) {
            case SOUTH -> 270;
            case WEST -> 0;
            case EAST -> 180;
            default -> 90;
        };
    }

    private void spawnElectricParticles(Vec3 startLocal, Vec3 endLocal, ShrinkingStationBlockEntity tile, int frequency) {
        if (tile.getLevel() == null) return;

        // Convert local block coordinates to absolute world coordinates
        Vec3 start = startLocal.add(tile.getBlockPos().getX(), tile.getBlockPos().getY(), tile.getBlockPos().getZ());
        Vec3 end = endLocal.add(tile.getBlockPos().getX(), tile.getBlockPos().getY(), tile.getBlockPos().getZ());

        // Spawn a few particles each frame
        for (int i = 0; i < frequency; i++) {
            float t = tile.getLevel().random.nextFloat();

            double x = start.x + (end.x - start.x) * t;
            double y = start.y + (end.y - start.y) * t;
            double z = start.z + (end.z - start.z) * t;

            double jitter = 0.05;
            double offsetX = (tile.getLevel().random.nextDouble() - 0.5) * jitter;
            double offsetY = (tile.getLevel().random.nextDouble() - 0.5) * jitter;
            double offsetZ = (tile.getLevel().random.nextDouble() - 0.5) * jitter;

            tile.getLevel().addParticle(ParticleTypes.ELECTRIC_SPARK,
                    x + offsetX, y + offsetY, z + offsetZ,
                    0, 0, 0);
        }
    }

    private void spawnParticleAt(Vec3 localPos, ShrinkingStationBlockEntity tile) {
        if (tile.getLevel() == null) return;

        // Convert local block coordinates to world coordinates
        double x = tile.getBlockPos().getX() + localPos.x;
        double y = tile.getBlockPos().getY() + localPos.y;
        double z = tile.getBlockPos().getZ() + localPos.z;

        // Spawn 1 electric spark with 0 velocity
        tile.getLevel().addParticle(ParticleRegistry.ELECTRICITY_PARTICLE.get(), x, y, z, 0.0, 0.0, 0.0);
    }
}