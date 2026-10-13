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
    private List<String> authors = new ArrayList<>();
    private List<String> dependencies = new ArrayList<>();

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

    public List<String> getAuthors() {
        return authors == null ? new ArrayList<String>() : authors;
    }

    /** Ids of modules that must be enabled before this one. */
    public List<String> getDependencies() {
        return dependencies == null ? new ArrayList<String>() : dependencies;
    }
}