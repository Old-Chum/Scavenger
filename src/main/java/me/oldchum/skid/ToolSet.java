package me.oldchum.skid;


import baritone.api.BaritoneAPI;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.init.Enchantments;
import net.minecraft.init.MobEffects;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemTool;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * This is a copy of Baritone's class of the same name because, for some reason, Baritone is EXTREMELY restrictive with
 * what util classes you are allowed to interact with.
 *
 * @author Avery, Brady, leijurv
 * @author Old Chum
 * @since 07/18/2021
 */
public class ToolSet {
    private final Map<Block, Double> breakStrengthCache = new HashMap();
    private final Function<Block, Double> backendCalculation;
    private final EntityPlayerSP player;

    public ToolSet(EntityPlayerSP player) {
        this.player = player;
        if ((Boolean) BaritoneAPI.getSettings().considerPotionEffects.value) {
            double amplifier = this.potionAmplifier();
            Function<Double, Double> amplify = (x) -> {
                return amplifier * x;
            };
            this.backendCalculation = amplify.compose(this::getBestDestructionTime);
        } else {
            this.backendCalculation = this::getBestDestructionTime;
        }

    }

    public double getStrVsBlock(IBlockState state) {
        return (Double)this.breakStrengthCache.computeIfAbsent(state.getBlock(), this.backendCalculation);
    }

    private int getMaterialCost(ItemStack itemStack) {
        if (itemStack.getItem() instanceof ItemTool) {
            ItemTool tool = (ItemTool)itemStack.getItem();
            return Item.ToolMaterial.valueOf(tool.getToolMaterialName()).ordinal();
        } else {
            return -1;
        }
    }

    public boolean hasSilkTouch(ItemStack stack) {
        return EnchantmentHelper.getEnchantmentLevel(Enchantments.SILK_TOUCH, stack) > 0;
    }

    public int getBestSlot(Block b, boolean preferSilkTouch) {
        return getBestSlot(b.getDefaultState(), preferSilkTouch);
    }

    public int getBestSlot(IBlockState blockState, boolean preferSilkTouch) {
        int best = 0;
        double highestSpeed = -1.0D / 0.0;
        int lowestCost = -2147483648;
        boolean bestSilkTouch = false;

        for(int i = 0; i < 36; ++i) {
            ItemStack itemStack = this.player.inventory.getStackInSlot(i);
            double speed = calculateSpeedVsBlock(itemStack, blockState);
            boolean silkTouch = this.hasSilkTouch(itemStack);
            if (speed > highestSpeed) {
                highestSpeed = speed;
                best = i;
                lowestCost = this.getMaterialCost(itemStack);
                bestSilkTouch = silkTouch;
            } else if (speed == highestSpeed) {
                int cost = this.getMaterialCost(itemStack);
                if (cost < lowestCost && (silkTouch || !bestSilkTouch) || preferSilkTouch && !bestSilkTouch && silkTouch) {
                    highestSpeed = speed;
                    best = i;
                    lowestCost = cost;
                    bestSilkTouch = silkTouch;
                }
            }
        }

        return best;
    }

    private double getBestDestructionTime(Block b) {
        ItemStack stack = this.player.inventory.getStackInSlot(this.getBestSlot(b, false));
        return calculateSpeedVsBlock(stack, b.getDefaultState()) * this.avoidanceMultiplier(b);
    }

    private double avoidanceMultiplier(Block b) {
        return ((List)BaritoneAPI.getSettings().blocksToAvoidBreaking.value).contains(b) ? 0.1D : 1.0D;
    }

    public static double calculateSpeedVsBlock(ItemStack item, IBlockState state) {
        float hardness = state.getBlockHardness((World)null, (BlockPos)null);
        if (hardness < 0.0F) {
            return -1.0D;
        } else {
            float speed = item.getDestroySpeed(state);
            if (speed > 1.0F) {
                int effLevel = EnchantmentHelper.getEnchantmentLevel(Enchantments.EFFICIENCY, item);
                if (effLevel > 0 && !item.isEmpty()) {
                    speed += (float)(effLevel * effLevel + 1);
                }
            }

            speed /= hardness;
            return !state.getMaterial().isToolNotRequired() && (item.isEmpty() || !item.canHarvestBlock(state)) ? (double)(speed / 100.0F) : (double)(speed / 30.0F);
        }
    }

    private double potionAmplifier() {
        double speed = 1.0D;
        if (this.player.isPotionActive(MobEffects.HASTE)) {
            speed *= 1.0D + (double)(this.player.getActivePotionEffect(MobEffects.HASTE).getAmplifier() + 1) * 0.2D;
        }

        if (this.player.isPotionActive(MobEffects.MINING_FATIGUE)) {
            switch(this.player.getActivePotionEffect(MobEffects.MINING_FATIGUE).getAmplifier()) {
                case 0:
                    speed *= 0.3D;
                    break;
                case 1:
                    speed *= 0.09D;
                    break;
                case 2:
                    speed *= 0.0027D;
                    break;
                default:
                    speed *= 8.1E-4D;
            }
        }

        return speed;
    }
}
