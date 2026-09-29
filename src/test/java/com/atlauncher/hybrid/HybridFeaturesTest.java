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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class HybridFeaturesTest {
    @TempDir
    Path temp;

    private static void write(Path path, String text) throws IOException {
        Files.createDirectories(path.getParent());
        Files.write(path, text.getBytes(StandardCharsets.UTF_8));
    }

    private static void jar(Path path, String entry, String content) throws IOException {
        Files.createDirectories(path.getParent());
        try (OutputStream out = Files.newOutputStream(path); ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry(entry));
            zip.write(content.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
    }

    @Test
    public void worldBackupPruneAndRestore() throws IOException {
        Path instance = temp.resolve("instance");
        Path world = instance.resolve("saves").resolve("My World");
        write(world.resolve("level.dat"), "original");
        write(world.resolve("region/r.0.0.mca"), "blocks");
        write(world.resolve("session.lock"), "lock");

        List<String> log = WorldBackup.backupChangedWorlds(instance, 2);
        assertEquals(1, log.size());
        assertEquals(1, WorldBackup.listBackups(instance, "My World").size());
        assertFalse(WorldBackup.needsBackup(world, instance));

        for (int i = 0; i < 3; i++) {
            WorldBackup.backupWorld(world, instance, 2);
        }
        assertEquals(2, WorldBackup.listBackups(instance, "My World").size());

        Path backup = WorldBackup.listBackups(instance, "My World").get(0);
        write(world.resolve("level.dat"), "griefed");
        WorldBackup.restoreBackup(backup, instance, "My World");

        assertEquals("original", new String(Files.readAllBytes(world.resolve("level.dat")), StandardCharsets.UTF_8));
        assertTrue(Files.exists(world.resolve("region/r.0.0.mca")));
        assertFalse(Files.exists(world.resolve("session.lock")));
        try (Stream<Path> saves = Files.list(instance.resolve("saves"))) {
            assertEquals(2, saves.count()); // the old world is kept aside
        }
    }

    @Test
    public void modCheckerFindsProblems() throws IOException {
        Path mods = temp.resolve("mods");
        jar(mods.resolve("sodium-a.jar"), "fabric.mod.json", "{\"id\":\"sodium\",\"name\":\"Sodium\"}");
        jar(mods.resolve("sodium-b.jar"), "fabric.mod.json", "{\"id\":\"sodium\",\"name\":\"Sodium\"}");
        jar(mods.resolve("OptiFine_1.20.jar"), "notes.txt", "optifine");
        jar(mods.resolve("extra.jar"), "fabric.mod.json",
                "{\"id\":\"sodium-extra\",\"name\":\"Sodium Extra\",\"depends\":{\"fabricloader\":\"*\",\"fabric-api-base\":\"*\",\"reeses-sodium-options\":\"*\",\"sodium\":\"*\"}}");
        jar(mods.resolve("mowzie.jar"), "META-INF/mods.toml",
                "modLoader=\"javafml\"\n[[mods]]\nmodId=\"mowziesmobs\"\ndisplayName=\"Mowzie's Mobs\"\n"
                        + "[[dependencies.mowziesmobs]]\n    modId=\"forge\"\n    mandatory=true\n"
                        + "[[dependencies.mowziesmobs]]\n    modId=\"geckolib\"\n    mandatory=true\n"
                        + "[[dependencies.mowziesmobs]]\n    modId=\"jei\"\n    mandatory=false\n");

        List<String> problems = ModChecker.check(ModChecker.readMods(mods));
        String all = String.join("\n", problems);
        assertTrue(all.contains("Sodium is installed 2 times"), all);
        assertTrue(all.contains("don't work together"), all);
        assertTrue(all.contains("Sodium Extra needs reeses-sodium-options"), all);
        assertTrue(all.contains("Mowzie's Mobs needs geckolib"), all);
        assertFalse(all.contains("jei"), all);
        assertFalse(all.contains("fabricloader"), all);
    }

    @Test
    public void sharedKeybindsAndServers() throws IOException {
        Path shared = temp.resolve("shared");
        Path a = temp.resolve("a");
        Path b = temp.resolve("b");
        write(a.resolve("options.txt"), "gamma:1.0\nkey_key.jump:key.keyboard.x\n");
        write(a.resolve("servers.dat"), "servers");
        write(b.resolve("options.txt"), "gamma:0.5\nkey_key.jump:key.keyboard.space\n");

        SharedConfigSync.push(shared, a);
        SharedConfigSync.pull(shared, b);

        List<String> bOptions = Files.readAllLines(b.resolve("options.txt"), StandardCharsets.UTF_8);
        assertEquals(Arrays.asList("gamma:0.5", "key_key.jump:key.keyboard.x"), bOptions);
        assertTrue(Files.exists(b.resolve("servers.dat")));
        List<String> sharedOptions = Files.readAllLines(shared.resolve("options.txt"), StandardCharsets.UTF_8)
                .stream().filter(l -> l.startsWith("gamma")).collect(Collectors.toList());
        assertTrue(sharedOptions.isEmpty()); // video settings are not shared
    }

    @Test
    public void memoryAdvice() {
        assertEquals(4096, MemoryAdvisor.recommendedMb(30, 16384));
        assertEquals(8192, MemoryAdvisor.recommendedMb(250, 32768));
        assertEquals(5120, MemoryAdvisor.recommendedMb(250, 8192));
        assertTrue(MemoryAdvisor.isWarning(100, 2048, 16384));
        assertFalse(MemoryAdvisor.isWarning(10, 4096, 16384));
    }
}
