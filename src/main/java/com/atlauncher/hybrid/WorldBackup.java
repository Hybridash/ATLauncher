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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Hybrid: zip based world backups.
 *
 * Backups live in {@code <instance>/backups/worlds/<world folder>/<yyyy-MM-dd_HH-mm-ss-SSS>.zip} and contain the
 * world folder itself, so restoring is just "extract into saves/".
 */
public final class WorldBackup {
    private WorldBackup() {
    }

    public static Path backupDirFor(Path instanceRoot, String worldFolderName) {
        return instanceRoot.resolve("backups").resolve("worlds").resolve(worldFolderName);
    }

    /** Backups of one world, newest first. */
    public static List<Path> listBackups(Path instanceRoot, String worldFolderName) {
        Path dir = backupDirFor(instanceRoot, worldFolderName);
        if (!Files.isDirectory(dir)) {
            return new ArrayList<>();
        }
        try (Stream<Path> files = Files.list(dir)) {
            List<Path> list = files.filter(p -> p.getFileName().toString().endsWith(".zip")).sorted()
                    .collect(Collectors.toList());
            // names are timestamps, so reversed name order is newest first
            Collections.reverse(list);
            return list;
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    private static long newestChange(Path world) throws IOException {
        try (Stream<Path> files = Files.walk(world)) {
            return files.filter(Files::isRegularFile)
                    .filter(p -> !p.getFileName().toString().equals("session.lock"))
                    .mapToLong(p -> {
                        try {
                            return Files.getLastModifiedTime(p).toMillis();
                        } catch (IOException e) {
                            return Long.MAX_VALUE;
                        }
                    }).max().orElse(0L);
        }
    }

    /** True if the world changed since its newest backup (or has none yet). */
    public static boolean needsBackup(Path world, Path instanceRoot) {
        List<Path> backups = listBackups(instanceRoot, world.getFileName().toString());
        if (backups.isEmpty()) {
            return true;
        }
        try {
            return newestChange(world) > Files.getLastModifiedTime(backups.get(0)).toMillis();
        } catch (IOException e) {
            return true;
        }
    }

    /** Zips one world and keeps only the newest {@code keep} backups. Returns the new zip. */
    public static Path backupWorld(Path world, Path instanceRoot, int keep) throws IOException {
        String worldName = world.getFileName().toString();
        Path dir = backupDirFor(instanceRoot, worldName);
        Files.createDirectories(dir);

        String stamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss-SSS").format(new Date());
        Path target = dir.resolve(stamp + ".zip");
        for (int n = 2; Files.exists(target); n++) {
            target = dir.resolve(stamp + "_" + n + ".zip");
        }

        Path savesDir = world.getParent();
        List<Path> files;
        try (Stream<Path> walk = Files.walk(world)) {
            files = walk.filter(Files::isRegularFile)
                    .filter(p -> !p.getFileName().toString().equals("session.lock"))
                    .collect(Collectors.toList());
        }

        try (OutputStream out = Files.newOutputStream(target); ZipOutputStream zip = new ZipOutputStream(out)) {
            for (Path file : files) {
                // entries always use "/" so the zip works on every OS
                String name = savesDir.relativize(file).toString().replace(File.separatorChar, '/');
                ZipEntry entry = new ZipEntry(name);
                entry.setLastModifiedTime(Files.getLastModifiedTime(file));
                zip.putNextEntry(entry);
                Files.copy(file, zip);
                zip.closeEntry();
            }
        } catch (IOException e) {
            Files.deleteIfExists(target);
            throw e;
        }

        List<Path> backups = listBackups(instanceRoot, worldName);
        for (int i = Math.max(keep, 1); i < backups.size(); i++) {
            Files.deleteIfExists(backups.get(i));
        }
        return target;
    }

    /** Backs up every world in {@code <instance>/saves} that changed. Returns one log line per world. */
    public static List<String> backupChangedWorlds(Path instanceRoot, int keep) {
        List<String> log = new ArrayList<>();
        Path saves = instanceRoot.resolve("saves");
        if (!Files.isDirectory(saves)) {
            return log;
        }
        try (DirectoryStream<Path> worlds = Files.newDirectoryStream(saves)) {
            for (Path world : worlds) {
                if (!Files.isRegularFile(world.resolve("level.dat"))) {
                    continue;
                }
                String name = world.getFileName().toString();
                if (!needsBackup(world, instanceRoot)) {
                    log.add("  " + name + ": unchanged since the last backup, skipped");
                    continue;
                }
                try {
                    Path zip = backupWorld(world, instanceRoot, keep);
                    log.add("  " + name + ": backed up to " + zip.getFileName());
                } catch (IOException e) {
                    log.add("  " + name + ": backup FAILED (" + e.getMessage() + ")");
                }
            }
        } catch (IOException e) {
            log.add("  Could not read the saves folder: " + e.getMessage());
        }
        return log;
    }

    /**
     * Replaces a world with a backup. The current world is renamed to "<name> (before restore <time>)" first, and
     * put back if extracting fails.
     */
    public static void restoreBackup(Path backupZip, Path instanceRoot, String worldFolderName) throws IOException {
        Path saves = instanceRoot.resolve("saves");
        Files.createDirectories(saves);
        Path savesReal = saves.toRealPath();
        Path world = savesReal.resolve(worldFolderName);

        Path aside = null;
        if (Files.exists(world)) {
            String stamp = new SimpleDateFormat("yyyy-MM-dd HH-mm-ss").format(new Date());
            aside = savesReal.resolve(worldFolderName + " (before restore " + stamp + ")");
            Files.move(world, aside);
        }

        try (ZipFile zip = new ZipFile(backupZip.toFile())) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                Path out = savesReal.resolve(entry.getName()).normalize();
                // never write outside the saves folder ("zip slip")
                if (!out.startsWith(savesReal)) {
                    throw new IOException("Unsafe path in backup: " + entry.getName());
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(out);
                    continue;
                }
                Files.createDirectories(out.getParent());
                try (InputStream in = zip.getInputStream(entry)) {
                    Files.copy(in, out, StandardCopyOption.REPLACE_EXISTING);
                }
                if (entry.getLastModifiedTime() != null) {
                    Files.setLastModifiedTime(out, FileTime.fromMillis(entry.getLastModifiedTime().toMillis()));
                }
            }
        } catch (IOException e) {
            // put the original world back so the player is no worse off
            if (aside != null) {
                deleteRecursively(world);
                Files.move(aside, world);
            }
            throw e;
        }
    }

    private static void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(path)) {
            List<Path> all = walk.sorted(Collections.reverseOrder()).collect(Collectors.toList());
            for (Path p : all) {
                Files.deleteIfExists(p);
            }
        }
    }
}
