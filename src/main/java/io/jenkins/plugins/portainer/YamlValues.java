package io.jenkins.plugins.portainer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/** Parse and merge Helm values YAML. Never log the input. */
final class YamlValues {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private YamlValues() {
    }

    static Map<String, Object> parseToMap(String yamlContent) {
        if (yamlContent == null || yamlContent.isBlank()) {
            throw new IllegalArgumentException("Values YAML is empty.");
        }
        final Object loaded;
        try {
            LoaderOptions options = new LoaderOptions();
            Yaml yaml = new Yaml(new SafeConstructor(options));
            loaded = yaml.load(yamlContent);
        } catch (YAMLException e) {
            String msg = e.getMessage();
            if (msg == null || msg.isBlank()) {
                msg = e.getClass().getSimpleName();
            }
            throw new IllegalArgumentException("Invalid values YAML: " + msg.replaceAll("\\s+", " ").trim());
        }
        if (loaded == null) {
            throw new IllegalArgumentException("Values YAML is empty.");
        }
        if (!(loaded instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException("Values YAML must be a mapping (key: value).");
        }
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> e : map.entrySet()) {
            if (e.getKey() == null) {
                continue;
            }
            out.put(String.valueOf(e.getKey()), e.getValue());
        }
        return Collections.unmodifiableMap(out);
    }

    static JsonNode toJsonNode(String yamlContent) {
        return MAPPER.valueToTree(parseToMap(yamlContent));
    }

    /**
     * Deep-merge Helm values maps. Overlay wins on the same key (Helm {@code -f overlay} semantics):
     * nested mappings recurse; scalars and lists replace the base value entirely.
     * Null overlay → base; null base → overlay.
     */
    static JsonNode merge(JsonNode base, JsonNode overlay) {
        if (overlay == null || overlay.isNull()) {
            return base;
        }
        if (base == null || base.isNull()) {
            return overlay;
        }
        if (!base.isObject() || !overlay.isObject()) {
            return overlay;
        }
        ObjectNode out = ((ObjectNode) base).deepCopy();
        Iterator<Map.Entry<String, JsonNode>> fields = overlay.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> e = fields.next();
            String key = e.getKey();
            JsonNode ov = e.getValue();
            JsonNode cur = out.get(key);
            if (cur != null && cur.isObject() && ov != null && ov.isObject()) {
                out.set(key, merge(cur, ov));
            } else {
                out.set(key, ov == null ? null : ov.deepCopy());
            }
        }
        return out;
    }

    /** YAML string for Portainer {@code values}. Empty mapping → {@code null} (omit the key). */
    static String toYaml(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode() || (node.isObject() && node.isEmpty())) {
            return null;
        }
        Object value = MAPPER.convertValue(node, MAP_TYPE);
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        String dumped = new Yaml(options).dump(value);
        if (dumped == null) {
            return null;
        }
        dumped = dumped.stripTrailing();
        return dumped.isBlank() ? null : dumped;
    }
}
