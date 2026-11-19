# Multi-session (peer-to-peer) chat test - BrowserStack Automate + Playwright (Java)

Example of testing an app where **two users interact with each other in real time** (for example a chat),
using **two separate BrowserStack Automate sessions**, one per user. Each session has its own browser,
cookies and login, so the test behaves like two real people on two machines.

| Session | User | Started by |
|---------|------|-----------|
| 1 | Alice | `BstackRunner` (the BrowserStack SDK session) |
| 2 | Bob   | `MultiSessionTest` (a second `connect()` to BrowserStack) |

**Chat flow the test walks through**
1. Alice logs in (session 1) and Bob logs in (session 2).
2. Alice sends a message, and Bob's session must show it.
3. Bob replies, and Alice's session must show the reply.
4. Alice sends a third message, and Bob must see all three in order. Alice's own view is checked at the end.

## Repository layout

| Path | Purpose |
|------|---------|
| `pom.xml` | Maven build; profile `multi-session-test` runs only this test with the BrowserStack SDK |
| `browserstack.yml` | BrowserStack config: platform (one), BrowserStack Local, build name |
| `src/test/java/tests/MultiSessionTest.java` | The test: both users, the chat flow, the auth header hook |
| `src/test/java/pages/ChatPage.java` | Page object: **all locators and the login steps** |
| `src/test/java/runners/BstackRunner.java` | Starts session 1 on BrowserStack (reads `browserstack.yml`) |
| `src/test/java/support/LocalTextServer.java` | Demo-only chat backend (delete when you use your own app) |
| `src/test/resources/site/index.html` | Demo-only chat page served by the demo backend |
| `src/test/resources/testdata/multisession.properties` | Site URL, user names, chat lines |
| `.github/workflows/` | Repository workflows (not used by this example) |

## Run the demo as-is
Requires Java 8+ and Maven.

1. Set your BrowserStack credentials (never commit them):
   ```
   export BROWSERSTACK_USERNAME=<your username>
   export BROWSERSTACK_ACCESS_KEY=<your access key>
   ```
2. Run: `mvn test -P multi-session-test`
3. Open the build on the BrowserStack Automate dashboard: you will see **two sessions** (one per user).

The demo chat site is a tiny local server started by the test and reached from BrowserStack through
**BrowserStack Local** (`browserstackLocal: true`, URL `http://bs-local.com:45455/`).

## Point it at your own chat app
Search the code for `CUSTOMER`: every place you change is marked.

| What | Where |
|------|-------|
| Site URL | `src/test/resources/testdata/multisession.properties` -> `site.url` |
| Test users + chat lines | same file (`user1.name`, `user2.name`, `chat.msg1..3`) |
| **Login steps** | `ChatPage.login(...)` in `ChatPage.java` |
| Locators (input, send button, message list) | `ChatPage.java` (prefer `data-testid`) |
| **Auth header** | `setUpAuthHeader(...)` in `MultiSessionTest.java`; the token comes from env var `CHAT_AUTH_TOKEN` |
| Remove the demo server | delete `startLocalSite` / `stopLocalSite` in the test and `support/LocalTextServer.java` |
| App is publicly reachable | set `browserstackLocal: false` in `browserstack.yml` and remove `browserstack.local` in `buildWsEndpoint` |
| Different browser/OS for user 2 | `buildWsEndpoint` in `MultiSessionTest.java` |

If you keep the method names in `ChatPage` (`open`, `login`, `sendMessage`, `messages`, `messageAt`),
`MultiSessionTest` needs no changes.

Keep secrets (passwords, tokens) in environment variables or CI secrets, not in source.

## Notes
* Run on a **single platform** (as in `browserstack.yml`): the two sessions share one chat, so parallel copies would see each other's messages.
* The demo page polls every second; your app may use websockets. The assertions already wait up to 10 s for messages to arrive.
* The `Authorization: Bearer ...` token in the demo is a throwaway value that only protects the local demo server.
