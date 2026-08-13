package com.xtoxray.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import com.xtoxray.XrayState;
import com.xtoxray.XtoXray;
import com.xtoxray.client.XrayToggleHandler;
import com.xtoxray.client.gui.AddBlockScreen;
import com.xtoxray.client.gui.ConfirmDeleteScreen;
import com.xtoxray.network.XrayPayloads;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Util;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

public class XrayModMenu extends Screen {
    private static final int SIDEBAR_W = 90;
    private static final int HEADER_H = 28;
    private static final int TEXT = -3355444;
    private static final int TEXT_DIM = -7829368;
    private static final int TEXT_BRIGHT = -1;
    private static final int SEPARATOR = -12303292;
    private static final int BTN_BG = -14803426;
    private static final int BTN_BG_HOVER = -13882324;
    private static final int BTN_BORDER = -12961222;
    private static final int GREEN = -13315175;
    private static final int PINK = -757066;
    private static final URI MODRINTH_API = URI.create("https://api.modrinth.com/v2/project/HbXXzLHU/version");
    private static final URI MODRINTH_URI = URI.create("https://modrinth.com/mod/x-to-xray");
    static final String CURRENT_VERSION = XtoXray.VERSION;
    private static final String[] CATEGORY_NAMES = new String[]{"Xray", "Vein Miner", "Container View", "Hitboxes", "Keybinds", "Versions", "Plugins"};
    private static final String[] COMMON_ENTITIES = new String[]{"minecraft:zombie", "minecraft:skeleton", "minecraft:spider", "minecraft:creeper", "minecraft:enderman", "minecraft:witch", "minecraft:slime", "minecraft:ghast", "minecraft:blaze", "minecraft:piglin", "minecraft:warden", "minecraft:husk", "minecraft:stray", "minecraft:drowned", "minecraft:cave_spider", "minecraft:pillager", "minecraft:vindicator", "minecraft:evoker", "minecraft:vex", "minecraft:guardian", "minecraft:elder_guardian", "minecraft:shulker", "minecraft:iron_golem", "minecraft:snow_golem", "minecraft:player"};
    private static final List<KeybindEntry> KEYBINDS = List.of(new KeybindEntry("Xray Toggle", XrayToggleHandler.TOGGLE_KEY), new KeybindEntry("Hitboxes", XrayToggleHandler.HITBOXES_KEY), new KeybindEntry("Container View", XrayToggleHandler.CONTAINER_VIEW_KEY), new KeybindEntry("Vein Miner", XrayToggleHandler.VEIN_MINER_KEY), new KeybindEntry("Open Menu", XrayToggleHandler.SETTINGS_KEY));

    private final Screen parent;
    private int panelW;
    private int panelH;
    private int panelX;
    private int panelY;
    private int contentX;
    private int contentY;
    private int contentW;
    private int contentH;
    private volatile String updateStatus = null;
    private volatile int updateStatusColor = TEXT_DIM;
    private int selectedCategory = 0;
    private int hoveredCategory = -1;
    private KeyMapping awaitingKeybindMapping;
    private float whitelistScroll = 0.0f;
    private float whitelistMaxScroll = 0.0f;
    private int whitelistCols;
    private int whitelistGridX;
    private int whitelistGridY;
    private float contentScroll = 0.0f;
    private float contentMaxScroll = 0.0f;
    private int hoveredBtn = -1;
    private int activeSlider = -1;
    private final List<Particle> particles = new ArrayList<Particle>();
    private final Random random = new Random();

    private volatile List<ChangelogEntry> changelogEntries;
    private volatile String changelogStatus = "Loading...";
    private float changelogScroll = 0.0f;
    private float changelogMaxScroll = 0.0f;
    private int expandedChangelog = -1;
    private int hoveredChangelogCard = -1;

    private static final List<PluginInfo> AVAILABLE_PLUGINS = List.of(
            new PluginInfo("debug_plugin", "Debug Plugin", "Debug Stuff", "Random Pixel Studios"));
    private static final int PLUGIN_DOWNLOAD_BTN = 2000;
    private static final int PLUGIN_REMOVE_BTN = 2001;
    private int pluginDownloadBtnX = -1;
    private int pluginDownloadBtnY = -1;
    private int pluginDownloadBtnW = 74;
    private int pluginDownloadBtnH = 20;
    private boolean pluginDownloadEnabled;
    private int pluginRemoveBtnX = -1;
    private int pluginRemoveBtnY = -1;
    private int pluginRemoveBtnW = 74;
    private int pluginRemoveBtnH = 20;

    public XrayModMenu(Screen parent) {
        super(Component.literal("XtoXray"));
        this.parent = parent;
    }

    protected void init() {
        this.panelW = Math.min(this.width - 20, 440);
        this.panelH = Math.min(this.height - 20, 340);
        this.panelX = (this.width - this.panelW) / 2;
        this.panelY = (this.height - this.panelH) / 2;
        this.contentX = this.panelX + SIDEBAR_W;
        this.contentY = this.panelY + HEADER_H;
        this.contentW = this.panelW - SIDEBAR_W;
        this.contentH = this.panelH - HEADER_H;
        this.hoveredBtn = -1;
        this.activeSlider = -1;
        this.contentScroll = 0.0f;
        this.contentMaxScroll = 0.0f;
        if (this.particles.isEmpty()) {
            for (int i = 0; i < 50; ++i) {
                this.particles.add(new Particle(this.random.nextFloat() * this.panelW, this.random.nextFloat() * this.panelH, (this.random.nextFloat() - 0.5f) * 0.4f, (this.random.nextFloat() * 0.3f + 0.05f) * (this.random.nextBoolean() ? 1 : -1), 0.6f + this.random.nextFloat() * 0.4f, 1.5f + this.random.nextFloat() * 2.5f, this.panelW, this.panelH));
            }
        }
        if (this.updateStatus == null) {
            this.checkForUpdates();
        }
        if (this.changelogEntries == null) {
            this.fetchChangelog();
        }
    }

    private void checkForUpdates() {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder().uri(MODRINTH_API).header("User-Agent", "xtoxray/" + XtoXray.VERSION).GET().build();
        client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenAccept(response -> {
            if (response.statusCode() == 200) {
                try {
                    Gson gson = new Gson();
                    JsonArray arr = gson.fromJson(response.body(), JsonArray.class);
                    String currentMcVersion = FabricLoader.getInstance().getModContainer("minecraft").get().getMetadata().getVersion().getFriendlyString();
                    String latestNumeric = null;
                    for (int i = 0; i < arr.size(); i++) {
                        JsonObject obj = arr.get(i).getAsJsonObject();
                        String v = obj.get("version_number").getAsString();
                        JsonArray gameArr = obj.get("game_versions").getAsJsonArray();
                        boolean matches = false;
                        for (int j = 0; j < gameArr.size(); j++) {
                            if (gameArr.get(j).getAsString().equals(currentMcVersion)) matches = true;
                        }
                        if (!matches) continue;
                        if (!isNumericVersion(v)) continue;
                        if (latestNumeric == null || compareVersions(v, latestNumeric) > 0) latestNumeric = v;
                    }
                    if (latestNumeric != null && compareVersions(latestNumeric, CURRENT_VERSION) > 0) {
                        this.updateStatus = "New update available!";
                        this.updateStatusColor = PINK;
                    } else {
                        this.updateStatus = "Up to date";
                        this.updateStatusColor = GREEN;
                    }
                } catch (Exception e) {
                    this.updateStatus = null;
                }
            }
        }).exceptionally(e -> {
            this.updateStatus = null;
            return null;
        });
    }

    private void fetchChangelog() {
        final String mcVersion;
        try {
            mcVersion = FabricLoader.getInstance().getModContainer("minecraft").get().getMetadata().getVersion().getFriendlyString();
        } catch (Exception e) { return; }
        final String loaderName;
        try {
            loaderName = FabricLoader.getInstance().getModContainer("fabricloader").get().getMetadata().getVersion().getFriendlyString();
        } catch (Exception e) { return; }

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder().uri(MODRINTH_API).header("User-Agent", "xtoxray/" + XtoXray.VERSION).GET().build();
        client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenAccept(response -> {
            if (response.statusCode() == 200) {
                try {
                    Gson gson = new Gson();
                    JsonArray arr = gson.fromJson(response.body(), JsonArray.class);
                    List<ChangelogEntry> entries = new ArrayList<>();
                    for (int i = 0; i < arr.size(); i++) {
                        JsonObject obj = arr.get(i).getAsJsonObject();
                        String v = obj.get("version_number").getAsString();
                        String cl = obj.has("changelog") && !obj.get("changelog").isJsonNull() ? obj.get("changelog").getAsString() : "";
                        List<String> gameVersions = new ArrayList<>();
                        JsonArray gameArr = obj.get("game_versions").getAsJsonArray();
                        for (int j = 0; j < gameArr.size(); j++) gameVersions.add(gameArr.get(j).getAsString());
                        List<String> loaders = new ArrayList<>();
                        JsonArray loadArr = obj.get("loaders").getAsJsonArray();
                        for (int j = 0; j < loadArr.size(); j++) loaders.add(loadArr.get(j).getAsString());
                        String date = "";
                        if (obj.has("date_published") && !obj.get("date_published").isJsonNull()) {
                            date = obj.get("date_published").getAsString().substring(0, 10);
                        }
                        entries.add(new ChangelogEntry(v, cl, gameVersions, loaders, date));
                    }
                    entries.sort((a, b) -> compareVersions(b.version, a.version));
                    ChangelogEntry current = new ChangelogEntry(CURRENT_VERSION, "", List.of(mcVersion), List.of(loaderName), "");
                    if (!entries.contains(current)) entries.add(0, current);
                    this.changelogEntries = entries;
                    this.changelogStatus = null;
                } catch (Exception e) {
                    this.changelogStatus = "Failed to load";
                }
            } else {
                this.changelogStatus = "Connection failed";
            }
        }).exceptionally(e -> {
            this.changelogStatus = "No internet";
            return null;
        });
    }

    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        super.extractRenderState(g, mouseX, mouseY, delta);
        g.fill(0, 0, this.width, this.height, -872415232);
        for (Particle p : this.particles) {
            p.update(delta);
            int px = this.panelX + (int) p.x;
            int py = this.panelY + (int) p.y;
            int size = (int) p.size;
            int a = (int) (p.alpha * 255.0f);
            g.fill(px, py, px + size, py + size, a << 24 | 0xFFFFFF);
        }
        g.fill(this.panelX, this.panelY, this.panelX + this.panelW, this.panelY + this.panelH, -267514354);
        g.fill(this.panelX, this.panelY, this.panelX + this.panelW, this.panelY + 2, -14013910);
        g.fill(this.panelX, this.panelY + this.panelH - 1, this.panelX + this.panelW, this.panelY + this.panelH, SEPARATOR);
        g.fill(this.panelX, this.panelY, this.panelX + 1, this.panelY + this.panelH, SEPARATOR);
        g.fill(this.panelX + this.panelW - 1, this.panelY, this.panelX + this.panelW, this.panelY + this.panelH, SEPARATOR);
        g.fill(this.panelX, this.panelY + HEADER_H, this.panelX + this.panelW, this.panelY + HEADER_H + 1, SEPARATOR);
        g.fill(this.panelX + SIDEBAR_W, this.panelY, this.panelX + SIDEBAR_W + 1, this.panelY + this.panelH, SEPARATOR);
        g.text(this.font, Component.literal("XtoXray"), this.panelX + 10, this.panelY + 9, TEXT_BRIGHT);
        String ver = "v" + XtoXray.VERSION;
        int verW = this.font.width(ver);
        int verEndX = this.panelX + this.panelW - 8;
        int verY = this.panelY + 9;
        int verX = verEndX - verW;
        g.text(this.font, Component.literal(ver), verX, verY, this.hoveredBtn == 999 ? TEXT_BRIGHT : TEXT_DIM);
        if (this.updateStatus != null) {
            int statusW = this.font.width(this.updateStatus);
            int statusX = verX - 6 - statusW;
            g.text(this.font, Component.literal(this.updateStatus), statusX, verY + 1, this.updateStatusColor);
            boolean hoverUpdate = mouseX >= statusX && mouseX < statusX + statusW && mouseY >= verY && mouseY < verY + 12;
            if (hoverUpdate && this.updateStatusColor == PINK) {
                this.hoveredBtn = 999;
            }
        }
        for (int i = 0; i < CATEGORY_NAMES.length; i++) {
            int itemY = this.panelY + HEADER_H + 2 + i * 22;
            boolean selected = i == this.selectedCategory;
            boolean hovered = i == this.hoveredCategory;
            if (selected) {
                g.fill(this.panelX + 2, itemY, this.panelX + SIDEBAR_W - 2, itemY + 20, -14013910);
            } else if (hovered) {
                g.fill(this.panelX + 2, itemY, this.panelX + SIDEBAR_W - 2, itemY + 20, 0x44FFFFFF);
            }
            int textColor = selected ? TEXT_BRIGHT : TEXT;
            g.text(this.font, Component.literal(CATEGORY_NAMES[i]), this.panelX + 10, itemY + 6, textColor);
        }
        String copyright = "\u00a9 Random Pixel Studios";
        g.text(this.font, Component.literal(copyright), this.panelX + this.panelW - this.font.width(copyright) - 10, this.panelY + this.panelH - 10, TEXT_DIM);
        g.enableScissor(this.contentX, this.contentY, this.contentX + this.contentW, this.contentY + this.contentH);
        this.renderContent(g, mouseX, mouseY);
        g.disableScissor();
    }

    private void renderContent(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        XrayState xray = XrayState.getInstance();
        int cx = this.contentX + 12;
        int baseCY = this.contentY + 10;
        int cy = baseCY - (int) this.contentScroll;
        int btnW = Math.min(this.contentW - 24, 200);
        this.hoveredBtn = -1;
        int totalEnd = baseCY;
        if (this.selectedCategory == 0) {
            this.drawSlider(g, cx, cy, btnW, "Render Distance: " + xray.getOreRenderDistance() + " blocks", (float)(xray.getOreRenderDistance() - 32) / 480.0f, 1, mouseX, mouseY);
            this.whitelistGridY = cy += 32;
            this.renderBlockWhitelistGrid(g, mouseX, mouseY, xray.getWhitelist());
            int slots = xray.getWhitelist().size() + 1;
            int cols = Math.max(4, (this.contentW - 24) / 22);
            totalEnd = this.whitelistGridY + (slots + cols - 1) / cols * 22;
        } else if (this.selectedCategory == 1) {
            this.drawToggleBtn(g, cx, cy, btnW, "Vein Miner", xray.isVeinMiner(), 3, mouseX, mouseY);
            this.drawToggleBtn(g, cx, cy += 26, btnW, "Fortune", xray.isVeinMinerFortune(), 11, mouseX, mouseY);
            this.drawToggleBtn(g, cx, cy += 26, btnW, "Silk Touch", xray.isVeinMinerSilkTouch(), 12, mouseX, mouseY);
            this.drawSlider(g, cx, cy += 30, btnW, "Durability per block: " + xray.getVeinMinerDurability(), (float)xray.getVeinMinerDurability() / 10.0f, 4, mouseX, mouseY);
            this.whitelistGridY = cy += 32;
            this.renderBlockWhitelistGrid(g, mouseX, mouseY, xray.getVeinMinerWhitelist());
            int slots = xray.getVeinMinerWhitelist().size() + 1;
            int cols = Math.max(4, (this.contentW - 24) / 22);
            totalEnd = this.whitelistGridY + (slots + cols - 1) / cols * 22;
        } else if (this.selectedCategory == 2) {
            this.drawToggleBtn(g, cx, cy, btnW, "Container View", xray.isShowContainerView(), 5, mouseX, mouseY);
            this.whitelistGridY = cy += 32;
            this.renderBlockWhitelistGrid(g, mouseX, mouseY, xray.getContainerWhitelist());
            int slots = xray.getContainerWhitelist().size() + 1;
            int cols = Math.max(4, (this.contentW - 24) / 22);
            totalEnd = this.whitelistGridY + (slots + cols - 1) / cols * 22;
        } else if (this.selectedCategory == 3) {
            this.drawToggleBtn(g, cx, cy, btnW, "Show Hitboxes", xray.isShowHitboxes(), 6, mouseX, mouseY);
            this.renderEntityList(g, mouseX, mouseY, cy += 32);
            totalEnd = cy + 32 + COMMON_ENTITIES.length * 18;
        } else if (this.selectedCategory == 4) {
            int keybindBtnW = Math.min(this.contentW - 24, 220);
            for (int i = 0; i < KEYBINDS.size(); i++) {
                this.drawKeybindBtn(g, cx, cy, keybindBtnW, KEYBINDS.get(i), 20 + i, mouseX, mouseY);
                cy += 26;
            }
            totalEnd = baseCY + KEYBINDS.size() * 26;
        } else if (this.selectedCategory == 5) {
            this.renderChangelog(g, mouseX, mouseY);
            totalEnd = baseCY;
        } else if (this.selectedCategory == 6) {
            this.renderPlugins(g, mouseX, mouseY);
            totalEnd = baseCY + 260;
        }
        this.contentMaxScroll = Math.max(0, totalEnd - (this.contentY + this.contentH));
        this.contentScroll = Math.max(0, Math.min(this.contentScroll, this.contentMaxScroll));
    }

    private void renderPlugins(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        XrayState state = XrayState.getInstance();
        int gap = 12;
        int colW = (this.contentW - 24 - gap) / 2;
        int col1X = this.contentX + 12;
        int col2X = col1X + colW + gap;
        int headerY = this.contentY + 10;
        int cardY = headerY + 18;
        int cardH = 90;

        this.pluginDownloadBtnX = -1;
        this.pluginRemoveBtnX = -1;
        this.pluginDownloadEnabled = false;

        g.text(this.font, Component.literal("Installed"), col1X, headerY, TEXT_BRIGHT);
        g.text(this.font, Component.literal("Download"), col2X, headerY, TEXT_BRIGHT);

        boolean installedAny = false;
        for (int i = 0; i < AVAILABLE_PLUGINS.size(); i++) {
            PluginInfo plugin = AVAILABLE_PLUGINS.get(i);
            boolean installed = state.getInstalledPlugins().contains(plugin.id());
            if (installed) {
                installedAny = true;
                this.drawPluginCard(g, col1X, cardY + i * (cardH + 8), colW, cardH, plugin, true, mouseX, mouseY);
            }
            this.drawPluginCard(g, col2X, cardY + i * (cardH + 8), colW, cardH, plugin, false, mouseX, mouseY);
        }
        if (!installedAny) {
            g.text(this.font, Component.literal("No plugins installed"), col1X, cardY + 6, TEXT_DIM);
        }
    }

    private void drawPluginCard(GuiGraphicsExtractor g, int x, int y, int w, int h, PluginInfo plugin, boolean removeMode, int mouseX, int mouseY) {
        boolean hoveredCard = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        int bg = hoveredCard ? -263218772 : -267514354;
        int border = hoveredCard ? -5592406 : SEPARATOR;
        g.fill(x, y, x + w, y + h, bg);
        g.fill(x, y, x + w, y + 1, border);
        g.fill(x, y + h - 1, x + w, y + h, border);
        g.fill(x, y, x + 1, y + h, border);
        g.fill(x + w - 1, y, x + w, y + h, border);

        int textX = x + 8;
        int textY = y + 6;
        g.text(this.font, Component.literal(plugin.name()), textX, textY, TEXT_BRIGHT);
        g.text(this.font, Component.literal(plugin.description()), textX, textY + 12, TEXT);
        g.text(this.font, Component.literal("by " + plugin.creator()), textX, textY + 24, TEXT_DIM);

        boolean installed = XrayState.getInstance().getInstalledPlugins().contains(plugin.id());
        int btnX = x + w - this.pluginDownloadBtnW - 8;
        int btnY = y + h - this.pluginDownloadBtnH - 8;
        int btnW = this.pluginDownloadBtnW;
        int btnH = this.pluginDownloadBtnH;
        boolean hoveredBtn = mouseX >= btnX && mouseX < btnX + btnW && mouseY >= btnY && mouseY < btnY + btnH;

        if (removeMode) {
            String label = "Remove";
            int tw = this.font.width(label);
            int bgBtn = hoveredBtn ? -13391309 : -5635817;
            int borderBtn = hoveredBtn ? TEXT_BRIGHT : SEPARATOR;
            g.fill(btnX, btnY, btnX + btnW, btnY + btnH, bgBtn);
            g.fill(btnX, btnY, btnX + btnW, btnY + 1, borderBtn);
            g.fill(btnX, btnY + btnH - 1, btnX + btnW, btnY + btnH, borderBtn);
            g.fill(btnX, btnY, btnX + 1, btnY + btnH, borderBtn);
            g.fill(btnX + btnW - 1, btnY, btnX + btnW, btnY + btnH, borderBtn);
            g.text(this.font, Component.literal(label), btnX + (btnW - tw) / 2, btnY + 6, TEXT_BRIGHT);
            this.pluginRemoveBtnX = btnX;
            this.pluginRemoveBtnY = btnY;
            if (hoveredBtn) this.hoveredBtn = PLUGIN_REMOVE_BTN;
        } else {
            boolean disabled = installed;
            String label = "Download";
            int tw = this.font.width(label);
            int bgBtn = disabled ? -15329770 : (hoveredBtn ? BTN_BG_HOVER : BTN_BG);
            int borderBtn = disabled ? SEPARATOR : (hoveredBtn ? TEXT : BTN_BORDER);
            g.fill(btnX, btnY, btnX + btnW, btnY + btnH, bgBtn);
            g.fill(btnX, btnY, btnX + btnW, btnY + 1, borderBtn);
            g.fill(btnX, btnY + btnH - 1, btnX + btnW, btnY + btnH, borderBtn);
            g.fill(btnX, btnY, btnX + 1, btnY + btnH, borderBtn);
            g.fill(btnX + btnW - 1, btnY, btnX + btnW, btnY + btnH, borderBtn);
            g.text(this.font, Component.literal(label), btnX + (btnW - tw) / 2, btnY + 6, disabled ? TEXT_DIM : TEXT_BRIGHT);
            this.pluginDownloadBtnX = btnX;
            this.pluginDownloadBtnY = btnY;
            this.pluginDownloadEnabled = !disabled;
            if (hoveredBtn && !disabled) this.hoveredBtn = PLUGIN_DOWNLOAD_BTN;
        }
    }

    private void renderChangelog(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        int cx = this.contentX + 8;
        int cardW = this.contentW - 24;
        int listY = this.contentY + 6;
        int listBottom = this.contentY + this.contentH;

        if (this.changelogStatus != null) {
            g.text(this.font, Component.literal(this.changelogStatus), cx, listY + 4, TEXT_DIM);
            return;
        }
        if (this.changelogEntries == null) return;

        String mcVersion = "";
        String currentLoader = "";
        try { mcVersion = FabricLoader.getInstance().getModContainer("minecraft").get().getMetadata().getVersion().getFriendlyString(); } catch (Exception ignored) {}
        try { currentLoader = FabricLoader.getInstance().getModContainer("fabricloader").get().getMetadata().getVersion().getFriendlyString(); } catch (Exception ignored) {}

        int cardGap = 4;
        int totalH = 0;
        for (int i = 0; i < this.changelogEntries.size(); i++) {
            ChangelogEntry e = this.changelogEntries.get(i);
            int lines = 1;
            if (!e.gameVersions.isEmpty()) lines++;
            if (!e.loaders.isEmpty()) lines++;
            if (!e.datePublished.isEmpty()) lines++;
            if (this.expandedChangelog == i && !e.changelog.isEmpty()) {
                lines += e.changelog.replace("\r", "").split("\n").length;
            }
            totalH += lines * 11 + 10 + cardGap;
        }
        this.changelogMaxScroll = Math.max(0, totalH - (listBottom - listY));
        this.changelogScroll = Math.max(0, Math.min(this.changelogScroll, this.changelogMaxScroll));

        g.enableScissor(cx, listY, cx + cardW + 4, listBottom);
        int cardY = listY - (int) this.changelogScroll;
        this.hoveredChangelogCard = -1;

        for (int i = 0; i < this.changelogEntries.size(); i++) {
            ChangelogEntry entry = this.changelogEntries.get(i);
            boolean isCurrent = entry.version.equals(CURRENT_VERSION) && entry.gameVersions.contains(mcVersion);
            boolean expanded = this.expandedChangelog == i;

            int lines = 1;
            if (!entry.gameVersions.isEmpty()) lines++;
            if (!entry.loaders.isEmpty()) lines++;
            if (!entry.datePublished.isEmpty()) lines++;
            if (expanded && !entry.changelog.isEmpty()) {
                String cl = entry.changelog.replace("\r", "");
                lines += cl.split("\n").length;
            }
            int cardH = lines * 11 + 10;

            if (cardY + cardH < listY) { cardY += cardH + cardGap; continue; }
            if (cardY > listBottom) break;

            boolean hovered = mouseX >= cx && mouseX < cx + cardW && mouseY >= cardY && mouseY < cardY + cardH;
            if (hovered) this.hoveredChangelogCard = i;

            int cardBg = hovered ? -263218772 : -267514354;
            int cardBorder = hovered ? -5592406 : SEPARATOR;

            g.fill(cx, cardY, cx + cardW, cardY + cardH, cardBg);
            g.fill(cx, cardY, cx + cardW, cardY + 1, cardBorder);
            g.fill(cx, cardY + cardH - 1, cx + cardW, cardY + cardH, cardBorder);
            g.fill(cx, cardY, cx + 1, cardY + cardH, cardBorder);
            g.fill(cx + cardW - 1, cardY, cx + cardW, cardY + cardH, cardBorder);

            int textX = cx + 8;
            int textY = cardY + 5;

            String versionText = entry.version;
            g.text(this.font, Component.literal(versionText), textX, textY, isCurrent ? TEXT_BRIGHT : TEXT);
            if (isCurrent) {
                g.text(this.font, Component.literal(" (current)"), textX + this.font.width(versionText), textY, GREEN);
            }

            if (!entry.gameVersions.isEmpty()) {
                StringBuilder gv = new StringBuilder("MC: ");
                for (int j = 0; j < entry.gameVersions.size(); j++) {
                    if (j > 0) gv.append(", ");
                    gv.append(entry.gameVersions.get(j));
                }
                g.text(this.font, Component.literal(gv.toString()), textX, textY + 11, TEXT_DIM);
            }

            if (!entry.loaders.isEmpty()) {
                StringBuilder ld = new StringBuilder("Loader: ");
                for (int j = 0; j < entry.loaders.size(); j++) {
                    if (j > 0) ld.append(", ");
                    ld.append(entry.loaders.get(j));
                }
                g.text(this.font, Component.literal(ld.toString()), textX, textY + 22, TEXT_DIM);
            }

            if (!entry.datePublished.isEmpty()) {
                g.text(this.font, Component.literal(entry.datePublished), textX, textY + 33, TEXT_DIM);
            }

            if (expanded && !entry.changelog.isEmpty()) {
                String[] clLines = entry.changelog.replace("\r", "").split("\n");
                int clY = textY + (entry.datePublished.isEmpty() ? 33 : 44);
                for (String line : clLines) {
                    if (clY + 11 > cardY + cardH) break;
                    g.text(this.font, Component.literal(line), textX + 4, clY, TEXT);
                    clY += 11;
                }
            }

            cardY += cardH + cardGap;
        }
        g.disableScissor();

        if (this.changelogMaxScroll > 0) {
            int scrollX = cx + cardW + 4;
            int scrollH = listBottom - listY;
            float thumbH = Math.max(16, (float) scrollH / (scrollH + this.changelogMaxScroll) * scrollH);
            float thumbY = listY + this.changelogScroll / this.changelogMaxScroll * (scrollH - thumbH);
            g.fill(scrollX, listY, scrollX + 4, listBottom, SEPARATOR);
            g.fill(scrollX, (int) thumbY, scrollX + 4, (int) (thumbY + thumbH), -5592406);
        }
    }

    private void renderBlockWhitelistGrid(GuiGraphicsExtractor g, int mouseX, int mouseY, Set<Block> whitelist) {
        ArrayList<Block> list = new ArrayList<Block>(whitelist);
        this.whitelistCols = Math.max(4, (this.contentW - 24) / 22);
        int gridW = this.whitelistCols * 22;
        this.whitelistGridX = this.contentX + (this.contentW - gridW) / 2;
        int cellStep = 22;
        int totalSlots = list.size() + 1;
        int totalRows = (totalSlots + this.whitelistCols - 1) / this.whitelistCols;
        int totalGridH = totalRows * cellStep;
        int visibleH = this.contentY + this.contentH - 4 - this.whitelistGridY;
        this.whitelistMaxScroll = Math.max(0, totalGridH - visibleH);
        this.whitelistScroll = Math.max(0.0f, Math.min(this.whitelistScroll, this.whitelistMaxScroll));
        g.text(this.font, Component.literal("Whitelist"), this.contentX + 12, this.whitelistGridY - 12, TEXT_DIM);
        for (int i = 0; i < totalSlots; ++i) {
            int col = i % this.whitelistCols;
            int row = i / this.whitelistCols;
            int x = this.whitelistGridX + col * cellStep;
            int y = this.whitelistGridY + row * cellStep - (int) this.whitelistScroll;
            if (y + 20 < this.whitelistGridY || y > this.contentY + this.contentH) continue;
            if (i < list.size()) {
                this.renderBlockCell(g, x, y, list.get(i), mouseX, mouseY);
                continue;
            }
            this.renderAddCell(g, x, y, mouseX, mouseY);
        }
    }

    private void renderEntityList(GuiGraphicsExtractor g, int mouseX, int mouseY, int startY) {
        Set<String> whitelist = XrayState.getInstance().getHitboxEntityWhitelist();
        int btnW = Math.min(this.contentW - 24, 200);
        int cx = this.contentX + 12;
        int rowH = 16;
        int gap = 2;
        int totalH = COMMON_ENTITIES.length * (rowH + gap);
        int visibleH = this.contentY + this.contentH - 4 - startY;
        this.whitelistMaxScroll = Math.max(0, totalH - visibleH);
        this.whitelistScroll = Math.max(0.0f, Math.min(this.whitelistScroll, this.whitelistMaxScroll));
        g.enableScissor(this.contentX, startY, this.contentX + this.contentW, this.contentY + this.contentH);
        for (int i = 0; i < COMMON_ENTITIES.length; ++i) {
            int y = startY + i * (rowH + gap) - (int) this.whitelistScroll;
            if (y + rowH < startY || y > this.contentY + this.contentH) continue;
            String id = COMMON_ENTITIES[i];
            boolean inList = whitelist.contains(id);
            String name = id.substring(id.indexOf(58) + 1).replace('_', ' ');
            boolean hovered = mouseX >= cx && mouseX < cx + btnW && mouseY >= y && mouseY < y + rowH;
            if (hovered) this.hoveredBtn = 100 + i;
            int bg = inList ? -15058406 : BTN_BG;
            int border = inList ? -13391309 : (hovered ? TEXT : BTN_BORDER);
            g.fill(cx, y, cx + btnW, y + rowH, bg);
            g.fill(cx, y, cx + btnW, y + 1, border);
            g.fill(cx, y + rowH - 1, cx + btnW, y + rowH, border);
            g.fill(cx, y, cx + 1, y + rowH, border);
            g.fill(cx + btnW - 1, y, cx + btnW, y + rowH, border);
            String status = inList ? "[ON] " : "[OFF] ";
            g.text(this.font, Component.literal(status + name), cx + 6, y + 4, inList ? TEXT_BRIGHT : TEXT_DIM);
        }
        g.disableScissor();
    }

    private void renderBlockCell(GuiGraphicsExtractor g, int x, int y, Block block, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX < x + 20 && mouseY >= y && mouseY < y + 20;
        int bg = -15066598;
        int border = hovered ? TEXT_BRIGHT : -14013910;
        g.fill(x, y, x + 20, y + 20, bg);
        g.fill(x, y, x + 20, y + 1, border);
        g.fill(x, y + 20 - 1, x + 20, y + 20, border);
        g.fill(x, y, x + 1, y + 20, border);
        g.fill(x + 20 - 1, y, x + 20, y + 20, border);
        ItemStack stack = XrayModMenu.makeStack(block);
        if (!stack.isEmpty()) {
            g.fakeItem(stack, x + 2, y + 2);
        }
    }

    private void renderAddCell(GuiGraphicsExtractor g, int x, int y, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX < x + 20 && mouseY >= y && mouseY < y + 20;
        g.fill(x, y, x + 20, y + 20, -15062502);
        g.fill(x, y, x + 20, y + 1, -14009814);
        g.fill(x, y + 20 - 1, x + 20, y + 20, -14009814);
        g.fill(x, y, x + 1, y + 20, -14009814);
        g.fill(x + 20 - 1, y, x + 20, y + 20, -14009814);
        MutableComponent plus = Component.literal("+");
        int tw = this.font.width((FormattedText) plus);
        g.text(this.font, plus, x + (20 - tw) / 2, y + 6, hovered ? TEXT_BRIGHT : TEXT_DIM);
    }

    private void drawToggleBtn(GuiGraphicsExtractor g, int x, int y, int w, String label, boolean state, int id, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 20;
        if (hovered) this.hoveredBtn = id;
        int bg = state ? -15058406 : -12969446;
        int border = state ? -13391309 : -5622989;
        if (hovered) border = TEXT_BRIGHT;
        g.fill(x, y, x + w, y + 20, bg);
        g.fill(x, y, x + w, y + 1, border);
        g.fill(x, y + 20 - 1, x + w, y + 20, border);
        g.fill(x, y, x + 1, y + 20, border);
        g.fill(x + w - 1, y, x + w, y + 20, border);
        String text = label + ": " + (state ? "ON" : "OFF");
        int tw = this.font.width(text);
        g.text(this.font, Component.literal(text), x + (w - tw) / 2, y + 6, TEXT_BRIGHT);
    }

    private void drawSlider(GuiGraphicsExtractor g, int x, int y, int w, String label, float value, int id, int mouseX, int mouseY) {
        boolean thumbHover = this.activeSlider == id || mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 20;
        if (mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 20) this.hoveredBtn = id;
        g.fill(x, y, x + w, y + 20, -15329770);
        g.fill(x, y, x + w, y + 1, BTN_BORDER);
        g.fill(x, y + 20 - 1, x + w, y + 20, BTN_BORDER);
        g.fill(x, y, x + 1, y + 20, BTN_BORDER);
        g.fill(x + w - 1, y, x + w, y + 20, BTN_BORDER);
        int fillW = (int) (value * (float) (w - 2));
        g.fill(x + 1, y + 1, x + 1 + fillW, y + 20 - 1, -11513776);
        int thumbX = x + 1 + fillW - 2;
        g.fill(thumbX, y + 2, thumbX + 4, y + 20 - 2, thumbHover ? TEXT_BRIGHT : -2236963);
        int tw = this.font.width(label);
        g.text(this.font, Component.literal(label), x + (w - tw) / 2, y + 6, TEXT_BRIGHT);
    }

    private void drawKeybindBtn(GuiGraphicsExtractor g, int x, int y, int w, KeybindEntry entry, int id, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + 20;
        if (hovered) this.hoveredBtn = id;
        int bg = hovered ? BTN_BG_HOVER : BTN_BG;
        int border = hovered ? TEXT : BTN_BORDER;
        g.fill(x, y, x + w, y + 20, bg);
        g.fill(x, y, x + w, y + 1, border);
        g.fill(x, y + 20 - 1, x + w, y + 20, border);
        g.fill(x, y, x + 1, y + 20, border);
        g.fill(x + w - 1, y, x + w, y + 20, border);
        String text = entry.label() + ": [" + entry.mapping().getTranslatedKeyMessage().getString() + "]";
        if (this.awaitingKeybindMapping == entry.mapping()) text = entry.label() + ": [...]";
        int tw = this.font.width(text);
        g.text(this.font, Component.literal(text), x + (w - tw) / 2, y + 6, TEXT_BRIGHT);
    }

    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int mx = (int) event.x();
        int my = (int) event.y();
        if (mx >= this.panelX && mx < this.panelX + SIDEBAR_W && my >= this.panelY + HEADER_H && my < this.panelY + this.panelH) {
            int idx = (my - this.panelY - HEADER_H - 2) / 22;
            if (idx >= 0 && idx < CATEGORY_NAMES.length) {
                this.selectedCategory = idx;
                this.whitelistScroll = 0.0f;
                this.changelogScroll = 0.0f;
                this.contentScroll = 0.0f;
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
                return true;
            }
        }
        if (this.selectedCategory == 5 && this.hoveredChangelogCard >= 0 && event.button() == 0) {
            if (this.expandedChangelog == this.hoveredChangelogCard) {
                this.expandedChangelog = -1;
            } else {
                this.expandedChangelog = this.hoveredChangelogCard;
            }
            return true;
        }
        if (this.hoveredBtn >= 0 && event.button() == 0) {
            if (this.hoveredBtn == PLUGIN_DOWNLOAD_BTN && this.pluginDownloadEnabled) {
                XrayState.getInstance().setInstalledPlugin("debug_plugin", true);
                com.xtoxray.client.DebugLogForwarder.install();
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
                return true;
            }
            if (this.hoveredBtn == PLUGIN_REMOVE_BTN) {
                XrayState.getInstance().setInstalledPlugin("debug_plugin", false);
                com.xtoxray.client.DebugLogForwarder.uninstall();
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
                return true;
            }
            if (this.hoveredBtn == 999) {
                Util.getPlatform().openUri(MODRINTH_URI);
                return true;
            }
            if (this.hoveredBtn == 800) {
                Util.getPlatform().openUri(MODRINTH_URI);
                return true;
            }
            if (this.hoveredBtn == 1 || this.hoveredBtn == 4 || this.hoveredBtn == 9 || this.hoveredBtn == 10) {
                this.activeSlider = this.hoveredBtn;
                int cx = this.contentX + 12;
                int btnW = Math.min(this.contentW - 24, 200);
                float val = (float) Math.max(0.0, Math.min(1.0, (mx - cx) / (double) btnW));
                this.applySlider(this.activeSlider, val);
            } else if (this.hoveredBtn >= 100) {
                int entityIdx = this.hoveredBtn - 100;
                if (entityIdx < COMMON_ENTITIES.length) {
                    String entityId = COMMON_ENTITIES[entityIdx];
                    Set<String> wl = XrayState.getInstance().getHitboxEntityWhitelist();
                    if (wl.contains(entityId)) wl.remove(entityId); else wl.add(entityId);
                    XrayState.getInstance().save();
                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
                }
            } else {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
                this.handleButtonClick(this.hoveredBtn);
            }
            return true;
        }
        if (event.button() == 0 && (this.selectedCategory == 0 || this.selectedCategory == 1 || this.selectedCategory == 2)) {
            Set<Block> whitelist = this.selectedCategory == 0 ? XrayState.getInstance().getWhitelist() : (this.selectedCategory == 1 ? XrayState.getInstance().getVeinMinerWhitelist() : XrayState.getInstance().getContainerWhitelist());
            ArrayList<Block> list = new ArrayList<Block>(whitelist);
            int cellStep = 22;
            for (int i = 0; i <= list.size(); ++i) {
                int col = i % this.whitelistCols;
                int row = i / this.whitelistCols;
                int x = this.whitelistGridX + col * cellStep;
                int y = this.whitelistGridY + row * cellStep - (int) this.whitelistScroll;
                if (mx < x || mx >= x + 20 || my < y || my >= y + 20) continue;
                if (i < list.size()) {
                    whitelist.remove(list.get(i));
                    XrayState.getInstance().save();
                    Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0f));
                } else {
                    int mode = this.selectedCategory == 0 ? 0 : (this.selectedCategory == 1 ? 1 : 2);
                    com.xtoxray.client.VersionAdapter.setScreen(Minecraft.getInstance(), new AddBlockScreen(this, mode));
                }
                return true;
            }
        }
        if (event.button() == 1 && (this.selectedCategory == 0 || this.selectedCategory == 1 || this.selectedCategory == 2)) {
            Set<Block> whitelist = this.selectedCategory == 0 ? XrayState.getInstance().getWhitelist() : (this.selectedCategory == 1 ? XrayState.getInstance().getVeinMinerWhitelist() : XrayState.getInstance().getContainerWhitelist());
            ArrayList<Block> list = new ArrayList<Block>(whitelist);
            int cellStep = 22;
            for (int i = 0; i < list.size(); ++i) {
                int col = i % this.whitelistCols;
                int row = i / this.whitelistCols;
                int x = this.whitelistGridX + col * cellStep;
                int y = this.whitelistGridY + row * cellStep - (int) this.whitelistScroll;
                if (mx < x || mx >= x + 20 || my < y || my >= y + 20) continue;
                com.xtoxray.client.VersionAdapter.setScreen(Minecraft.getInstance(), new ConfirmDeleteScreen(this, list.get(i)));
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private void handleButtonClick(int id) {
        XrayState xray = XrayState.getInstance();
        Minecraft mc = Minecraft.getInstance();
        switch (id) {
            case 3: {
                xray.setVeinMiner(!xray.isVeinMiner());
                if (mc.getConnection() != null) XrayPayloads.sendSyncVeinMiner(xray.isVeinMiner());
                break;
            }
            case 5: { xray.setShowContainerView(!xray.isShowContainerView()); break; }
            case 6: { xray.setShowHitboxes(!xray.isShowHitboxes()); break; }
            case 11: { xray.setVeinMinerFortune(!xray.isVeinMinerFortune()); break; }
            case 12: { xray.setVeinMinerSilkTouch(!xray.isVeinMinerSilkTouch()); break; }
            default: {
                if (id >= 20 && id < 20 + KEYBINDS.size()) this.awaitingKeybindMapping = KEYBINDS.get(id - 20).mapping();
            }
        }
    }

    public boolean mouseDragged(MouseButtonEvent event, double mouseX, double mouseY) {
        if (this.activeSlider >= 0) {
            int cx = this.contentX + 12;
            int btnW = Math.min(this.contentW - 24, 200);
            float val = (float) Math.max(0.0, Math.min(1.0, (mouseX - cx) / (double) btnW));
            this.applySlider(this.activeSlider, val);
            return true;
        }
        return super.mouseDragged(event, mouseX, mouseY);
    }

    public boolean mouseReleased(MouseButtonEvent event) {
        this.activeSlider = -1;
        return super.mouseReleased(event);
    }

    private void applySlider(int id, float value) {
        XrayState xray = XrayState.getInstance();
        switch (id) {
            case 1: { xray.setOreRenderDistance(32 + (int) (value * 480.0f)); break; }
            case 4: { xray.setVeinMinerDurability((int) (value * 10.0f)); break; }
        }
    }

    public void mouseMoved(double mouseX, double mouseY) {
        int mx = (int) mouseX;
        int my = (int) mouseY;
        this.hoveredCategory = -1;
        if (mx >= this.panelX && mx < this.panelX + SIDEBAR_W && my >= this.panelY + HEADER_H && my < this.panelY + this.panelH) {
            int idx = (my - this.panelY - HEADER_H - 2) / 22;
            if (idx >= 0 && idx < CATEGORY_NAMES.length) this.hoveredCategory = idx;
        }
        super.mouseMoved(mouseX, mouseY);
    }

    public boolean keyPressed(KeyEvent event) {
        if (this.awaitingKeybindMapping != null) {
            if (event.isEscape()) { this.awaitingKeybindMapping = null; return true; }
            InputConstants.Key newKey = InputConstants.Type.KEYSYM.getOrCreate(event.key());
            this.awaitingKeybindMapping.setKey(newKey);
            KeyMapping.resetMapping();
            this.awaitingKeybindMapping = null;
            return true;
        }
        return super.keyPressed(event);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (this.selectedCategory == 5 && this.changelogMaxScroll > 0) {
            this.changelogScroll = Math.max(0, Math.min(this.changelogScroll - (float) verticalAmount * 16.0f, this.changelogMaxScroll));
            return true;
        }
        if (this.whitelistMaxScroll > 0.0f) {
            this.whitelistScroll = Math.max(0.0f, Math.min(this.whitelistScroll - (float) verticalAmount * 16.0f, this.whitelistMaxScroll));
            return true;
        }
        if (this.contentMaxScroll > 0.0f) {
            this.contentScroll = Math.max(0.0f, Math.min(this.contentScroll - (float) verticalAmount * 16.0f, this.contentMaxScroll));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    public void onClose() {
        com.xtoxray.client.VersionAdapter.setScreen(Minecraft.getInstance(), this.parent);
    }

    public void removed() {
    }

    public boolean shouldPause() {
        return false;
    }

    static boolean isNumericVersion(String v) {
        for (String p : v.split("\\.")) {
            try { Integer.parseInt(p); } catch (NumberFormatException e) { return false; }
        }
        return true;
    }

    static int compareVersions(String a, String b) {
        String[] partsA = a.split("\\.");
        String[] partsB = b.split("\\.");
        int len = Math.max(partsA.length, partsB.length);
        for (int i = 0; i < len; i++) {
            int numA = i < partsA.length ? parseOr(partsA[i], -1) : 0;
            int numB = i < partsB.length ? parseOr(partsB[i], -1) : 0;
            if (numA != numB) return Integer.compare(numA, numB);
        }
        return 0;
    }

    private static int parseOr(String s, int def) {
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return def; }
    }

    private static ItemStack makeStack(Block block) {
        if (block == Blocks.WATER) return new ItemStack(Items.WATER_BUCKET);
        if (block == Blocks.LAVA) return new ItemStack(Items.LAVA_BUCKET);
        try {
            Item item = block.asItem();
            Optional opt = BuiltInRegistries.ITEM.getResourceKey(item).flatMap(k -> BuiltInRegistries.ITEM.get(k.identifier()));
            if (opt.isPresent() && ((Holder) opt.get()).areComponentsBound()) return new ItemStack((Holder) opt.get(), 1);
            Holder.Reference intrusiveHolder = item.builtInRegistryHolder();
            if (intrusiveHolder.areComponentsBound()) return new ItemStack((Holder) intrusiveHolder, 1);
            return ItemStack.EMPTY;
        } catch (Throwable t) { return ItemStack.EMPTY; }
    }

    private static class Particle {
        float x, y, speedX, speedY, alpha, size, boundW, boundH;
        Particle(float x, float y, float speedX, float speedY, float alpha, float size, float boundW, float boundH) {
            this.x = x; this.y = y; this.speedX = speedX; this.speedY = speedY;
            this.alpha = alpha; this.size = size; this.boundW = boundW; this.boundH = boundH;
        }
        void update(float delta) {
            this.x += this.speedX * delta * 6.0f;
            this.y += this.speedY * delta * 6.0f;
            if (this.x < 0) this.x = this.boundW;
            if (this.x > this.boundW) this.x = 0;
            if (this.y < 0) this.y = this.boundH;
            if (this.y > this.boundH) this.y = 0;
        }
    }

    private record KeybindEntry(String label, KeyMapping mapping) {
    }

    private record PluginInfo(String id, String name, String description, String creator) {
    }

    private static class ChangelogEntry {
        final String version;
        final String changelog;
        final List<String> gameVersions;
        final List<String> loaders;
        final String datePublished;
        ChangelogEntry(String version, String changelog, List<String> gameVersions, List<String> loaders, String datePublished) {
            this.version = version; this.changelog = changelog; this.gameVersions = gameVersions; this.loaders = loaders; this.datePublished = datePublished;
        }
        public boolean equals(Object o) { return o instanceof ChangelogEntry && this.version.equals(((ChangelogEntry) o).version); }
        public int hashCode() { return this.version.hashCode(); }
    }
}
