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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

public class CrashExplainerTest {
    private static boolean anyCauseContains(List<CrashExplainer.Finding> findings, String text) {
        for (CrashExplainer.Finding f : findings) {
            if (f.cause.contains(text)) {
                return true;
            }
        }
        return false;
    }

    @Test
    public void fabricMissingDependency() {
        String log = "[main/ERROR]: Incompatible mods found!\n"
                + "\t - Install fabric-api, any version.\n"
                + "\t - Mod 'Sodium Extra' (sodium-extra) 0.5.1 requires any version of fabric-api, which is missing!\n";
        List<CrashExplainer.Finding> findings = CrashExplainer.analyze(log);
        assertTrue(anyCauseContains(findings, "Install fabric-api"));
        assertTrue(anyCauseContains(findings, "Sodium Extra"));
    }

    @Test
    public void forgeMissingDependency() {
        String log = "\tMod ID: 'geckolib', Requested by: 'mowziesmobs', Expected range: '[4.4,)', Actual version: '[MISSING]'\n";
        List<CrashExplainer.Finding> findings = CrashExplainer.analyze(log);
        assertEquals(1, findings.size());
        assertTrue(findings.get(0).cause.contains("mowziesmobs"));
        assertTrue(findings.get(0).cause.contains("geckolib"));
    }

    @Test
    public void wrongJava() {
        String log = "has been compiled by a more recent version of the Java Runtime (class file version 65.0), "
                + "this version of the Java Runtime only recognizes class file versions up to 52.0\n";
        List<CrashExplainer.Finding> findings = CrashExplainer.analyze(log);
        assertTrue(anyCauseContains(findings, "Java 21"));
        assertTrue(anyCauseContains(findings, "Java 8"));
    }

    @Test
    public void outOfMemory() {
        assertTrue(anyCauseContains(CrashExplainer.analyze("java.lang.OutOfMemoryError: Java heap space\n"), "memory"));
    }

    @Test
    public void missingFabricApiClass() {
        List<CrashExplainer.Finding> findings = CrashExplainer
                .analyze("java.lang.NoClassDefFoundError: net/fabricmc/fabric/api/event/Event\n");
        assertEquals(1, findings.size());
        assertTrue(findings.get(0).fix.contains("Fabric API"));
    }

    @Test
    public void crashReportNamesMod() {
        String log = "Suspected Mods: \n\tAlex's Mobs (alexsmobs), Version: 1.22\n"
                + "Mod File: /home/u/.minecraft/mods/alexsmobs-1.22.jar\n";
        List<CrashExplainer.Finding> findings = CrashExplainer.analyze(log);
        assertTrue(anyCauseContains(findings, "Alex's Mobs"));
        assertTrue(anyCauseContains(findings, "alexsmobs-1.22.jar"));
    }

    @Test
    public void nothingFound() {
        assertTrue(CrashExplainer.analyze("Everything is fine\n").isEmpty());
        assertTrue(CrashExplainer.explain("Checking Java\nJava not found\n").isEmpty());
    }
}
