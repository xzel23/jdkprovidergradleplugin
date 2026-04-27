// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2025 Axel Howind
// This file is part of the JDK Provider Gradle Plugin.
// The JDK Provider Gradle Plugin is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
// The JDK Provider Gradle Plugin is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU General Public License for more details.
// You should have received a copy of the GNU General Public License
// along with this program. If not, see https://www.gnu.org/licenses/

package com.dua3.gradle.jdkprovider.swingtester;

import com.dua3.gradle.jdkprovider.disco.DiscoApiClient;
import com.dua3.gradle.jdkprovider.local.JdkInstallation;
import com.dua3.gradle.jdkprovider.local.LocalJdkScanner;
import com.dua3.gradle.jdkprovider.types.DiscoPackage;
import com.dua3.gradle.jdkprovider.types.JdkQuery;
import com.dua3.gradle.jdkprovider.types.JdkQueryBuilder;
import com.dua3.gradle.jdkprovider.types.OSFamily;
import com.dua3.gradle.jdkprovider.types.SystemArchitecture;
import com.dua3.gradle.jdkprovider.types.VersionSpec;
import org.gradle.jvm.toolchain.JvmVendorSpec;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.List;
import java.util.Optional;

/**
 * Simple Swing application for testing JDK Provider query behavior.
 */
public final class JdkProviderSwingTester {

    private static final List<String> VENDORS = List.of(
            "any",
            "adoptium",
            "adoptopenjdk",
            "amazon",
            "azul",
            "bellsoft",
            "graal_vm",
            "hewlett_packard",
            "ibm",
            "jetbrains",
            "microsoft",
            "oracle",
            "sap",
            "tencent"
    );

    private JdkProviderSwingTester() {
        // utility class
    }

    /**
     * The main entry point for the application.
     *
     * @param args command-line arguments passed to the application; not utilized in this method.
     */
    public static void main(String[] args) {
        SwingUtilities.invokeLater(JdkProviderSwingTester::createAndShowUi);
    }

    private static void createAndShowUi() {
        JFrame frame = new JFrame("JDK Provider Swing Tester");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));

        JTextField versionField = new JTextField("latest", 16);
        JComboBox<String> vendorBox = new JComboBox<>(VENDORS.stream().sorted().toArray(String[]::new));
        vendorBox.setSelectedItem("any");
        JComboBox<OSFamily> osBox = new JComboBox<>(OSFamily.values());
        osBox.setSelectedItem(OSFamily.current());
        JComboBox<SystemArchitecture> archBox = new JComboBox<>(SystemArchitecture.values());
        archBox.setSelectedItem(SystemArchitecture.current());
        JCheckBox nativeImageCapable = new JCheckBox("nativeImageCapable");
        JCheckBox javaFxBundled = new JCheckBox("javaFxBundled");
        JButton testButton = new JButton("Test");

        JTextArea output = new JTextArea(18, 80);
        output.setEditable(false);
        output.setLineWrap(true);
        output.setWrapStyleWord(true);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.WEST;

        int row = 0;
        addRow(panel, gbc, row++, "version", versionField);
        addRow(panel, gbc, row++, "vendor", vendorBox);
        addRow(panel, gbc, row++, "os", osBox);
        addRow(panel, gbc, row++, "arch", archBox);
        addRow(panel, gbc, row++, "", nativeImageCapable);
        addRow(panel, gbc, row++, "", javaFxBundled);

        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 2;
        panel.add(testButton, gbc);

        testButton.addActionListener(e -> {
            final JdkQuery query;
            try {
                query = JdkQueryBuilder.builder()
                        .versionSpec(VersionSpec.parse(versionField.getText().trim()))
                        .vendorSpec(getVendorSpec((String) vendorBox.getSelectedItem()))
                        .os((OSFamily) osBox.getSelectedItem())
                        .arch((SystemArchitecture) archBox.getSelectedItem())
                        .nativeImageCapable(nativeImageCapable.isSelected())
                        .javaFxBundled(javaFxBundled.isSelected())
                        .build();
            } catch (IllegalArgumentException ex) {
                JOptionPane.showMessageDialog(frame, "Invalid input: " + ex.getMessage(), "Input Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            testButton.setEnabled(false);
            output.setText("Resolving with query: " + query + "\n\nPlease wait...");

            new SwingWorker<String, Void>() {
                @Override
                protected String doInBackground() {
                    DiscoApiClient discoApiClient = new DiscoApiClient();
                    Optional<JdkInstallation> local = new LocalJdkScanner()
                            .getCompatibleInstalledJdks(query)
                            .stream()
                            .findFirst();

                    Optional<DiscoPackage> remote;
                    String remoteError = null;
                    try {
                        remote = discoApiClient.findPackage(query);
                    } catch (RuntimeException ex) {
                        remote = Optional.empty();
                        remoteError = ex.getMessage();
                    }

                    return formatResult(query, discoApiClient.getPackagesQueryUrl(query), local, remote, remoteError);
                }

                @Override
                protected void done() {
                    try {
                        output.setText(get());
                        output.setCaretPosition(0);
                    } catch (Exception ex) {
                        output.setText("Error while resolving JDK data: " + ex.getMessage());
                    } finally {
                        testButton.setEnabled(true);
                    }
                }
            }.execute();
        });

        frame.getContentPane().setLayout(new BorderLayout());
        frame.add(panel, BorderLayout.NORTH);
        frame.add(new JScrollPane(output), BorderLayout.CENTER);
        frame.pack();
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private static void addRow(JPanel panel, GridBagConstraints gbc, int row, String label, java.awt.Component component) {
        gbc.gridwidth = 1;
        gbc.gridx = 0;
        gbc.gridy = row;
        panel.add(new JLabel(label), gbc);

        gbc.gridx = 1;
        panel.add(component, gbc);
    }

    private static JvmVendorSpec getVendorSpec(String vendor) {
        if (vendor == null || "any".equals(vendor)) {
            return null;
        }
        return switch (vendor) {
            case "adoptium" -> JvmVendorSpec.ADOPTIUM;
            case "adoptopenjdk" -> JvmVendorSpec.ADOPTOPENJDK;
            case "amazon" -> JvmVendorSpec.AMAZON;
            case "azul" -> JvmVendorSpec.AZUL;
            case "bellsoft" -> JvmVendorSpec.BELLSOFT;
            case "graal_vm" -> JvmVendorSpec.GRAAL_VM;
            case "hewlett_packard" -> JvmVendorSpec.HEWLETT_PACKARD;
            case "ibm" -> JvmVendorSpec.IBM;
            case "jetbrains" -> JvmVendorSpec.JETBRAINS;
            case "microsoft" -> JvmVendorSpec.MICROSOFT;
            case "oracle" -> JvmVendorSpec.ORACLE;
            case "sap" -> JvmVendorSpec.SAP;
            case "tencent" -> JvmVendorSpec.TENCENT;
            default -> throw new IllegalArgumentException("Unsupported vendor: " + vendor);
        };
    }

    private static String formatResult(
            JdkQuery query,
            java.net.URI queryUrl,
            Optional<JdkInstallation> local,
            Optional<DiscoPackage> remote,
            String remoteError
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("Query:\n").append(query).append("\n\n");
        sb.append("Disco query URL:\n").append(queryUrl).append("\n\n");

        sb.append("1. Local JDK that would be used:\n");
        if (local.isPresent()) {
            JdkInstallation installation = local.get();
            sb.append("- jdkHome: ").append(installation.jdkHome()).append('\n');
            sb.append("- jdkSpec: ").append(installation.jdkSpec()).append("\n\n");
        } else {
            sb.append("- none found\n\n");
        }

        sb.append("2. JDK that would be downloaded:\n");
        if (remote.isPresent()) {
            DiscoPackage pkg = remote.get();
            sb.append("- distribution: ").append(pkg.distribution()).append('\n');
            sb.append("- version: ").append(pkg.version()).append('\n');
            sb.append("- os/arch: ").append(pkg.os()).append('/').append(pkg.architecture()).append('\n');
            sb.append("- archive: ").append(pkg.archiveType()).append(" (").append(pkg.filename()).append(")\n");
            sb.append("- uri: ").append(pkg.downloadUri()).append('\n');
            sb.append("- sha256: ").append(pkg.sha256()).append('\n');
        } else if (remoteError != null) {
            sb.append("- query failed: ").append(remoteError).append('\n');
        } else {
            sb.append("- no package found for query\n");
        }

        return sb.toString();
    }
}
