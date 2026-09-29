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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hybrid: reads a game log after a crash and explains the most likely cause in plain English.
 *
 * Only common, recognisable failures are covered (missing mods, wrong Java, out of memory, broken mixins, ...).
 * Every rule is a plain text match so it can be unit tested with pasted logs.
 */
public final class CrashExplainer {
    private CrashExplainer() {
    }

    public static final class Finding {
        public final String cause;
        public final String fix;

        Finding(String cause, String fix) {
            this.cause = cause;
            this.fix = fix;
        }
    }

    private static final Pattern FABRIC_LINE = Pattern.compile("^\\s*- ((?:Mod|Install|Replace|Remove) .+)$",
            Pattern.MULTILINE);
    private static final Pattern FORGE_DEP = Pattern.compile(
            "Mod ID: '([^']+)', Requested by: '([^']+)', Expected range: '([^']*)', Actual version: '([^']*)'");
    private static final Pattern CLASS_VERSION = Pattern.compile(
            "class file version (\\d+)(?:\\.\\d+)?\\), this version of the Java Runtime only recognizes class file versions up to (\\d+)");
    private static final Pattern MIXIN_MOD = Pattern.compile("Mixin apply for mod ([\\w\\-]+) failed");
    private static final Pattern MIXIN_CONFIG = Pattern.compile("Mixin \\[([\\w\\-.]+)\\.mixins\\.json");
    private static final Pattern MISSING_CLASS = Pattern
            .compile("(?:NoClassDefFoundError|ClassNotFoundException): ([\\w.$/]+)");
    private static final Pattern SUSPECTED = Pattern.compile("Suspected Mods?:\\s*\\n\\s*([^\\n]+)");
    private static final Pattern MOD_FILE = Pattern.compile("Mod File: (?:[^\\n]*[/\\\\])?([^/\\\\\\n]+\\.jar)");
    private static final Pattern CRASH_REPORT = Pattern
            .compile("Crash report saved to:?\\s*(?:#@!@#\\s*)?(\\S[^\\n]*)");

    private static String javaForClassVersion(int classVersion) {
        return classVersion >= 52 ? String.valueOf(classVersion - 44) : "?";
    }

    public static List<Finding> analyze(String log) {
        Map<String, Finding> found = new LinkedHashMap<>();

        // Fabric / Quilt dependency resolution. Their messages are already readable, so we quote them.
        if (log.contains("Incompatible mods found!") || log.contains("Mod resolution failed")
                || log.contains("Some of your mods are incompatible")) {
            Matcher m = FABRIC_LINE.matcher(log);
            int count = 0;
            while (m.find() && count < 6) {
                add(found, m.group(1).trim(), "Install, update or remove the mods named above, then launch again.");
                count++;
            }
            if (count == 0) {
                add(found, "The mod loader found mods that need other mods (or other versions) to work.",
                        "Scroll up to the \"Incompatible mods found\" section to see which mods, then install or update them.");
            }
        }

        // Forge / NeoForge missing mandatory dependencies
        Matcher dep = FORGE_DEP.matcher(log);
        while (dep.find()) {
            String modId = dep.group(1);
            String requestedBy = dep.group(2);
            String range = dep.group(3);
            String actual = dep.group(4);
            if (actual.toUpperCase().contains("MISSING")) {
                add(found, requestedBy + " needs the mod \"" + modId + "\", which is not installed.",
                        "Download " + modId + " (version " + range
                                + ") for this Minecraft version and loader, and add it to the instance.");
            } else {
                add(found, requestedBy + " needs " + modId + " version " + range + ", but you have " + actual + ".",
                        "Update or change the version of " + modId + ".");
            }
        }

        // Duplicate mods
        if (log.toLowerCase().contains("found duplicate mods") || log.toLowerCase().contains("duplicate mods found")
                || log.contains("DuplicateModsFoundException")) {
            add(found, "The same mod is installed more than once.",
                    "Open Edit Mods, find the doubled mod and remove the older copy.");
        }

        // Wrong Java version
        Matcher cv = CLASS_VERSION.matcher(log);
        if (cv.find()) {
            String needed = javaForClassVersion(Integer.parseInt(cv.group(1)));
            String have = javaForClassVersion(Integer.parseInt(cv.group(2)));
            add(found, "Something needs Java " + needed + ", but the game was started with Java " + have + ".",
                    "In the instance's Settings, on the Java/Minecraft tab, pick Java " + needed
                            + " or newer (or turn \"Use Java Provided By Minecraft\" back on).");
        } else if (log.contains("java.lang.UnsupportedClassVersionError")) {
            add(found, "A mod or the game needs a newer version of Java.",
                    "In the instance's Settings, on the Java/Minecraft tab, pick a newer Java.");
        }

        // Memory
        if (log.contains("java.lang.OutOfMemoryError")
                || log.contains("There is insufficient memory for the Java Runtime Environment")) {
            add(found, "Minecraft ran out of memory (RAM).",
                    "Give the instance more memory in its Settings. 4096 MB is a good start for modpacks; 6144+ for big ones.");
        }
        if (log.contains("Could not reserve enough space for") || log.contains("Invalid maximum heap size")
                || log.contains("Could not create the Java Virtual Machine")) {
            add(found, "Java could not start with the memory settings you picked.",
                    "Lower the maximum memory in the instance's Settings, and make sure you use 64-bit Java.");
        }

        // Graphics drivers
        if (log.contains("Pixel format not accelerated") || log.contains("GLFW error 65542")
                || log.contains("does not appear to support OpenGL") || log.contains("No OpenGL context found")) {
            add(found, "Your graphics driver couldn't start OpenGL.",
                    "Update your graphics driver from the NVIDIA, AMD or Intel website (not Windows Update).");
        }

        // Mixins
        Matcher mixin = MIXIN_MOD.matcher(log);
        boolean anyMixin = false;
        while (mixin.find()) {
            String mod = mixin.group(1);
            add(found, "The mod \"" + mod + "\" failed to patch the game (mixin error).",
                    mod + " is probably not made for this Minecraft version, or clashes with another mod. Update it or remove it.");
            anyMixin = true;
        }
        if (!anyMixin && (log.contains("MixinApplyError") || log.contains("InvalidInjectionException")
                || log.contains("MixinTransformerError"))) {
            Matcher cfg = MIXIN_CONFIG.matcher(log);
            String who = cfg.find() ? cfg.group(1) : "A mod";
            add(found, who + " failed to patch the game (mixin error).",
                    "Update the mod, or remove mods one at a time to find the one that clashes.");
        }

        // Missing classes
        Matcher mc = MISSING_CLASS.matcher(log);
        if (mc.find()) {
            String cls = mc.group(1).replace('/', '.');
            String hint;
            if (cls.startsWith("net.fabricmc.fabric")) {
                hint = "This usually means Fabric API is missing. Install Fabric API.";
            } else if (cls.startsWith("me.shedaniel.clothconfig") || cls.startsWith("me.shedaniel.autoconfig")) {
                hint = "This usually means Cloth Config is missing. Install Cloth Config.";
            } else if (cls.startsWith("software.bernie.geckolib")) {
                hint = "This usually means GeckoLib is missing. Install GeckoLib.";
            } else if (cls.startsWith("dev.architectury")) {
                hint = "This usually means Architectury API is missing. Install Architectury API.";
            } else if (cls.startsWith("net.minecraftforge") || cls.startsWith("net.neoforged")) {
                hint = "A Forge/NeoForge mod was loaded by a different loader. Check that every mod is for this instance's loader.";
            } else {
                hint = "A required library mod is probably missing, or a mod is for a different Minecraft version or loader.";
            }
            add(found, "The game couldn't find the code \"" + cls + "\".", hint);
        }

        // Forge crash reports name the mod
        Matcher sus = SUSPECTED.matcher(log);
        if (sus.find()) {
            String s = sus.group(1).trim();
            if (!s.toUpperCase().contains("NONE") && !s.toLowerCase().contains("unknown")) {
                add(found, "The crash report suspects: " + s, "Try updating or removing that mod first.");
            }
        }
        Matcher mf = MOD_FILE.matcher(log);
        if (mf.find()) {
            add(found, "The crash happened inside " + mf.group(1) + ".", "Try updating or removing that mod first.");
        }

        return new ArrayList<>(found.values());
    }

    private static void add(Map<String, Finding> found, String cause, String fix) {
        if (!found.containsKey(cause)) {
            found.put(cause, new Finding(cause, fix));
        }
    }

    /**
     * Ready-to-print lines (header, findings, fallback advice). Empty when the log shows no sign of a game crash.
     */
    public static List<String> explain(String log) {
        List<String> lines = new ArrayList<>();
        List<Finding> findings = analyze(log);
        Matcher report = CRASH_REPORT.matcher(log);

        if (findings.isEmpty()) {
            boolean gameRan = log.contains("Exception") || log.contains("Crash") || log.contains("Error");
            if (!gameRan) {
                return lines;
            }
            lines.add("==== What went wrong? (Hybrid) ====");
            lines.add("No known cause was found automatically.");
            lines.add("Tip: look for the first line starting with \"Caused by:\" above; the mod name is often in it.");
            lines.add("Tip: use the Upload button in the console to share this log when asking for help.");
        } else {
            lines.add("==== What went wrong? (Hybrid) ====");
            for (int i = 0; i < findings.size(); i++) {
                lines.add((i + 1) + ". " + findings.get(i).cause);
                boolean sameAsNext = i + 1 < findings.size() && findings.get(i + 1).fix.equals(findings.get(i).fix);
                if (!sameAsNext) {
                    lines.add("   Fix: " + findings.get(i).fix);
                }
            }
        }
        if (report.find()) {
            lines.add("Full crash report: " + report.group(1).trim());
        }
        lines.add("===================================");
        return lines;
    }
}
