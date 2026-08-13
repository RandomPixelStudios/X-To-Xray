/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.client.Minecraft
 *  net.minecraft.client.gui.GuiGraphicsExtractor
 *  net.minecraft.client.gui.components.EditBox
 *  net.minecraft.client.gui.components.events.GuiEventListener
 *  net.minecraft.client.gui.screens.Screen
 *  net.minecraft.client.input.KeyEvent
 *  net.minecraft.client.input.MouseButtonEvent
 *  net.minecraft.core.Holder
 *  net.minecraft.core.Holder$Reference
 *  net.minecraft.core.registries.BuiltInRegistries
 *  net.minecraft.network.chat.Component
 *  net.minecraft.resources.Identifier
 *  net.minecraft.world.item.Item
 *  net.minecraft.world.item.ItemStack
 *  net.minecraft.world.item.Items
 *  net.minecraft.world.level.ItemLike
 *  net.minecraft.world.level.block.BaseEntityBlock
 *  net.minecraft.world.level.block.Block
 *  net.minecraft.world.level.block.Blocks
 */
package com.xtoxray.client.gui;

import com.xtoxray.XrayState;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public class AddBlockScreen
extends Screen {
    private static final int TEXT = -3355444;
    private static final int TEXT_DIM = -7829368;
    private static final int TEXT_BRIGHT = -1;
    private static final int SEPARATOR = -12303292;
    private static final int BTN_BG = -14803426;
    private static final int BTN_BG_HOVER = -13882324;
    private static final int BTN_BORDER = -12961222;
    public static final int MODE_XRAY = 0;
    public static final int MODE_VEIN_MINER = 1;
    public static final int MODE_CONTAINER = 2;
    private final Screen parent;
    private final int mode;
    private EditBox searchBox;
    private List<Block> filteredBlocks = new ArrayList<Block>();
    private List<Block> allBlocks = new ArrayList<Block>();
    private Set<Integer> selectedIndices = new HashSet<Integer>();
    private int scrollOffset = 0;
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private int cols;
    private int cellStep = 24;
    private int gridStartY;
    private int gridX;
    private int visibleRows;
    private int btnY;
    private int hoveredBtn = -1;

    public AddBlockScreen(Screen parent, int mode) {
        super((Component)Component.literal((String)"Add Block"));
        this.parent = parent;
        this.mode = mode;
    }

    protected void init() {
        this.panelW = Math.min(this.width - 20, 440);
        this.panelH = Math.min(this.height - 20, 340);
        this.panelX = (this.width - this.panelW) / 2;
        this.panelY = (this.height - this.panelH) / 2;
        this.cols = Math.max(4, (this.panelW - 48) / this.cellStep);
        this.gridX = this.panelX + (this.panelW - this.cols * this.cellStep) / 2;
        this.gridStartY = this.panelY + 62;
        this.visibleRows = Math.max(3, (this.panelH - 102) / this.cellStep);
        this.btnY = this.panelY + this.panelH - 34;
        this.searchBox = new EditBox(this.font, this.panelX + (this.panelW - 200) / 2, this.panelY + 36, 200, 18, (Component)Component.literal((String)"Search"));
        this.searchBox.setHint((Component)Component.literal((String)"Search blocks..."));
        this.searchBox.setResponder(s -> {
            this.filterBlocks((String)s);
            this.selectedIndices.clear();
            this.scrollOffset = 0;
        });
        this.addRenderableWidget(this.searchBox);
        this.loadAllBlocks();
    }

    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        g.fill(0, 0, this.width, this.height, -872415232);
        g.fill(this.panelX, this.panelY, this.panelX + this.panelW, this.panelY + this.panelH, -267514354);
        g.fill(this.panelX, this.panelY, this.panelX + this.panelW, this.panelY + 2, -14013910);
        g.fill(this.panelX, this.panelY + this.panelH - 1, this.panelX + this.panelW, this.panelY + this.panelH, -12303292);
        g.fill(this.panelX, this.panelY, this.panelX + 1, this.panelY + this.panelH, -12303292);
        g.fill(this.panelX + this.panelW - 1, this.panelY, this.panelX + this.panelW, this.panelY + this.panelH, -12303292);
        String title = switch (this.mode) {
            case 1 -> "Add Vein Miner Block";
            case 2 -> "Add Container Block";
            default -> "Add Xray Block";
        };
        int tw = this.font.width(title);
        g.text(this.font, (Component)Component.literal((String)title), (this.width - tw) / 2, this.panelY + 12, -1);
        int totalRows = (this.filteredBlocks.size() + this.cols - 1) / this.cols;
        int maxOffset = Math.max(0, totalRows - this.visibleRows);
        g.enableScissor(this.panelX, this.gridStartY, this.panelX + this.panelW, this.panelY + this.panelH - 40);
        for (int i = 0; i < this.filteredBlocks.size(); ++i) {
            int bg;
            boolean hovered;
            int row = i / this.cols;
            int col = i % this.cols;
            if (row < this.scrollOffset || row >= this.scrollOffset + this.visibleRows + 1) continue;
            int x = this.gridX + col * this.cellStep;
            int y = this.gridStartY + (row - this.scrollOffset) * this.cellStep;
            boolean selected = this.selectedIndices.contains(i);
            boolean inList = this.isInWhitelist(this.filteredBlocks.get(i));
            boolean bl = hovered = mouseX >= x && mouseX < x + 22 && mouseY >= y && mouseY < y + 22;
            bg = inList ? -15058406 : (selected ? -14013926 : -15066598);
            int border = selected ? -5592525 : (inList ? -13391309 : (hovered ? -3355444 : -12961222));
            g.fill(x, y, x + 22, y + 22, bg);
            g.fill(x, y, x + 22, y + 1, border);
            g.fill(x, y + 22 - 1, x + 22, y + 22, border);
            g.fill(x, y, x + 1, y + 22, border);
            g.fill(x + 22 - 1, y, x + 22, y + 22, border);
            ItemStack stack = AddBlockScreen.makeStack(this.filteredBlocks.get(i));
            if (stack.isEmpty()) continue;
            g.fakeItem(stack, x + 3, y + 3);
        }
        g.disableScissor();
        int btnW = 90;
        this.drawBtn(g, this.width / 2 - btnW - 5, this.btnY, btnW, "Cancel", 0, mouseX, mouseY);
        this.drawBtn(g, this.width / 2 + 5, this.btnY, btnW, "Confirm", 1, mouseX, mouseY);
        String count = this.filteredBlocks.size() + " blocks";
        g.text(this.font, (Component)Component.literal((String)count), this.panelX + 12, this.panelY + this.panelH - 14, -7829368);
    }

    private boolean isInWhitelist(Block block) {
        return switch (this.mode) {
            case 1 -> XrayState.getInstance().isVeinMinerWhitelisted(block);
            case 2 -> XrayState.getInstance().isContainerWhitelisted(block);
            default -> XrayState.getInstance().isWhitelisted(block);
        };
    }

    private Set<Block> getTargetWhitelist() {
        return switch (this.mode) {
            case 1 -> XrayState.getInstance().getVeinMinerWhitelist();
            case 2 -> XrayState.getInstance().getContainerWhitelist();
            default -> XrayState.getInstance().getWhitelist();
        };
    }

    private void drawBtn(GuiGraphicsExtractor g, int x, int y, int w, String label, int id, int mouseX, int mouseY) {
        boolean hovered;
        boolean bl = hovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 20;
        if (hovered) {
            this.hoveredBtn = id;
        }
        int bg = hovered ? -13882324 : -14803426;
        int border = hovered ? -3355444 : -12961222;
        g.fill(x, y, x + w, y + 20, bg);
        g.fill(x, y, x + w, y + 1, border);
        g.fill(x, y + 19, x + w, y + 20, border);
        g.fill(x, y, x + 1, y + 20, border);
        g.fill(x + w - 1, y, x + w, y + 20, border);
        int tw = this.font.width(label);
        g.text(this.font, (Component)Component.literal((String)label), x + (w - tw) / 2, y + 6, -1);
    }

    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int mx = (int)event.x();
        int my = (int)event.y();
        if (event.button() == 0) {
            int btnW = 90;
            int midX = this.width / 2;
            if (mx >= midX - btnW - 5 && mx < midX - 5 && my >= this.btnY && my < this.btnY + 20) {
                this.onClose();
                return true;
            }
            if (mx >= midX + 5 && mx < midX + 5 + btnW && my >= this.btnY && my < this.btnY + 20) {
                this.confirmSelection();
                return true;
            }
        }
        for (int i = 0; i < this.filteredBlocks.size(); ++i) {
            int row = i / this.cols;
            int col = i % this.cols;
            if (row < this.scrollOffset || row >= this.scrollOffset + this.visibleRows + 1) continue;
            int x = this.gridX + col * this.cellStep;
            int y = this.gridStartY + (row - this.scrollOffset) * this.cellStep;
            if (mx < x || mx >= x + 22 || my < y || my >= y + 22) continue;
            if (this.selectedIndices.contains(i)) {
                this.selectedIndices.remove(i);
            } else {
                this.selectedIndices.add(i);
            }
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int totalRows = (this.filteredBlocks.size() + this.cols - 1) / this.cols;
        int maxOffset = Math.max(0, totalRows - this.visibleRows);
        this.scrollOffset = Math.max(0, Math.min(this.scrollOffset - (int)verticalAmount, maxOffset));
        return true;
    }

    public boolean keyPressed(KeyEvent event) {
        if (this.searchBox != null && this.searchBox.isFocused()) {
            return super.keyPressed(event);
        }
        return super.keyPressed(event);
    }

    public boolean shouldPause() {
        return false;
    }

    private void confirmSelection() {
        Set<Block> whitelist = this.getTargetWhitelist();
        boolean changed = false;
        for (int i : this.selectedIndices) {
            Block block;
            if (i < 0 || i >= this.filteredBlocks.size() || whitelist.contains(block = this.filteredBlocks.get(i))) continue;
            whitelist.add(block);
            changed = true;
        }
        if (changed) {
            XrayState.getInstance().save();
        }
        this.onClose();
    }

    private void loadAllBlocks() {
        this.allBlocks.clear();
        for (Block block : BuiltInRegistries.BLOCK) {
            Identifier id;
            if (block == Blocks.AIR || block == Blocks.CAVE_AIR || block == Blocks.VOID_AIR || block.defaultBlockState().isAir() || (id = BuiltInRegistries.BLOCK.getKey(block)).getPath().contains("wall") || this.mode == 2 && !XrayState.getInstance().isContainerWhitelisted(block) && !this.isContainerType(block)) continue;
            this.allBlocks.add(block);
        }
        this.filterBlocks("");
    }

    private boolean isContainerType(Block block) {
        return block instanceof BaseEntityBlock;
    }

    private void filterBlocks(String query) {
        this.filteredBlocks.clear();
        String q = query.toLowerCase().trim();
        for (Block block : this.allBlocks) {
            Identifier id = BuiltInRegistries.BLOCK.getKey(block);
            String name = id.getPath().replace('_', ' ');
            if (!q.isEmpty() && !id.getPath().contains(q) && !name.contains(q)) continue;
            this.filteredBlocks.add(block);
        }
    }

    public void onClose() {
        com.xtoxray.client.VersionAdapter.setScreen(Minecraft.getInstance(), this.parent);
    }

    private static ItemStack makeStack(Block block) {
        if (block == Blocks.WATER) {
            return new ItemStack((ItemLike)Items.WATER_BUCKET);
        }
        if (block == Blocks.LAVA) {
            return new ItemStack((ItemLike)Items.LAVA_BUCKET);
        }
        try {
            Item item = block.asItem();
            Optional opt = BuiltInRegistries.ITEM.getResourceKey(item).flatMap(k -> BuiltInRegistries.ITEM.get(k.identifier()));
            if (opt.isPresent() && ((Holder)opt.get()).areComponentsBound()) {
                return new ItemStack((Holder)opt.get(), 1);
            }
            Holder.Reference intrusiveHolder = item.builtInRegistryHolder();
            if (intrusiveHolder.areComponentsBound()) {
                return new ItemStack((Holder)intrusiveHolder, 1);
            }
            return ItemStack.EMPTY;
        }
        catch (Throwable t) {
            return ItemStack.EMPTY;
        }
    }
}

