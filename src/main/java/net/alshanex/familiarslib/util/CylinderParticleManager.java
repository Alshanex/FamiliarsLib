package net.alshanex.familiarslib.util;

import io.redspace.ironsspellbooks.capabilities.magic.MagicManager;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

public abstract class CylinderParticleManager {
    // Credits to GameTech

    private static final Random RANDOM = new Random();

    public enum ParticleDirection {
        UPWARD,
        DOWNWARD
    }

    public static void spawnParticlesAtBlockPos(Level level, Vec3 pos, int particleCount, ParticleOptions particleType, ParticleDirection direction, double radius, double height, double yOffset) {
        if (!level.isClientSide) {
            double baseY = pos.y + yOffset;

            for (int i = 0; i < particleCount; i++) {
                double theta = 2 * Math.PI * RANDOM.nextDouble();

                double yPosition = baseY + (RANDOM.nextDouble() * height);

                double xOffset = radius * Math.cos(theta);
                double zOffset = radius * Math.sin(theta);

                Vec3 directionVector;
                if (direction == ParticleDirection.UPWARD) {
                    directionVector = new Vec3(0, 1, 0).normalize();
                } else {
                    directionVector = new Vec3(0, -1, 0).normalize();
                }

                MagicManager.spawnParticles(level, particleType,
                        pos.x + xOffset, yPosition, pos.z + zOffset,
                        0, directionVector.x, directionVector.y, directionVector.z, 0.1, true);
            }
        }
    }

    public static void spawnParticles(Level level, LivingEntity entity, int particleCount, ParticleOptions particleType, ParticleDirection direction, double radius, double height, double yOffset) {
        if (!level.isClientSide) {
            double baseY = entity.getY() + yOffset;

            for (int i = 0; i < particleCount; i++) {
                double theta = 2 * Math.PI * RANDOM.nextDouble();

                double yPosition = baseY + (RANDOM.nextDouble() * height);

                double xOffset = radius * Math.cos(theta);
                double zOffset = radius * Math.sin(theta);

                Vec3 directionVector;
                if (direction == ParticleDirection.UPWARD) {
                    directionVector = new Vec3(0, 1, 0).normalize();
                } else {
                    directionVector = new Vec3(0, -1, 0).normalize();
                }

                MagicManager.spawnParticles(level, particleType,
                        entity.getX() + xOffset, yPosition, entity.getZ() + zOffset,
                        0, directionVector.x, directionVector.y, directionVector.z, 0.1, true);
            }
        }
    }
}
