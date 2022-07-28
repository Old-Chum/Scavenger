package me.oldchum;

import baritone.api.BaritoneAPI;
import baritone.api.pathing.goals.GoalGetToBlock;
import baritone.api.pathing.goals.GoalInverted;
import baritone.api.process.IBaritoneProcess;
import baritone.api.process.PathingCommand;
import baritone.api.process.PathingCommandType;
import baritone.api.utils.IPlayerContext;
import baritone.api.utils.Rotation;
import baritone.api.utils.RotationUtils;
import me.oldchum.skid.ToolSet;
import me.rigamortis.seppuku.Seppuku;
import me.rigamortis.seppuku.api.util.MathUtil;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityMinecartChest;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.ClickType;
import net.minecraft.network.play.client.CPacketPlayerTryUseItemOnBlock;
import net.minecraft.network.play.client.CPacketUseEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.HashSet;
import java.util.Set;

// TODO: Stop to defend against mobs
// TODO: save this.searched
// TODO: Avoid desert temple pressure plates
// TODO: Allow users to pick which items they want to collect

/**
 * @author Old Chum
 * @since 7/27/2022
 */
public class ScavengeProcess implements IBaritoneProcess {
    public static ScavengeProcess INSTANCE = new ScavengeProcess();
    public Set<BlockPos> searched = new HashSet<>();

    private int timeout = 0;
    public BlockPos start = null;

    public float partialTicks = 0;

    @Override
    public boolean isActive() {
        return Seppuku.INSTANCE.getModuleManager().find(ScavengerModule.class).isEnabled();
    }

    @Override
    public PathingCommand onTick(boolean b, boolean b1) {
        if (this.timeout > 0) {
            this.timeout--;
            return new PathingCommand(null, PathingCommandType.DEFER);
        }

        Minecraft mc = Minecraft.getMinecraft();
        IPlayerContext ctx = BaritoneAPI.getProvider().getPrimaryBaritone().getPlayerContext();

        double distance = Double.MAX_VALUE;
        EntityMinecartChest minecartChest = null;
        BlockPos pos = null;
        for (Entity entity : mc.world.loadedEntityList) {
            if (entity instanceof EntityItem) {
                EntityItem itemEntity = (EntityItem) entity;
                if (itemEntity.getItem().getItem() == Items.GOLDEN_APPLE) {
                    return new PathingCommand(new GoalGetToBlock(new BlockPos(itemEntity.posX, itemEntity.posY, itemEntity.posZ)), PathingCommandType.SET_GOAL_AND_PATH);
                }
            } else if (entity instanceof EntityMinecartChest) {
                double dist = MathUtil.getDistance(new Vec3d(entity.posX, entity.posY, entity.posZ), ctx.player().posX, ctx.player().posY, ctx.player().posZ);
                if (dist < distance) {
                    boolean cached = false;
                    for (BlockPos searchedPos : this.searched) {
                        if (entity.getEntityBoundingBox().intersects(new AxisAlignedBB(searchedPos))) {
                            cached = true;
                            break;
                        }
                    }

                    if (!cached) {
                        minecartChest = (EntityMinecartChest) entity;
                        pos = new BlockPos(entity.posX, entity.posY, entity.posZ);
                        distance = dist;
                    }
                }
            }
        }


        for (TileEntity tileEntity : Minecraft.getMinecraft().world.loadedTileEntityList) {
            BlockPos tilePos = tileEntity.getPos();
            Block block = tileEntity.getBlockType();

            if (!this.searched.contains(tilePos)) {
                if (block == Blocks.CHEST || block == Blocks.TRAPPED_CHEST) {
                    double dist = MathUtil.getDistance(new Vec3d(tilePos), ctx.player().posX, ctx.player().posY, ctx.player().posZ);
                    if (dist < distance) {
                        pos = tilePos;
                        distance = dist;
                        minecartChest = null; // We found a chest closer than the minecart chest
                    }
                }
            }
        }

        if (ctx.player().openContainer != ctx.player().inventoryContainer) {
            for (int i = 0; i < ctx.player().openContainer.getInventory().size() - 36; i++) {
                if (ctx.player().openContainer.getInventory().get(i).getItem() == Items.GOLDEN_APPLE) {
                    mc.playerController.windowClick(ctx.player().openContainer.windowId, i, 0, ClickType.QUICK_MOVE, mc.player);
                    this.timeout = 10;
                    return new PathingCommand(null, PathingCommandType.DEFER);
                }
            }

            ctx.player().closeScreen();
            this.searched.add(pos);
            return new PathingCommand(null, PathingCommandType.DEFER);
        } else if (minecartChest == null && pos != null) {
            if (distance > 3.0D) { // 3 is a good range :-)
                return new PathingCommand(new GoalGetToBlock(pos), PathingCommandType.SET_GOAL_AND_PATH);
            } else {
                // Open the goal container if it is not already opened
                mc.getConnection().sendPacket(new CPacketPlayerTryUseItemOnBlock(pos, EnumFacing.NORTH, EnumHand.MAIN_HAND, 0.0f, 0.0f, 0.0f));
                timeout = 20; // Wait half a second after opening the chest
                return new PathingCommand(null, PathingCommandType.DEFER);
            }
        } else if (minecartChest != null) {
            if (distance > 3.0D) { // 3 is a good range :-)
                return new PathingCommand(new GoalGetToBlock(pos), PathingCommandType.SET_GOAL_AND_PATH);
            } else {
                Rotation rotation = RotationUtils.calcRotationFromVec3d(new Vec3d(ctx.player().posX, ctx.player().posY, ctx.player().posZ), new Vec3d(minecartChest.posX, minecartChest.posY - 1, minecartChest.posZ), new Rotation(ctx.player().rotationYaw, ctx.player().rotationPitch));
                ctx.player().rotationYaw = rotation.getYaw();
                ctx.player().rotationPitch = rotation.getPitch();

                mc.entityRenderer.getMouseOver(mc.getRenderPartialTicks());

                Vec3d vec3d = new Vec3d(mc.objectMouseOver.hitVec.x - minecartChest.posX, mc.objectMouseOver.hitVec.y - minecartChest.posY, mc.objectMouseOver.hitVec.z - minecartChest.posZ);

                mc.getConnection().sendPacket(new CPacketUseEntity(minecartChest, EnumHand.MAIN_HAND, vec3d));
                mc.getConnection().sendPacket(new CPacketUseEntity(minecartChest, EnumHand.MAIN_HAND));
                timeout = 20;
                return new PathingCommand(null, PathingCommandType.DEFER);
            }
        } else {
            return new PathingCommand(new GoalInverted(new GoalGetToBlock(this.start)), PathingCommandType.SET_GOAL_AND_PATH);
        }
    }

    @Override
    public boolean isTemporary() {
        return false;
    }

    @Override
    public void onLostControl() {
        Seppuku.INSTANCE.getModuleManager().find(ScavengerModule.class).onDisable();
    }

    @Override
    public double priority() {
        return IBaritoneProcess.super.priority();
    }

    @Override
    public String displayName() {
        return IBaritoneProcess.super.displayName();
    }

    @Override
    public String displayName0() {
        return "Scavenger Process";
    }

    public static void switchToBestTool (IBlockState blockState, boolean preferSilkTouch) {
        Minecraft mc = Minecraft.getMinecraft();
        int slotID = new ToolSet(mc.player).getBestSlot(blockState, preferSilkTouch);

        if (slotID >= 9) {
            int toSlot = 0;
            for (int i = 0; i < 9; i++) {
                if (mc.player.inventory.mainInventory.get(i).isEmpty()) {
                    toSlot = i;
                    break;
                }
            }

            mc.playerController.windowClick(mc.player.inventoryContainer.windowId, slotID, toSlot, ClickType.SWAP, mc.player);
        } else {
            mc.player.inventory.currentItem = slotID;
            mc.playerController.updateController();
        }
    }
}
