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

/**
 * Hybrid: suggests a maximum memory setting based on how many mods are installed.
 */
public final class MemoryAdvisor {
    private MemoryAdvisor() {
    }

    /** Suggested maximum memory in MB. {@code systemRamMb} of 0 means unknown. */
    public static int recommendedMb(int modCount, int systemRamMb) {
        int recommended = 2048;
        if (modCount > 0) {
            recommended = 4096;
        }
        if (modCount > 60) {
            recommended = 6144;
        }
        if (modCount > 180) {
            recommended = 8192;
        }
        if (systemRamMb > 0) {
            // leave at least 3 GB for the operating system and everything else
            recommended = Math.min(recommended, Math.max(1024, systemRamMb - 3072));
        }
        return recommended;
    }

    /** One line of advice for the launch log. */
    public static String advice(int modCount, int currentMb, int systemRamMb) {
        int recommended = recommendedMb(modCount, systemRamMb);
        if (currentMb < recommended) {
            return "Memory tip: this instance has " + modCount + " mods but may only use " + currentMb
                    + " MB of RAM. " + recommended
                    + " MB is recommended. Change it in the instance's Settings (Java/Minecraft tab).";
        }
        if (currentMb > recommended * 2 && currentMb > 8192) {
            return "Memory tip: " + currentMb + " MB is a lot for " + modCount
                    + " mods. Giving Java much more than it needs can cause lag spikes; around " + recommended
                    + " MB is usually enough.";
        }
        return "Memory: " + currentMb + " MB for " + modCount + " mods looks good.";
    }

    public static boolean isWarning(int modCount, int currentMb, int systemRamMb) {
        return currentMb < recommendedMb(modCount, systemRamMb);
    }
}
