// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.miam1ku.mibandoplusbridge.data;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.zip.ZipFile;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class DeviceLogExporterTest {
    @Test public void archiveContainsTheExpectedTextEntries() throws Exception {
        File dir = Files.createTempDirectory("device-log-test").toFile();
        File archive = new File(dir, "device-log.zip");
        LinkedHashMap<String, String> entries = new LinkedHashMap<>();
        entries.put("manifest.txt", "format=" + DeviceLogExporter.FORMAT + "\n");
        entries.put("session-log.txt", "spp connect\nrx ble bytes=8\n");
        try {
            DeviceLogExporter.writeZip(archive, entries);
            try (ZipFile zip = new ZipFile(archive)) {
                assertEquals(entries.size(), zip.size());
                assertEquals(entries.get("manifest.txt"), read(zip, "manifest.txt"));
                assertEquals(entries.get("session-log.txt"), read(zip, "session-log.txt"));
            }
        } finally {
            Files.deleteIfExists(archive.toPath());
            Files.deleteIfExists(dir.toPath());
        }
    }

    @Test public void manifestKeepsOneRedactedValuePerLine() {
        LinkedHashMap<String, String> details = new LinkedHashMap<>();
        details.put("device.model", "miwear.band.m66nfc");
        details.put("device.note", "line one\nline two");
        String text = DeviceLogExporter.manifest(details);
        assertTrue(text.startsWith("device.model=miwear.band.m66nfc\n"));
        assertTrue(text.contains("device.note=line one line two\n"));
        assertEquals(2, text.lines().count());
    }

    private static String read(ZipFile zip, String name) throws Exception {
        try (var input = zip.getInputStream(zip.getEntry(name))) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
