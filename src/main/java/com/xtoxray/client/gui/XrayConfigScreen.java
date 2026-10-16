package com.xtoxray.client.gui;

import com.xtoxray.ConfigManager;
import com.xtoxray.XrayState;
import com.xtoxray.XtoXray;
import com.xtoxray.client.XrayToggleHandler;
import com.xtoxray.hitboxes.HitboxesState;
import com.xtoxray.plugin.PluginData;
import com.xtoxray.plugin.PluginState;
import com.xtoxray.plugin.PluginManager;
import com.xtoxray.plugin.ExternalPlugin;
import com.xtoxray.api.plugin.Plugin;
import com.xtoxray.api.plugin.PluginDescriptor;
import com.xtoxray.veinminer.VeinminerState;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import java.io.ByteArrayOutputStream;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;

public class XrayConfigScreen extends Screen {
    private static final int NAV_W = 120;
    private static final int NAV_Y = 24;
    private static final int NAV_GAP = 24;
    private static final int TEXT_WHITE = 0xFFFFFFFF;
    private static final int TEXT_GRAY = 0xFFAAAAAA;
    private static final int COLUMN_HEADER = 0xFFAAAAAA;
    private static final int SEPARATOR = 0x50FFFFFF;
    private static final int SLOT_SIZE = 18;
    private static final int SLOT_GAP = 2;
    private static final int SLOT_BG = 0xFF2A2A2A;
    private static final int SLOT_HOVER = 0xFF3A3A3A;
    private static final int SLOT_BORDER = 0xFF555555;
    private static final int CARD_BG = 0xC01A1A1A;
    private static final int CARD_BORDER = 0xFF444444;
    private static final int CARD_SPACING = 5;

    private final String[] baseTabs = {"Xray", "Plugins"};
    private int selectedTab = 0;
    private int renderDistance;
    private boolean renderWhileMoving;
    private boolean nightVision;
    private EditBox blockInput;
    private List<Block> whitelistCache;
    private int whitelistStartY;
    private int whitelistCols;
    private int whitelistGridY;

    private boolean veinminerEnabled;
    private int veinminerMaxBlocks;
    private int veinminerSilkTouchMode;
    private int veinminerDurabilityCost;
    private EditBox veinminerBlockInput;
    private List<Block> veinminerWhitelistCache;
    private int veinminerWhitelistStartY;
    private int veinminerWhitelistCols;
    private int veinminerWhitelistGridY;

    private boolean hitboxesEnabled;
    private boolean hitboxesSeeThroughWalls;
    private int hitboxesMaxDistance;
    private final boolean[] hitboxesShown = new boolean[HitboxesState.CATEGORY_COUNT];
    private final int[] hitboxesColors = new int[HitboxesState.CATEGORY_COUNT];
    private static final int BUG_REPORT_TAB = 1000;
    private final List<String> logLines = Collections.synchronizedList(new ArrayList<>());
    private long logFilePosition = 0;
    private Path logFile;
    private Thread logWatcherThread;
    private volatile boolean logWatcherRunning = false;
    private int logScrollY = 0;
    private String bugReportStatus = "";
    private int bugReportStatusColor = 0xFFAAAAAA;

    private int contentX() {
        return 10 + NAV_W + 20;
    }

    private int contentWidth() {
        return this.width - contentX() - 10;
    }

    private int sliderWidth() {
        return Math.min(200, contentWidth());
    }

    private int cardHeight() {
        int available = this.height - NAV_Y - 60;
        int count = Math.max(1, PluginState.getInstance().getPlugins().size());
        int h = (available - (count - 1) * CARD_SPACING) / count;
        return Math.max(40, Math.min(80, h));
    }

    private int iconSize() {
        return Math.max(16, Math.min(32, cardHeight() - 16));
    }

    private int pluginContentRight() {
        return this.width - 10;
    }

    private int pluginColWidth() {
        return (pluginContentRight() - contentX()) / 2;
    }

    private int pluginsInstalledRight() {
        return contentX() + pluginColWidth() - 5;
    }

    private int pluginsDownloadX() {
        return contentX() + pluginColWidth() + 10;
    }

    public XrayConfigScreen() {
        super(Component.literal("X to Xray Config"));
        XrayState state = XrayState.getInstance();
        this.renderDistance = state.getRenderDistance();
        this.renderWhileMoving = state.isRenderWhileMoving();
        this.nightVision = state.isNightVision();
        VeinminerState vState = VeinminerState.getInstance();
        this.veinminerEnabled = vState.isEnabled();
        this.veinminerMaxBlocks = vState.getMaxBlocks();
        this.veinminerSilkTouchMode = vState.getSilkTouchMode();
        this.veinminerDurabilityCost = vState.getDurabilityCost();
        HitboxesState hState = HitboxesState.getInstance();
        this.hitboxesEnabled = hState.isEnabled();
        this.hitboxesSeeThroughWalls = hState.isSeeThroughWalls();
        this.hitboxesMaxDistance = hState.getMaxDistance();
        for (int i = 0; i < HitboxesState.CATEGORY_COUNT; i++) {
            this.hitboxesShown[i] = hState.isShown(i);
            this.hitboxesColors[i] = hState.getColor(i);
        }
    }

    protected void init() {
        PluginManager.getInstance().initBuiltin();
        Path pluginsFolder = Minecraft.getInstance().gameDirectory.toPath().resolve("xtoxray").resolve("plugins");
        PluginManager.getInstance().loadExternalPlugins(pluginsFolder, null);
        com.xtoxray.plugin.PluginFolderWatcher.start(pluginsFolder);
        startLogWatcher();
        populateWidgets();
    }

    private int getPluginTab(String name) {
        int idx = baseTabs.length;
        for (PluginData plugin : PluginState.getInstance().getPlugins()) {
            if (plugin.getName().equals(name)) {
                return idx;
            }
            idx++;
        }
        for (Map.Entry<String, Plugin> entry : PluginManager.getInstance().getSidebarTabs().entrySet()) {
            if (entry.getKey().equals(name)) {
                return idx;
            }
            idx++;
        }
        return -1;
    }

    public void populateWidgets() {
        clearWidgets();
        blockInput = null;
        veinminerBlockInput = null;
        whitelistCache = new ArrayList<>(XrayState.getInstance().getWhitelist());
        veinminerWhitelistCache = new ArrayList<>(VeinminerState.getInstance().getWhitelist());

        int cx = contentX();
        int sw = sliderWidth();
        int cw = contentWidth();

        int navY = NAV_Y;
        addRenderableWidget(navButton(0, baseTabs[0], navY));
        navY += NAV_GAP;
        addRenderableWidget(navButton(1, baseTabs[1], navY));
        navY += NAV_GAP;
        for (PluginData plugin : PluginState.getInstance().getPlugins()) {
            if (plugin.isInstalled()) {
                addRenderableWidget(navButton(getPluginTab(plugin.getName()), plugin.getName(), navY));
                navY += NAV_GAP;
            }
        }
        for (Map.Entry<String, Plugin> entry : PluginManager.getInstance().getSidebarTabs().entrySet()) {
            addRenderableWidget(navButton(getPluginTab(entry.getKey()), entry.getKey(), navY));
            navY += NAV_GAP;
        }

        addRenderableWidget(navButton(BUG_REPORT_TAB, "Bug Report", navY));
        navY += NAV_GAP;

        if (selectedTab == 0) {
            int y = 30;
            addRenderableWidget(new XraySlider(cx, y, sw, 20,
                "Render Distance", 1, 64, renderDistance,
                val -> this.renderDistance = val));
            y += 30;
            addRenderableWidget(Checkbox.builder(Component.literal("Render While Moving"), this.font)
                .pos(cx, y)
                .selected(renderWhileMoving)
                .onValueChange((cb, sel) -> this.renderWhileMoving = sel)
                .build());
            y += 25;
            addRenderableWidget(Checkbox.builder(Component.literal("Night Vision"), this.font)
                .pos(cx, y)
                .selected(nightVision)
                .onValueChange((cb, sel) -> this.nightVision = sel)
                .build());
            y += 35;
            whitelistStartY = y;
            whitelistCols = Math.max(1, cw / (SLOT_SIZE + SLOT_GAP));
            whitelistGridY = whitelistStartY + 44;
            int inputW = Math.max(60, cw - 52);
            blockInput = new EditBox(this.font, cx, whitelistStartY + 18, inputW, 20, Component.literal("Block ID"));
            blockInput.setHint(Component.literal("minecraft:diamond_block"));
            blockInput.setMaxLength(64);
            blockInput.setCanLoseFocus(true);
            addRenderableWidget(blockInput);
            addRenderableWidget(Button.builder(
                Component.literal("Add"),
                btn -> addBlockFromInput()
            ).pos(cx + inputW + 5, whitelistStartY + 18).width(47).build());
            int resetY = whitelistGridY + 15 + ((whitelistCache.size() + whitelistCols - 1) / whitelistCols) * (SLOT_SIZE + SLOT_GAP) + 10;
            addRenderableWidget(Button.builder(
                Component.literal("Reset to Default"),
                btn -> {
                    XrayState.getInstance().resetToDefault();
                    this.renderDistance = XrayState.getInstance().getRenderDistance();
                    this.renderWhileMoving = XrayState.getInstance().isRenderWhileMoving();
                    this.nightVision = XrayState.getInstance().isNightVision();
                    populateWidgets();
                }
            ).pos(cx, resetY).width(Math.min(200, cw)).build());
        } else if (selectedTab == getPluginTab("Veinminer")) {
            int y = 30;
            addRenderableWidget(Checkbox.builder(Component.literal("Enabled"), this.font)
                .pos(cx, y)
                .selected(veinminerEnabled)
                .onValueChange((cb, sel) -> this.veinminerEnabled = sel)
                .build());
            y += 30;
            addRenderableWidget(new XraySlider(cx, y, sw, 20,
                "Max Blocks", 1, 64, veinminerMaxBlocks,
                val -> this.veinminerMaxBlocks = val));
            y += 30;
            addRenderableWidget(Button.builder(
                Component.literal(VeinminerState.getInstance().getSilkTouchModeName()),
                btn -> {
                    VeinminerState.getInstance().cycleSilkTouchMode();
                    this.veinminerSilkTouchMode = VeinminerState.getInstance().getSilkTouchMode();
                    populateWidgets();
                }
            ).pos(cx, y).width(sw).build());
            y += 30;
            addRenderableWidget(new XraySlider(cx, y, sw, 20,
                "Durability Cost", 0, 10, veinminerDurabilityCost,
                val -> this.veinminerDurabilityCost = val));
            y += 30;
            addRenderableWidget(Button.builder(
                Component.literal("Reset to Default"),
                btn -> {
                    VeinminerState.getInstance().resetToDefault();
                    this.veinminerEnabled = VeinminerState.getInstance().isEnabled();
                    this.veinminerMaxBlocks = VeinminerState.getInstance().getMaxBlocks();
                    this.veinminerSilkTouchMode = VeinminerState.getInstance().getSilkTouchMode();
                    this.veinminerDurabilityCost = VeinminerState.getInstance().getDurabilityCost();
                    populateWidgets();
                }
            ).pos(cx, y).width(sw).build());
            y += 35;
            veinminerWhitelistStartY = y;
            veinminerWhitelistCols = Math.max(1, cw / (SLOT_SIZE + SLOT_GAP));
            veinminerWhitelistGridY = veinminerWhitelistStartY + 44;
            int vmInputW = Math.max(60, cw - 52);
            veinminerBlockInput = new EditBox(this.font, cx, veinminerWhitelistStartY + 18, vmInputW, 20, Component.literal("Block ID"));
            veinminerBlockInput.setHint(Component.literal("minecraft:diamond_ore"));
            veinminerBlockInput.setMaxLength(64);
            veinminerBlockInput.setCanLoseFocus(true);
            addRenderableWidget(veinminerBlockInput);
            addRenderableWidget(Button.builder(
                Component.literal("Add"),
                btn -> addVeinminerBlockFromInput()
            ).pos(cx + vmInputW + 5, veinminerWhitelistStartY + 18).width(47).build());
        } else if (selectedTab == getPluginTab("Hitboxes")) {
            int y = 30;
            addRenderableWidget(Checkbox.builder(Component.literal("Enabled"), this.font)
                .pos(cx, y)
                .selected(hitboxesEnabled)
                .onValueChange((cb, sel) -> this.hitboxesEnabled = sel)
                .build());
            y += 30;
            addRenderableWidget(new XraySlider(cx, y, sw, 20,
                "Max Distance", 8, 256, hitboxesMaxDistance,
                val -> this.hitboxesMaxDistance = val));
            y += 30;
            addRenderableWidget(Checkbox.builder(Component.literal("See Through Walls"), this.font)
                .pos(cx, y)
                .selected(hitboxesSeeThroughWalls)
                .onValueChange((cb, sel) -> this.hitboxesSeeThroughWalls = sel)
                .build());
            y += 30;
            for (int i = 0; i < HitboxesState.CATEGORY_COUNT; i++) {
                final int cat = i;
                addRenderableWidget(Checkbox.builder(Component.literal("Show " + HitboxesState.getInstance().getCategoryName(cat)), this.font)
                    .pos(cx, y)
                    .selected(hitboxesShown[cat])
                    .onValueChange((cb, sel) -> this.hitboxesShown[cat] = sel)
                    .build());
                int colorBtnW = Math.min(150, Math.max(60, cw - 160));
                addRenderableWidget(Button.builder(
                    Component.literal("Color: " + HitboxesState.getInstance().getColorName(cat)),
                    btn -> {
                        HitboxesState.getInstance().cycleColor(cat);
                        this.hitboxesColors[cat] = HitboxesState.getInstance().getColor(cat);
                        populateWidgets();
                    }
                ).pos(cx + cw - colorBtnW, y).width(colorBtnW).build());
                y += 25;
            }
            y += 10;
            addRenderableWidget(Button.builder(
                Component.literal("Reset to Default"),
                btn -> {
                    HitboxesState.getInstance().resetToDefault();
                    this.hitboxesEnabled = HitboxesState.getInstance().isEnabled();
                    this.hitboxesSeeThroughWalls = HitboxesState.getInstance().isSeeThroughWalls();
                    this.hitboxesMaxDistance = HitboxesState.getInstance().getMaxDistance();
                    for (int j = 0; j < HitboxesState.CATEGORY_COUNT; j++) {
                        this.hitboxesShown[j] = HitboxesState.getInstance().isShown(j);
                        this.hitboxesColors[j] = HitboxesState.getInstance().getColor(j);
                    }
                    populateWidgets();
                }
            ).pos(cx, y).width(sw).build());
        } else if (selectedTab == 1) {
            int ch = cardHeight();
            int btnW = Math.min(60, Math.max(40, pluginColWidth() / 4));
            int i = 0;
            for (PluginData plugin : PluginState.getInstance().getPlugins()) {
                int rowY = 30 + 15 + i * (ch + CARD_SPACING);
                if (plugin.isInstalled()) {
                    Button installedBtn = Button.builder(
                        Component.literal("Download"),
                        btn -> {}
                    ).pos(pluginsDownloadX() + pluginColWidth() - btnW - 8, rowY + ch - 6 - 20).width(btnW).build();
                    installedBtn.active = false;
                    addRenderableWidget(installedBtn);
                } else {
                    addRenderableWidget(Button.builder(
                        Component.literal("Download"),
                        btn -> {
                            PluginState.getInstance().install(plugin.getName());
                            populateWidgets();
                        }
                    ).pos(pluginsDownloadX() + pluginColWidth() - btnW - 8, rowY + ch - 6 - 20).width(btnW).build());
                }
                i++;
            }
            int instY = 30 + 15;
            for (PluginData plugin : PluginState.getInstance().getPlugins()) {
                if (plugin.isInstalled()) {
                    addRenderableWidget(Button.builder(
                        Component.literal("Delete"),
                        btn -> {
                            PluginState.getInstance().uninstall(plugin.getName());
                            populateWidgets();
                        }
                    ).pos(contentX() + pluginColWidth() - btnW - 8, instY + ch - 6 - 20).width(btnW).build());
                    instY += ch + CARD_SPACING;
                }
            }
            for (ExternalPlugin ext : PluginManager.getInstance().getExternalPlugins()) {
                int rowY2 = instY;
                addRenderableWidget(Button.builder(
                    Component.literal("Folder"),
                    btn -> {
                        try {
                            Path pluginsFolder = Minecraft.getInstance().gameDirectory.toPath().resolve("xtoxray").resolve("plugins");
                            if (!Files.exists(pluginsFolder)) {
                                Files.createDirectories(pluginsFolder);
                            }
                            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                                new ProcessBuilder("explorer.exe", pluginsFolder.toAbsolutePath().toString()).start();
                            } else {
                                java.awt.Desktop.getDesktop().open(pluginsFolder.toFile());
                            }
                        } catch (Exception e) {
                            XtoXray.LOGGER.error("Failed to open plugins folder", e);
                        }
                    }
                ).pos(pluginsInstalledRight() - btnW - 8, rowY2 + ch - 6 - 20).width(btnW).build());
                instY += ch + CARD_SPACING;
            }
        }

        else if (selectedTab == BUG_REPORT_TAB) {
        }

        addRenderableWidget(Button.builder(
            Component.literal("Done"),
            btn -> this.onClose()
        ).pos(this.width - 70, 6).width(60).build());
    }

    private Button navButton(int tab, String label, int y) {
        return Button.builder(
            Component.literal(label),
            btn -> {
                this.selectedTab = tab;
                populateWidgets();
            }
        ).pos(10, y).width(NAV_W).build();
    }

    private void addBlockFromInput() {
        if (blockInput == null) return;
        String text = blockInput.getValue().trim();
        if (text.isEmpty()) return;
        Identifier id;
        if (text.contains(":")) {
            id = Identifier.parse(text);
        } else {
            id = Identifier.parse("minecraft:" + text);
        }
        if (BuiltInRegistries.BLOCK.containsKey(id)) {
            Block block = BuiltInRegistries.BLOCK.get(id).map(ref -> ref.value()).orElse(Blocks.AIR);
            if (block != Blocks.AIR) {
                XrayState.getInstance().addToWhitelist(block);
                blockInput.setValue("");
                populateWidgets();
            }
        }
    }

    private void addVeinminerBlockFromInput() {
        if (veinminerBlockInput == null) return;
        String text = veinminerBlockInput.getValue().trim();
        if (text.isEmpty()) return;
        Identifier id;
        if (text.contains(":")) {
            id = Identifier.parse(text);
        } else {
            id = Identifier.parse("minecraft:" + text);
        }
        if (BuiltInRegistries.BLOCK.containsKey(id)) {
            Block block = BuiltInRegistries.BLOCK.get(id).map(ref -> ref.value()).orElse(Blocks.AIR);
            if (block != Blocks.AIR) {
                VeinminerState.getInstance().addToWhitelist(block);
                veinminerBlockInput.setValue("");
                populateWidgets();
            }
        }
    }

    private void importPluginFromFile(Path sourcePath) {
        new Thread(() -> {
            try {
                Path pluginsFolder = Minecraft.getInstance().gameDirectory.toPath().resolve("xtoxray").resolve("plugins");
                if (!Files.exists(pluginsFolder)) {
                    Files.createDirectories(pluginsFolder);
                }
                String fileName = sourcePath.getFileName().toString();
                if (!fileName.toLowerCase().endsWith(".jar")) {
                    fileName = fileName + ".jar";
                }
                final String finalFileName = fileName;
                Path dest = pluginsFolder.resolve(finalFileName);
                Files.copy(sourcePath, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                PluginManager.getInstance().loadExternalPlugins(pluginsFolder, null);
                Minecraft.getInstance().execute(() -> {
                    populateWidgets();
                });
            } catch (Exception e) {
                XtoXray.LOGGER.error("Failed to import plugin from {}", sourcePath, e);
                Minecraft.getInstance().execute(() -> {
                    bugReportStatus = "Failed to import plugin.";
                    bugReportStatusColor = 0xFFFF5555;
                    populateWidgets();
                });
            }
        }).start();
    }

    @Override
    public void onFilesDrop(List<Path> paths) {
        if (paths == null || paths.isEmpty()) {
            super.onFilesDrop(paths);
            return;
        }
        if (selectedTab == 1) {
            for (Path path : paths) {
                String name = path.getFileName().toString();
                if (name.toLowerCase().endsWith(".jar")) {
                    importPluginFromFile(path);
                    return;
                }
            }
            return;
        }
        super.onFilesDrop(paths);
    }

    public void onClose() {
        stopLogWatcher();
        XrayState state = XrayState.getInstance();
        state.setRenderDistance(this.renderDistance);
        state.setRenderWhileMoving(this.renderWhileMoving);
        state.setNightVision(this.nightVision);
        VeinminerState vState = VeinminerState.getInstance();
        vState.setEnabled(this.veinminerEnabled);
        vState.setMaxBlocks(this.veinminerMaxBlocks);
        vState.setSilkTouchMode(this.veinminerSilkTouchMode);
        vState.setDurabilityCost(this.veinminerDurabilityCost);
        HitboxesState hState = HitboxesState.getInstance();
        hState.setEnabled(this.hitboxesEnabled);
        hState.setSeeThroughWalls(this.hitboxesSeeThroughWalls);
        hState.setMaxDistance(this.hitboxesMaxDistance);
        for (int i = 0; i < HitboxesState.CATEGORY_COUNT; i++) {
            hState.setShown(i, this.hitboxesShown[i]);
            hState.setColor(i, this.hitboxesColors[i]);
        }
        Minecraft mc = Minecraft.getInstance();
        if (state.isActive() && mc.player != null) {
            if (state.isNightVision()) {
                mc.player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, -1, 0, false, false, false));
            } else {
                mc.player.removeEffect(MobEffects.NIGHT_VISION);
            }
            XrayToggleHandler.rebuildAllSections(mc);
        }
        super.onClose();
        ConfigManager.save();
    }

    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = event.x();
        double my = event.y();

        if (selectedTab == 0 && whitelistCache != null && !whitelistCache.isEmpty()) {
            int startX = contentX();
            int gridTop = whitelistGridY;
            if (mx >= startX && my >= gridTop) {
                int col = (int) ((mx - startX) / (SLOT_SIZE + SLOT_GAP));
                int row = (int) ((my - gridTop) / (SLOT_SIZE + SLOT_GAP));
                if (col >= 0 && col < whitelistCols) {
                    int index = row * whitelistCols + col;
                    if (index >= 0 && index < whitelistCache.size()) {
                        int slotX = startX + col * (SLOT_SIZE + SLOT_GAP);
                        int slotY = gridTop + row * (SLOT_SIZE + SLOT_GAP);
                        if (mx >= slotX && mx < slotX + SLOT_SIZE && my >= slotY && my < slotY + SLOT_SIZE) {
                            XrayState.getInstance().removeFromWhitelist(whitelistCache.get(index));
                            populateWidgets();
                            return true;
                        }
                    }
                }
            }
        }

        if (selectedTab == getPluginTab("Veinminer") && veinminerWhitelistCache != null && !veinminerWhitelistCache.isEmpty()) {
            int startX = contentX();
            int gridTop = veinminerWhitelistGridY;
            if (mx >= startX && my >= gridTop) {
                int col = (int) ((mx - startX) / (SLOT_SIZE + SLOT_GAP));
                int row = (int) ((my - gridTop) / (SLOT_SIZE + SLOT_GAP));
                if (col >= 0 && col < veinminerWhitelistCols) {
                    int index = row * veinminerWhitelistCols + col;
                    if (index >= 0 && index < veinminerWhitelistCache.size()) {
                        int slotX = startX + col * (SLOT_SIZE + SLOT_GAP);
                        int slotY = gridTop + row * (SLOT_SIZE + SLOT_GAP);
                        if (mx >= slotX && mx < slotX + SLOT_SIZE && my >= slotY && my < slotY + SLOT_SIZE) {
                            VeinminerState.getInstance().removeFromWhitelist(veinminerWhitelistCache.get(index));
                            populateWidgets();
                            return true;
                        }
                    }
                }
            }
        }

        return super.mouseClicked(event, doubleClick);
    }

    private void drawWhitelist(GuiGraphicsExtractor gge, List<Block> cache, int startY, int gridY, int cols, int mouseX, int mouseY) {
        int cx = contentX();
        int cw = contentWidth();
        gge.text(this.font, "Whitelist", cx, startY - 2, COLUMN_HEADER);
        gge.fill(cx, startY + 10, cx + cw, startY + 11, SEPARATOR);
        if (cache == null) {
            return;
        }
        for (int i = 0; i < cache.size(); i++) {
            int col = i % cols;
            int row = i / cols;
            int x = cx + col * (SLOT_SIZE + SLOT_GAP);
            int y = gridY + row * (SLOT_SIZE + SLOT_GAP);
            boolean hovered = mouseX >= x && mouseX < x + SLOT_SIZE && mouseY >= y && mouseY < y + SLOT_SIZE;
            gge.fill(x, y, x + SLOT_SIZE, y + SLOT_SIZE, SLOT_BG);
            gge.fill(x, y, x + SLOT_SIZE, y + 1, SLOT_BORDER);
            gge.fill(x, y + SLOT_SIZE - 1, x + SLOT_SIZE, y + SLOT_SIZE, SLOT_BORDER);
            gge.fill(x, y, x + 1, y + SLOT_SIZE, SLOT_BORDER);
            gge.fill(x + SLOT_SIZE - 1, y, x + SLOT_SIZE, y + SLOT_SIZE, SLOT_BORDER);
            if (hovered) {
                gge.fill(x + 1, y + 1, x + SLOT_SIZE - 1, y + SLOT_SIZE - 1, SLOT_HOVER);
            }
            gge.item(new ItemStack(cache.get(i)), x + 1, y + 1);
            if (hovered) {
                gge.setTooltipForNextFrame(this.font, cache.get(i).getName(), mouseX, mouseY);
            }
        }
    }

    private void drawCard(GuiGraphicsExtractor gge, int x0, int y0, int x1, int y1, PluginData plugin) {
        int ch = y1 - y0;
        int isz = Math.max(16, Math.min(32, ch - 16));
        gge.fill(x0, y0, x1, y1, CARD_BG);
        gge.fill(x0, y0, x1, y0 + 1, CARD_BORDER);
        gge.fill(x0, y1 - 1, x1, y1, CARD_BORDER);
        gge.fill(x0, y0, x0 + 1, y1, CARD_BORDER);
        gge.fill(x1 - 1, y0, x1, y1, CARD_BORDER);
        int textX = x0 + 8;
        if (PluginIconRenderer.hasIcon(plugin.getName())) {
            PluginIconRenderer.render(gge, plugin.getName(), x0 + 8, y0 + (ch - isz) / 2, isz);
            textX = x0 + 8 + isz + 8;
        }
        gge.text(this.font, plugin.getName(), textX, y0 + 6, TEXT_WHITE);
        gge.text(this.font, plugin.getDescription(), textX, y0 + 6 + 12, TEXT_GRAY);
        gge.text(this.font, "Made by " + plugin.getCreator(), textX, y0 + 6 + 24, TEXT_GRAY);
    }

    private void drawCard(GuiGraphicsExtractor gge, int x0, int y0, int x1, int y1, String name, String description, String creator) {
        int ch = y1 - y0;
        int isz = Math.max(16, Math.min(32, ch - 16));
        gge.fill(x0, y0, x1, y1, CARD_BG);
        gge.fill(x0, y0, x1, y0 + 1, CARD_BORDER);
        gge.fill(x0, y1 - 1, x1, y1, CARD_BORDER);
        gge.fill(x0, y0, x0 + 1, y1, CARD_BORDER);
        gge.fill(x1 - 1, y0, x1, y1, CARD_BORDER);
        int textX = x0 + 8;
        gge.text(this.font, name, textX, y0 + 6, TEXT_WHITE);
        int ty = y0 + 6 + 12;
        if (description != null && !description.isEmpty()) {
            gge.text(this.font, description, textX, ty, TEXT_GRAY);
            ty += 12;
        }
        if (creator != null && !creator.isEmpty()) {
            gge.text(this.font, "Made by " + creator, textX, ty, TEXT_GRAY);
        }
    }

    public void extractRenderState(GuiGraphicsExtractor gge, int mouseX, int mouseY, float delta) {
        int cx = contentX();
        int cw = contentWidth();
        int ch = cardHeight();

        gge.centeredText(this.font, this.title.getString(), this.width / 2, 8, TEXT_WHITE);

        if (selectedTab == 0) {
            gge.text(this.font, "Xray Settings", cx, 30 - 14, TEXT_WHITE);
            drawWhitelist(gge, whitelistCache, whitelistStartY, whitelistGridY, whitelistCols, mouseX, mouseY);
        } else if (selectedTab == 1) {
            int instW = pluginsInstalledRight() - cx;
            gge.text(this.font, "Plugins", cx, 30 - 14, TEXT_WHITE);
            gge.text(this.font, "Installed", cx, 30, COLUMN_HEADER);
            gge.text(this.font, "| Drag & Drop JAR to Import", cx + 50, 30, TEXT_GRAY);
            gge.fill(cx, 30 + 10, cx + instW, 30 + 11, SEPARATOR);
            gge.text(this.font, "Download", pluginsDownloadX(), 30, COLUMN_HEADER);
            gge.fill(pluginsDownloadX(), 30 + 10, pluginContentRight(), 30 + 11, SEPARATOR);
            int i = 0;
            for (PluginData plugin : PluginState.getInstance().getPlugins()) {
                int cardY = 30 + 15 + i * (ch + CARD_SPACING);
                drawCard(gge, pluginsDownloadX(), cardY, pluginContentRight(), cardY + ch, plugin);
                i++;
            }
            int instY = 30 + 15;
            boolean hasInstalled = false;
            for (PluginData plugin : PluginState.getInstance().getPlugins()) {
                if (plugin.isInstalled()) {
                    hasInstalled = true;
                    drawCard(gge, cx, instY, cx + instW, instY + ch, plugin);
                    instY += ch + CARD_SPACING;
                }
            }
            for (ExternalPlugin ext : PluginManager.getInstance().getExternalPlugins()) {
                hasInstalled = true;
                PluginDescriptor desc = ext.getDescriptor();
                drawCard(gge, cx, instY, cx + instW, instY + ch, desc.getName(), desc.getDescription(), desc.getAuthor());
                instY += ch + CARD_SPACING;
            }
            if (!hasInstalled) {
                gge.text(this.font, "No plugins installed.", cx + 8, 30 + 20, TEXT_GRAY);
                gge.text(this.font, "Drag & Drop JAR to import", cx + 8, 30 + 34, TEXT_GRAY);
            }
        } else if (selectedTab == getPluginTab("Veinminer")) {
            gge.text(this.font, "Veinminer Settings", cx, 30 - 14, TEXT_WHITE);
            drawWhitelist(gge, veinminerWhitelistCache, veinminerWhitelistStartY, veinminerWhitelistGridY, veinminerWhitelistCols, mouseX, mouseY);
        } else if (selectedTab == getPluginTab("Hitboxes")) {
            gge.text(this.font, "Hitboxes Settings", cx, 30 - 14, TEXT_WHITE);
        } else {
            for (Map.Entry<String, Plugin> entry : PluginManager.getInstance().getSidebarTabs().entrySet()) {
                if (selectedTab == getPluginTab(entry.getKey())) {
                    gge.text(this.font, entry.getKey(), cx, 30 - 14, TEXT_WHITE);
                    String[] lines = entry.getValue().getSidebarTabContent();
                    if (lines != null) {
                        int ty = 30 + 14;
                        for (String line : lines) {
                            ty += 12;
                            gge.text(this.font, line, cx, ty, TEXT_GRAY);
                        }
                    }
                    break;
                }
            }
        }
        if (selectedTab == BUG_REPORT_TAB) {
            int wy = 30;
            gge.text(this.font, "Bug Report", cx, wy - 14, TEXT_WHITE);
            wy += 6;
            gge.text(this.font, "Step 1: Open an issue on github.com/RandomPixelStudios/X-To-Xray.", cx, wy, TEXT_WHITE);
            wy += 12;
            gge.text(this.font, "Step 2: Describe the error / attach your log / send a screenshot.", cx, wy, TEXT_WHITE);
            wy += 12;
            gge.text(this.font, "Step 3: Wait for the fix. Usually within 1-7 days.", cx, wy, TEXT_WHITE);
            wy += 20;

            int logStartY = getLogStartY();
            int logHeight = this.height - logStartY - 20;
            int lineHeight = 12;
            gge.fill(cx, logStartY, cx + cw, logStartY + logHeight, 0xFF000000);
            gge.fill(cx, logStartY, cx + cw, logStartY + 1, 0xFF444444);
            gge.fill(cx, logStartY + logHeight - 1, cx + cw, logStartY + logHeight, 0xFF444444);
            gge.text(this.font, "Live Minecraft Log", cx + 2, logStartY + 2, TEXT_GRAY);

            int maxScroll = Math.max(0, logLines.size() * lineHeight - (logHeight - 14));
            logScrollY = Math.max(0, Math.min(logScrollY, maxScroll));
            int startLine = Math.max(0, (logScrollY / lineHeight));
            int visibleLines = (logHeight - 14) / lineHeight;
            int lineIdx = 0;
            synchronized (logLines) {
                for (String line : logLines) {
                    if (lineIdx < startLine) {
                        lineIdx++;
                        continue;
                    }
                    if (lineIdx >= startLine + visibleLines) break;
                    int y = logStartY + 14 + (lineIdx - startLine) * lineHeight;
                    renderLogLine(gge, line, cx + 2, y, cw - 4);
                    lineIdx++;
                }
            }
            if (!bugReportStatus.isEmpty()) {
                gge.text(this.font, bugReportStatus, cx, logStartY + logHeight + 4, bugReportStatusColor);
            }
        }

        super.extractRenderState(gge, mouseX, mouseY, delta);
    }

    private void startLogWatcher() {
        if (logWatcherRunning) return;
        logWatcherRunning = true;
        logFile = Minecraft.getInstance().gameDirectory.toPath().resolve("logs").resolve("latest.log");
        logWatcherThread = new Thread(() -> {
            try {
                while (logWatcherRunning) {
                    if (Files.exists(logFile)) {
                        long size = Files.size(logFile);
                        if (size > logFilePosition) {
                            try (RandomAccessFile raf = new RandomAccessFile(logFile.toFile(), "r")) {
                                raf.seek(logFilePosition);
                                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                int b;
                                while ((b = raf.read()) != -1) {
                                    if (b == '\n') {
                                        String line = baos.toString(java.nio.charset.StandardCharsets.UTF_8);
                                        logLines.add(line);
                                        if (logLines.size() > 1000) {
                                            logLines.remove(0);
                                        }
                                        logScrollY = Integer.MAX_VALUE;
                                        baos.reset();
                                    } else if (b != '\r') {
                                        baos.write(b);
                                    }
                                }
                                if (baos.size() > 0) {
                                    String line = baos.toString(java.nio.charset.StandardCharsets.UTF_8);
                                    logLines.add(line);
                                    if (logLines.size() > 1000) {
                                        logLines.remove(0);
                                    }
                                    logScrollY = Integer.MAX_VALUE;
                                }
                                logFilePosition = raf.getFilePointer();
                            }
                        }
                    }
                    Thread.sleep(500);
                }
            } catch (Exception e) {
            }
        }, "XtoXray-LogWatcher");
        logWatcherThread.setDaemon(true);
        logWatcherThread.start();
    }

    private void stopLogWatcher() {
        logWatcherRunning = false;
        if (logWatcherThread != null) {
            try {
                logWatcherThread.join(1000);
            } catch (InterruptedException e) {
            }
        }
    }

    private void copyLogToClipboard() {
        try {
            String text = String.join("\n", logLines);
            StringSelection selection = new StringSelection(text);
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, null);
            bugReportStatus = "Log copied to clipboard.";
            bugReportStatusColor = 0xFF55FF55;
        } catch (Exception e) {
            bugReportStatus = "Failed to copy log.";
            bugReportStatusColor = 0xFFFF5555;
        }
    }

    private int getLogStartY() {
        return 80;
    }

    private void renderLogLine(GuiGraphicsExtractor gge, String line, int x, int y, int maxWidth) {
        int levelColor = TEXT_WHITE;
        int prefixEnd = 0;

        try {
            int firstBracket = line.indexOf('[');
            int secondBracket = line.indexOf(']', firstBracket + 1);
            int thirdBracket = line.indexOf('[', secondBracket + 2);
            int fourthBracket = line.indexOf(']', thirdBracket + 1);
            int colon = line.indexOf(':', fourthBracket + 2);

            if (firstBracket >= 0 && secondBracket > firstBracket &&
                thirdBracket > secondBracket && fourthBracket > thirdBracket && colon > fourthBracket) {
                String level = line.substring(fourthBracket + 2, colon).trim().toUpperCase();
                switch (level) {
                    case "ERROR" -> levelColor = 0xFFFF5555;
                    case "WARN" -> levelColor = 0xFFFFAA00;
                    case "DEBUG" -> levelColor = 0xFF888888;
                    default -> levelColor = TEXT_WHITE;
                }
                prefixEnd = colon + 2;
            }
        } catch (Exception e) {
        }

        if (prefixEnd > 0) {
            String prefix = line.substring(0, prefixEnd);
            String message = line.substring(prefixEnd);

            gge.text(this.font, prefix, x, y, 0xFF888888);
            int msgX = x + this.font.width(prefix);
            int msgMaxWidth = maxWidth - (msgX - x);

            if (msgMaxWidth > 0 && this.font.width(message) > msgMaxWidth) {
                String truncated = message;
                while (truncated.length() > 0 && this.font.width(truncated + "...") > msgMaxWidth) {
                    truncated = truncated.substring(0, truncated.length() - 1);
                }
                if (truncated.length() < message.length()) {
                    truncated = truncated + "...";
                }
                gge.text(this.font, truncated, msgX, y, levelColor);
            } else {
                gge.text(this.font, message, msgX, y, levelColor);
            }
        } else {
            gge.text(this.font, line, x, y, TEXT_WHITE);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (selectedTab == BUG_REPORT_TAB) {
            logScrollY -= deltaY * 10;
            int lineHeight = 12;
            int logStartY = getLogStartY();
            int logHeight = this.height - logStartY - 20;
            int maxScroll = Math.max(0, logLines.size() * lineHeight - (logHeight - 14));
            logScrollY = Math.max(0, Math.min(logScrollY, maxScroll));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, deltaX, deltaY);
    }
}