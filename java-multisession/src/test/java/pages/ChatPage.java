package pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.WaitUntilState;

/**
 * Page object for the demo chat page (src/test/resources/site/index.html).
 *
 * CUSTOMER: this is the ONE class to rewrite for your own chat app. Keep the method names
 * (open, login, sendMessage, messages, messageFrom) and the test in MultiSessionTest keeps working;
 * only the locators and the login steps inside change. Prefer data-testid attributes in your app.
 */
public class ChatPage {
    private static final double TIMEOUT_MS = 10000;

    private final Page page;

    public ChatPage(Page page) {
        this.page = page;
        this.page.setDefaultTimeout(TIMEOUT_MS);
        this.page.setDefaultNavigationTimeout(30000);
    }

    public void open(String url) {
        page.navigate(url, new Page.NavigateOptions().setWaitUntil(WaitUntilState.DOMCONTENTLOADED));
        usernameInput().waitFor();
    }

    // ---------------------------------------------------------------------------------------------
    // CUSTOMER: REPLACE THIS METHOD with your app's real login flow (fields, SSO redirect, MFA, ...).
    // Anything your login needs (user names, passwords) should come from environment variables or
    // a test data file, never from source code.
    // ---------------------------------------------------------------------------------------------
    public void login(String username) {
        usernameInput().fill(username);
        page.getByTestId("login-button").click();
        currentUser().waitFor();
    }

    public void sendMessage(String text) {
        messageInput().fill(text);
        page.getByTestId("send-button").click();
    }

    public Locator usernameInput() {
        return page.getByTestId("username-input");
    }

    public Locator currentUser() {
        return page.getByTestId("current-user");
    }

    public Locator messageInput() {
        return page.getByTestId("message-input");
    }

    /** Every message bubble in the conversation, oldest first. */
    public Locator messages() {
        return page.getByTestId("message");
    }

    /** The single bubble at position index (0 = first message). */
    public Locator messageAt(int index) {
        return messages().nth(index);
    }
}
