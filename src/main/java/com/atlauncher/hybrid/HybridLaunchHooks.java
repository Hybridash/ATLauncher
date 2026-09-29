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
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.mini2Dx.gettext.GetText;

import com.atlauncher.App;
import com.atlauncher.FileSystem;
import com.atlauncher.builders.HTMLBuilder;
import com.atlauncher.data.Instance;
import com.atlauncher.managers.DialogManager;
import com.atlauncher.managers.LogManager;
import com.atlauncher.utils.OS;

/**
 * Hybrid: the places where the Hybrid features plug into launching an instance.
 */
public final class HybridLaunchHooks {
    /** Keep at most this much of the game log in memory for the crash explainer. */
    private static final int MAX_LOG_CHARS = 4 * 1024 * 1024;

    private HybridLaunchHooks() {
    }

    public static Path sharedConfigDir() {
        return FileSystem.CONFIGS.resolve("shared-config");
    }

    /** Runs before the game starts: mod check, memory advice, world backups, shared keybinds/servers. */
    public static void beforeLaunch(Instance instance) {
        Path root = instance.getRoot();

        try {
            List<ModChecker.ModInfo> mods = ModChecker.readMods(root.resolve("mods"));

            if (App.settings.hybridModChecker && !mods.isEmpty()) {
                List<String> problems = ModChecker.check(mods);
                if (problems.isEmpty()) {
                    LogManager.info("Mod check: no problems found in " + mods.size() + " mods.");
                } else {
                    LogManager.warn("Mod check found " + problems.size() + " possible problem(s):");
                    for (String problem : problems) {
                        LogManager.warn("  - " + problem);
                    }
                }
            }

            if (App.settings.hybridMemoryAdvice) {
                int current = Optional.ofNullable(instance.launcher.maximumMemory).orElse(App.settings.maximumMemory);
                int systemRam = OS.getSystemRam();
                String advice = MemoryAdvisor.advice(mods.size(), current, systemRam);
                if (MemoryAdvisor.isWarning(mods.size(), current, systemRam)) {
                    LogManager.warn(advice);
                } else {
                    LogManager.info(advice);
                }
            }
        } catch (Exception e) {
            LogManager.logStackTrace("Hybrid: mod check failed", e);
        }

        if (App.settings.hybridWorldBackups) {
            int keep = Math.max(1, App.settings.hybridWorldBackupsKeep);
            LogManager.info("Backing up worlds (keeping the newest " + keep + " backups of each)...");
            for (String line : WorldBackup.backupChangedWorlds(root, keep)) {
                LogManager.info(line);
            }
        }

        if (App.settings.hybridSyncSharedConfig) {
            try {
                LogManager.info(SharedConfigSync.pull(sharedConfigDir(), root));
            } catch (IOException e) {
                LogManager.warn("Shared settings: could not apply (" + e.getMessage() + ")");
            }
        }
    }

    /** Appends a game log line to the buffer used by the crash explainer. */
    public static void recordLogLine(StringBuilder buffer, String line) {
        if (!App.settings.hybridCrashExplainer) {
            return;
        }
        buffer.append(line).append('\n');
        if (buffer.length() > MAX_LOG_CHARS) {
            // drop the oldest half; crash details are almost always near the end
            buffer.delete(0, buffer.length() / 2);
        }
    }

    /**
     * Runs after the game closes.
     *
     * @param crashed          the game exited with an error
     * @param popupAlreadyShown ATLauncher already showed its own crash popup
     */
    public static void afterExit(Instance instance, StringBuilder log, boolean crashed, boolean popupAlreadyShown) {
        if (crashed && App.settings.hybridCrashExplainer) {
            List<CrashExplainer.Finding> findings = CrashExplainer.analyze(log.toString());
            for (String line : CrashExplainer.explain(log.toString())) {
                LogManager.warn(line);
            }
            if (!findings.isEmpty() && !popupAlreadyShown) {
                HTMLBuilder html = new HTMLBuilder().center().split(100);
                StringBuilder text = new StringBuilder();
                int shown = 0;
                for (CrashExplainer.Finding finding : findings) {
                    if (shown++ == 3) {
                        text.append("<br/>...and more in the console.");
                        break;
                    }
                    text.append("<b>").append(escape(finding.cause)).append("</b><br/>").append(escape(finding.fix))
                            .append("<br/><br/>");
                }
                DialogManager.okDialog().setTitle(GetText.tr("What Went Wrong?"))
                        .setContent(html.text(text.toString()).build()).setType(DialogManager.INFO).show();
            }
        }

        if (!crashed && App.settings.hybridSyncSharedConfig) {
            try {
                LogManager.info(SharedConfigSync.push(sharedConfigDir(), instance.getRoot()));
            } catch (IOException e) {
                LogManager.warn("Shared settings: could not save (" + e.getMessage() + ")");
            }
        }
    }

    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
