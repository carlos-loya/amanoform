package com.amanoform.state;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages the Amanoform infrastructure state file.
 *
 * <p>Tracks the current state of manually provisioned infrastructure.
 * State is persisted to a local JSON file, as is tradition.</p>
 *
 * <p>The state file includes resource identifiers, configuration attributes,
 * and browser session metadata for drift detection via visual inspection.</p>
 *
 * <p>In Python, this class was 57 lines. The Java equivalent you are now
 * reading is approximately three times that length, primarily due to the
 * Gson type token ceremony, checked IOException handling, and the fact
 * that Java's type system requires us to write
 * {@code Map<String, Map<String, Object>>} instead of {@code dict}.</p>
 *
 * @author Carlos Loya
 * @version 0.1.0
 */
public class AmanoformInfrastructureStateManager {

    private static final String STATE_FILE = "amanoform.state";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type STATE_TYPE = new TypeToken<Map<String, Object>>() {}.getType();

    private final Map<String, Object> data;

    /**
     * Construct a new state manager with the given data.
     *
     * @param data the state data, or {@code null} for a fresh state
     */
    @SuppressWarnings("unchecked")
    public AmanoformInfrastructureStateManager(Map<String, Object> data) {
        if (data != null) {
            this.data = data;
        } else {
            this.data = new HashMap<>();
            this.data.put("version", 1.0);
            this.data.put("serial", 0.0);
            this.data.put("provider", "aws-console-manual");
            this.data.put("resources", new HashMap<String, Object>());
        }
    }

    /**
     * Load state from the state file, or create a fresh state.
     *
     * @return a new state manager instance
     */
    public static AmanoformInfrastructureStateManager load() {
        Path path = Path.of(STATE_FILE);
        if (Files.exists(path)) {
            try {
                String json = Files.readString(path);
                Map<String, Object> data = GSON.fromJson(json, STATE_TYPE);
                return new AmanoformInfrastructureStateManager(data);
            } catch (IOException e) {
                throw new RuntimeException("Failed to read state file: " + e.getMessage(), e);
            }
        }
        return new AmanoformInfrastructureStateManager(null);
    }

    /**
     * Persist state to disk.
     *
     * <p>Increments the serial number on each save, providing a basic
     * form of optimistic concurrency control. Not that concurrent access
     * is likely — this tool operates one browser at a time.</p>
     */
    public void save() {
        double serial = ((Number) data.getOrDefault("serial", 0.0)).doubleValue();
        data.put("serial", serial + 1);

        try {
            String json = GSON.toJson(data) + "\n";
            Files.writeString(Path.of(STATE_FILE), json);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write state file: " + e.getMessage(), e);
        }
    }

    /**
     * Retrieve a resource's state by its key.
     *
     * @param key the resource key (e.g., "af_ec2_instance.web")
     * @return the resource state map, or {@code null} if not found
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getResource(String key) {
        Map<String, Object> resources = getResources();
        Object resource = resources.get(key);
        if (resource instanceof Map) {
            return (Map<String, Object>) resource;
        }
        return null;
    }

    /**
     * Store or update a resource's state.
     *
     * @param key the resource key
     * @param attrs the resource attributes to store
     */
    public void setResource(String key, Map<String, Object> attrs) {
        getResources().put(key, attrs);
    }

    /**
     * Remove a resource from state.
     *
     * @param key the resource key to remove
     */
    public void removeResource(String key) {
        getResources().remove(key);
    }

    /**
     * List all tracked resource keys.
     *
     * @return a list of resource key strings
     */
    public List<String> listResources() {
        return new ArrayList<>(getResources().keySet());
    }

    /**
     * Get the resources map from the state data.
     *
     * @return the mutable resources map
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> getResources() {
        return (Map<String, Object>) data.computeIfAbsent("resources",
                k -> new HashMap<String, Object>());
    }
}
