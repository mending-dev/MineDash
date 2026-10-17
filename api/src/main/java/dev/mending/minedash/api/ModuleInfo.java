package dev.mending.minedash.api;

import java.util.ArrayList;
import java.util.List;

/** Content of module.json. Filled by Gson, so the fields must stay non-final. */
public class ModuleInfo {

    private String id;
    private String name;
    private String version;
    private String description;
    private String main;
    private int apiVersion;
    private String frontend;
    private String icon;
    private List<String> authors = new ArrayList<>();
    private List<String> dependencies = new ArrayList<>();
    private List<WidgetInfo> widgets = new ArrayList<>();

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getVersion() {
        return version;
    }

    public String getDescription() {
        return description == null ? "" : description;
    }

    public String getMain() {
        return main;
    }

    public int getApiVersion() {
        return apiVersion;
    }

    /** Path of the frontend entry file inside the "web" folder of the module jar, or null. */
    public String getFrontend() {
        return frontend == null || frontend.trim().isEmpty() ? null : frontend.trim();
    }

    /**
     * Icon of the module: the path of an SVG file inside the module jar (e.g. "icon.svg"), or the SVG
     * markup itself, starting with "&lt;svg". Null if the module sets none. The core then looks for
     * "icon.svg" at the root of the jar.
     */
    public String getIcon() {
        return icon == null || icon.trim().isEmpty() ? null : icon.trim();
    }

    public List<String> getAuthors() {
        return authors == null ? new ArrayList<String>() : authors;
    }

    /** Ids of modules that must be enabled before this one. */
    public List<String> getDependencies() {
        return dependencies == null ? new ArrayList<String>() : dependencies;
    }

    /** Overview widgets of this module. Requires a frontend. */
    public List<WidgetInfo> getWidgets() {
        return widgets == null ? new ArrayList<WidgetInfo>() : widgets;
    }
}