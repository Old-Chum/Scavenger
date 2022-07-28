package me.oldchum;

import baritone.api.BaritoneAPI;
import me.rigamortis.seppuku.api.event.player.EventPlayerUpdate;
import me.rigamortis.seppuku.api.event.player.EventUpdateWalkingPlayer;
import me.rigamortis.seppuku.api.event.render.EventRenderBlock;
import me.rigamortis.seppuku.api.module.Module;
import net.minecraft.block.Block;
import net.minecraft.block.BlockChest;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.init.Blocks;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import team.stiff.pomelo.impl.annotated.handler.annotation.Listener;

/**
 * @author Old Chum
 * @since 7/27/2022
 */
public class ScavengerModule extends Module {
    private boolean initialized = false;

    public ScavengerModule() {
        super("Scavenger", new String[]{"Scav", "Scavenge"}, "Automatically explores the world looking for dungeon chests to loot.", "NONE", -1, ModuleType.WORLD);
    }

    @Override
    public void onEnable() {
        super.onEnable();
        if (this.initialized) {
            ScavengeProcess.INSTANCE.start = new BlockPos(Minecraft.getMinecraft().player.posX, Minecraft.getMinecraft().player.posY, Minecraft.getMinecraft().player.posZ);
        }
    }

    @Listener
    public void onUpdate (EventUpdateWalkingPlayer event) {
        // Baritone really doesn't like it when you register stuff without a world
        if (!this.initialized) {
            BaritoneAPI.getProvider().getBaritoneForPlayer(Minecraft.getMinecraft().player).getPathingControlManager().registerProcess(ScavengeProcess.INSTANCE);
            ScavengeProcess.INSTANCE.start = new BlockPos(Minecraft.getMinecraft().player.posX, Minecraft.getMinecraft().player.posY, Minecraft.getMinecraft().player.posZ);
            this.initialized = true;
        }
    }
}
