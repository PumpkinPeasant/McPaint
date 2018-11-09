package ichttt.mods.mcpaint.client.render;

import ichttt.mods.mcpaint.common.capability.CapabilityPaintable;
import ichttt.mods.mcpaint.common.capability.IPaintable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.tileentity.TileEntityItemStackRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.util.InputMappings;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.IItemPropertyGetter;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nullable;

public class TEISRStamp extends TileEntityItemStackRenderer implements IItemPropertyGetter {
    public static final TEISRStamp INSTANCE = new TEISRStamp();

    private TEISRStamp() {}

    @Override
    public void renderByItem(ItemStack itemStack) {
        if (InputMappings.isKeyDown(GLFW.GLFW_KEY_LEFT_SHIFT)) {
            IPaintable paint = itemStack.getCapability(CapabilityPaintable.PAINTABLE, null).orElse(null);
            if (paint != null && paint.hasPaintData()) {
                Tessellator tessellator = Tessellator.getInstance();
                BufferBuilder builder = tessellator.getBuffer();
                builder.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
                PictureRenderer.renderInGame(paint.getScaleFactor(), builder, paint.getPictureData());
                PictureRenderer.setWorldGLState();
                tessellator.draw();
                PictureRenderer.resetWorldGLState();
            }
        }
    }

    @Override
    public float call(ItemStack stack, @Nullable World world, @Nullable EntityLivingBase entity) {
        if (InputMappings.isKeyDown(GLFW.GLFW_KEY_LEFT_SHIFT) && entity != null && Minecraft.getInstance().player != null && entity.getName().equals(Minecraft.getInstance().player.getName())) {
            IPaintable paint = stack.getCapability(CapabilityPaintable.PAINTABLE, null).orElseThrow(RuntimeException::new);
            if (paint != null && paint.hasPaintData()) {
                return 1F;
            }
        }
        return 0F;
    }
}
