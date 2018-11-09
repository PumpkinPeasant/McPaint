package ichttt.mods.mcpaint.common;

import ichttt.mods.mcpaint.MCPaint;
import ichttt.mods.mcpaint.common.block.BlockCanvas;
import ichttt.mods.mcpaint.common.block.TileEntityCanvas;
import ichttt.mods.mcpaint.common.capability.CapabilityPaintable;
import ichttt.mods.mcpaint.common.capability.CapabilityProvider;
import ichttt.mods.mcpaint.common.item.ItemBrush;
import ichttt.mods.mcpaint.common.item.ItemStamp;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntityType;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.ObjectHolder;

import javax.annotation.Nonnull;

@ObjectHolder(MCPaint.MODID)
public class EventHandler {
    public static Item BRUSH = getNull();
    public static Item STAMP = getNull();
    public static BlockCanvas CANVAS_WOOD = getNull();
    public static BlockCanvas CANVAS_ROCK = getNull();
    public static BlockCanvas CANVAS_GROUND = getNull();
    public static TileEntityType<TileEntityCanvas> CANVAS_TE = getNull();

    //Avoids warnings of a field being null because it is populated by the ObjectHolder
    //So Nonnull despite returning null
    @SuppressWarnings({"ConstantConditions", "SameReturnValue"})
    @Nonnull
    private static <T> T getNull() {
        return null;
    }

    public static void update() {
        BRUSH = ForgeRegistries.ITEMS.getValue(new ResourceLocation(MCPaint.MODID, "brush"));
        STAMP = ForgeRegistries.ITEMS.getValue(new ResourceLocation(MCPaint.MODID, "stamp"));
        CANVAS_WOOD = (BlockCanvas) ForgeRegistries.BLOCKS.getValue(new ResourceLocation(MCPaint.MODID, "canvas_wood"));
        CANVAS_ROCK = (BlockCanvas) ForgeRegistries.BLOCKS.getValue(new ResourceLocation(MCPaint.MODID, "canvas_rock"));
        CANVAS_GROUND = (BlockCanvas) ForgeRegistries.BLOCKS.getValue(new ResourceLocation(MCPaint.MODID, "canvas_ground"));
        CANVAS_TE = (TileEntityType<TileEntityCanvas>) ForgeRegistries.TILE_ENTITIES.getValue(new ResourceLocation(MCPaint.MODID, "canvas_te"));
    }

    @SubscribeEvent
    public static void registerItems(RegistryEvent.Register<Item> event) {
        IForgeRegistry<Item> registry = event.getRegistry();
        registry.register(new ItemBrush(new ResourceLocation(MCPaint.MODID, "brush")));
        registry.register(new ItemStamp(new ResourceLocation(MCPaint.MODID, "stamp")));
    }

    @SubscribeEvent
    public static void registerBlocks(RegistryEvent.Register<Block> event) {
        event.getRegistry().register(new BlockCanvas(Material.WOOD, new ResourceLocation(MCPaint.MODID, "canvas_wood")));
        event.getRegistry().register(new BlockCanvas(Material.ROCK, new ResourceLocation(MCPaint.MODID, "canvas_rock")));
        event.getRegistry().register(new BlockCanvas(Material.GROUND, new ResourceLocation(MCPaint.MODID, "canvas_ground")));
    }

    @SubscribeEvent
    public static void registerTileEntity(RegistryEvent.Register<TileEntityType<?>> event) {
        event.getRegistry().register(TileEntityType.Builder.create(TileEntityCanvas::new).build(null).setRegistryName(MCPaint.MODID, "canvas_te"));
    }

    @SubscribeEvent
    public static void attachCaps(AttachCapabilitiesEvent<ItemStack> event) {
        if (event.getObject().getItem() == STAMP)
            event.addCapability(CapabilityProvider.LOCATION, new CapabilityProvider());
    }

//    @SubscribeEvent TODO
//    public static void onConfigChange(ConfigChangedEvent event) {
//        if (event.getModID().equals(MCPaint.MODID)) {
//            ConfigManager.sync(MCPaint.MODID, Config.Type.INSTANCE);
//            MCPaint.proxy.onConfigReload();
//        }
//    }

    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.RightClickItem event) {
        ItemStack stack = event.getEntityPlayer().getHeldItem(event.getHand());
        if (event.getEntityPlayer().isSneaking() && stack.getItem() == EventHandler.STAMP) {
            stack.getCapability(CapabilityPaintable.PAINTABLE, null).orElseThrow(RuntimeException::new).clear(null, null);
            event.setCanceled(true);
            event.setCancellationResult(EnumActionResult.SUCCESS);
        }
    }

    @SubscribeEvent
    public static void missingMapping(RegistryEvent.MissingMappings<Block> event) {
        for (RegistryEvent.MissingMappings.Mapping<Block> entry : event.getAllMappings()) {
            if (entry.key.equals(new ResourceLocation(MCPaint.MODID, "canvas")))
                entry.remap(CANVAS_GROUND); //make legacy non-burning
        }
    }
}
