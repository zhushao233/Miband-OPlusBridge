// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.miam1ku.mibandoplusbridge.data;

import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.Assert.*;

public final class SessionLogTest {
    @Test public void onlyTheNewestThreeModuleStartsRemain() {
        String log = """
                old preamble
                --- module start 1 ---
                one
                --- module start 2 ---
                two
                --- module start 3 ---
                three
                --- module start 4 ---
                four
                """;
        String kept = SessionLog.keepLastStarts(log, SessionLog.KEPT_STARTS);
        assertFalse(kept.contains("module start 1 "));
        assertFalse(kept.contains("\none\n"));
        assertTrue(kept.contains("--- module start 2 ---"));
        assertTrue(kept.contains("two"));
        assertTrue(kept.contains("--- module start 4 ---"));
        assertTrue(kept.contains("four"));
        assertEquals(3, count(kept));
    }

    @Test public void redactStripsAMacBeforeItCanReachTheLog() {
        String clean = SessionLog.redact("read failed AA:bb:cc:dd:ee:ff\nstill");
        assertFalse(clean.toLowerCase().contains("aa:bb"));
        assertFalse(clean.contains("\n"));
        assertEquals("read failed mac still", clean);
        assertEquals("gone mac", SessionLog.redact("gone aa-bb-cc-dd-ee-ff"));
    }

    @Test public void captureBundleKeepsNewestFilesWithinBothLimits() throws Exception {
        java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("captures");
        try {
            java.io.File older = write(dir, "capture-old.jsonl", 40, 1_000L);
            java.io.File newer = write(dir, "capture-new.jsonl", 40, 3_000L);
            java.io.File huge = write(dir, "capture-huge.jsonl", 400, 2_000L);
            java.io.File empty = write(dir, "capture-empty.jsonl", 0, 4_000L);
            // Newest first; a zero-byte capture and one that would overflow the budget are skipped.
            assertEquals(java.util.List.of(newer, older),
                    SessionLog.pickCaptures(new java.io.File[]{older, newer, huge, empty}, 3, 100));
            assertEquals(java.util.List.of(newer),
                    SessionLog.pickCaptures(new java.io.File[]{older, newer, huge, empty}, 3, 50));
            assertEquals(2, SessionLog.pickCaptures(new java.io.File[]{older, newer, huge}, 2, 1_000).size());
            assertTrue(SessionLog.pickCaptures(new java.io.File[]{huge}, 3, 100).isEmpty());
            assertTrue(SessionLog.pickCaptures(null, 3, 100).isEmpty());
        } finally {
            for (java.io.File file : dir.toFile().listFiles()) file.delete();
            java.nio.file.Files.deleteIfExists(dir);
        }
    }

    private static java.io.File write(java.nio.file.Path dir, String name, int bytes, long modified)
            throws java.io.IOException {
        java.io.File file = dir.resolve(name).toFile();
        java.nio.file.Files.write(file.toPath(), new byte[bytes]);
        assertTrue(file.setLastModified(modified));
        return file;
    }

    @Test public void fewerThanThreeStartsAreLeftIntact() {
        String log = "--- module start 1 ---\nonly\n";
        assertEquals(log, SessionLog.keepLastStarts(log, 3));
    }

    private static int count(String text) {
        int count = 0;
        int from = 0;
        while ((from = text.indexOf(SessionLog.START_MARK, from)) >= 0) {
            count++;
            from += SessionLog.START_MARK.length();
        }
        return count;
    }

    @Test public void archiveOmitsVoiceSamplesWhenCaptureIsOff() throws Exception {
        List<String> entries = archiveEntries(List.of());
        assertTrue(entries.contains("band-debug.txt"));
        assertTrue(entries.contains("transport-observation.json"));
        assertTrue(entries.contains("README.txt"));
        for (String entry : entries) {
            assertFalse("voice samples leaked with capture off: " + entry,
                    entry.startsWith("voice-samples/"));
        }
    }

    @Test public void archiveIncludesVoiceSamplesWhenCaptureIsOn() throws Exception {
        java.nio.file.Path dir = java.nio.file.Files.createTempDirectory("voice-samples");
        try {
            java.io.File sample = write(dir, "voice-1.raw", 32, 1_000L);
            List<String> entries = archiveEntries(List.of(sample));
            assertTrue(entries.contains("voice-samples/voice-1.raw"));
        } finally {
            for (java.io.File file : dir.toFile().listFiles()) file.delete();
            java.nio.file.Files.deleteIfExists(dir);
        }
    }

    @Test public void readmeWarnsThatSamplesAreRealRecordings() throws Exception {
        String text = readmeText(archiveBytes(List.of()));
        assertTrue("len=" + text.length() + " text=" + text, text.contains("真实录音片段"));
    }

    private static List<String> archiveEntries(List<java.io.File> samples) throws Exception {
        List<String> names = new ArrayList<>();
        try (ZipInputStream zip = new ZipInputStream(
                new ByteArrayInputStream(archiveBytes(samples)))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) names.add(entry.getName());
        }
        return names;
    }

    private static byte[] archiveBytes(List<java.io.File> samples) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        SessionLog.writeArchive(out, "trace\n".getBytes(StandardCharsets.UTF_8),
                "{}".getBytes(StandardCharsets.UTF_8), List.of(), samples);
        return out.toByteArray();
    }

    private static String readmeText(byte[] archive) throws Exception {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!"README.txt".equals(entry.getName())) continue;
                return new String(zip.readAllBytes(), StandardCharsets.UTF_8);
            }
        }
        return "";
    }
}
