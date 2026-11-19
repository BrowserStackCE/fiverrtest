# Multi-session (peer-to-peer) chat test - BrowserStack Automate + Playwright (Java)

Two users chat with each other, each in their **own BrowserStack Automate session**:

| Session | User | Started by |
|---------|------|-----------|
| 1 | Alice | `BstackRunner` (the SDK session) |
| 2 | Bob   | `MultiSessionTest` (a second `connect()` to BrowserStack) |

Flow: both log in -> Alice sends -> Bob sees it -> Bob replies -> Alice sees it -> Alice sends again -> Bob sees all three in order.

## Run the demo as-is
1. Set your BrowserStack credentials (never commit them):
   ```
   export BROWSERSTACK_USERNAME=<your username>
   export BROWSERSTACK_ACCESS_KEY=<your access key>
   ```
2. `mvn test -P multi-session-test`

The demo chat site is a tiny local server started by the test and reached from BrowserStack through
**BrowserStack Local** (`browserstackLocal: true`, URL `http://bs-local.com:45455/`).

## Point it at your own chat app
Search the code for `CUSTOMER` - every place you change is marked.

| What | Where |
|------|-------|
| Site URL | `src/test/resources/testdata/multisession.properties` -> `site.url` |
| Test users + chat lines | same file (`user1.name`, `user2.name`, `chat.msg1..3`) |
| **Login steps** | `ChatPage.login(...)` in `src/test/java/pages/ChatPage.java` |
| Locators (input, send button, message list) | `ChatPage.java` (prefer `data-testid`) |
| **Auth header** | `setUpAuthHeader(...)` in `MultiSessionTest.java`; token comes from env var `CHAT_AUTH_TOKEN` |
| Remove the demo server | delete `startLocalSite/stopLocalSite` in the test and `support/LocalTextServer.java` |
| App is publicly reachable | set `browserstackLocal: false` and remove `browserstack.local` in `buildWsEndpoint` |
| Different browser/OS for user 2 | `buildWsEndpoint` in `MultiSessionTest.java` |

Keep secrets (passwords, tokens) in environment variables or CI secrets, not in source.

## Notes
* Run on a **single platform** (as in the yml): the two sessions share one chat, so parallel copies would see each other's messages.
* The chat page polls every second; your app may use websockets - the assertions already wait up to 10 s for messages to arrive.
