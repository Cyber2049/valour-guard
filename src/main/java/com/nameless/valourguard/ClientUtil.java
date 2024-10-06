package com.nameless.valourguard;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.TerrainParticle;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

import java.util.Random;

@OnlyIn(Dist.CLIENT)
public class ClientUtil {
    public static void dust(LivingEntityPatch<?> entityPatch){
        LivingEntity entity = entityPatch.getOriginal();
        Level level = entity.level;
        if(!level.isClientSide() || !entity.isOnGround()) return;
        BlockPos bp = entity.getOnPos();
        BlockState bs = entity.level.getBlockState(bp);
        Minecraft.getInstance().getBlockRenderer().getBlockModelShaper().getTexture(bs, level, bp);
        for (int i = 0; i < 16; i += 1) {
            double x = bp.getX() + (i % 2);
            double z = bp.getZ() + 1 - (i % 2);

            TerrainParticle blockParticle = new TerrainParticle((ClientLevel)level, x, bp.getY() + 1, z, 0, 0, 0, bs, bp);
            blockParticle.setParticleSpeed((Math.random() - 0.5D) * 0.4D, Math.random() * 0.3D, (Math.random() - 0.5D) * 0.4D);
            blockParticle.setLifetime(10 + new Random().nextInt(60));

            Minecraft mc = Minecraft.getInstance();
            mc.particleEngine.add(blockParticle);
        }
    }
}
