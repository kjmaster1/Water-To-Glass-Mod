package com.kjmaster.replacewaterwithglass;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BucketPickup;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fluids.FluidAttributes;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.IFluidBlock;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.w3c.dom.Text;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.List;


// The value here should match an entry in the META-INF/mods.toml file
@Mod("replacewaterwithglass")
public class ReplaceWaterWithGlass {
    // Directly reference a log4j logger.
    private static final Logger LOGGER = LogManager.getLogger();
    private int numTicks = 0;
    private int total = 0;
    private int numBlocksReplaced = 15365090;

    private FluidStack activeType = FluidStack.EMPTY;

    private final Set<BlockPos> recurringNodes = new ObjectOpenHashSet<>();


    public ReplaceWaterWithGlass() {
        // Register ourselves for server and other game events we are interested in
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onTick(TickEvent.PlayerTickEvent event) {
        if (!event.player.level.isClientSide()) {
            numTicks++;

            if (numTicks == 1) {
                int[] stats = getStats("https://www.tiktok.com/@minecraftcodedchallenges/video/6939261771148545286?lang=en");
                int likes1 = stats[0];
                int shares1 = stats[1];

                total = (1000000 * 10) + likes1 + (shares1 * 10);

                TextComponent currentTotal = new TextComponent("Current total is: " + Integer.toString(total));
                event.player.sendMessage(currentTotal, event.player.getUUID());
            }

            if (numTicks % 6000 == 0 && numBlocksReplaced < total) {

                int amount = total - numBlocksReplaced;
                TextComponent amountBeingReplaced = new TextComponent("Currently replacing a total of: " + Integer.toString(amount) + " water blocks");
                event.player.sendMessage(amountBeingReplaced, event.player.getUUID());

                while (amount > 0) {
                    if (suck(event.player.getOnPos(), event.player.level)) {
                        amount--;
                    } else {
                        reset();
                    }
                    System.out.println(amount);
                }

                TextComponent currentTotal = new TextComponent("Currently replaced a total of: " + Integer.toString(total) + " water blocks");
                event.player.sendMessage(currentTotal, event.player.getUUID());
                numBlocksReplaced = total;
            }

            if (numTicks >= 8000) {
                numTicks = 0;
            }
        }
    }

    private int getFollowers() {

        Document doc = null;
        try {
            doc = Jsoup.connect("https://www.tiktok.com/@minecraftcodedchallenges?lang=en")
                    .timeout(240 * 1000)
                    .userAgent("Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/535.2 (KHTML, like Gecko) Chrome/15.0.874.120 Safari/535.2")
                    .get();
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println(doc);

        Elements elements = Objects.requireNonNull(doc).getElementsByClass("count-infos");

        System.out.println(elements);
        System.out.println();

        Element element = elements.get(0);

        System.out.println(element);

        String followers = element.children().get(1).getElementsByAttribute("title").text();
        String[][] conversionMatrix = {{"K", "1000"}, {"M", "1000000"}};

        for (String[] matrix : conversionMatrix) {
            if (followers.endsWith(matrix[0])) {
                BigDecimal temp = new BigDecimal(followers.substring(0, followers.indexOf(matrix[0])));
                temp = temp.multiply(new BigDecimal(matrix[1]));
                followers = temp.toBigInteger().toString();
            }
        }

        return Integer.parseInt(followers);
    }

    private int[] getStats(String url) {
        Document doc = null;
        try {
            doc = Jsoup.connect(url)
                    .timeout(240 * 1000)
                    .ignoreContentType(true)
                    .get();
        } catch (IOException e) {
            e.printStackTrace();
        }

        System.out.println(doc);

        Elements scripts = Objects.requireNonNull(doc).getElementsByTag("script");

        Element script = scripts.get(6);

        String scriptData = script.data();

        JsonParser parser = new JsonParser();
        JsonElement element = parser.parse(scriptData);
        JsonObject object = element.getAsJsonObject();
        JsonObject stats = object.getAsJsonObject("props")
                .getAsJsonObject("pageProps")
                .getAsJsonObject("itemInfo")
                .getAsJsonObject("itemStruct")
                .getAsJsonObject("stats");
        int likes = stats.get("diggCount").getAsInt();
        int shares = stats.get("shareCount").getAsInt();

        return new int[]{likes, shares};
    }

    private boolean suck(BlockPos pos, Level world) {
        boolean hasFilter = false;
        //First see if there are any fluid blocks touching the pump - if so, sucks and adds the location to the recurring list
        for (Direction orientation : Direction.values()) {
            if (suck(pos.relative(orientation), hasFilter, true, world)) {
                return true;
            }
        }
        //Even though we can add to recurring in the above for loop, we always then exit and don't get to here if we did so
        List<BlockPos> tempPumpList = Arrays.asList(recurringNodes.toArray(new BlockPos[0]));
        Collections.shuffle(tempPumpList);
        //Finally, go over the recurring list of nodes and see if there is a fluid block available to suck - if not, will iterate around the recurring block, attempt to suck,
        //and then add the adjacent block to the recurring list
        for (BlockPos tempPumpPos : tempPumpList) {
            if (suck(tempPumpPos, hasFilter, false, world)) {
                return true;
            }
            //Add all the blocks surrounding this recurring node to the recurring node list
            for (Direction orientation : Direction.values()) {
                BlockPos side = tempPumpPos.relative(orientation);
                if (distanceBetween(pos, side) <= Integer.MAX_VALUE) {
                    if (suck(side, hasFilter, true, world)) {
                        return true;
                    }
                }
            }
            recurringNodes.remove(tempPumpPos);
        }
        return false;
    }

    private boolean suck(BlockPos pos, boolean hasFilter, boolean addRecurring, Level world) {
        //Note: we get the block state from the world so that we can get the proper block in case it is fluid logged
        Optional<BlockState> state = getBlockState(world, pos);
        if (state.isPresent()) {
            BlockState blockState = state.get();
            FluidState fluidState = blockState.getFluidState();
            if (!fluidState.isEmpty() && fluidState.isSource()) {
                //Just in case someone does weird things and has a fluid state that is empty and a source
                // only allow collecting from non empty sources
                Fluid sourceFluid = fluidState.getType();
                FluidStack fluidStack = new FluidStack(sourceFluid, FluidAttributes.BUCKET_VOLUME);
                Block block = blockState.getBlock();
                if (block instanceof IFluidBlock) {
                    fluidStack = ((IFluidBlock) block).drain(world, pos, IFluidHandler.FluidAction.SIMULATE);
                    if (validFluid(fluidStack, true)) {
                        //Actually drain it
                        fluidStack = ((IFluidBlock) block).drain(world, pos, IFluidHandler.FluidAction.EXECUTE);
                        suck(fluidStack, pos, addRecurring);
                        world.setBlock(pos, Blocks.GLASS.defaultBlockState(), Constants.BlockFlags.DEFAULT);
                        return true;
                    }
                } else if (block instanceof BucketPickup && validFluid(fluidStack, false)) {
                    //If it can be picked up by a bucket and we actually want to pick it up, do so to update the fluid type we are doing
                    if (true) {
                        ((ServerLevel) world).setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, true);
                        //Note we only attempt taking if it is not water, or we want to pump water sources
                        // otherwise we assume the type from the fluid state is correct
                        ItemStack itemStack = ((BucketPickup) block).pickupBlock(world, pos, blockState);
                        if (FluidUtil.getFluidContained(itemStack).isPresent()) {
                            sourceFluid = FluidUtil.getFluidContained(itemStack).get().getFluid();
                        }
                        //Update the fluid stack in case something somehow changed about the type
                        // making sure that we replace to heavy water if we got heavy water
                        world.setBlock(pos, Blocks.GLASS.defaultBlockState(), Constants.BlockFlags.DEFAULT);
                        fluidStack = new FluidStack(sourceFluid, FluidAttributes.BUCKET_VOLUME);
                        if (!validFluid(fluidStack, false)) {
                            LOGGER.warn("Fluid removed without successfully picking up. Fluid {} at {} in {} was valid, but after picking up was {}.",
                                    fluidState.getType(), pos, world, sourceFluid);
                            return false;
                        }
                        ((ServerLevel) world).setChunkForced(pos.getX() >> 4, pos.getZ() >> 4, false);
                    }
                    suck(fluidStack, pos, addRecurring);
                    return true;
                }
                //Otherwise, we do not know how to drain from the block or it is not valid and we shouldn't take it so don't handle it
            }
        }
        return false;
    }

    private void suck(@Nonnull FluidStack fluidStack, BlockPos pos, boolean addRecurring) {
        //Size doesn't matter, but we do want to take the NBT into account
        activeType = new FluidStack(fluidStack, 1);
        if (addRecurring) {
            recurringNodes.add(pos);
        }
    }

    private boolean validFluid(@Nonnull FluidStack fluidStack, boolean recheckSize) {
        if (!fluidStack.isEmpty() && (activeType.isEmpty() || activeType.isFluidEqual(fluidStack))) {
            return true;
        }
        return false;
    }

    private Optional<BlockState> getBlockState(@Nullable Level world, @Nonnull BlockPos pos) {
        if (!isBlockLoaded(world, pos)) {
            //If the world is null or its a world reader and the block is not loaded, return empty
            return Optional.empty();
        }
        return Optional.of(world.getBlockState(pos));
    }

    private boolean isBlockLoaded(@Nullable Level world, @Nonnull BlockPos pos) {
        if (world == null || !world.isInWorldBounds(pos)) {
            return false;
        }
        else {
                return ((Level) world).isLoaded(pos);
        }
            //Note: We don't bother checking if it is a world and then isBlockPresent because
            // all that does is also validate the y value is in bounds, and we already check to make
            // sure the position is valid both in the y and xz directions
    }

    private double distanceBetween(BlockPos start, BlockPos end) {
        return Math.sqrt(start.distSqr(end, true));
    }

    private void reset() {
        activeType = FluidStack.EMPTY;
        recurringNodes.clear();
    }
}
