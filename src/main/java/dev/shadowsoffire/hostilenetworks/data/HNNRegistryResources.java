package dev.shadowsoffire.hostilenetworks.data;

import java.io.IOException;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;

import dev.shadowsoffire.hostilenetworks.HostileNetworks;
import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

/** Loads the original HNN datapack layout alongside Placebo's namespaced registry layout. */
final class HNNRegistryResources {

    private HNNRegistryResources() {}

    static void addLegacyFiles(Map<Identifier, JsonElement> files, ResourceManager manager, String folder) {
        FileToIdConverter converter = FileToIdConverter.json(folder);
        converter.listMatchingResources(manager).forEach((location, resource) -> {
            Identifier id = converter.fileToId(location);
            // An entry in Placebo's namespaced layout takes precedence over its old-format equivalent.
            if (files.containsKey(id)) return;
            try (var reader = resource.openAsReader()) {
                files.put(id, JsonParser.parseReader(reader));
            }
            catch (JsonParseException | IOException ex) {
                HostileNetworks.LOGGER.error("Couldn't parse {} file {}", folder, location, ex);
            }
        });
    }
}
