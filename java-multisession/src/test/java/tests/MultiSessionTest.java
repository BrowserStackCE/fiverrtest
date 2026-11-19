package tests;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.json.JSONObject;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.assertions.LocatorAssertions;
import com.microsoft.playwright.assertions.PlaywrightAssertions;

import pages.ChatPage;
import runners.BstackRunner;
import support.LocalTextServer;

/**
 * Multi-session (peer-to-peer) demo: two users chat with each other, each in their OWN BrowserStack
 * Automate session (own browser, own cookies, own login).
 *
 *   Session 1 (this class's inherited "page", started by BstackRunner)  = user 1 (Alice)
 *   Session 2 (opened inside the test with openSecondSession)           = user 2 (Bob)
 *
 * Chat flow the test walks through:
 *   1. Alice logs in (session 1) and Bob logs in (session 2)
 *   2. Alice sends msg1            -> Bob's session must show msg1 from Alice
 *   3. Bob replies with msg2       -> Alice's session must show msg2 from Bob
 *   4. Alice sends msg3            -> Bob's session must show all three, in order
 *
 * The site under test here is a tiny local demo (LocalTextServer) reached through BrowserStack Local.
 *
 * ===== WHAT THE CUSTOMER CHANGES TO USE THEIR OWN CHAT APP =====
 *   A. Site URL ........... multisession.properties -> site.url (and drop BrowserStack Local if the
 *                           app is publicly reachable: browserstackLocal: false and remove
 *                           "browserstack.local" in buildCapabilities below).
 *   B. Start/stop server .. delete startLocalSite / stopLocalSite and the LocalTextServer class.
 *   C. Login .............. ChatPage.login(...) and the user names/credentials (see AUTH below).
 *   D. Locators ........... ChatPage (one class holds every locator).
 *   E. Auth header ........ see setUpAuthHeader below.
 */
public class MultiSessionTest extends BstackRunner {

    // Project standard explicit-wait threshold: 10 seconds
    private static final double WAIT_MS = 10000;

    private static final Properties DATA = new Properties();
    private static LocalTextServer server;

    /**
     * AUTH HEADER (demo only). The demo backend rejects API calls without
     * "Authorization: Bearer <token>". The token is read from the environment so no secret is in code:
     *     export CHAT_AUTH_TOKEN=...      (locally, or a CI secret)
     * The default below is NOT a secret: it only protects the throw-away local demo server.
     */
    private static final String AUTH_TOKEN = System.getenv("CHAT_AUTH_TOKEN") != null
            ? System.getenv("CHAT_AUTH_TOKEN") : "demo-local-token";

    @BeforeAll
    static void startLocalSite() throws IOException {
        try (InputStream in = MultiSessionTest.class.getResourceAsStream("/testdata/multisession.properties")) {
            DATA.load(in);
        }
        // CUSTOMER (B): delete these two lines - your real chat app is already running somewhere.
        server = new LocalTextServer(AUTH_TOKEN);
        server.start();
    }

    @AfterAll
    static void stopLocalSite() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void twoUsersCanChatWithEachOtherInSeparateSessions() throws Exception {
        String siteUrl = DATA.getProperty("site.url");
        String user1 = DATA.getProperty("user1.name");
        String user2 = DATA.getProperty("user2.name");
        String msg1 = DATA.getProperty("chat.msg1");
        String msg2 = DATA.getProperty("chat.msg2");
        String msg3 = DATA.getProperty("chat.msg3");
        LocatorAssertions.ContainsTextOptions wait = new LocatorAssertions.ContainsTextOptions().setTimeout(WAIT_MS);
        LocatorAssertions.HasCountOptions waitCount = new LocatorAssertions.HasCountOptions().setTimeout(WAIT_MS);

        // ---- STEP 1a: user 1 (Alice) logs in using the runner's own session ----
        setUpAuthHeader(page);
        ChatPage alice = new ChatPage(page);
        alice.open(siteUrl);
        alice.login(user1);
        PlaywrightAssertions.assertThat(alice.currentUser())
                .hasText(user1, new LocatorAssertions.HasTextOptions().setTimeout(WAIT_MS));

        // ---- STEP 1b: user 2 (Bob) logs in using a SECOND, separate Automate session ----
        Playwright playwright2 = Playwright.create();
        try {
            Browser browser2 = playwright2.chromium().connect(buildWsEndpoint("Session 2 - " + user2));
            try {
                Page page2 = browser2.newPage();
                setUpAuthHeader(page2);
                ChatPage bob = new ChatPage(page2);
                bob.open(siteUrl);
                bob.login(user2);
                PlaywrightAssertions.assertThat(bob.currentUser())
                        .hasText(user2, new LocatorAssertions.HasTextOptions().setTimeout(WAIT_MS));

                // ---- STEP 2: Alice sends msg1 -> Bob must receive it ----
                alice.sendMessage(msg1);
                PlaywrightAssertions.assertThat(bob.messages()).hasCount(1, waitCount);
                PlaywrightAssertions.assertThat(bob.messageAt(0)).containsText(user1, wait);
                PlaywrightAssertions.assertThat(bob.messageAt(0)).containsText(msg1, wait);

                // ---- STEP 3: Bob replies with msg2 -> Alice must receive it ----
                bob.sendMessage(msg2);
                PlaywrightAssertions.assertThat(alice.messages()).hasCount(2, waitCount);
                PlaywrightAssertions.assertThat(alice.messageAt(1)).containsText(user2, wait);
                PlaywrightAssertions.assertThat(alice.messageAt(1)).containsText(msg2, wait);

                // ---- STEP 4: Alice sends msg3 -> Bob must see the whole conversation in order ----
                alice.sendMessage(msg3);
                PlaywrightAssertions.assertThat(bob.messages()).hasCount(3, waitCount);
                PlaywrightAssertions.assertThat(bob.messageAt(0)).containsText(msg1, wait);
                PlaywrightAssertions.assertThat(bob.messageAt(1)).containsText(msg2, wait);
                PlaywrightAssertions.assertThat(bob.messageAt(2)).containsText(msg3, wait);

                page2.close();
            } finally {
                browser2.close();
            }
        } finally {
            playwright2.close();
        }

        // Alice's own view must end up identical (checked after Bob's session is closed)
        PlaywrightAssertions.assertThat(alice.messages()).hasCount(3, waitCount);
        PlaywrightAssertions.assertThat(alice.messageAt(2)).containsText(msg3, wait);
    }

    /**
     * ===== CUSTOMER (E): AUTH HEADER GOES HERE =====
     * Called once per session, BEFORE the first page.navigate, so every request that session's browser
     * makes carries the header. Use one token per user if your app issues per-user tokens: change
     * the signature to setUpAuthHeader(Page p, String token) and pass a different value for each user.
     *
     * Read the token from an environment variable / CI secret (as AUTH_TOKEN does above); never
     * hard-code it or commit it.
     *
     * Note: this applies the header to every request the page makes, including third-party hosts.
     * If that is a problem, scope it with p.route("**&#47;api/**", ...) instead.
     */
    private void setUpAuthHeader(Page p) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + AUTH_TOKEN);
        p.context().setExtraHTTPHeaders(headers);
    }

    /**
     * Opens the connection for the second Automate session. Same style as BstackRunner.
     * Capabilities worth knowing:
     *   browserstack.local = true  -> reach the app on this machine / private network via BrowserStack Local
     *                                 (remove for a publicly reachable app)
     *   name                       -> label shown in the Automate dashboard for this session
     * To give user 2 a different browser or OS, change "browser" / add "os" and "os_version" here.
     */
    private String buildWsEndpoint(String sessionName) throws Exception {
        HashMap<String, Object> caps = new HashMap<>();
        caps.put("browserstack.user", userName);
        caps.put("browserstack.key", accessKey);
        caps.put("browserstack.source", "java-playwright-browserstack:sample-sdk:v1.0");
        caps.put("browserstack.local", "true");
        caps.put("browser", "chrome");
        caps.put("name", sessionName);
        return "wss://cdp.browserstack.com/playwright?caps="
                + URLEncoder.encode(new JSONObject(caps).toString(), "utf-8");
    }
}
