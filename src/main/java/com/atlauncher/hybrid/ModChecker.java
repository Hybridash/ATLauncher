/*
 * ATLauncher - https://github.com/ATLauncher/ATLauncher
 * Copyright (C) 2026 ATLauncher
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package com.atlauncher.hybrid;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Hybrid: looks at the mods right before launch and warns about
 * <ul>
 * <li>the same mod installed twice,</li>
 * <li>mods that are known not to work together,</li>
 * <li>required mods that seem to be missing.</li>
 * </ul>
 * It only returns warnings; it never blocks the launch.
 */
public final class ModChecker {
    private ModChecker() {
    }

    /** What we could read from one mod jar. */
    public static final class ModInfo {
        public final String file;
        public final String id; // may be null if the jar has no metadata we understand
        public final String name;
        public final List<String> requires = new ArrayList<>();

        ModInfo(String file, String id, String name) {
            this.file = file;
            this.id = id == null ? null : id.toLowerCase(Locale.ROOT);
            this.name = name == null || name.isEmpty() ? file : name;
        }
    }

    private static final class Conflict {
        final List<String> a;
        final List<String> b;
        final String reason;

        Conflict(List<String> a, List<String> b, String reason) {
            this.a = a;
            this.b = b;
            this.reason = reason;
        }
    }

    // Keep this list short and certain: a false alarm teaches players to ignore the warning.
    private static final List<Conflict> KNOWN_CONFLICTS = Arrays.asList(
            new Conflict(Arrays.asList("optifine", "optifabric"), Arrays.asList("sodium"),
                    "both replace the rendering engine"),
            new Conflict(Arrays.asList("optifine", "optifabric"), Arrays.asList("embeddium"),
                    "both replace the rendering engine"),
            new Conflict(Arrays.asList("optifine", "optifabric"), Arrays.asList("rubidium"),
                    "both replace the rendering engine"),
            new Conflict(Arrays.asList("optifine", "optifabric"), Arrays.asList("iris"),
                    "Iris is a replacement for OptiFine shaders"),
            new Conflict(Arrays.asList("optifine", "optifabric"), Arrays.asList("oculus"),
                    "Oculus is a replacement for OptiFine shaders"),
            new Conflict(Arrays.asList("sodium"), Arrays.asList("embeddium"),
                    "Embeddium is a fork of Sodium; use only one"),
            new Conflict(Arrays.asList("sodium"), Arrays.asList("rubidium"),
                    "Rubidium is a fork of Sodium; use only one"),
            new Conflict(Arrays.asList("embeddium"), Arrays.asList("rubidium"),
                    "Embeddium replaces Rubidium; use only one"),
            new Conflict(Arrays.asList("canvas"), Arrays.asList("sodium"), "both replace the rendering engine"));

    // Provided by the loader, the game or Java itself
    private static final Set<String> BUILT_IN = new HashSet<>(Arrays.asList("minecraft", "java", "forge", "neoforge",
            "fabricloader", "fabric", "quilt_loader", "quilt_base", "mixinextras"));

    private static final Pattern TOML_SECTION = Pattern.compile("^\\s*\\[\\[?\\s*([\\w.\\-\"]+)\\s*\\]\\]?");
    private static final Pattern TOML_STRING = Pattern.compile("^\\s*(\\w+)\\s*=\\s*\"([^\"]*)\"");
    private static final Pattern TOML_BOOL = Pattern.compile("^\\s*(\\w+)\\s*=\\s*(true|false)");

    /** Reads every enabled jar in {@code modsDir}. */
    public static List<ModInfo> readMods(Path modsDir) {
        List<ModInfo> mods = new ArrayList<>();
        if (!Files.isDirectory(modsDir)) {
            return mods;
        }
        try (DirectoryStream<Path> jars = Files.newDirectoryStream(modsDir, "*.jar")) {
            for (Path jar : jars) {
                mods.add(readMod(jar));
            }
        } catch (IOException e) {
            // unreadable folder: nothing to check
        }
        return mods;
    }

    static ModInfo readMod(Path jar) {
        String file = jar.getFileName().toString();
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            ZipEntry fabric = zip.getEntry("fabric.mod.json");
            if (fabric != null) {
                return readFabric(file, zip, fabric, "depends");
            }
            ZipEntry quilt = zip.getEntry("quilt.mod.json");
            if (quilt != null) {
                return readQuilt(file, zip, quilt);
            }
            ZipEntry toml = zip.getEntry("META-INF/neoforge.mods.toml");
            if (toml == null) {
                toml = zip.getEntry("META-INF/mods.toml");
            }
            if (toml != null) {
                return readToml(file, read(zip, toml));
            }
        } catch (Exception e) {
            // not a readable jar; still counts as a mod file
        }
        return new ModInfo(file, null, null);
    }

    private static String read(ZipFile zip, ZipEntry entry) throws IOException {
        try (InputStream in = zip.getInputStream(entry)) {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int n;
            while ((n = in.read(buffer)) > 0) {
                out.write(buffer, 0, n);
            }
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static ModInfo readFabric(String file, ZipFile zip, ZipEntry entry, String dependsKey)
            throws IOException {
        try (Reader reader = new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8)) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            ModInfo mod = new ModInfo(file, string(json, "id"), string(json, "name"));
            if (json.has(dependsKey) && json.get(dependsKey).isJsonObject()) {
                for (String dep : json.getAsJsonObject(dependsKey).keySet()) {
                    // fabric-* are Fabric API modules, which come bundled in Fabric API
                    if (!dep.startsWith("fabric-")) {
                        mod.requires.add(dep.toLowerCase(Locale.ROOT));
                    }
                }
            }
            return mod;
        }
    }

    private static ModInfo readQuilt(String file, ZipFile zip, ZipEntry entry) throws IOException {
        try (Reader reader = new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8)) {
            JsonObject loader = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("quilt_loader");
            JsonObject metadata = loader.has("metadata") ? loader.getAsJsonObject("metadata") : new JsonObject();
            ModInfo mod = new ModInfo(file, string(loader, "id"), string(metadata, "name"));
            if (loader.has("depends") && loader.get("depends").isJsonArray()) {
                for (JsonElement dep : loader.getAsJsonArray("depends")) {
                    String id = null;
                    if (dep.isJsonPrimitive()) {
                        id = dep.getAsString();
                    } else if (dep.isJsonObject()) {
                        JsonObject obj = dep.getAsJsonObject();
                        if (obj.has("optional") && obj.get("optional").getAsBoolean()) {
                            continue;
                        }
                        id = string(obj, "id");
                    }
                    if (id != null && !id.startsWith("quilt_") && !id.startsWith("fabric-")) {
                        mod.requires.add(id.toLowerCase(Locale.ROOT));
                    }
                }
            }
            return mod;
        }
    }

    /** Minimal reader for Forge / NeoForge mods.toml: first [[mods]] id and required [[dependencies.x]]. */
    static ModInfo readToml(String file, String toml) {
        String section = "";
        String id = null;
        String name = null;
        boolean inFirstMod = false;
        boolean seenMod = false;

        String depId = null;
        boolean depRequired = false;
        List<String> requires = new ArrayList<>();

        for (String line : toml.split("\\r?\\n")) {
            Matcher sec = TOML_SECTION.matcher(line);
            if (sec.find() && line.trim().startsWith("[")) {
                if (section.startsWith("dependencies") && depId != null && depRequired) {
                    requires.add(depId);
                }
                depId = null;
                depRequired = false;
                section = sec.group(1).replace("\"", "");
                inFirstMod = section.equals("mods") && !seenMod;
                if (section.equals("mods")) {
                    seenMod = true;
                }
                continue;
            }
            Matcher str = TOML_STRING.matcher(line);
            if (str.find()) {
                String key = str.group(1);
                String value = str.group(2);
                if (inFirstMod && key.equals("modId") && id == null) {
                    id = value;
                } else if (inFirstMod && key.equals("displayName") && name == null) {
                    name = value;
                } else if (section.startsWith("dependencies") && key.equals("modId")) {
                    depId = value;
                } else if (section.startsWith("dependencies") && key.equals("type")) {
                    depRequired = value.equalsIgnoreCase("required");
                }
                continue;
            }
            Matcher bool = TOML_BOOL.matcher(line);
            if (bool.find() && section.startsWith("dependencies") && bool.group(1).equals("mandatory")) {
                depRequired = Boolean.parseBoolean(bool.group(2));
            }
        }
        if (section.startsWith("dependencies") && depId != null && depRequired) {
            requires.add(depId);
        }

        ModInfo mod = new ModInfo(file, id, name);
        for (String dep : requires) {
            String lower = dep.toLowerCase(Locale.ROOT);
            if (!lower.equals(mod.id)) {
                mod.requires.add(lower);
            }
        }
        return mod;
    }

    private static String string(JsonObject json, String key) {
        return json != null && json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsString() : null;
    }

    private static boolean matches(ModInfo mod, List<String> needles) {
        String file = mod.file.toLowerCase(Locale.ROOT);
        for (String needle : needles) {
            if (needle.equals(mod.id) || (mod.id == null && file.contains(needle))
                    || (needle.equals("optifine") && file.contains("optifine"))) {
                return true;
            }
        }
        return false;
    }

    /** All problems found, as readable sentences. Empty when everything looks fine. */
    public static List<String> check(List<ModInfo> mods) {
        List<String> problems = new ArrayList<>();

        // 1. the same mod twice
        Map<String, List<ModInfo>> byId = new LinkedHashMap<>();
        for (ModInfo mod : mods) {
            if (mod.id != null) {
                byId.computeIfAbsent(mod.id, k -> new ArrayList<>()).add(mod);
            }
        }
        for (List<ModInfo> same : byId.values()) {
            if (same.size() > 1) {
                List<String> files = new ArrayList<>();
                for (ModInfo mod : same) {
                    files.add(mod.file);
                }
                problems.add(same.get(0).name + " is installed " + same.size() + " times (" + String.join(", ", files)
                        + "). Remove all but one.");
            }
        }

        // 2. known conflicts
        for (Conflict conflict : KNOWN_CONFLICTS) {
            ModInfo first = null;
            ModInfo second = null;
            for (ModInfo mod : mods) {
                if (first == null && matches(mod, conflict.a)) {
                    first = mod;
                } else if (second == null && matches(mod, conflict.b)) {
                    second = mod;
                }
            }
            if (first != null && second != null) {
                problems.add(first.name + " and " + second.name + " don't work together: " + conflict.reason + ".");
            }
        }

        // 3. missing dependencies
        Set<String> installed = new HashSet<>(byId.keySet());
        for (ModInfo mod : mods) {
            List<String> missing = new ArrayList<>();
            for (String dep : mod.requires) {
                if (!installed.contains(dep) && !BUILT_IN.contains(dep)) {
                    missing.add(dep);
                }
            }
            if (!missing.isEmpty()) {
                problems.add(mod.name + " needs " + String.join(", ", missing)
                        + ", which doesn't seem to be installed (or is disabled).");
            }
        }
        return problems;
    }
}
