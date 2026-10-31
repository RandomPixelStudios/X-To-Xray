package com.xtoxray.client.gui;

import com.xtoxray.ConfigManager;
import com.xtoxray.XrayState;
import com.xtoxray.XtoXray;
import com.xtoxray.client.XrayToggleHandler;
import com.xtoxray.plugin.PluginManager;
import com.xtoxray.plugin.ExternalPlugin;
import com.xtoxray.plugin.LuaPlugin;
import com.xtoxray.plugin.PluginDefinition;
import com.xtoxray.plugin.PluginPermission;
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
import net.minecraft.client.gui.screens.ConfirmScreen;
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
    private String pluginStatus;
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

    private static final int BROWSER_LIST_Y = 62;
    private static final int BROWSER_ROW_H = 26;
    private String pluginSelected;
    private static final java.net.URI PUBLISHING_GUIDE =
        java.net.URI.create("https://github.com/RandomPixelStudios/X-To-Xray-Plugins/blob/main/publish.md");
    private String pluginSearch = "";
    private boolean pluginSortByName;
    private boolean pluginShowInstalled;
    private int pluginScroll;
    private EditBox pluginSearchBox;
    private EditBox pluginBlockInput;
    private List<Block> pluginBlocksCache;
    private LuaPlugin.Widget pluginBlocksWidget;
    private int pluginBlocksStartY;
    private int pluginBlocksCols;
    private int pluginBlocksGridY;
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
        PluginManager pm = PluginManager.getInstance();
        int count = Math.max(1, Math.max(pm.getAvailable().size(), pm.getExternalPlugins().size() + pm.getRejected().size()));
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
    }

    protected void init() {
        PluginManager.getInstance().rescan();
        startLogWatcher();
        populateWidgets();
    }

    private LuaPlugin selectedPlugin() {
        List<LuaPlugin> tabs = PluginManager.getInstance().getSettingsPlugins();
        int index = selectedTab - 2;
        return selectedTab != BUG_REPORT_TAB && index >= 0 && index < tabs.size() ? tabs.get(index) : null;
    }

    private void addPluginSettings(LuaPlugin plugin, int cx, int sw, int cw) {
        int y = 30;
        for (LuaPlugin.Widget w : plugin.widgets()) {
            Object value = plugin.value(w.key());
            switch (w.kind()) {
                case TOGGLE -> {
                    addRenderableWidget(Checkbox.builder(Component.literal(w.label()), this.font).pos(cx, y)
                        .selected(Boolean.TRUE.equals(value)).onValueChange((cb, sel) -> plugin.setValue(w.key(), sel)).build());
                    y += 25;
                }
                case SLIDER -> {
                    addRenderableWidget(new XraySlider(cx, y, sw, 20, w.label(), w.min(), w.max(), value instanceof Integer i ? i : w.min(),
                        val -> plugin.setValue(w.key(), val)));
                    y += 25;
                }
                case CYCLE -> {
                    addRenderableWidget(Button.builder(Component.literal(w.label() + ": " + PluginDefinition.clean(String.valueOf(value))), btn -> {
                        int next = (w.options().indexOf(value) + 1) % w.options().size();
                        plugin.setValue(w.key(), w.options().get(next));
                        populateWidgets();
                    }).pos(cx, y).width(sw).build());
                    y += 25;
                }
                case BLOCKS -> { }
            }
        }
        addRenderableWidget(Button.builder(Component.literal("Reset to Default"), btn -> {
            plugin.resetSettings();
            populateWidgets();
        }).pos(cx, y).width(sw).build());
        y += 30;
        for (LuaPlugin.Widget w : plugin.widgets()) {
            if (w.kind() != LuaPlugin.WidgetKind.BLOCKS) continue;
            pluginBlocksWidget = w;
            pluginBlocksCache = new ArrayList<>();
            if (plugin.value(w.key()) instanceof List<?> ids) {
                for (Object id : ids) {
                    Identifier identifier = Identifier.tryParse(String.valueOf(id));
                    if (identifier != null) BuiltInRegistries.BLOCK.get(identifier).ifPresent(ref -> pluginBlocksCache.add(ref.value()));
                }
            }
            pluginBlocksStartY = y;
            pluginBlocksCols = Math.max(1, cw / (SLOT_SIZE + SLOT_GAP));
            pluginBlocksGridY = y + 44;
            int inputW = Math.max(60, cw - 52);
            pluginBlockInput = new EditBox(this.font, cx, y + 18, inputW, 20, Component.literal("Block ID"));
            pluginBlockInput.setHint(Component.literal("minecraft:diamond_ore"));
            pluginBlockInput.setMaxLength(64);
            pluginBlockInput.setCanLoseFocus(true);
            addRenderableWidget(pluginBlockInput);
            addRenderableWidget(Button.builder(Component.literal("Add"), btn -> addPluginBlockFromInput(plugin))
                .pos(cx + inputW + 5, y + 18).width(47).build());
            break;
        }
    }

    @SuppressWarnings("unchecked")
    private void addPluginBlockFromInput(LuaPlugin plugin) {
        String text = pluginBlockInput == null ? "" : pluginBlockInput.getValue().trim();
        if (text.isEmpty() || pluginBlocksWidget == null) return;
        Identifier id = Identifier.tryParse(text.contains(":") ? text : "minecraft:" + text);
        if (id == null || !BuiltInRegistries.BLOCK.containsKey(id)) return;
        if (plugin.value(pluginBlocksWidget.key()) instanceof List<?> list) {
            ((List<String>) list).add(id.toString());
        }
        populateWidgets();
    }

    public void openPluginsTab(boolean installed) {
        pluginShowInstalled = installed;
        openTab(1);
    }

    public void openTab(int tab) {
        selectedTab = tab;
        if (this.font != null) populateWidgets();
    }

    private List<PluginDefinition> browseEntries() {
        String query = pluginSearch.toLowerCase();
        PluginManager pm = PluginManager.getInstance();
        List<PluginDefinition> entries = new ArrayList<>();
        for (PluginDefinition entry : pm.getCatalog()) {
            if ((entry.name() + " " + entry.description() + " " + entry.author() + " " + entry.categories()).toLowerCase().contains(query)) {
                entries.add(entry);
            }
        }
        java.util.Comparator<PluginDefinition> byName = java.util.Comparator.comparing(e -> e.name().toLowerCase());
        entries.sort(pluginSortByName ? byName
            : java.util.Comparator.<PluginDefinition>comparingLong(e -> -pm.downloadsOf(e.id())).thenComparing(byName));
        return entries;
    }

    private List<ExternalPlugin> installedEntries() {
        String query = pluginSearch.toLowerCase();
        return PluginManager.getInstance().getExternalPlugins().stream()
            .filter(p -> p.getDefinition().name().toLowerCase().contains(query)).toList();
    }

    /** One line of the plugin list; the same shape for catalog, installed and blocked entries. */
    private record BrowserRow(String key, String id, String name, String author, String version, String description,
                              List<String> categories, List<String> permissions, String status,
                              PluginDefinition catalog, ExternalPlugin installed, PluginManager.RejectedFile rejected) {
    }

    private static List<String> permissionLines(PluginDefinition definition) {
        return definition.permissions().stream().map(PluginPermission::description).sorted().toList();
    }

    private List<BrowserRow> browserRows() {
        PluginManager pm = PluginManager.getInstance();
        List<BrowserRow> rows = new ArrayList<>();
        if (pluginShowInstalled) {
            for (ExternalPlugin ext : installedEntries()) {
                PluginDefinition d = ext.getDefinition();
                rows.add(new BrowserRow(d.id(), d.id(), d.name(), d.author(), d.version(), d.description(), d.categories(),
                    permissionLines(d), statusLabel(ext.getStatus()), null, ext, null));
            }
            for (PluginManager.RejectedFile file : pm.getRejected()) {
                String name = file.file().getFileName().toString();
                rows.add(new BrowserRow("file:" + name, "blocked", name, "Unknown", "-", file.reason(), List.of(), List.of(),
                    "Blocked", null, null, file));
            }
        } else {
            for (PluginDefinition d : browseEntries()) {
                rows.add(new BrowserRow(d.id(), d.id(), d.name(), d.author(), d.version(), d.description(), d.categories(),
                    permissionLines(d), "", d, pm.installedById(d.id()), null));
            }
        }
        return rows;
    }

    private BrowserRow selectedRow(List<BrowserRow> rows) {
        for (BrowserRow row : rows) {
            if (row.key().equals(pluginSelected)) return row;
        }
        return rows.isEmpty() ? null : rows.get(0);
    }

    private int browserListWidth(int cw) {
        return Math.max(110, cw * 2 / 5);
    }

    private int browserVisibleBottom() {
        return this.height - 8;
    }

    private void clampPluginScroll() {
        int content = browserRows().size() * BROWSER_ROW_H;
        int visible = browserVisibleBottom() - BROWSER_LIST_Y;
        pluginScroll = Math.max(0, Math.min(pluginScroll, Math.max(0, content - visible)));
    }

    private void addPluginBrowser(int cx, int cw) {
        clampPluginScroll();
        int tabW = 52;
        Button browse = Button.builder(Component.literal("Browse"), btn -> {
            pluginShowInstalled = false;
            pluginScroll = 0;
            pluginSelected = null;
            populateWidgets();
        }).pos(cx, 16).width(tabW).build();
        browse.active = pluginShowInstalled;
        addRenderableWidget(browse);
        Button installed = Button.builder(Component.literal("Installed"), btn -> {
            pluginShowInstalled = true;
            pluginScroll = 0;
            pluginSelected = null;
            populateWidgets();
        }).pos(cx + tabW + 4, 16).width(tabW).build();
        installed.active = !pluginShowInstalled;
        addRenderableWidget(installed);
        addRenderableWidget(Button.builder(Component.literal("Upload"), btn -> openPublishingGuide())
            .pos(cx + 2 * (tabW + 4), 16).width(tabW).build());
        int sortW = 72;
        int searchW = Math.max(50, cw - sortW - 4);
        pluginSearchBox = new EditBox(this.font, cx, 38, searchW, 20, Component.literal("Search"));
        pluginSearchBox.setHint(Component.literal("Search plugins..."));
        pluginSearchBox.setValue(pluginSearch);
        pluginSearchBox.setResponder(text -> {
            if (text.equals(pluginSearch)) return;
            pluginSearch = text;
            pluginScroll = 0;
            populateWidgets();
            setFocused(pluginSearchBox);
            pluginSearchBox.setFocused(true);
        });
        addRenderableWidget(pluginSearchBox);
        if (pluginShowInstalled) {
            addRenderableWidget(Button.builder(Component.literal("Folder"), btn -> openPluginsFolder())
                .pos(cx + searchW + 4, 38).width(sortW).build());
        } else {
            addRenderableWidget(Button.builder(Component.literal(pluginSortByName ? "Name" : "Downloads"), btn -> {
                pluginSortByName = !pluginSortByName;
                populateWidgets();
            }).pos(cx + searchW + 4, 38).width(sortW).build());
        }
        BrowserRow row = selectedRow(browserRows());
        if (row != null) addDetailButtons(row, cx + browserListWidth(cw) + 6, cx + cw);
    }

    private void addDetailButtons(BrowserRow row, int left, int right) {
        PluginManager pm = PluginManager.getInstance();
        int y = browserVisibleBottom() - 26;
        int width = right - left - 12;
        int x = left + 6;
        if (row.rejected() != null) {
            addRenderableWidget(Button.builder(Component.literal("Delete"), btn -> {
                pm.deleteRejected(row.rejected());
                populateWidgets();
            }).pos(x, y).width(width).build());
        } else if (row.catalog() != null) {
            ExternalPlugin ext = row.installed();
            boolean current = ext != null && ext.getDefinition().sha256().equals(row.catalog().sha256());
            Button action = Button.builder(Component.literal(current ? "Installed" : ext != null ? "Update" : "Install"),
                btn -> confirmRepoPlugin(row.catalog())).pos(x, y).width(width).build();
            action.active = !current;
            addRenderableWidget(action);
        } else {
            ExternalPlugin ext = row.installed();
            int half = (width - 4) / 2;
            ExternalPlugin.Status status = ext.getStatus();
            if (status == ExternalPlugin.Status.NEEDS_APPROVAL) {
                addRenderableWidget(Button.builder(Component.literal("Allow"), btn -> confirmLocalPlugin(ext)).pos(x, y).width(half).build());
            } else if (status != ExternalPlugin.Status.REVOKED) {
                boolean on = status == ExternalPlugin.Status.ENABLED;
                addRenderableWidget(Button.builder(Component.literal(on ? "Disable" : "Enable"), btn -> {
                    pm.setEnabled(ext, !on);
                    populateWidgets();
                }).pos(x, y).width(half).build());
            }
            addRenderableWidget(Button.builder(Component.literal("Delete"), btn -> {
                pm.uninstall(ext);
                populateWidgets();
            }).pos(x + half + 4, y).width(half).build());
        }
    }

    /** @return true if a row of the plugin list was clicked */
    private boolean clickBrowserRow(double mx, double my) {
        int cx = contentX();
        if (mx < cx || mx >= cx + browserListWidth(contentWidth()) || my < BROWSER_LIST_Y || my >= browserVisibleBottom()) return false;
        int index = (int) ((my - BROWSER_LIST_Y + pluginScroll) / BROWSER_ROW_H);
        List<BrowserRow> rows = browserRows();
        if (index < 0 || index >= rows.size()) return false;
        pluginSelected = rows.get(index).key();
        populateWidgets();
        return true;
    }

    private static String formatCount(long count) {
        if (count < 0) return "-";
        if (count < 1000) return Long.toString(count);
        if (count < 1_000_000) return String.format(java.util.Locale.ROOT, "%.1fk", count / 1000.0);
        return String.format(java.util.Locale.ROOT, "%.1fM", count / 1_000_000.0);
    }

    private String fit(String text, int width) {
        if (this.font.width(text) <= width) return text;
        return this.font.plainSubstrByWidth(text, Math.max(0, width - this.font.width("..."))) + "...";
    }

    private List<String> wrap(String text, int width) {
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String next = line.isEmpty() ? word : line + " " + word;
            if (this.font.width(next) > width && !line.isEmpty()) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(next);
            }
        }
        if (!line.isEmpty()) lines.add(line.toString());
        return lines;
    }

    private void drawPluginIcon(GuiGraphicsExtractor gge, String id, String name, int x, int y, int size) {
        if (PluginIconRenderer.hasIcon(id)) {
            PluginIconRenderer.render(gge, id, x, y, size);
            return;
        }
        int color = 0xFF000000 | java.awt.Color.HSBtoRGB((id.hashCode() & 0xFF) / 255f, 0.55f, 0.65f);
        gge.fill(x, y, x + size, y + size, color);
        String letter = name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase();
        gge.centeredText(this.font, letter, x + size / 2, y + size / 2 - 4, TEXT_WHITE);
    }

    private void drawPluginBrowser(GuiGraphicsExtractor gge, int cx, int cw) {
        PluginManager pm = PluginManager.getInstance();
        List<BrowserRow> rows = browserRows();
        BrowserRow selected = selectedRow(rows);
        int bottom = browserVisibleBottom();
        int listW = browserListWidth(cw);
        int listRight = cx + listW;
        gge.fill(cx, BROWSER_LIST_Y, listRight, bottom, CARD_BG);
        int y = BROWSER_LIST_Y - pluginScroll;
        for (BrowserRow row : rows) {
            if (y >= BROWSER_LIST_Y && y + BROWSER_ROW_H <= bottom) {
                if (row == selected) gge.fill(cx, y, listRight, y + BROWSER_ROW_H, 0xFF2F3A34);
                drawPluginIcon(gge, row.id(), row.name(), cx + 4, y + 3, BROWSER_ROW_H - 6);
                int textX = cx + BROWSER_ROW_H + 2;
                int textW = listRight - textX - 8;
                gge.text(this.font, fit(row.name(), textW), textX, y + 4, TEXT_WHITE);
                String sub = pluginShowInstalled ? row.status() : "\u2193 " + formatCount(pm.downloadsOf(row.id()));
                gge.text(this.font, fit(sub, textW), textX, y + 14, pluginShowInstalled ? TEXT_GRAY : 0xFF55DD88);
            }
            y += BROWSER_ROW_H;
        }
        int content = rows.size() * BROWSER_ROW_H;
        int visible = bottom - BROWSER_LIST_Y;
        if (content > visible) {
            int barH = Math.max(12, visible * visible / content);
            int barY = BROWSER_LIST_Y + (visible - barH) * pluginScroll / Math.max(1, content - visible);
            gge.fill(listRight - 3, barY, listRight - 1, barY + barH, 0xFF777777);
        }

        int left = listRight + 6;
        int right = cx + cw;
        gge.fill(left, BROWSER_LIST_Y, right, bottom, CARD_BG);
        if (selected == null) {
            String status = pluginStatus != null ? pluginStatus : pm.getRepositoryStatus();
            String empty = pluginShowInstalled ? "No plugins installed. Drag & drop a .lua file here." : !status.isEmpty() ? status : "No plugins found.";
            int ty = BROWSER_LIST_Y + 8;
            for (String line : wrap(empty, right - left - 12)) {
                gge.text(this.font, line, left + 6, ty, TEXT_GRAY);
                ty += 11;
            }
            return;
        }
        int icon = 28;
        int top = BROWSER_LIST_Y + 6;
        drawPluginIcon(gge, selected.id(), selected.name(), left + 6, top, icon);
        int headX = left + 6 + icon + 6;
        int headW = right - headX - 6;
        gge.text(this.font, fit(selected.name(), headW), headX, top, TEXT_WHITE);
        gge.text(this.font, fit("by " + selected.author(), headW), headX, top + 10, TEXT_GRAY);
        String stats = "v" + selected.version() + (pluginShowInstalled ? "" : "  \u2193 " + formatCount(pm.downloadsOf(selected.id())));
        gge.text(this.font, fit(stats, headW), headX, top + 20, 0xFF55DD88);
        int ty = top + icon + 6;
        int textW = right - left - 12;
        int limit = bottom - 30;
        for (String line : wrap(selected.description(), textW)) {
            if (ty + 9 > limit) return;
            gge.text(this.font, line, left + 6, ty, TEXT_GRAY);
            ty += 11;
        }
        if (!selected.categories().isEmpty() && ty + 16 <= limit) {
            ty += 2;
            int tagX = left + 6;
            for (String tag : selected.categories()) {
                int w = this.font.width(tag) + 8;
                if (tagX + w > right - 6) break;
                gge.fill(tagX, ty, tagX + w, ty + 12, 0xFF2F3A34);
                gge.text(this.font, tag, tagX + 4, ty + 2, 0xFF7FD6A0);
                tagX += w + 4;
            }
            ty += 15;
        }
        if (selected.rejected() == null && ty + 11 <= limit) {
            gge.text(this.font, selected.permissions().isEmpty() ? "No permissions" : "Permissions:", left + 6, ty, COLUMN_HEADER);
            ty += 11;
            for (String permission : selected.permissions()) {
                if (ty + 9 > limit) return;
                gge.text(this.font, fit("- " + permission, textW), left + 6, ty, TEXT_GRAY);
                ty += 11;
            }
        }
        if (pluginStatus != null) gge.text(this.font, fit(pluginStatus, textW), left + 6, bottom - 40, 0xFFFF5555);
    }

    public void populateWidgets() {
        clearWidgets();
        blockInput = null;
        pluginBlockInput = null;
        pluginBlocksCache = null;
        pluginBlocksWidget = null;
        whitelistCache = new ArrayList<>(XrayState.getInstance().getWhitelist());

        int cx = contentX();
        int sw = sliderWidth();
        int cw = contentWidth();

        int navY = NAV_Y;
        addRenderableWidget(navButton(0, baseTabs[0], navY));
        navY += NAV_GAP;
        addRenderableWidget(navButton(1, baseTabs[1], navY));
        navY += NAV_GAP;
        List<LuaPlugin> settingsPlugins = PluginManager.getInstance().getSettingsPlugins();
        if (selectedTab != BUG_REPORT_TAB && selectedTab >= 2 + settingsPlugins.size()) {
            selectedTab = 1;
        }
        for (int t = 0; t < settingsPlugins.size(); t++) {
            addRenderableWidget(navButton(2 + t, settingsPlugins.get(t).definition().name(), navY));
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
        } else if (selectedPlugin() != null) {
            addPluginSettings(selectedPlugin(), cx, sw, cw);
        } else if (selectedTab == 1) {
            addPluginBrowser(cx, cw);
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

    private static String describePermissions(PluginDefinition definition) {
        if (definition.permissions().isEmpty()) return "Permissions: none";
        StringBuilder text = new StringBuilder("This plugin can:");
        for (PluginPermission permission : definition.permissions()) {
            text.append("\n- ").append(permission.description());
        }
        return text.toString();
    }

    private void confirmLocalPlugin(ExternalPlugin ext) {
        PluginDefinition definition = ext.getDefinition();
        com.xtoxray.client.ScreenSwitcher.open(new ConfirmScreen(allowed -> {
            if (allowed) PluginManager.getInstance().approve(ext);
            com.xtoxray.client.ScreenSwitcher.open(this);
        }, Component.literal("Allow " + definition.name() + " by " + definition.author() + "?"),
            Component.literal("This file did not come from the plugin repository.\n" + describePermissions(definition))));
    }

    private void confirmRepoPlugin(PluginDefinition definition) {
        com.xtoxray.client.ScreenSwitcher.open(new ConfirmScreen(allowed -> {
            if (allowed) {
                String error = PluginManager.getInstance().install(definition);
                if (error != null) pluginStatus = "Install failed: " + error;
            }
            com.xtoxray.client.ScreenSwitcher.open(this);
        }, Component.literal("Install " + definition.name() + " " + definition.version() + " by " + definition.author() + "?"),
            Component.literal(describePermissions(definition))));
    }

    private void openPublishingGuide() {
        net.minecraft.client.gui.screens.ConfirmLinkScreen.confirmLinkNow(this, PUBLISHING_GUIDE);
    }

    private void openPluginsFolder() {
        try {
            Path pluginsFolder = PluginManager.getInstance().pluginsFolder();
            Files.createDirectories(pluginsFolder);
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                new ProcessBuilder("explorer.exe", pluginsFolder.toAbsolutePath().toString()).start();
            } else {
                java.awt.Desktop.getDesktop().open(pluginsFolder.toFile());
            }
        } catch (Exception e) {
            XtoXray.LOGGER.error("Failed to open plugins folder", e);
        }
    }

    private void importPluginFromFile(Path sourcePath) {
        new Thread(() -> {
            try {
                Path pluginsFolder = PluginManager.getInstance().pluginsFolder();
                Files.createDirectories(pluginsFolder);
                String fileName = sourcePath.getFileName().toString();
                final String finalFileName = fileName;
                Path dest = pluginsFolder.resolve(finalFileName);
                Files.copy(sourcePath, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                Minecraft.getInstance().execute(() -> {
                    PluginManager.getInstance().rescan();
                    populateWidgets();
                });
            } catch (Exception e) {
                XtoXray.LOGGER.error("Failed to import plugin from {}", sourcePath, e);
                Minecraft.getInstance().execute(() -> {
                    pluginStatus = "Failed to import plugin.";
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
                if (name.toLowerCase().endsWith(".lua")) {
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
        PluginManager.getInstance().saveAllSettings();
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

        if (selectedTab == 1 && clickBrowserRow(mx, my)) {
            return true;
        }

        LuaPlugin clickedPlugin = selectedPlugin();
        if (clickedPlugin != null && pluginBlocksCache != null && !pluginBlocksCache.isEmpty()) {
            int startX = contentX();
            if (mx >= startX && my >= pluginBlocksGridY) {
                int col = (int) ((mx - startX) / (SLOT_SIZE + SLOT_GAP));
                int row = (int) ((my - pluginBlocksGridY) / (SLOT_SIZE + SLOT_GAP));
                int index = row * pluginBlocksCols + col;
                if (col < pluginBlocksCols && index >= 0 && index < pluginBlocksCache.size()
                    && mx < startX + col * (SLOT_SIZE + SLOT_GAP) + SLOT_SIZE
                    && my < pluginBlocksGridY + row * (SLOT_SIZE + SLOT_GAP) + SLOT_SIZE
                    && clickedPlugin.value(pluginBlocksWidget.key()) instanceof List<?> list) {
                    list.remove(BuiltInRegistries.BLOCK.getKey(pluginBlocksCache.get(index)).toString());
                    populateWidgets();
                    return true;
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
            if (Minecraft.getInstance().level != null) {
                // Item data is only bound once a world is loaded; creating a stack earlier crashes.
                gge.item(new ItemStack(cache.get(i)), x + 1, y + 1);
            }
            if (hovered) {
                gge.setTooltipForNextFrame(this.font, cache.get(i).getName(), mouseX, mouseY);
            }
        }
    }

    private static String statusLabel(ExternalPlugin.Status status) {
        return switch (status) {
            case NEEDS_APPROVAL -> "Needs approval";
            case ENABLED -> "On";
            case DISABLED -> "Off";
            case CRASHED -> "Crashed";
            case REVOKED -> "Blocked as unsafe";
        };
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

        gge.centeredText(this.font, this.title.getString(), this.width / 2, 4, TEXT_WHITE);

        if (selectedTab == 0) {
            gge.text(this.font, "Xray Settings", cx, 30 - 14, TEXT_WHITE);
            drawWhitelist(gge, whitelistCache, whitelistStartY, whitelistGridY, whitelistCols, mouseX, mouseY);
        } else if (selectedTab == 1) {
            drawPluginBrowser(gge, cx, cw);
        } else if (selectedPlugin() != null) {
            gge.text(this.font, selectedPlugin().definition().name() + " Settings", cx, 30 - 14, TEXT_WHITE);
            if (pluginBlocksCache != null) {
                drawWhitelist(gge, pluginBlocksCache, pluginBlocksStartY, pluginBlocksGridY, pluginBlocksCols, mouseX, mouseY);
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
        if (selectedTab == 1 && mouseX >= contentX()) {
            pluginScroll -= (int) (deltaY * BROWSER_ROW_H);
            populateWidgets();
            return true;
        }
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