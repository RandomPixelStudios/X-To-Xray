package com.xtoxray.client.news;

import java.util.ArrayList;
import java.util.List;

public class NewsEntry {
    private String title = "";
    private List<String> lines = new ArrayList<>();
    private int color = 0x55FFFF;
    private String url = "";
    private String image = "";

    public NewsEntry() {
    }

    public NewsEntry(String title, List<String> lines, int color, String url) {
        this(title, lines, color, url, "");
    }

    public NewsEntry(String title, List<String> lines, int color, String url, String image) {
        this.title = title;
        this.lines = new ArrayList<>(lines);
        this.color = color;
        this.url = url == null ? "" : url;
        this.image = image == null ? "" : image;
    }

    public String getTitle() {
        return title == null ? "" : title;
    }

    public List<String> getLines() {
        return lines == null ? List.of() : lines;
    }

    public int getColor() {
        return color;
    }

    public String getUrl() {
        return url == null ? "" : url;
    }

    public boolean hasUrl() {
        return url != null && !url.isBlank();
    }

    public String getImage() {
        return image == null ? "" : image;
    }

    public void setImage(String image) {
        this.image = image == null ? "" : image;
    }

    public boolean hasImage() {
        return image != null && !image.isBlank();
    }
}
