package dev.mending.minedash.api;

/** A dashboard widget declared in module.json. Its UI is mounted by the module frontend. */
public class WidgetInfo {

    private String id;
    private String title;
    private String size;
    private String permission;

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    /** "small", "medium" or "large". Defaults to "small". */
    public String getSize() {
        return size == null || size.trim().isEmpty() ? "small" : size.trim();
    }

    /** Permission needed to see the widget, or null if any user may see it. */
    public String getPermission() {
        return permission == null || permission.trim().isEmpty() ? null : permission.trim();
    }
}