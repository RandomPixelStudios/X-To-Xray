package com.xtoxray.hitboxes;

public class HitboxesState {
    public static final int CATEGORY_PLAYERS = 0;
    public static final int CATEGORY_HOSTILE = 1;
    public static final int CATEGORY_FRIENDLY = 2;
    public static final int CATEGORY_OTHER = 3;
    public static final int CATEGORY_COUNT = 4;

    private static final int[] PALETTE = {
        0xFFFF0000,
        0xFFFF8800,
        0xFFFFFF00,
        0xFF00FF00,
        0xFF00FFFF,
        0xFF0088FF,
        0xFF0000FF,
        0xFF8800FF,
        0xFFFF00FF,
        0xFFFFFFFF
    };
    private static final String[] CATEGORY_NAMES = {
        "Players",
        "Hostile Mobs",
        "Friendly Mobs",
        "Other"
    };
    private static final HitboxesState INSTANCE = new HitboxesState();

    private boolean enabled = true;
    private boolean seeThroughWalls = true;
    private int maxDistance = 64;
    private final boolean[] shown = new boolean[CATEGORY_COUNT];
    private final int[] colors = new int[CATEGORY_COUNT];

    private HitboxesState() {
        for (int i = 0; i < CATEGORY_COUNT; i++) {
            shown[i] = true;
            colors[i] = 0xFFFF0000;
        }
    }

    public static HitboxesState getInstance() {
        return INSTANCE;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isSeeThroughWalls() {
        return seeThroughWalls;
    }

    public void setSeeThroughWalls(boolean seeThroughWalls) {
        this.seeThroughWalls = seeThroughWalls;
    }

    public int getMaxDistance() {
        return maxDistance;
    }

    public void setMaxDistance(int maxDistance) {
        this.maxDistance = Math.max(8, Math.min(256, maxDistance));
    }

    public boolean isShown(int category) {
        return shown[category];
    }

    public void setShown(int category, boolean show) {
        shown[category] = show;
    }

    public int getColor(int category) {
        return colors[category];
    }

    public void setColor(int category, int color) {
        colors[category] = color;
    }

    public String getCategoryName(int category) {
        return CATEGORY_NAMES[category];
    }

    public String getColorName(int category) {
        int c = colors[category] & 0xFFFFFF;
        int closest = 0;
        int bestDist = Integer.MAX_VALUE;
        for (int i = 0; i < PALETTE.length; i++) {
            int p = PALETTE[i] & 0xFFFFFF;
            int dr = (p >> 16 & 0xFF) - (c >> 16 & 0xFF);
            int dg = (p >> 8 & 0xFF) - (c >> 8 & 0xFF);
            int db = (p & 0xFF) - (c & 0xFF);
            int dist = dr * dr + dg * dg + db * db;
            if (dist < bestDist) {
                bestDist = dist;
                closest = i;
            }
        }
        return switch (closest) {
            case 0 -> "Red";
            case 1 -> "Orange";
            case 2 -> "Yellow";
            case 3 -> "Green";
            case 4 -> "Cyan";
            case 5 -> "Blue";
            case 6 -> "Dark Blue";
            case 7 -> "Purple";
            case 8 -> "Pink";
            default -> "White";
        };
    }

    public void cycleColor(int category) {
        int c = colors[category] & 0xFFFFFF;
        int closest = 0;
        int bestDist = Integer.MAX_VALUE;
        for (int i = 0; i < PALETTE.length; i++) {
            int p = PALETTE[i] & 0xFFFFFF;
            int dr = (p >> 16 & 0xFF) - (c >> 16 & 0xFF);
            int dg = (p >> 8 & 0xFF) - (c >> 8 & 0xFF);
            int db = (p & 0xFF) - (c & 0xFF);
            int dist = dr * dr + dg * dg + db * db;
            if (dist < bestDist) {
                bestDist = dist;
                closest = i;
            }
        }
        int next = (closest + 1) % PALETTE.length;
        colors[category] = PALETTE[next];
    }

    public void resetToDefault() {
        this.enabled = true;
        this.seeThroughWalls = true;
        this.maxDistance = 64;
        for (int i = 0; i < CATEGORY_COUNT; i++) {
            shown[i] = true;
            colors[i] = 0xFFFF0000;
        }
    }
}
