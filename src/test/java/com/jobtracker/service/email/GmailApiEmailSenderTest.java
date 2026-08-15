package com.jobtracker.service.email;

import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers the two failure modes that are invisible in production: refusing to start without
 * credentials, and building a message Gmail would reject.
 *
 * <p>A malformed MIME message comes back as a 400 from the API, which the sender logs and
 * swallows — so the only symptom a user sees is a password-reset email that never arrives.
 * Worth pinning down here rather than discovering it that way.
 *
 * <p>Plain unit test, no Spring context: the constructor performs no network I/O.
 */
class GmailApiEmailSenderTest {

    private static final String CLIENT_ID = "test-client-id";
    private static final String CLIENT_SECRET = "test-client-secret";
    private static final String REFRESH_TOKEN = "test-refresh-token";
    private static final String FROM_NAME = "JobJuggler";
    private static final String FROM_ADDRESS = "jobjugglerio@gmail.com";
    private static final String REPLY_TO = "jobjugglerio@gmail.com";

    private GmailApiEmailSender sender() {
        return new GmailApiEmailSender(
                CLIENT_ID, CLIENT_SECRET, REFRESH_TOKEN, FROM_NAME, FROM_ADDRESS, REPLY_TO);
    }

    private String buildMime(GmailApiEmailSender sender, String to, String subject, String body)
            throws Exception {
        Method method = GmailApiEmailSender.class.getDeclaredMethod(
                "buildMimeMessage", String.class, String.class, String.class);
        method.setAccessible(true);
        return (String) method.invoke(sender, to, subject, body);
    }

    @Test
    void refusesToStartWithoutCredentials() {
        // The @Value defaults are empty strings so that provider=log needs no configuration.
        // An empty string satisfies the placeholder, so the constructor has to reject it — the
        // alternative is an app that boots healthy and discards every email.
        IllegalStateException blankRefreshToken = assertThrows(IllegalStateException.class,
                () -> new GmailApiEmailSender(CLIENT_ID, CLIENT_SECRET, "  ",
                        FROM_NAME, FROM_ADDRESS, REPLY_TO));
        assertTrue(blankRefreshToken.getMessage().contains("GMAIL_OAUTH_REFRESH_TOKEN"),
                "message should name the missing env var, got: " + blankRefreshToken.getMessage());

        assertThrows(IllegalStateException.class,
                () -> new GmailApiEmailSender("", CLIENT_SECRET, REFRESH_TOKEN,
                        FROM_NAME, FROM_ADDRESS, REPLY_TO));
        assertThrows(IllegalStateException.class,
                () -> new GmailApiEmailSender(CLIENT_ID, null, REFRESH_TOKEN,
                        FROM_NAME, FROM_ADDRESS, REPLY_TO));
    }

    @Test
    void buildsHeadersAndSeparatesBodyWithABlankLine() throws Exception {
        String mime = buildMime(sender(), "someone@example.com", "Reset your password", "Hello");

        assertTrue(mime.startsWith("From: JobJuggler <jobjugglerio@gmail.com>\r\n"), mime);
        assertTrue(mime.contains("To: someone@example.com\r\n"), mime);
        assertTrue(mime.contains("Reply-To: jobjugglerio@gmail.com\r\n"), mime);
        assertTrue(mime.contains("Subject: Reset your password\r\n"), mime);
        assertTrue(mime.contains("MIME-Version: 1.0\r\n"), mime);
        assertTrue(mime.contains("Content-Type: text/plain; charset=\"UTF-8\"\r\n"), mime);
        assertTrue(mime.contains("Content-Transfer-Encoding: base64\r\n"), mime);

        // RFC 5322: exactly one blank line ends the header block. Without it the whole message
        // is headers and the body silently disappears.
        assertTrue(mime.contains("\r\n\r\n"), "missing header/body separator");
    }

    @Test
    void bodySurvivesTheBase64RoundTrip() throws Exception {
        String body = "Click the link below to set a new password:\n\n"
                + "http://localhost:5173/reset-password?token=b5599c5a-78a8-4163-8a49-fd0dc0014173\n\n"
                + "If you didn't request this, ignore this email.";

        String mime = buildMime(sender(), "someone@example.com", "Reset your password", body);
        String encoded = mime.substring(mime.indexOf("\r\n\r\n") + 4);

        assertEquals(body, new String(Base64.getMimeDecoder().decode(encoded), StandardCharsets.UTF_8));
    }

    @Test
    void encodesNonAsciiSubjectsPerRfc2047() throws Exception {
        // A company name a user typed can be non-ASCII, and headers are ASCII-only on the wire.
        String mime = buildMime(sender(), "someone@example.com", "Follow up on Zürich Café", "body");

        assertFalse(mime.contains("Zürich"), "raw non-ASCII must not appear in a header");
        assertTrue(mime.contains("Subject: =?UTF-8?B?"), mime);

        String subjectLine = mime.lines()
                .filter(line -> line.startsWith("Subject: "))
                .findFirst().orElseThrow();
        String base64 = subjectLine.substring("Subject: =?UTF-8?B?".length(), subjectLine.indexOf("?="));
        assertEquals("Follow up on Zürich Café",
                new String(Base64.getDecoder().decode(base64), StandardCharsets.UTF_8));
    }

    @Test
    void asciiSubjectsAreLeftAlone() throws Exception {
        String mime = buildMime(sender(), "someone@example.com", "Follow up on Acme", "body");
        assertTrue(mime.contains("Subject: Follow up on Acme\r\n"), mime);
    }

    @Test
    void wrapsLongBodyLinesForRfcCompliance() throws Exception {
        // RFC 5322 caps a line at 998 octets. A long unwrapped base64 blob is a valid-looking
        // message that servers may reject or mangle.
        String mime = buildMime(sender(), "someone@example.com", "Subject", "x".repeat(5000));

        mime.lines().forEach(line ->
                assertTrue(line.length() <= 998, "line exceeds RFC 5322 limit: " + line.length()));
    }

    /** Sanity check that reflection failures surface as real errors rather than passing tests. */
    @Test
    void mimeBuilderIsReachable() {
        assertDoesNotThrow(() -> buildMime(sender(), "a@b.com", "s", "b"),
                "buildMimeMessage should be invokable; a rename would silently void these tests");
        assertThrows(NoSuchMethodException.class,
                () -> GmailApiEmailSender.class.getDeclaredMethod("buildMimeMessageRenamed"));
    }

    /** Unwrap reflection wrapper so assertThrows sees the real exception type. */
    @SuppressWarnings("unused")
    private static Throwable unwrap(InvocationTargetException e) {
        return e.getCause();
    }
}
