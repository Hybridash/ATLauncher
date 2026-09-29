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
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.swing.JOptionPane;

import org.mini2Dx.gettext.GetText;

import com.atlauncher.App;
import com.atlauncher.data.Instance;
import com.atlauncher.managers.LogManager;

/**
 * Hybrid: "Restore World Backup..." from the instance's Backup menu.
 */
public final class WorldRestoreDialog {
    private WorldRestoreDialog() {
    }

    public static void show(Instance instance) {
        String title = GetText.tr("Restore World Backup");

        if (App.launcher.minecraftLaunched) {
            JOptionPane.showMessageDialog(App.launcher.getParent(), GetText.tr("Close the game before restoring a world."),
                    title, JOptionPane.WARNING_MESSAGE);
            return;
        }

        Path worldsBackups = instance.getRoot().resolve("backups").resolve("worlds");
        List<String> worlds = new ArrayList<>();
        if (Files.isDirectory(worldsBackups)) {
            try (Stream<Path> dirs = Files.list(worldsBackups)) {
                worlds = dirs.filter(Files::isDirectory).map(p -> p.getFileName().toString())
                        .filter(name -> !WorldBackup.listBackups(instance.getRoot(), name).isEmpty()).sorted()
                        .collect(Collectors.toList());
            } catch (IOException e) {
                LogManager.logStackTrace("Hybrid: could not list world backups", e);
            }
        }

        if (worlds.isEmpty()) {
            JOptionPane.showMessageDialog(App.launcher.getParent(), GetText.tr(
                    "This instance has no world backups yet. They are made every time you launch the game."),
                    title, JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        String world = (String) JOptionPane.showInputDialog(App.launcher.getParent(),
                GetText.tr("Which world do you want to restore?"), title, JOptionPane.QUESTION_MESSAGE, null,
                worlds.toArray(), worlds.get(0));
        if (world == null) {
            return;
        }

        List<Path> backups = WorldBackup.listBackups(instance.getRoot(), world);
        DateFormat format = DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.SHORT);
        String[] choices = new String[backups.size()];
        for (int i = 0; i < backups.size(); i++) {
            try {
                choices[i] = format.format(new Date(Files.getLastModifiedTime(backups.get(i)).toMillis()));
            } catch (IOException e) {
                choices[i] = backups.get(i).getFileName().toString();
            }
        }
        String choice = (String) JOptionPane.showInputDialog(App.launcher.getParent(),
                GetText.tr("Restore \"{0}\" to how it was on:", world), title, JOptionPane.QUESTION_MESSAGE, null,
                choices, choices[0]);
        if (choice == null) {
            return;
        }
        int index = java.util.Arrays.asList(choices).indexOf(choice);

        int confirm = JOptionPane.showConfirmDialog(App.launcher.getParent(), GetText.tr(
                "Replace \"{0}\" with the backup from {1}?\n\nThe current world is not deleted: it is kept as a copy named \"{0} (before restore ...)\".",
                world, choice), title, JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        try {
            WorldBackup.restoreBackup(backups.get(index), instance.getRoot(), world);
            JOptionPane.showMessageDialog(App.launcher.getParent(), GetText.tr("\"{0}\" was restored.", world), title,
                    JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException e) {
            LogManager.logStackTrace("Hybrid: restoring world backup failed", e);
            JOptionPane.showMessageDialog(App.launcher.getParent(),
                    GetText.tr("Restoring failed: {0}", e.getMessage()), title, JOptionPane.ERROR_MESSAGE);
        }
    }
}
