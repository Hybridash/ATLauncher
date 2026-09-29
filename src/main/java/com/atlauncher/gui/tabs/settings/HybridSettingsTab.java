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
package com.atlauncher.gui.tabs.settings;

import java.awt.GridBagConstraints;
import java.awt.event.ItemEvent;
import java.util.function.Consumer;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JSpinner;
import javax.swing.SpinnerNumberModel;

import org.mini2Dx.gettext.GetText;

import com.atlauncher.App;
import com.atlauncher.constants.UIConstants;
import com.atlauncher.gui.components.JLabelWithHover;
import com.atlauncher.hybrid.HybridLaunchHooks;
import com.atlauncher.utils.OS;

/**
 * Hybrid: turns each Hybrid feature on or off. Changes are saved right away.
 */
public class HybridSettingsTab extends AbstractSettingsTab {
    @Override
    protected void createViewModel() {
    }

    @Override
    protected void onShow() {
        gbc.gridy = -1;

        addCheckbox(GetText.tr("Back Up Worlds On Launch") + "?",
                GetText.tr("Before each launch, zip every world that changed since its last backup."),
                App.settings.hybridWorldBackups, v -> App.settings.hybridWorldBackups = v);

        gbc.gridx = 0;
        gbc.gridy++;
        gbc.insets = UIConstants.LABEL_INSETS;
        gbc.anchor = GridBagConstraints.BASELINE_TRAILING;
        add(new JLabelWithHover(GetText.tr("World Backups To Keep") + ":", HELP_ICON,
                GetText.tr("How many backups of each world to keep. Older ones are deleted.")), gbc);
        gbc.gridx++;
        gbc.insets = UIConstants.FIELD_INSETS;
        gbc.anchor = GridBagConstraints.BASELINE_LEADING;
        JSpinner keep = new JSpinner(new SpinnerNumberModel(Math.max(1, App.settings.hybridWorldBackupsKeep), 1, 100, 1));
        keep.addChangeListener(e -> {
            App.settings.hybridWorldBackupsKeep = (Integer) keep.getValue();
            App.settings.save();
        });
        add(keep, gbc);

        addCheckbox(GetText.tr("Explain Crashes") + "?",
                GetText.tr("When the game crashes, explain the most likely cause and how to fix it."),
                App.settings.hybridCrashExplainer, v -> App.settings.hybridCrashExplainer = v);

        addCheckbox(GetText.tr("Check Mods Before Launch") + "?",
                GetText.tr("Warn about duplicate mods, mods that clash, and missing required mods."),
                App.settings.hybridModChecker, v -> App.settings.hybridModChecker = v);

        addCheckbox(GetText.tr("Suggest RAM Setting") + "?",
                GetText.tr("Suggest a maximum memory setting based on how many mods you have."),
                App.settings.hybridMemoryAdvice, v -> App.settings.hybridMemoryAdvice = v);

        addCheckbox(GetText.tr("Share Keybinds And Servers") + "?",
                GetText.tr("Use the same keybinds and server list in every instance. Video and sound settings stay separate."),
                App.settings.hybridSyncSharedConfig, v -> App.settings.hybridSyncSharedConfig = v);

        gbc.gridx = 1;
        gbc.gridy++;
        gbc.insets = UIConstants.FIELD_INSETS;
        gbc.anchor = GridBagConstraints.BASELINE_LEADING;
        JButton openShared = new JButton(GetText.tr("Open Shared Settings Folder"));
        openShared.addActionListener(e -> {
            HybridLaunchHooks.sharedConfigDir().toFile().mkdirs();
            OS.openFileExplorer(HybridLaunchHooks.sharedConfigDir());
        });
        add(openShared, gbc);
    }

    private void addCheckbox(String label, String help, boolean value, Consumer<Boolean> setter) {
        gbc.gridx = 0;
        gbc.gridy++;
        gbc.insets = UIConstants.LABEL_INSETS;
        gbc.anchor = GridBagConstraints.BASELINE_TRAILING;
        add(new JLabelWithHover(label, HELP_ICON, help), gbc);

        gbc.gridx++;
        gbc.insets = UIConstants.CHECKBOX_FIELD_INSETS;
        gbc.anchor = GridBagConstraints.BASELINE_LEADING;
        JCheckBox checkBox = new JCheckBox();
        checkBox.setSelected(value);
        checkBox.addItemListener(e -> {
            setter.accept(e.getStateChange() == ItemEvent.SELECTED);
            App.settings.save();
        });
        add(checkBox, gbc);
    }

    @Override
    protected void onDestroy() {
        removeAll();
    }

    @Override
    public String getTitle() {
        return GetText.tr("Hybrid");
    }

    @Override
    public String getAnalyticsScreenViewName() {
        return "Hybrid";
    }
}
