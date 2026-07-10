# RuneFlip Companion

A strictly **observation-only** RuneLite plugin that mirrors your 8 Grand
Exchange slots to a [RuneFlip](https://github.com/jumunozc) flipping
dashboard and shows the dashboard's market recommendations in a sidebar
panel.

RuneFlip is **manual-assisted, no botting**: it may assist input, but never
execute intent — every Grand Exchange offer is reviewed and confirmed
manually by you, in the official client.

One-way by design: it observes `GrandExchangeOffer` through the official
RuneLite plugin API and POSTs a JSON snapshot — the HTTP response is a bare
acknowledgement that is only logged. There is no command channel.

**It never** confirms, buys, sells, cancels or collects offers —
automatically or otherwise; never runs flipping loops; never clicks, types
or moves the mouse; never simulates input of any kind; never uses OCR,
screenshots, screen capture or pixel reading; never reads Jagex credentials
or session data; never receives commands from the backend.

## What it does

- **GE slot sync (observation-only).** Mirrors the state of your 8 GE slots
  (item, price, quantity, progress, status) to a RuneFlip backend so the
  dashboard can show them next to its market analysis. Sends only when a
  backend URL *and* an ingest token are configured — with either missing,
  nothing is ever sent.
- **Sidebar panel (informational only).** Shows connection status, the
  backend's top flip recommendations (price, est. profit, ROI, risk, score
  and reason) and your latest completed offers. The only interactions are
  **Refresh**, **Open Wiki** (external browser) and **Copy name** to the
  system clipboard. "Buy/Sell" appear strictly as data labels — there are no
  trade actions, no overlays, and nothing that clicks, types or guides input
  in the game client. (The old Copy price / Copy qty buttons were removed in
  v0.8.10: the game accepts no paste, so they assisted nothing.)
- **Primary GE suggestion (v0.8.10, display-only chip).** The **#1** item of
  the Fast Flip list is always marked as the single primary suggestion for
  your next GE search — a gold border plus a compact **"GE suggestion"**
  chip, Flipping-Copilot style. #2/#3 stay informational and cannot be
  selected as the suggestion.
- **Explicit GE Field Assist (v0.8.11, click-gated; visible since
  v0.8.13).** Official rule: **RuneFlip can prepare GE fields after explicit
  user action, but must never submit or execute the offer.** While the
  matching GE editor is open you get, Copilot-style:
  - a **visible chatbox hint** — `RuneFlip item: <item>` on the GE search
    (always the #1 suggestion), `Press [<key>] to set RuneFlip price:
    <price> gp` on the price editor (buy offers get the buy target, sell
    offers the sell target) and `Press [<key>] to set RuneFlip quantity:
    <qty>` on the quantity editor. The hint is clickable and only appears
    on the real GE prompts — an unknown editor shows nothing;
  - the **assist hotkey** (default **Right Brace `]`**, rebindable in the
    config if it conflicts): press it while the editor is open and the
    hinted value is prepared;
  - the right-click `RuneFlip: select / set qty / set price` options.

  All three prepare the pending typed value — exactly as if you had typed
  it — and print *"RuneFlip prepared the value. Review manually."* You
  still press Enter, still review, and still confirm every offer yourself.
  Your own key press is **listened** through RuneLite's KeyManager, never
  synthesized. Toggles: **GE field assist** (on) and **GE field assist
  hotkey**.
- **Context-aware GE panel (v0.8.4, focused in v0.8.5, display-only).** When you
  open an item in the Grand Exchange Buy/Sell setup, the panel shows *that
  item's* RuneFlip context: Wiki **low/high** legs, safe/quick/recommended
  targets, a **Wiki vs RuneFlip targets** comparison (buy anchored to the wiki
  low leg, sell to the wiki high leg — e.g. *"Target is 3 gp above Wiki low for
  faster fill"*), the recommended action, and a compact Qty / Cost / Profit /
  ROI / Time plan. With no item open, it shows the **Top 3 Fast Flips** (rank,
  item, buy→sell, expected profit, ROI, risk, action). The panel is **focused**:
  it shows one view at a time and hides the legacy recommendation dashboard and
  the GE-completed summary (turn the *Context-aware GE panel* setting off to keep
  the full legacy panel). Which item is open is learned by a **read-only** poll
  of the current-GE-item VarPlayer — no OCR, no screen scraping, no input. Items
  with no RuneFlip target show "No RuneFlip target yet" + Open Wiki. Requires a
  RuneFlip backend on **v0.8.4+** (`GET /fast-flip/item/:itemId`); against an
  older backend the panel keeps showing the Top 3.
- **Sell-context retention (v0.8.12).** RuneFlip keeps sell context after a
  buy so the player can close flips manually. The panel detects whether the
  open GE setup is a **Buy or a Sell** (read-only `GE_OFFER_CREATION_TYPE`
  varbit) and renders that side's context: a Sell setup is titled
  **"SELECTED GE ITEM · SELL"** and shows wiki low/high, the RuneFlip sell
  target, Safe/Quick sell, tax and the after-tax edge, ROI and the action
  (Review sell / Hold / Avoid). The last context per item is retained
  locally for ~30 minutes, so opening the Sell offer of a just-bought item
  renders instantly while a sell-focused refresh
  (`/fast-flip/item/:itemId?side=SELL`, backend v0.8.12+) runs in the
  background. A not-recommended answer no longer hides the card when sell
  targets exist — a low/negative after-tax edge shows *"Low or negative
  edge after tax. Review manually."* (amber/red) instead.
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
| Capital sync (observation) | Opt-in coins reporting described above. | **off** |
| Sidebar panel | Show the informational panel. | on |
| Context-aware GE panel | When you open an item in the GE setup, show a focused view: that item's RuneFlip context (wiki vs targets, action, ROI) or, with none open, the Top 3 — hiding the legacy dashboard and GE completed. Reads only the selected item id — never OCR, screen scraping or input. Off = full legacy panel. | on |
| GE field assist | Add right-click "RuneFlip: select / set qty / set price" options while a GE editor is open (v0.8.11). Prepares the typed value only, after your explicit click — you still press Enter and confirm every offer yourself. Never submits, cancels or collects. | on |
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

## Compliance (manual-assisted / no-botting contract)

Official rule: **RuneFlip is manual-assisted, no botting. It may assist
input, but never execute intent.** This plugin is built against a hard
observation-only contract:

- No game actions of any kind — never buys, sells, cancels, collects or
  confirms offers, automatically or otherwise; no flipping loops.
- No synthetic input — no `Robot`, no mouse/keyboard events, no menu
  invocation. The ONE permitted write (v0.8.11) is the click-gated field
  prepare in `GeFieldAssistService`: it sets the pending chatbox input text
  (as if you had typed it) after your explicit click on a "RuneFlip: …"
  option, in a verified GE editor, and runs only the input-redraw script —
  a build-time scan forbids those APIs in every other file and any other
  client script even there.
- No acting without human input — every assist is a menu OPTION; nothing
  runs until you click it, and nothing can run in the background.
- No OCR, screenshots, screen or pixel reading — data comes exclusively
  from the official RuneLite plugin API.
- No credential access — it runs inside your already-authenticated client
  and never touches login or session data.
- One-way traffic — the backend can only answer with an HTTP status code;
  there is no way for it to command the plugin.

The panel's legacy "Search item" button stays **disabled**: a panel-side
button cannot verify the GE editor at the moment of the click. The
supported path is the in-game one — with the GE search open, right-click
and choose **"RuneFlip: select …"** (v0.8.11). The context-aware GE panel
(v0.8.4) only **reads** which item you have open (the current-GE-item
VarPlayer) to pick what to display.

**Explicit GE Field Assist — how the write is contained (v0.8.11).**
`GeFieldAssistService` is the single class allowed to touch a game input,
and a build-time `ComplianceScanTest` enforces the whole posture: input
automation, menu invocation, server-var writes and OCR APIs
(`invokeMenuAction`/`KeyEvent`/`Robot`/screenshot/…) are forbidden in
**every** file; `setVarcStrValue`/`runScript` are forbidden **outside** the
service; and inside it the only script ever referenced must be the
input-redraw one (`CHAT_TEXT_INPUT_REBUILD`). Every prepare requires the
`USER_CLICK` action source (automatic/background execution is rejected) and
a verified open editor, offers only the #1 primary suggestion (item) or the
item you actually have open (qty/price), and never confirms, submits,
cancels, aborts or collects.

## Build

Requires JDK 11+.

```bash
./gradlew clean test build   # plugin jar in build/libs/
```

To run against a development client, open the project in IntelliJ IDEA and
run `RuneFlipCompanionPluginTest.main()` (the standard RuneLite
external-plugin dev flow), then enable **RuneFlip Companion** in the plugin
list.

## Manual preview install (Plugin Hub deferred)

RuneFlip Companion is **not on the RuneLite Plugin Hub yet** — the Plugin Hub
submission PR is intentionally deferred while the project stabilises. Until
it lands there, the plugin is distributed as a **preview build you sideload
yourself**:

1. Build the jar (see **Build** above): `./gradlew clean test build` produces
   `build/libs/runeflip-companion-<version>.jar` (currently
   `runeflip-companion-0.8.13.jar`).
2. Copy that jar into RuneLite's sideloaded-plugins folder:
   - Windows: `%USERPROFILE%\.runelite\sideloaded-plugins\`
   - macOS / Linux: `~/.runelite/sideloaded-plugins/`
3. Restart RuneLite, then enable **RuneFlip Companion** in the plugin list and
   configure it as described under **Configuration** / **Pairing**.

Sideloaded plugins run unsigned and are **not** vetted by the RuneLite team —
install only a jar you built (or trust). The default **Backend URL**
(`https://runeflip-api.onrender.com/api`) points at the public RuneFlip
service; point it at your own backend if you self-host.

> **v0.8.13** (2026-07): visible GE chatbox assist, Copilot-style. The field
> assist now shows a **chatbox hint** while the matching GE editor is open —
> `RuneFlip item: <item>` on the search (the #1 primary suggestion only),
> `Press [<key>] to set RuneFlip price: <price> gp` / `…quantity: <qty>` on
> the value editors — detected from the real prompts ("What would you like
> to buy/sell?", "Set a price for each item:", "How many do you wish to
> buy/sell?"); unknown prompts show nothing. New **GE field assist hotkey**
> (default Right Brace `]`, rebindable): pressed with the editor open it
> prepares the hinted value (`USER_HOTKEY` — your own key press, listened
> via RuneLite's KeyManager and quarantined in one file; RuneFlip never
> synthesizes input). The hint is also clickable. Write path unchanged:
> prepare-only, `GeFieldAssistService`, never submits/confirms/cancels.
> `gradlew clean test build` green (incl. the extended `ComplianceScanTest`),
> jar emitted.
>
> **v0.8.12** (2026-07): sell-context retention — **RuneFlip keeps sell
> context after a buy so the player can close flips manually.** In real play
> the panel dropped to *"No RuneFlip target yet"* right after a buy filled
> and the Sell offer of the same item was opened. Now the panel is
> side-aware (read-only `GE_OFFER_CREATION_TYPE` varbit): a Sell setup shows
> the sell-focused card ("SELECTED GE ITEM · SELL": wiki legs, sell target,
> Safe/Quick sell, tax, after-tax edge, ROI, Review sell / Hold / Avoid), a
> per-item context is retained locally ~30 min so buy→sell renders
> instantly while `?side=SELL` refreshes in the background (backend
> v0.8.12+, older backends degrade gracefully), and `recommended:false` no
> longer hides the card when sell targets exist — a low/negative after-tax
> edge shows an explicit amber/red warning instead. Read-only detection, no
> compliance change. `gradlew clean test build` green (incl.
> `FlipContextCacheTest`, `SellContextRetentionTest`, `ComplianceScanTest`),
> jar emitted.
>
> **v0.8.11** (2026-07): Explicit GE Field Assist. New official rule:
> **RuneFlip can prepare GE fields after explicit user action, but must
> never submit or execute the offer.** While the matching GE editor is open,
> right-click for `RuneFlip: select <item>` (the #1 primary suggestion, in
> the item search), `RuneFlip: set qty <qty>` and `RuneFlip: set price
> <price> gp` (open item only; buy offers use the buy target, sell offers
> the sell target). A click prepares the pending typed value — as if you had
> typed it — and prints *"RuneFlip prepared the value. Review manually."*;
> you still press Enter and confirm every offer yourself. The write is
> contained in `GeFieldAssistService` (the only file allowed to hold
> `setVarcStrValue`/`runScript`, scan-enforced; `USER_CLICK` + verified
> editor required; only the input-redraw script permitted). New **GE field
> assist** toggle (on). `gradlew clean test build` green (incl. the reworked
> `ComplianceScanTest` and the new `GeFieldAssistTest`), jar emitted.
>
> **v0.8.10** (2026-07): primary GE suggestion + Copy price/qty removal +
> responsiveness (this release supersedes the earlier v0.8.10 preview tag,
> which carried the responsiveness work alone).
> The **#1** Fast Flip row is now always the single **primary GE
> suggestion** — gold border + "GE suggestion" chip — for your next manual
> GE search; #2/#3 stay informational. The chip is display-only: the
> investigation concluded **Primary GE Search Assist blocked — no safe API
> found** (every route needs `setVarcStrValue`+`runScript`/widget mutation/
> synthetic input, all forbidden), so nothing is written into the game and
> no price/quantity is ever set. The v0.8.3 clipboard **Copy price / Copy
> qty buttons were removed everywhere** (the game accepts no paste, so they
> assisted nothing in real play); Copy name and Open Wiki stay, and the
> retired `enableAssistedOfferSetup` config key survives hidden, read by no
> code. Also bundles the responsiveness work: strategy pills highlight
> optimistically on click (with an "Updating…" indicator), stale responses
> are dropped so rapid clicks render only the last strategy, short
> in-memory caches (20s) make pill toggles and item re-selections instant,
> and opening a GE item shows "Loading item context…" immediately.
> `gradlew clean test build` green (incl. `ComplianceScanTest` and the new
> `PrimaryGeSuggestionTest`), jar emitted.
>
> **v0.8.9** (2026-07): header hotfix — in the narrow sidebar, "Connected" and
> "Refresh" overlapped the "RuneFlip v0.8.8" wordmark. The header now stacks
> two rows (wordmark + version, then status + Refresh); layout only, no logic
> change. `gradlew clean test build` green (117 tests, incl.
> `ComplianceScanTest`), jar emitted.
>
> **v0.8.8** (2026-07): the contextual panel now implements RuneFlip's official
> panel design. New **strategy pills** (5m/30m/2h/8h · Low/Med/High) tune the
> read-only fetch locally (never written to the backend); Top-3 rows become
> compact cards with rank/action/risk chips and highlighted profit; the
> selected-item card gains WIKI / TARGET / EDGE VS WIKI / PLAN / ACTION
> sections; and a new **Session panel** answers "how did this session go?"
> (profit, flips, ROI, session time, hourly rate — computed only from offers
> you completed yourself; login replay never counts). Empty state redesigned
> with a strategy chip + relax tips. Compliance unchanged: display-only,
> clipboard-only opt-in copy buttons, *"Review manually. RuneFlip never
> confirms trades."* `gradlew clean test build` green (116 tests, incl.
> `ComplianceScanTest`), jar emitted.
>
> **v0.8.7** (2026-07): hotfix — fixes the REAL cause of "Fast flip · 0": the
> backend sends `dumpRisk`/`competitionRisk` as grade strings (`"LOW"`…), but
> the plugin DTO typed them as numbers, so parsing **every** real overview
> payload failed and rendered as an empty panel. The DTO now matches the
> production contract, pinned by a real sanitized production fixture in the
> test suite. A failed fetch now shows *"Could not reach the RuneFlip
> backend."* (never "no matches"), a saved strategy that matches nothing falls
> back to the default-strategy ideas (labelled), the card heading names its
> source ("Top 3 Fast Flips" / "General ideas") and a compact strategy echo
> ("8h · HIGH risk") tops the rows. `gradlew clean test build` green (96
> tests, incl. `ComplianceScanTest`), jar emitted.
>
> **v0.8.6** (2026-07): hotfix — the contextual panel no longer reads
> "Fast flip · 0" while the web still lists Top flips. It now falls back from an
> empty Top ranking to fast-buy/-sell **"General ideas"**, and when nothing
> matches it shows the current strategy, a **"No matches for current strategy"**
> line, a concrete relax hint (Medium risk, 30m, lower min profit) and a Refresh
> button instead of a blank box — so a restrictive saved strategy is never
> silently empty. Footer reads *"Review manually. RuneFlip never confirms
> trades."* Display-only, no backend change; `gradlew clean test build` green
> (85 tests, incl. `ComplianceScanTest`), jar emitted.
>
> **v0.8.5** (2026-07): corrects the Wiki vs RuneFlip comparison (buy anchored to
> the wiki low leg, sell to the high leg) and **focuses** the contextual panel —
> it shows only the selected-item card or the Top 3 and hides the legacy
> dashboard + GE completed; the selected card and Top-3 rows were compacted.
> `gradlew clean test build` green (74 tests, incl. `ComplianceScanTest`), jar
> emitted. The contextual panel needs a RuneFlip backend on v0.8.4+
> (`/fast-flip/item/:itemId`); until the public backend is updated it degrades
> gracefully to the Top 3. Previously smoke-tested against production for v0.8.3
> (`strategy` echo, recommended-`action`, `/pairing`, `/strategy/preferences`,
> `/ge-slots/snapshot` verified live). Assisted Offer Setup stays **OFF by
> default** and clipboard-only. See `docs/runelite-readonly-contract.md` in the
> monorepo for the full contract.

## License

[BSD 2-Clause](LICENSE).
