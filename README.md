# RuneFlip Companion

A strictly **read-only** RuneLite plugin that mirrors your 8 Grand Exchange
slots to a [RuneFlip](https://github.com/jumunozc) flipping dashboard and
shows the dashboard's market recommendations in a sidebar panel.

One-way by design: it observes `GrandExchangeOffer` through the official
RuneLite plugin API and POSTs a JSON snapshot — the HTTP response is a bare
acknowledgement that is only logged. There is no command channel.

**It never** buys, sells, cancels or collects offers; never clicks, types or
moves the mouse; never simulates input of any kind; never uses OCR, screen
capture or pixel reading; never reads Jagex credentials or session data;
never receives commands from the backend.

## What it does

- **GE slot sync (read-only).** Mirrors the state of your 8 GE slots
  (item, price, quantity, progress, status) to a RuneFlip backend so the
  dashboard can show them next to its market analysis. Sends only when a
  backend URL *and* an ingest token are configured — with either missing,
  nothing is ever sent.
- **Sidebar panel (informational only).** Shows connection status, the
  backend's top flip recommendations (price, est. profit, ROI, risk, score
  and reason) and your latest completed offers. The only interactions are
  **Refresh**, **Open Wiki** (external browser) and **Copy** to the system
  clipboard. "Buy/Sell" appear strictly as data labels — there are no trade
  actions, no overlays, and nothing that clicks, types or guides input in
  the game client.
- **Capital sync (opt-in, OFF by default).** Optionally also reports
  inventory coins — and bank coins as *last seen* when **you** open the
  bank. Observation only; nothing is ever acted on.

## Configuration

Open RuneLite → Configuration → **RuneFlip Companion**:

| Setting | Meaning | Default |
| --- | --- | --- |
| Backend URL | RuneFlip API base URL. Point it at the public RuneFlip service or your own self-hosted backend. | `https://runeflip-api.onrender.com/api` |
| Ingest token | Filled automatically by **Pairing** (below). Self-hosted backends can still paste `OSRS_GE_INGEST_TOKEN` manually. Stored by RuneLite as a secret config value, never logged. **Not** a Jagex credential. Without it, slot sync stays off. | empty |
| Sync enabled | Master switch for GE slot sync. | on (inert until URL + token are set) |
| Heartbeat (seconds) | GE check cadence; unchanged snapshots are skipped. | 60 (min 30) |
| Keepalive (minutes) | Re-send an unchanged snapshot after this long. | 5 (min 1) |
| Capital sync (read-only) | Opt-in coins reporting described above. | **off** |
| Sidebar panel | Show the informational panel. | on |
| Panel refresh (seconds) | Panel re-fetch cadence while open. | 60 (min 30) |

The plugin also generates a random anonymous client id (a UUID) the first
time it runs and sends it as `X-RuneFlip-Client-Id` so the backend keeps
your data separate from other users. It identifies the *install*, not you:
no account name, no hardware info, no Jagex identity.

## Pairing

The easy way to connect (no config files, no manual tokens):

1. In the RuneFlip dashboard (web or mobile), open **Settings → RuneLite
   Pairing** and press **Generate pairing code**.
2. In RuneLite, open the RuneFlip sidebar panel → **Pairing**, paste the
   code and click **Pair**.

The code is one-time and expires after ~10 minutes. On success the plugin
adopts the dashboard's anonymous client id and receives an ingest token
scoped to it — stored as a secret RuneLite config value, never logged and
never displayed. **Unpair** revokes the token server-side and removes it
locally. Pairing exchanges only the pasted code over HTTPS; it reads and
writes nothing in the game.

## Data sent

Only to the backend URL **you** configure, and only:

- GE slot snapshots: slot index, item id/name, offer type, price, quantity,
  quantity filled, status.
- (If capital sync is enabled) inventory coins and last-seen bank coins.
- The anonymous client id header above.

No chat, no location, no skills, no inventory beyond coins, no friends
list, no credentials — nothing else is read or transmitted.

## Compliance (no-automation contract)

This plugin is built against a hard read-only contract:

- No game actions of any kind — never buys, sells, cancels, collects or
  confirms offers.
- No synthetic input — no `Robot`, no mouse/keyboard events, no menu
  invocation, no client scripts, no writing into the chatbox or GE search.
- No OCR, screenshots, screen or pixel reading — data comes exclusively
  from the official RuneLite plugin API.
- No credential access — it runs inside your already-authenticated client
  and never touches login or session data.
- One-way traffic — the backend can only answer with an HTTP status code;
  there is no way for it to command the plugin.

The panel's "Search item" button is intentionally **disabled**: there is no
safe, client-supported way to prefill the in-game GE search without
synthetic input, so the supported flow is **Copy name** + typing it
yourself in the official client.

## Build

Requires JDK 11+.

```bash
./gradlew clean test build   # plugin jar in build/libs/
```

To run against a development client, open the project in IntelliJ IDEA and
run `RuneFlipCompanionPluginTest.main()` (the standard RuneLite
external-plugin dev flow), then enable **RuneFlip Companion** in the plugin
list.

## License

[BSD 2-Clause](LICENSE).
