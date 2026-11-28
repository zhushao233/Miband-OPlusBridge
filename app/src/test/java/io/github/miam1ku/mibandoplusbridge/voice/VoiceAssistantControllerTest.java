// SPDX-License-Identifier: AGPL-3.0-or-later
package io.github.miam1ku.mibandoplusbridge.voice;

import java.io.File;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class VoiceAssistantControllerTest {
    private static final long WAIT_SECONDS = 3;

    @Test public void staleGenerationDoesNotSendLateAnswer() throws Exception {
        BlockingClient client = new BlockingClient();
        List<byte[]> sent = new CopyOnWriteArrayList<>();
        List<String> failures = new CopyOnWriteArrayList<>();
        try (VoiceAssistantController controller = controller(client, sent, failures)) {
            VoiceAssistantSettings.Values settings = settings();
            controller.beginSession(start(), settings);
            controller.appendAudio(audioBody(), settings);
            controller.finishCapture("band-end", settings);

            assertTrue(client.transcribeStarted.await(WAIT_SECONDS, TimeUnit.SECONDS));
            controller.resetTransport();
            client.release.countDown();
            controller.awaitApiIdleForTest();

            assertTrue(sent.isEmpty());
            assertEquals(0, client.chatCalls.get());
            assertTrue(failures.isEmpty());
        }
    }

    @Test public void finishCaptureIsIdempotent() throws Exception {
        BlockingClient client = new BlockingClient();
        client.release.countDown();
        List<byte[]> sent = new CopyOnWriteArrayList<>();
        List<String> failures = new CopyOnWriteArrayList<>();
        try (VoiceAssistantController controller = controller(client, sent, failures)) {
            VoiceAssistantSettings.Values settings = settings();
            controller.beginSession(start(), settings);
            controller.appendAudio(audioBody(), settings);

            controller.finishCapture("test", settings);
            controller.finishCapture("test", settings);
            controller.awaitApiIdleForTest();

            assertEquals(1, client.transcribeCalls.get());
            assertTrue(failures.isEmpty());
        }
    }

    @Test public void failedPartialIsBestEffortAndCanStartAgain() throws Exception {
        FailingClient client = new FailingClient();
        List<byte[]> sent = new CopyOnWriteArrayList<>();
        List<String> failures = new CopyOnWriteArrayList<>();
        try (VoiceAssistantController controller = controller(client, sent, failures)) {
            VoiceAssistantSettings.Values settings = settings();
            controller.beginSession(start(), settings);
            controller.appendAudio(audioBody(), settings);
            controller.appendAudio(audioBody(), settings);

            long now = System.currentTimeMillis() + 2_000;
            controller.maybeStartPartial(settings, now);
            controller.awaitApiIdleForTest();
            assertFalse(controller.partialInFlightForTest());
            assertEquals(1, client.transcribeCalls.get());

            controller.maybeStartPartial(settings, now + 2_000);
            controller.awaitApiIdleForTest();
            assertFalse(controller.partialInFlightForTest());
            assertEquals(2, client.transcribeCalls.get());

            controller.finishCapture("band-end", settings);
            controller.awaitApiIdleForTest();
            assertTrue(failures.contains("VOICE_TRANSCRIPT_EMPTY"));
        }
    }

    @Test public void slowWatchCommandDoesNotBlockNextTurnTranscription() throws Exception {
        TwoTurnClient client = new TwoTurnClient();
        BlockingWatchExecutor watch = new BlockingWatchExecutor();
        List<byte[]> sent = new CopyOnWriteArrayList<>();
        List<String> failures = new CopyOnWriteArrayList<>();
        try (VoiceAssistantController controller = controller(client, watch, sent, failures)) {
            VoiceAssistantSettings.Values settings = settings();
            controller.beginSession(start(), settings);
            controller.appendAudio(audioBody(), settings);
            controller.finishCapture("band-end", settings);
            assertTrue(watch.started.await(WAIT_SECONDS, TimeUnit.SECONDS));

            controller.beginSession(start(), settings);
            controller.appendAudio(audioBody(), settings);
            controller.finishCapture("band-end", settings);
            assertTrue("second turn transcription waited for the watch command",
                    client.secondTranscribe.await(WAIT_SECONDS, TimeUnit.SECONDS));

            watch.release.countDown();
            assertTrue(watch.finished.await(WAIT_SECONDS, TimeUnit.SECONDS));
            controller.awaitApiIdleForTest();
            assertTrue(failures.isEmpty());
        } finally {
            watch.release.countDown();
        }
    }

    private static VoiceAssistantController controller(VoiceApiClient client,
                                                       List<byte[]> sent,
                                                       List<String> failures) throws Exception {
        return controller(client, null, sent, failures);
    }

    private static VoiceAssistantController controller(VoiceApiClient client,
                                                       WatchCommandExecutor executor,
                                                       List<byte[]> sent,
                                                       List<String> failures) throws Exception {
        File historyFile = File.createTempFile("voice-history", ".json");
        historyFile.delete();
        historyFile.deleteOnExit();
        return new VoiceAssistantController(null,
                sent::add, executor, null,
                ignored -> { }, ignored -> { }, failures::add,
                client, new VoiceConversationHistory(historyFile));
    }

    private static AivsStreamParser.Command start() {
        return new AivsStreamParser.Command(AivsStreamParser.TYPE_START,
                new byte[] {0x07, 0x00});
    }

    private static byte[] audioBody() {
        byte[] body = new byte[400];
        for (int offset = 0; offset < body.length; offset += 80) body[offset] = 0x4b;
        return body;
    }

    private static VoiceAssistantSettings.Values settings() {
        return new VoiceAssistantSettings.Values(
                VoiceAssistantPreset.OPENAI, true,
                "https://api.example.com/v1", "whisper-1", "gpt-4.1-mini",
                "system", "test-key", false,
                "https://api.example.com/v1/audio/transcriptions", "test-key");
    }

    private static class BlockingClient implements VoiceApiClient {
        final AtomicInteger transcribeCalls = new AtomicInteger();
        final AtomicInteger chatCalls = new AtomicInteger();
        final CountDownLatch transcribeStarted = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);

        @Override public String transcribe(VoiceAssistantSettings.Values settings, byte[] audio)
                throws Exception {
            transcribeCalls.incrementAndGet();
            transcribeStarted.countDown();
            if (!release.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException("VOICE_TEST_RELEASE_TIMEOUT");
            }
            return "你好";
        }

        @Override public String chat(VoiceAssistantSettings.Values settings, String prompt) {
            chatCalls.incrementAndGet();
            return "你好，有什么可以帮你？";
        }

        @Override public String chat(VoiceAssistantSettings.Values settings, String prompt,
                                     String extraSystemContext) {
            return chat(settings, prompt);
        }
    }

    private static final class FailingClient implements VoiceApiClient {
        final AtomicInteger transcribeCalls = new AtomicInteger();

        @Override public String transcribe(VoiceAssistantSettings.Values settings, byte[] audio) {
            transcribeCalls.incrementAndGet();
            throw new IllegalStateException("VOICE_TEST_TRANSCRIBE");
        }

        @Override public String chat(VoiceAssistantSettings.Values settings, String prompt) {
            throw new IllegalStateException("VOICE_TEST_CHAT");
        }

        @Override public String chat(VoiceAssistantSettings.Values settings, String prompt,
                                     String extraSystemContext) {
            throw new IllegalStateException("VOICE_TEST_CHAT");
        }
    }

    private static final class TwoTurnClient implements VoiceApiClient {
        final AtomicInteger transcribeCalls = new AtomicInteger();
        final CountDownLatch secondTranscribe = new CountDownLatch(1);

        @Override public String transcribe(VoiceAssistantSettings.Values settings, byte[] audio) {
            return transcribeCalls.incrementAndGet() == 1 ? "查看闹钟" : second();
        }

        private String second() {
            secondTranscribe.countDown();
            return "你好";
        }

        @Override public String chat(VoiceAssistantSettings.Values settings, String prompt) {
            return "你好，有什么可以帮你？";
        }

        @Override public String chat(VoiceAssistantSettings.Values settings, String prompt,
                                     String extraSystemContext) {
            return chat(settings, prompt);
        }
    }

    private static final class BlockingWatchExecutor implements WatchCommandExecutor {
        final CountDownLatch started = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final CountDownLatch finished = new CountDownLatch(1);

        @Override public String execute(WatchCommand command) throws Exception {
            started.countDown();
            try {
                if (!release.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("VOICE_TEST_WATCH_RELEASE_TIMEOUT");
                }
                return "闹钟列表";
            } finally {
                finished.countDown();
            }
        }
    }
}
