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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Hybrid: keeps keybinds and the multiplayer server list the same in every instance.
 *
 * Before launch ({@link #pull}) the shared keybinds and servers.dat are copied into the instance. After the game
 * closes normally ({@link #push}) the instance's keybinds and servers.dat are saved back. Only keybind lines of
 * options.txt are shared, so video and sound settings stay per instance.
 */
public final class SharedConfigSync {
    private static final String OPTIONS = "options.txt";
    private static final String SERVERS = "servers.dat";

    private SharedConfigSync() {
    }

    private static boolean isKeybind(String line) {
        return line.startsWith("key_");
    }

    private static String keyOf(String line) {
        int colon = line.indexOf(':');
        return colon < 0 ? line : line.substring(0, colon);
    }

    private static List<String> readLines(Path path) throws IOException {
        if (!Files.isRegularFile(path)) {
            return new ArrayList<>();
        }
        return Files.readAllLines(path, StandardCharsets.UTF_8);
    }

    /** Replaces keybind lines of {@code target} with those in {@code source}. Returns how many changed. */
    static int mergeKeybinds(Path source, Path target) throws IOException {
        Map<String, String> keybinds = new LinkedHashMap<>();
        for (String line : readLines(source)) {
            if (isKeybind(line)) {
                keybinds.put(keyOf(line), line);
            }
        }
        if (keybinds.isEmpty()) {
            return 0;
        }

        int changed = 0;
        List<String> result = new ArrayList<>();
        for (String line : readLines(target)) {
            if (isKeybind(line) && keybinds.containsKey(keyOf(line))) {
                String replacement = keybinds.remove(keyOf(line));
                if (!replacement.equals(line)) {
                    changed++;
                }
                result.add(replacement);
            } else {
                result.add(line);
            }
        }
        // keybinds the target doesn't have yet (new instance, or a mod added keys)
        for (String line : keybinds.values()) {
            result.add(line);
            changed++;
        }
        Files.createDirectories(target.getParent());
        Files.write(target, result, StandardCharsets.UTF_8);
        return changed;
    }

    /** Copies shared keybinds and servers into the instance. Returns a line for the log. */
    public static String pull(Path sharedDir, Path gameDir) throws IOException {
        Path sharedOptions = sharedDir.resolve(OPTIONS);
        Path sharedServers = sharedDir.resolve(SERVERS);
        if (!Files.exists(sharedOptions) && !Files.exists(sharedServers)) {
            return "Shared settings: nothing saved yet. Your keybinds and servers will be shared after this game closes.";
        }
        int changed = mergeKeybinds(sharedOptions, gameDir.resolve(OPTIONS));
        if (Files.exists(sharedServers)) {
            Files.copy(sharedServers, gameDir.resolve(SERVERS), StandardCopyOption.REPLACE_EXISTING);
        }
        return "Shared settings: applied " + changed + " keybind change(s) and the shared server list.";
    }

    /** Saves the instance's keybinds and servers for the other instances. Returns a line for the log. */
    public static String push(Path sharedDir, Path gameDir) throws IOException {
        Files.createDirectories(sharedDir);
        Path gameOptions = gameDir.resolve(OPTIONS);
        if (Files.exists(gameOptions)) {
            // merge (not overwrite) so keybinds of mods that only other instances have are kept
            mergeKeybinds(gameOptions, sharedDir.resolve(OPTIONS));
        }
        Path gameServers = gameDir.resolve(SERVERS);
        if (Files.exists(gameServers)) {
            Files.copy(gameServers, sharedDir.resolve(SERVERS), StandardCopyOption.REPLACE_EXISTING);
        }
        return "Shared settings: saved your keybinds and server list for other instances.";
    }
}
