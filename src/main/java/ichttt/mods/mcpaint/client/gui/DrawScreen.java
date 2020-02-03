package ichttt.mods.mcpaint.client.gui;

import com.google.common.base.Preconditions;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import ichttt.mods.mcpaint.MCPaint;
import ichttt.mods.mcpaint.client.gui.button.GuiButtonTextToggle;
import ichttt.mods.mcpaint.client.gui.button.GuiColorButton;
import ichttt.mods.mcpaint.client.gui.drawutil.EnumDrawType;
import ichttt.mods.mcpaint.client.gui.drawutil.PictureState;
import ichttt.mods.mcpaint.client.render.RenderUtil;
import ichttt.mods.mcpaint.common.MCPaintUtil;
import ichttt.mods.mcpaint.common.capability.IPaintable;
import ichttt.mods.mcpaint.networking.MessageDrawAbort;
import net.minecraft.block.BlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.Widget;
import net.minecraft.client.gui.widget.button.Button;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.model.BakedQuad;
import net.minecraft.client.renderer.model.IBakedModel;
import net.minecraft.client.renderer.texture.AtlasTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.InputMappings;
import net.minecraft.inventory.container.PlayerContainer;
import net.minecraft.resources.IResource;
import net.minecraft.util.Direction;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.util.text.event.ClickEvent;
import net.minecraftforge.client.model.data.EmptyModelData;
import net.minecraftforge.fml.client.gui.widget.Slider;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Random;

public class DrawScreen extends Screen implements Slider.ISlider {
    public static final ResourceLocation BACKGROUND = new ResourceLocation(MCPaint.MODID, "textures/gui/setup.png");
    public static final int ZERO_ALPHA = new Color(255, 255,255, 0).getRGB();
    private static final int PICTURE_START_LEFT = 6;
    private static final int PICTURE_START_TOP = 9;
    private static final int xSize = 176;
    private static final int ySize = 166;
    private static final int toolXSize = 80;
    private static final int toolYSize = 95;
    private static final int sizeXSize = toolXSize;
    private static final int sizeYSize = 34;
    private static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd_HH.mm.ss");

    private final BlockPos pos;
    private final Direction facing;
    private final BlockState state;
    private final IBakedModel model;
    private final LinkedList<PictureState> statesForUndo = new LinkedList<>();
    private final LinkedList<PictureState> statesForRedo = new LinkedList<>();
    private final boolean hadPaint;

    private Color color = Color.BLACK;
    private PictureState paintingState;
    private PictureState currentState;
    private int guiLeft;
    private int guiTop;
    private boolean clickStartedInPicture = false;
    private final List<GuiButtonTextToggle> textToggleList = new ArrayList<>();
    private EnumDrawType activeDrawType = EnumDrawType.PENCIL;
    private int toolSize = 1;
    private Button undo, redo;
    private Button lessSize, moreSize;
    private Slider redSlider, blueSlider, greenSlider, alphaSlider;
    private boolean hasSizeWindow;
    private boolean noRevert = false;
    private boolean updating = false;

    public DrawScreen(IPaintable canvas, List<IPaintable> prevImages, BlockPos pos, Direction facing, BlockState state) {
        super(new TranslationTextComponent("mcpaint.drawgui"));
        Objects.requireNonNull(canvas, "Canvas is null");
        Preconditions.checkArgument(canvas.hasPaintData(), "No data in canvas");
        this.pos = pos;
        this.facing = facing;
        this.state = state;
        this.model = Minecraft.getInstance().getBlockRendererDispatcher().getModelForState(state);
        this.currentState = new PictureState(canvas);
        for (IPaintable paint : prevImages)
            this.statesForUndo.add(new PictureState(paint));
        hadPaint = true;
    }

    protected DrawScreen(byte scaleFactor, BlockPos pos, Direction facing, BlockState state) {
        super(new TranslationTextComponent("mcpaint.drawgui"));
        this.pos = Objects.requireNonNull(pos);
        this.facing = facing;
        this.state = state;
        this.model = Minecraft.getInstance().getBlockRendererDispatcher().getModelForState(state);
        int[][] picture = new int[128 / scaleFactor][128 / scaleFactor];
        for (int[] tileArray : picture)
            Arrays.fill(tileArray, ZERO_ALPHA);
        this.currentState = new PictureState(picture, scaleFactor);
        hadPaint = false;
    }



    @Override
    public void init() {
        this.hasSizeWindow = false;
        this.textToggleList.clear();
        this.guiLeft = (this.width - xSize) / 2;
        this.guiTop = (this.height - ySize) / 2;

        Button saveImage = new Button(this.guiLeft + xSize, this.guiTop + 96, 80, 20, "Export", button -> {
                try {
                    saveImage(true);
                } catch (IOException e) {
                    MCPaint.LOGGER.error("Could not save image!", e);
                    minecraft.player.sendStatusMessage(new StringTextComponent("Failed to save file!"), true);
                    minecraft.player.sendStatusMessage(new StringTextComponent("Failed to save file!"), false);
                }
        });
        Button rotateRight = new Button(this.guiLeft - toolXSize + 2 + 39, this.guiTop + 5 + 22 + 22 + 22, 36, 20, I18n.format("mcpaint.gui.rright"), button -> {
                int[][] newData = new int[DrawScreen.this.currentState.picture.length][DrawScreen.this.currentState.picture[0].length];
                for (int x = 0; x < DrawScreen.this.currentState.picture.length; x++) {
                    int[] yData = DrawScreen.this.currentState.picture[x];
                    for (int y = 0; y < yData.length; y++) {
                        newData[yData.length - y - 1][x] = yData[y];
                    }
                }
                newPictureState(new PictureState(newData, DrawScreen.this.currentState.scaleFactor));
        });
        Button rotateLeft = new Button(this.guiLeft - toolXSize + 3, this.guiTop + 5 + 22 + 22 + 22, 36, 20, I18n.format("mcpaint.gui.rleft"), button -> {
                int[][] newData = new int[DrawScreen.this.currentState.picture.length][DrawScreen.this.currentState.picture[0].length];
                for (int x = 0; x < DrawScreen.this.currentState.picture.length; x++) {
                    int[] yData = DrawScreen.this.currentState.picture[x];
                    for (int y = 0; y < yData.length; y++) {
                        newData[y][DrawScreen.this.currentState.picture.length - x - 1] = yData[y];
                    }
                }
                newPictureState(new PictureState(newData, DrawScreen.this.currentState.scaleFactor));
        });
        this.redo = new Button(this.guiLeft - toolXSize + 2 + 39, this.guiTop + 5 + 22 + 22, 36, 20, I18n.format("mcpaint.gui.redo"), button -> redo());
        this.undo = new Button(this.guiLeft - toolXSize + 3, this.guiTop + 5 + 22 + 22, 36, 20, I18n.format("mcpaint.gui.undo"), button -> undo());
        Button pickColor = new GuiButtonTextToggle(this.guiLeft - toolXSize + 2 + 39, this.guiTop + 5 + 22, 36, 20, EnumDrawType.PICK_COLOR, DrawScreen.this::handleToolButton);
        Button erase = new GuiButtonTextToggle(this.guiLeft - toolXSize + 3, this.guiTop + 5 + 22, 36, 20, EnumDrawType.ERASER, DrawScreen.this::handleToolButton);
        Button fill = new GuiButtonTextToggle(this.guiLeft - toolXSize + 2 + 39, this.guiTop + 5, 36, 20, EnumDrawType.FILL, DrawScreen.this::handleToolButton);
        Button pencil = new GuiButtonTextToggle(this.guiLeft - toolXSize + 3, this.guiTop + 5, 36, 20,  EnumDrawType.PENCIL, DrawScreen.this::handleToolButton);
        this.moreSize = new Button(this.guiLeft - toolXSize + 3 + 55, this.guiTop + toolYSize + 5, 20, 20, ">", button -> {
                DrawScreen.this.toolSize++;
                handleSizeChanged();
        });
        this.lessSize = new Button(this.guiLeft - toolXSize + 3, this.guiTop + toolYSize + 5, 20, 20, "<", button -> {
                DrawScreen.this.toolSize--;
                handleSizeChanged();
        });
        Button done = new Button(this.guiLeft + (xSize / 2) - (200 / 2), this.guiTop + ySize + 20, 200, 20, I18n.format("gui.done"), button -> {
                if (Arrays.stream(DrawScreen.this.currentState.picture).anyMatch(ints -> Arrays.stream(ints).anyMatch(value -> value != ZERO_ALPHA))) {
                    DrawScreen.this.noRevert = true;
                    MCPaintUtil.uploadPictureToServer(DrawScreen.this.minecraft.world.getTileEntity(DrawScreen.this.pos), DrawScreen.this.facing, DrawScreen.this.currentState.scaleFactor, DrawScreen.this.currentState.picture, false);
                } else if (hadPaint) {
                    MCPaintUtil.uploadPictureToServer(DrawScreen.this.minecraft.world.getTileEntity(DrawScreen.this.pos), DrawScreen.this.facing, DrawScreen.this.currentState.scaleFactor, DrawScreen.this.currentState.picture, true);
                }
                DrawScreen.this.minecraft.displayGuiScreen(null);
        });

        GuiColorButton black = new GuiColorButton(0, this.guiLeft + 137, this.guiTop + 9, 16, 16, Color.BLUE.getRGB(), this::handleColorChange);
        GuiColorButton white = new GuiColorButton(1, this.guiLeft + 137 + 18, this.guiTop + 9, 16, 16, Color.BLUE.getRGB(), this::handleColorChange);
        GuiColorButton gray = new GuiColorButton(2, this.guiLeft + 137, this.guiTop + 9 + 18, 16, 16, Color.BLUE.getRGB(), this::handleColorChange);
        GuiColorButton red = new GuiColorButton(3, this.guiLeft + 137 + 18, this.guiTop + 9 + 18, 16, 16, Color.BLUE.getRGB(), this::handleColorChange);
        GuiColorButton orange = new GuiColorButton(4, this.guiLeft + 137, this.guiTop + 9 + 36, 16, 16, Color.BLUE.getRGB(), this::handleColorChange);
        GuiColorButton yellow = new GuiColorButton(5, this.guiLeft + 137 + 18, this.guiTop + 9 + 36, 16, 16, Color.BLUE.getRGB(), this::handleColorChange);

        GuiColorButton lime = new GuiColorButton(6, this.guiLeft + 137, this.guiTop + 9 + 54, 16, 16, Color.BLACK.getRGB(), this::handleColorChange);
        GuiColorButton green = new GuiColorButton(7, this.guiLeft + 137 + 18, this.guiTop + 9 + 54, 16, 16, Color.BLACK.getRGB(), this::handleColorChange);
        GuiColorButton lightBlue = new GuiColorButton(8, this.guiLeft + 137, this.guiTop + 9 + 72, 16, 16, Color.BLACK.getRGB(), this::handleColorChange);
        GuiColorButton darkBlue = new GuiColorButton(9, this.guiLeft + 137 + 18, this.guiTop + 9 + 72, 16, 16, Color.BLACK.getRGB(), this::handleColorChange);
        GuiColorButton purple = new GuiColorButton(10, this.guiLeft + 137, this.guiTop + 9 + 90, 16, 16, Color.BLACK.getRGB(), this::handleColorChange);
        GuiColorButton pink = new GuiColorButton(11, this.guiLeft + 137 + 18, this.guiTop + 9 + 90, 16, 16, Color.BLACK.getRGB(), this::handleColorChange);

        this.redSlider = makeSlider(this.guiLeft + xSize + 3, this.guiTop + 4, "mcpaint.gui.red");
        this.greenSlider = makeSlider(this.guiLeft + xSize + 3, this.guiTop + 26, "mcpaint.gui.green");
        this.blueSlider = makeSlider(this.guiLeft + xSize + 3, this.guiTop + 48,"mcpaint.gui.blue");
        this.alphaSlider = makeSlider(this.guiLeft + xSize + 3, this.guiTop + 70, "mcpaint.gui.alpha");

        addButton(saveImage);
        addButton(rotateRight);
        addButton(rotateLeft);
        addButton(redo);
        addButton(undo);
        addButton(pickColor);
        addButton(erase);
        addButton(fill);
        addButton(pencil);
        addButton(done);
        addButton(black);
        addButton(white);
        addButton(gray);
        addButton(red);
        addButton(orange);
        addButton(yellow);
        addButton(lime);
        addButton(green);
        addButton(lightBlue);
        addButton(darkBlue);
        addButton(purple);
        addButton(pink);
        addButton(this.redSlider);
        addButton(this.greenSlider);
        addButton(this.blueSlider);
        addButton(this.alphaSlider);
        for (Widget button : this.buttons) {
            if (button instanceof GuiButtonTextToggle) {
                this.textToggleList.add((GuiButtonTextToggle) button);
            }
        }
        //trigger defaults
        pencil.onClick(0, 0);
        this.lessSize.onClick(0, 0);
        black.onClick(0, 0);
        this.redo.onClick(0, 0);
    }

    @Override
    public void render(int mouseX, int mouseY, float partialTicks) {
        minecraft.getTextureManager().bindTexture(BACKGROUND);
        //main
        this.blit(this.guiLeft, this.guiTop, 0, 0, xSize, ySize);
        //color
        this.blit(this.guiLeft + xSize, this.guiTop, xSize, 0, toolXSize, toolYSize);
        //tools
        this.blit(this.guiLeft - toolXSize, this.guiTop, xSize, 0, toolXSize, toolYSize);
        //size
        if (this.hasSizeWindow) {
            this.blit(this.guiLeft - toolXSize, this.guiTop + toolYSize + 1, xSize, toolYSize + 1, sizeXSize, sizeYSize);
            drawCenteredString(this.font, toolSize + "", this.guiLeft - toolXSize + 40, this.guiTop + toolYSize + 11, Color.WHITE.getRGB());
        }


        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();

        //Background block
        List<BakedQuad> quads = model.getQuads(state, facing.getOpposite(), new Random(), EmptyModelData.INSTANCE);
        for (BakedQuad quad : quads) {
            TextureAtlasSprite sprite = quad.func_187508_a();
            RenderSystem.pushMatrix();
            minecraft.getTextureManager().bindTexture(PlayerContainer.LOCATION_BLOCKS_TEXTURE);
            //See BlockModelRenderer
            if (quad.hasTintIndex()) {
                int color = minecraft.getBlockColors().getColor(state, minecraft.world, pos, quad.getTintIndex());
                float red = (float) (color >> 16 & 255) / 255.0F;
                float green = (float) (color >> 8 & 255) / 255.0F;
                float blue = (float) (color & 255) / 255.0F;
                RenderSystem.color3f(red, green, blue);
            }
            blit(this.guiLeft + PICTURE_START_LEFT, this.guiTop + PICTURE_START_TOP, -1, 128, 128, sprite);
            RenderSystem.popMatrix();
        }

        super.render(mouseX, mouseY, partialTicks);

        fill(this.guiLeft + 138, this.guiTop + 125, this.guiLeft + 138 + 32, this.guiTop + 125 + 32, this.color.getRGB());

        int offsetMouseX = mouseX - this.guiLeft - PICTURE_START_LEFT;
        int offsetMouseY = mouseY - this.guiTop - PICTURE_START_TOP;
        boolean drawSelect = isInWindow(offsetMouseX, offsetMouseY) && this.activeDrawType != EnumDrawType.PICK_COLOR;
        int[][] toDraw = this.paintingState == null ? this.currentState.picture : this.paintingState.picture;
        if (drawSelect) {
            int pixelPosX = offsetMouseX / this.currentState.scaleFactor;
            int pixelPosY = offsetMouseY / this.currentState.scaleFactor;
            toDraw = MCPaintUtil.copyOf(toDraw);
            this.activeDrawType.draw(toDraw, color, pixelPosX, pixelPosY, this.toolSize);
        }

        //draw picture
        //we batch everything together to increase the performance
        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_COLOR);
        RenderUtil.renderInGui(this.guiLeft + PICTURE_START_LEFT, this.guiTop + PICTURE_START_TOP, this.currentState.scaleFactor, buffer, toDraw);
        RenderSystem.disableTexture();
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        tessellator.draw();
        RenderSystem.enableTexture();
        RenderSystem.disableBlend();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int mouseButton) {
        if (handleMouse(mouseX, mouseY, mouseButton)) {
            this.clickStartedInPicture = true;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int clickedMouseButton, double v2, double v3) {
        if (this.clickStartedInPicture && handleMouse(mouseX, mouseY, clickedMouseButton))
            return true;
        return super.mouseDragged(mouseX, mouseY, clickedMouseButton, v2, v3);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int state) {
        this.clickStartedInPicture = false;
        if (this.paintingState != null) {
            this.newPictureState(this.paintingState);
            this.paintingState = null;
        }
        return super.mouseReleased(mouseX, mouseY, state);
    }

    @Override
    public boolean keyPressed(int keyCode, int i1, int i2) {
        if (keyCode == GLFW.GLFW_KEY_Z && InputMappings.isKeyDown(minecraft.getMainWindow().getHandle(), GLFW.GLFW_KEY_LEFT_CONTROL)) {
            if (InputMappings.isKeyDown(minecraft.getMainWindow().getHandle(), GLFW.GLFW_KEY_LEFT_SHIFT))
                redo();
            else
                undo();
            return true;
        }
        return super.keyPressed(keyCode, i1, i2);
    }

    private void handleColorChange(Color color) {
        this.color = color;
        updateSliders();
    }

    protected void handleToolButton(Button button) {
        for (GuiButtonTextToggle toggleButton : this.textToggleList) {
            boolean toggled = toggleButton == button;
            toggleButton.toggled = toggled;
            if (toggled) {
                this.activeDrawType = toggleButton.type;
                if (this.activeDrawType.hasSizeRegulator && !this.hasSizeWindow) {
                    addButton(moreSize);
                    addButton(lessSize);
                } else if (!this.activeDrawType.hasSizeRegulator && this.hasSizeWindow) {
                    this.buttons.remove(this.moreSize);
                    this.buttons.remove(this.lessSize);
                }
                this.hasSizeWindow = this.activeDrawType.hasSizeRegulator;
            }
        }
    }

    @Override
    public void onClose() {
        if (!noRevert) {
            MCPaint.NETWORKING.sendToServer(new MessageDrawAbort(pos));
        }
    }

    private void newPictureState(PictureState state) {
        if (state.isSame(this.currentState)) return;
        this.statesForRedo.clear();
        this.statesForUndo.add(this.currentState);
        this.currentState = state;
        if (this.statesForUndo.size() > 20) {
            this.statesForUndo.removeFirst();
        }
        updateUndoRedoButton();
    }

    private void undo() {
        if (this.statesForUndo.size() > 0) {
            this.statesForRedo.add(this.currentState);
            this.currentState = this.statesForUndo.removeLast();
        }
        updateUndoRedoButton();
    }

    private void redo() {
        if (this.statesForRedo.size() > 0) {
            this.statesForUndo.add(this.currentState);
            this.currentState = this.statesForRedo.removeLast();
        }
        updateUndoRedoButton();
    }

    private void updateUndoRedoButton() {
        this.undo.active = !this.statesForUndo.isEmpty();
        this.redo.active = !this.statesForRedo.isEmpty();
    }

    private boolean handleMouse(double mouseXD, double mouseYD, int mouseButton) {
        if (mouseButton != 0) return false;
        int mouseX = (int) Math.round(mouseXD);
        int mouseY = (int) Math.round(mouseYD);
        int offsetMouseX = mouseX - this.guiLeft - PICTURE_START_LEFT;
        int offsetMouseY = mouseY - this.guiTop - PICTURE_START_TOP;
        if (isInWindow(offsetMouseX, offsetMouseY)) {
            int pixelPosX = offsetMouseX / this.currentState.scaleFactor;
            int pixelPosY = offsetMouseY / this.currentState.scaleFactor;
            if (this.paintingState == null)
                this.paintingState = new PictureState(this.currentState);
            if (pixelPosX < this.paintingState.picture.length && pixelPosY < this.paintingState.picture.length && this.color != null) {
                this.color = this.activeDrawType.draw(this.paintingState.picture, this.color, pixelPosX, pixelPosY, this.toolSize);
                updateSliders();
                return true;
            }
        }
        return false;
    }

    private boolean isInWindow(int offsetMouseX, int offsetMouseY) {
        return offsetMouseX >= 0 && offsetMouseX < (this.currentState.picture.length * this.currentState.scaleFactor) && offsetMouseY >= 0 && offsetMouseY < (this.currentState.picture.length * this.currentState.scaleFactor);
    }

    private Slider makeSlider(int xPos, int yPos, String key) {
        return new Slider(xPos, yPos, 74, 20, I18n.format(key), "", 0, 255, 0,false, true, null, this);
    }

    private void updateSliders() {
        this.redSlider.setValue(this.color.getRed());
        this.blueSlider.setValue(this.color.getBlue());
        this.greenSlider.setValue(this.color.getGreen());
        this.alphaSlider.setValue(this.color.getAlpha());
        updating = true;
        this.redSlider.updateSlider();
        this.blueSlider.updateSlider();
        this.greenSlider.updateSlider();
        this.alphaSlider.updateSlider();
        updating = false;
    }

    private void handleSizeChanged() {
        if (this.toolSize >= 10) {
            this.toolSize = 10;
            this.moreSize.active = false;
        } else {
            this.moreSize.active = true;
        }
        if (this.toolSize <= 1) {
            this.toolSize = 1;
            this.lessSize.active = false;
        } else {
            this.lessSize.active = true;
        }
    }

    private void saveImage(boolean background) throws IOException {
        BufferedImage paint = new BufferedImage(128 / this.currentState.scaleFactor, 128 / this.currentState.scaleFactor, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < this.currentState.picture.length; x++) {
            for (int y = 0; y < this.currentState.picture[0].length; y++) {
                paint.setRGB(x, y, this.currentState.picture[x][y]);
            }
        }
        BufferedImage output = new BufferedImage(paint.getWidth(), paint.getHeight(), BufferedImage.TYPE_INT_ARGB);

        if (background) {
            List<BakedQuad> quads = model.getQuads(state, facing.getOpposite(), new Random(), EmptyModelData.INSTANCE);
            for (BakedQuad quad : quads) {
                TextureAtlasSprite sprite = quad.func_187508_a();
                try (IResource resource = minecraft.getResourceManager().getResource(getResourceLocation(sprite))) {
                    Image image = ImageIO.read(resource.getInputStream());
                    if (quad.hasTintIndex()) {
                        int color = minecraft.getBlockColors().getColor(state, minecraft.world, pos, quad.getTintIndex());
                        float red = (float) (color >> 16 & 255) / 255.0F;
                        float green = (float) (color >> 8 & 255) / 255.0F;
                        float blue = (float) (color & 255) / 255.0F;
                        BufferedImage asBufferedImage = (BufferedImage) image;
                        for (int x = 0; x < asBufferedImage.getWidth(); x++) {
                            for (int y = 0; y < asBufferedImage.getHeight(); y++) {
                                Color originalColor = new Color(asBufferedImage.getRGB(x, y), true);
                                int newRed = Math.round(originalColor.getRed() * red);
                                int newGreen = Math.round(originalColor.getGreen() * green);
                                int newBlue = Math.round(originalColor.getBlue() * blue);
                                asBufferedImage.setRGB(x, y, new Color(newRed, newGreen, newBlue, originalColor.getAlpha()).getRGB());
                            }
                        }
                    }
                    image = image.getScaledInstance(output.getWidth(), output.getHeight(), Image.SCALE_FAST);
                    output.getGraphics().drawImage(image, 0, 0, null);
                }
            }
        }
        output.getGraphics().drawImage(paint, 0, 0, null);

        File file = new File(this.minecraft.gameDir, "paintings");
        if (!file.exists() && !file.mkdir())
            throw new IOException("Could not create folder");
        final File finalFile = getTimestampedPNGFileForDirectory(file);
        if (!ImageIO.write(output, "png", finalFile))
            throw new IOException("Could not encode image as png!");
        ITextComponent component = new StringTextComponent(finalFile.getName());
        component = component.applyTextStyle(TextFormatting.UNDERLINE).applyTextStyle(style -> style.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_FILE, finalFile.getAbsolutePath())));
        minecraft.player.sendStatusMessage(new TranslationTextComponent("mcpaint.gui.saved", component), false);
    }

    @Override
    public void onChangeSliderValue(Slider slider) {
        if (updating) return;
        this.color = new Color(this.redSlider.getValueInt(),
                this.greenSlider.getValueInt(),
                this.blueSlider.getValueInt(),
                this.alphaSlider.getValueInt());
    }

    private ResourceLocation getResourceLocation(TextureAtlasSprite p_184396_1_) {
        ResourceLocation resourcelocation = p_184396_1_.getName();
        return new ResourceLocation(resourcelocation.getNamespace(), String.format("textures/%s%s", resourcelocation.getPath(), ".png"));
    }

    //See ScreenShotHelper
    private static File getTimestampedPNGFileForDirectory(File gameDirectory) {
        String s = DATE_FORMAT.format(new Date());
        int i = 1;

        while(true) {
            File file1 = new File(gameDirectory, s + (i == 1 ? "" : "_" + i) + ".png");
            if (!file1.exists()) {
                return file1;
            }

            ++i;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
