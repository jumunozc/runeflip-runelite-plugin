package com.runeflip.companion;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.time.Instant;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.util.AsyncBufferedImage;
import net.runelite.client.util.LinkBrowser;
import net.runelite.client.util.QuantityFormatter;

/**
 * RuneFlip sidebar — an INFORMATIONAL mirror of what the user's own backend
 * computed (recommendations, capital, completed offers). Read-only by
 * construction: the only interactions are Refresh (re-fetch), Copy
 * (clipboard) and Open Wiki (external browser). Nothing here can click,
 * type, trade or otherwise touch the game — see
 * docs/runelite-readonly-contract.md.
 */
public class RuneFlipPanel extends PluginPanel
{
	// ── Design tokens (v0.8.7, docs/design/runelite-panel-v0.8.7.md) ────────
	private static final Color PANEL_BG = new Color(0x15, 0x17, 0x1d);
	private static final Color HEADER_BG = new Color(0x18, 0x1b, 0x21);
	private static final Color CARD_BG = new Color(0x1b, 0x1e, 0x25);
	private static final Color CARD_BORDER = new Color(0x2a, 0x2e, 0x37);
	private static final Color CELL_BG = new Color(0x23, 0x26, 0x2e);
	private static final Color PROFIT = new Color(0x4c, 0xba, 0x86);
	private static final Color GOLD = new Color(0xe3, 0xb7, 0x5d);
	private static final Color ALERT = new Color(0xe8, 0xa0, 0x4a);
	private static final Color RED = new Color(0xe0, 0x67, 0x67);
	private static final Color BLUE = new Color(0x9f, 0xb6, 0xef);
	private static final Color TEXT = new Color(0xe7, 0xe9, 0xef);
	private static final Color CREAM = new Color(0xf0, 0xe6, 0xd2);
	private static final Color MUTED = new Color(0x8b, 0x91, 0xa0);
	private static final Color FAINT = new Color(0x5c, 0x62, 0x70);
	// Chip backgrounds: the design's 16% accent tints blended over CARD_BG.
	private static final Color CHIP_GREEN_BG = new Color(0x23, 0x37, 0x35);
	private static final Color CHIP_AMBER_BG = new Color(0x3c, 0x33, 0x2b);
	private static final Color CHIP_RED_BG = new Color(0x3b, 0x2a, 0x30);
	private static final Color CHIP_BLUE_BG = new Color(0x2e, 0x33, 0x42);
	private static final Color CHIP_GOLD_BG = new Color(0x33, 0x30, 0x2c);

	private final ItemManager itemManager;
	private final Runnable onRefresh;
	private final PairingActions pairingActions;
	private final DesignActions designActions;

	private final JLabel statusLabel = new JLabel("Offline");
	private final JLabel capitalLabel = new JLabel(" ");
	private final JPanel capitalRow;
	/** StrategyPill (v0.8.7 design): timeframe + risk pills, contextual 1a. */
	private final JPanel strategyPill = new JPanel();
	/** SessionPanel (v0.8.7 design): full KPI block (1a/1c) and the collapsed
	 *  one-liner (1b). Rebuilt from the latest {@link SessionTracker.Stats}. */
	private final JPanel sessionPanel = new JPanel();
	private final JPanel sessionCompactRow = new JPanel(new BorderLayout(6, 0));
	private final JLabel sessionCompactProfit = new JLabel(" ");
	/** Context-aware GE item card (v0.8.4) — shown only while an item is open
	 *  in the GE Buy/Sell setup; hidden (zero space) otherwise. */
	private final JLabel selectedHeader = new JLabel("SELECTED GE ITEM");
	private final JPanel selectedCard = new JPanel();
	private final JPanel topCard = new JPanel();
	private final JPanel listPanel = new JPanel();
	private final JLabel fastFlipHeader = new JLabel("Fast flip · 0");
	private final JPanel fastFlipCard = new JPanel();
	private final JLabel completedHeader = new JLabel("GE completed · 0");
	private final JPanel completedPanel = new JPanel();
	private final JLabel disclaimer = new JLabel();

	private final JLabel pairingStatus = new JLabel("Not paired");
	private final javax.swing.JTextField pairingCodeField = new javax.swing.JTextField();
	private final JButton pairButton;
	private final JButton unpairButton;
	private final JPanel pairingInputRow = new JPanel(new BorderLayout(6, 0));
	private final JLabel pairingHint = new JLabel();

	/** Context-aware GE panel opt-in (v0.8.5), refreshed from config. When ON,
	 *  the panel is focused: only the selected-item card OR the Top 3, never the
	 *  legacy dashboard or GE completed. OFF keeps the full legacy panel. */
	private boolean contextualMode = false;
	/** Whether an item is currently open in the GE Buy/Sell setup (v0.8.5). */
	private boolean hasSelection = false;
	/** Latest session KPIs (v0.8.7); null until the plugin pushes them. */
	private SessionTracker.Stats sessionStats;
	/** Portfolio coins (inventory + bank last seen) from the capital fetch;
	 *  null when capital sync is off or nothing was observed ("si existe"). */
	private Long portfolioCoins;
	/** ACTIVE strategy echoed by the last overview response — drives which
	 *  timeframe/risk pill renders highlighted. Never derived client-side.
	 *  A pill click sets it OPTIMISTICALLY (v0.8.10) so the UI reacts at once;
	 *  the next response echo confirms or corrects it. */
	private Integer activeTimeframe;
	private String activeRisk;
	/** In-flight indicator (v0.8.10), visible from pill click to response. */
	private final JLabel updatingLabel = new JLabel(UPDATING_LINE);

	/** Rows shown in the compact completed summary; the rest is "+n more". */
	private static final int MAX_COMPLETED_ROWS = 3;
	/** Entries shown in the compact Fast Flip card (backend sends up to 3).
	 *  Package-visible so the plugin can pre-check a response with the same cap. */
	static final int MAX_FAST_FLIP_ROWS = 3;
	/** Shown verbatim when the backend omits its own disclaimer string. */
	static final String FAST_FLIP_DISCLAIMER =
		"Fast flip estimates are informational. Review manually before trading.";
	/** Price Edge fallback disclaimer (v0.7.1), same rule as above. */
	static final String PRICE_EDGE_DISCLAIMER =
		"Targets are estimates. Review manually.";
	/**
	 * Primary GE suggestion chip (v0.8.10) — the compact marker on the FIRST
	 * row of the Fast Flip list. The #1 item is RuneFlip's single primary
	 * suggestion for the user's next manual GE search, Flipping-Copilot style;
	 * #2/#3 stay informational and are never selectable as a suggestion.
	 *
	 * <p>The chip itself is DISPLAY-ONLY. Since v0.8.11 the #1 can also be
	 * prepared into the in-game GE item search — but only through the
	 * "RuneFlip: select …" right-click option and only on the user's explicit
	 * click, encapsulated in {@link GeFieldAssistService} (official rule:
	 * RuneFlip can prepare GE fields after explicit user action, but must
	 * never submit or execute the offer). The panel writes nothing into the
	 * game.
	 */
	static final String GE_SUGGESTION_CHIP = "GE suggestion";
	/** Short contextual-card footer (v0.8.5) — the compliance rule, compact. */
	static final String SHORT_DISCLAIMER = "Review manually.";
	/** Sell-context warning (v0.8.12): shown amber (zero) / red (negative)
	 *  when the after-tax edge does not clear — never hides the card. */
	static final String LOW_EDGE_WARNING =
		"Low or negative edge after tax. Review manually.";
	/** Fast Flip card footer (v0.8.5-c) — the compliance rule in one short line,
	 *  wide enough that "never confirms trades" is unmistakable in the sidebar. */
	static final String FAST_FLIP_FOOTER =
		"Review manually. RuneFlip never confirms trades.";
	/** Empty Fast Flip state (v0.8.5-c): shown when the current strategy matches
	 *  nothing and the overview has no fallback candidates either. */
	static final String NO_MATCH_LINE = "No matches for current strategy";
	/** Relax tips rendered as bullets in the EmptyState (v0.8.7 design). */
	static final String[] RELAX_TIPS = {
		"Try Medium risk",
		"Try 30m timeframe",
		"Lower min profit",
	};
	/** Banner over the fast-buy/-sell fallback rows (v0.8.5-c). */
	static final String GENERAL_IDEAS_LABEL = "General ideas";
	/** Offline Fast Flip state (v0.8.6): a FAILED overview fetch is not "no
	 *  matches" — it must say so instead of blaming the strategy. */
	static final String OFFLINE_LINE = "Could not reach the RuneFlip backend.";
	static final String OFFLINE_HINT_LINE =
		"Check the Backend URL in the plugin settings, then Refresh.";
	/** Shown when the saved strategy matched nothing and the panel fell back to
	 *  the default-strategy ideas instead of an empty box (v0.8.6). */
	static final String DEFAULT_FALLBACK_NOTE =
		"Saved strategy matched nothing — showing default strategy.";
	/** SessionPanel before any completed offer this session (v0.8.7). */
	static final String SESSION_EMPTY_LINE =
		"No completed flips yet this session.";
	/** Selected-item loading state (v0.8.10): shown the instant a GE item is
	 *  detected, while its context fetch runs in the background. */
	static final String ITEM_LOADING_LINE = "Loading item context…";
	/** Selected card fallback (v0.8.14): the context fetch failed with nothing
	 *  cached — the SELECTION IS KEPT and says so; never a silent fall back
	 *  to the Top 3. */
	static final String ITEM_UNAVAILABLE_LINE =
		"Could not load RuneFlip context. Keeping your selection — Refresh to retry.";
	/** Discreet stale note (v0.8.14): the live refresh failed; the retained
	 *  context stays on screen instead of being wiped. */
	static final String STALE_CONTEXT_NOTE =
		"Live refresh failed — showing the last RuneFlip context.";
	/** Small in-flight indicator next to the Top-3 header (v0.8.10): shown
	 *  from a pill click until the matching response renders — stale rows are
	 *  never mistaken for the new strategy, and "no matches" never shows for a
	 *  request still in flight. */
	static final String UPDATING_LINE = "Updating…";
	/** Hard cap so one absurd name can never distort the narrow sidebar. */
	private static final int MAX_NAME_CHARS = 40;
	/** Shown in the header next to the wordmark (v0.8.7 design). Must match
	 *  build.gradle's version — pinned by RuneFlipPanelTextTest. */
	static final String PLUGIN_VERSION = "0.8.14";

	/**
	 * Pairing callbacks implemented by the plugin (v0.6.3). Both are
	 * user-click only, touch nothing but HTTP + plugin config, and report
	 * back with a display message — the token itself never passes through
	 * the panel.
	 */
	interface PairingActions
	{
		void pair(String code, java.util.function.Consumer<String> onResult);

		void unpair(java.util.function.Consumer<String> onResult);
	}

	/**
	 * Design-panel callbacks (v0.8.7), implemented by the plugin. All three are
	 * user-click only and display-only: the pills change WHICH read-only fetch
	 * the panel makes (local config + HTTP), the reset clears the local session
	 * accounting. Nothing here can reach the game.
	 */
	interface DesignActions
	{
		void onTimeframePill(int minutes);

		void onRiskPill(String riskLevel);

		void onSessionReset();
	}

	public RuneFlipPanel(
		ItemManager itemManager,
		Runnable onRefresh,
		PairingActions pairingActions,
		DesignActions designActions,
		boolean initiallyPaired)
	{
		this.itemManager = itemManager;
		this.onRefresh = onRefresh;
		this.pairingActions = pairingActions;
		this.designActions = designActions;
		this.pairButton = smallButton("Pair");
		this.unpairButton = smallButton("Unpair");

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(PANEL_BG);
		setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

		// ── Header (design, hotfix v0.8.9): two rows on the header band so the
		//    wordmark, status and Refresh never overlap in the ~225px sidebar.
		//    Row 1: RuneFlip wordmark + discreet version.
		//    Row 2: Connected/Stale/Offline status left, small Refresh right.
		JPanel header = new JPanel();
		header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
		header.setOpaque(true);
		header.setBackground(HEADER_BG);
		header.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createMatteBorder(0, 0, 1, 0, CARD_BORDER),
			BorderFactory.createEmptyBorder(5, 6, 5, 6)));

		JPanel headerTitleRow = new JPanel(new BorderLayout());
		headerTitleRow.setOpaque(false);
		headerTitleRow.setAlignmentX(LEFT_ALIGNMENT);
		JLabel title = new JLabel(html(
			"<span style='color:#f0e6d2'><b>Rune</b></span>"
				+ "<span style='color:#e3b75d'><b>Flip</b></span>"
				+ " <span style='color:#5c6270'>v" + PLUGIN_VERSION + "</span>"));
		title.setFont(FontManager.getRunescapeBoldFont());
		headerTitleRow.add(title, BorderLayout.WEST);
		headerTitleRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
		header.add(headerTitleRow);
		header.add(Box.createVerticalStrut(3));

		JPanel headerStatusRow = new JPanel(new BorderLayout(6, 0));
		headerStatusRow.setOpaque(false);
		headerStatusRow.setAlignmentX(LEFT_ALIGNMENT);
		statusLabel.setFont(FontManager.getRunescapeSmallFont());
		statusLabel.setForeground(MUTED);
		headerStatusRow.add(statusLabel, BorderLayout.WEST);
		JButton refresh = smallButton("Refresh");
		refresh.addActionListener(e -> onRefresh.run());
		headerStatusRow.add(refresh, BorderLayout.EAST);
		headerStatusRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
		header.add(headerStatusRow);

		header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 52));
		header.setAlignmentX(LEFT_ALIGNMENT);
		add(header);
		add(Box.createVerticalStrut(8));

		// ── StrategyPill (v0.8.7 design, contextual 1a) ─────────────────────
		buildStrategyPill();
		add(strategyPill);
		add(Box.createVerticalStrut(8));
		strategyPill.setVisible(false);

		// ── Capital (read-only observation; legacy mode — the contextual
		//    panel shows it as the Session "Portfolio" row instead) ──────────
		capitalLabel.setFont(FontManager.getRunescapeSmallFont());
		capitalLabel.setForeground(MUTED);
		capitalRow = wrap(capitalLabel);
		add(capitalRow);
		add(Box.createVerticalStrut(8));

		// ── Context-aware GE item (v0.8.4 — display only) ───────────────────
		selectedHeader.setFont(FontManager.getRunescapeSmallFont());
		selectedHeader.setForeground(MUTED);
		add(wrap(selectedHeader));
		add(Box.createVerticalStrut(4));
		selectedCard.setLayout(new BoxLayout(selectedCard, BoxLayout.Y_AXIS));
		selectedCard.setBackground(CARD_BG);
		// Gold-tinted border (design 1b): the selected card is the focus.
		selectedCard.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(new Color(0x4f, 0x44, 0x2f)),
			BorderFactory.createEmptyBorder(8, 8, 8, 8)));
		selectedCard.setAlignmentX(LEFT_ALIGNMENT);
		add(selectedCard);
		add(Box.createVerticalStrut(8));
		selectedHeader.setVisible(false);
		selectedCard.setVisible(false);

		// ── Pairing (v0.6.3 — config + HTTP only, never touches the game) ──
		JPanel pairingHeader = new JPanel(new BorderLayout());
		pairingHeader.setOpaque(false);
		JLabel pairingTitle = new JLabel("Pairing");
		pairingTitle.setFont(FontManager.getRunescapeSmallFont());
		pairingTitle.setForeground(MUTED);
		pairingHeader.add(pairingTitle, BorderLayout.WEST);
		JPanel pairingHeaderRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
		pairingHeaderRight.setOpaque(false);
		pairingStatus.setFont(FontManager.getRunescapeSmallFont());
		pairingHeaderRight.add(pairingStatus);
		pairingHeaderRight.add(unpairButton);
		pairingHeader.add(pairingHeaderRight, BorderLayout.EAST);
		pairingHeader.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
		pairingHeader.setAlignmentX(LEFT_ALIGNMENT);
		add(pairingHeader);
		add(Box.createVerticalStrut(4));

		pairingCodeField.setFont(FontManager.getRunescapeSmallFont());
		pairingCodeField.setToolTipText(
			"Paste the pairing code from RuneFlip Settings → RuneLite Pairing");
		pairingInputRow.setOpaque(false);
		pairingInputRow.add(pairingCodeField, BorderLayout.CENTER);
		pairingInputRow.add(pairButton, BorderLayout.EAST);
		pairingInputRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
		pairingInputRow.setAlignmentX(LEFT_ALIGNMENT);
		add(pairingInputRow);
		add(Box.createVerticalStrut(4));

		pairingHint.setFont(FontManager.getRunescapeSmallFont());
		pairingHint.setForeground(MUTED);
		add(wrap(pairingHint));
		add(Box.createVerticalStrut(8));

		pairButton.addActionListener(e -> submitPairing());
		unpairButton.addActionListener(e ->
		{
			unpairButton.setEnabled(false);
			pairingActions.unpair(message ->
			{
				unpairButton.setEnabled(true);
				pairingHint.setText(html(message));
			});
		});
		setPaired(initiallyPaired);

		// ── Top recommendation card ─────────────────────────────────────────
		topCard.setLayout(new BoxLayout(topCard, BoxLayout.Y_AXIS));
		topCard.setBackground(CARD_BG);
		topCard.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		add(topCard);
		add(Box.createVerticalStrut(8));

		// ── Compact list ────────────────────────────────────────────────────
		listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
		listPanel.setOpaque(false);
		add(listPanel);
		add(Box.createVerticalStrut(10));

		// ── Fast Flip (v0.7.0 — backend-computed, display only) ────────────
		// Section header row (v0.8.7 design): title left, ↻ Refresh link right.
		fastFlipHeader.setFont(FontManager.getRunescapeSmallFont());
		fastFlipHeader.setForeground(MUTED);
		JPanel fastFlipHeaderRow = new JPanel(new BorderLayout(6, 0));
		fastFlipHeaderRow.setOpaque(false);
		fastFlipHeaderRow.add(fastFlipHeader, BorderLayout.CENTER);
		// A JButton styled as a link — plain ActionListener, no low-level input
		// APIs (the compliance scan forbids those even for panel-internal use).
		JPanel headerRowRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 4, 0));
		headerRowRight.setOpaque(false);
		updatingLabel.setFont(FontManager.getRunescapeSmallFont());
		updatingLabel.setForeground(GOLD);
		updatingLabel.setVisible(false);
		headerRowRight.add(updatingLabel);
		JButton refreshLink = linkButton("↻ Refresh", GOLD, onRefresh);
		headerRowRight.add(refreshLink);
		fastFlipHeaderRow.add(headerRowRight, BorderLayout.EAST);
		fastFlipHeaderRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
		fastFlipHeaderRow.setAlignmentX(LEFT_ALIGNMENT);
		add(fastFlipHeaderRow);
		add(Box.createVerticalStrut(4));
		fastFlipCard.setLayout(new BoxLayout(fastFlipCard, BoxLayout.Y_AXIS));
		fastFlipCard.setOpaque(false);
		fastFlipCard.setAlignmentX(LEFT_ALIGNMENT);
		JLabel fastFlipLoading = new JLabel("Loading…");
		fastFlipLoading.setFont(FontManager.getRunescapeSmallFont());
		fastFlipLoading.setForeground(MUTED);
		fastFlipCard.add(fastFlipLoading);
		add(fastFlipCard);
		add(Box.createVerticalStrut(10));

		// ── SessionPanel (v0.8.7 design) — full KPI block + collapsed row ──
		sessionPanel.setLayout(new BoxLayout(sessionPanel, BoxLayout.Y_AXIS));
		sessionPanel.setBackground(CARD_BG);
		sessionPanel.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(CARD_BORDER),
			BorderFactory.createEmptyBorder(7, 9, 8, 9)));
		sessionPanel.setAlignmentX(LEFT_ALIGNMENT);
		add(sessionPanel);
		add(Box.createVerticalStrut(8));
		sessionPanel.setVisible(false);

		sessionCompactRow.setBackground(CARD_BG);
		sessionCompactRow.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(CARD_BORDER),
			BorderFactory.createEmptyBorder(6, 9, 6, 9)));
		JLabel sessionCompactTitle = new JLabel("SESSION");
		sessionCompactTitle.setFont(FontManager.getRunescapeSmallFont());
		sessionCompactTitle.setForeground(MUTED);
		sessionCompactRow.add(sessionCompactTitle, BorderLayout.WEST);
		sessionCompactProfit.setFont(FontManager.getRunescapeSmallFont());
		sessionCompactProfit.setHorizontalAlignment(SwingConstants.RIGHT);
		sessionCompactRow.add(sessionCompactProfit, BorderLayout.EAST);
		sessionCompactRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		sessionCompactRow.setAlignmentX(LEFT_ALIGNMENT);
		add(sessionCompactRow);
		add(Box.createVerticalStrut(8));
		sessionCompactRow.setVisible(false);

		// ── Completed offers (compact, visually secondary) ─────────────────
		completedHeader.setFont(FontManager.getRunescapeSmallFont());
		completedHeader.setForeground(MUTED);
		add(wrap(completedHeader));
		add(Box.createVerticalStrut(4));
		completedPanel.setLayout(new BoxLayout(completedPanel, BoxLayout.Y_AXIS));
		completedPanel.setOpaque(false);
		add(completedPanel);
		add(Box.createVerticalStrut(10));

		disclaimer.setText(html(
			"Informational only. RuneFlip never buys, sells or acts — "
				+ "review every flip manually in the official client."));
		disclaimer.setFont(FontManager.getRunescapeSmallFont());
		disclaimer.setForeground(MUTED);
		add(wrap(disclaimer));

		showLoading();
	}

	// ── state updates (always called on the EDT) ─────────────────────────────

	/**
	 * Paired = the plugin holds a pairing-issued token in its LOCAL config.
	 * Display only; the token value never reaches the panel.
	 */
	void setPaired(boolean paired)
	{
		pairingStatus.setText(paired ? "Paired" : "Not paired");
		pairingStatus.setForeground(paired ? PROFIT : MUTED);
		pairingInputRow.setVisible(!paired);
		unpairButton.setVisible(paired);
		if (paired)
		{
			pairingCodeField.setText("");
			pairingHint.setText(html(
				"GE slot sync is connected to your RuneFlip dashboard."));
		}
		else
		{
			pairingHint.setText(html(
				"Generate a code in RuneFlip Settings → RuneLite Pairing "
					+ "(web or mobile) and paste it here."));
		}
		revalidate();
		repaint();
	}

	private void submitPairing()
	{
		String code = pairingCodeField.getText() == null
			? ""
			: pairingCodeField.getText().trim();
		if (code.isEmpty())
		{
			pairingHint.setText(html("Paste a pairing code first."));
			return;
		}
		pairButton.setEnabled(false);
		pairingHint.setText(html("Pairing…"));
		pairingActions.pair(code, message ->
		{
			pairButton.setEnabled(true);
			pairingHint.setText(html(message));
		});
	}

	void showLoading()
	{
		statusLabel.setText("Loading…");
		statusLabel.setForeground(MUTED);
	}

	void showStatus(boolean ok)
	{
		statusLabel.setText(ok ? "Connected" : "Offline");
		statusLabel.setForeground(ok ? PROFIT : new Color(0xe2, 0x6a, 0x5e));
	}

	void showStale()
	{
		statusLabel.setText("Stale");
		statusLabel.setForeground(GOLD);
	}

	void updateCapital(RuneFlipData.CapitalLatestResponse response, String capitalSource)
	{
		RuneFlipData.Capital capital = response == null ? null : response.capital;
		// Session "Portfolio (coins)" row (v0.8.7 design, "si existe"): the sum
		// of the observed coin figures; null (row hidden) when nothing observed.
		this.portfolioCoins = portfolioCoinsOf(capital);
		rebuildSession();
		if (capital == null)
		{
			capitalLabel.setText(html(
				"Capital: manual / default (enable Capital sync in the plugin "
					+ "settings for an observed estimate)"));
			return;
		}
		StringBuilder sb = new StringBuilder("Capital");
		if (capitalSource != null)
		{
			sb.append(" (").append(capitalSource).append(')');
		}
		sb.append(": ");
		sb.append(capital.inventoryCoins != null
			? QuantityFormatter.quantityToStackSize(capital.inventoryCoins) + " inv"
			: "inv —");
		sb.append(" · ");
		sb.append(capital.bankCoinsLastSeen != null
			? QuantityFormatter.quantityToStackSize(capital.bankCoinsLastSeen) + " bank (last seen)"
			: "bank not seen yet");
		capitalLabel.setText(html(sb.toString()));
	}

	// ── Context-aware GE item (v0.8.4) ───────────────────────────────────────

	/**
	 * Shows the RuneFlip context for the item the user just opened in the GE
	 * Buy/Sell setup. Display only: every price, comparison string and figure
	 * comes verbatim from GET /fast-flip/item/:itemId — the plugin renders them
	 * and never acts on the game. When the backend does not stand behind the
	 * targets the side needs (AVOID / not recommended / no data) it shows "No
	 * RuneFlip target yet" plus Open Wiki, honestly — but a SELL setup with
	 * sell targets available keeps the full card (v0.8.12): the player needs
	 * them to CLOSE the flip they already opened.
	 */
	void updateSelectedItem(RuneFlipData.FastFlipItemContextResponse response)
	{
		updateSelectedItem(response, null);
	}

	/** Side-aware variant (v0.8.12): "BUY"/"SELL" from the GE setup varbit;
	 *  null keeps the pre-v0.8.12 entry-side rendering. */
	void updateSelectedItem(
		RuneFlipData.FastFlipItemContextResponse response, String side)
	{
		selectedCard.removeAll();
		if (response == null)
		{
			clearSelectedItem();
			return;
		}
		this.hasSelection = true;
		buildSelectedCard(response, side);
		applyVisibility();
	}

	/** Hides the context card (no item open in the GE setup, or the lookup
	 *  failed) — the sidebar falls back to the Top-3 Fast Flip list. */
	void clearSelectedItem()
	{
		selectedCard.removeAll();
		this.hasSelection = false;
		applyVisibility();
	}

	/**
	 * Selected-item LOADING state (v0.8.10): swaps the panel to the context
	 * card the instant a GE item is detected, with a "Loading item context…"
	 * placeholder while the fetch runs in the background — the user's action
	 * reflects immediately instead of after a network round-trip.
	 */
	void showSelectedItemLoading()
	{
		selectedCard.removeAll();
		this.hasSelection = true;
		JLabel loading = new JLabel(html(safe(ITEM_LOADING_LINE)));
		loading.setFont(FontManager.getRunescapeSmallFont());
		loading.setForeground(MUTED);
		loading.setAlignmentX(LEFT_ALIGNMENT);
		selectedCard.add(loading);
		applyVisibility();
	}

	/**
	 * Selected-item UNAVAILABLE state (v0.8.14): the context fetch failed (and
	 * its no-side compatibility retry too) with nothing cached to show. The
	 * selection is KEPT — the card says so instead of silently falling back
	 * to the Top 3, which read as the panel "losing" the item.
	 */
	void showSelectedItemUnavailable()
	{
		selectedCard.removeAll();
		this.hasSelection = true;
		JLabel line = new JLabel(html(safe(ITEM_UNAVAILABLE_LINE)));
		line.setFont(FontManager.getRunescapeSmallFont());
		line.setForeground(ALERT);
		line.setAlignmentX(LEFT_ALIGNMENT);
		selectedCard.add(line);
		applyVisibility();
	}

	/**
	 * Appends one discreet amber notice to the visible selected card
	 * (v0.8.14) — e.g. "live refresh failed, showing the last context" —
	 * without rebuilding or discarding what is already rendered.
	 */
	void appendSelectedNotice(String notice)
	{
		if (!hasSelection || notice == null || notice.isEmpty())
		{
			return;
		}
		JLabel label = new JLabel(html(
			"<span style='color:#e8a04a'>" + safe(notice) + "</span>"));
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setAlignmentX(LEFT_ALIGNMENT);
		selectedCard.add(label);
		selectedCard.revalidate();
		selectedCard.repaint();
	}

	/**
	 * Optimistic timeframe-pill highlight (v0.8.10): the clicked value renders
	 * active immediately (null = toggle-off, no highlight until the response
	 * echo names the effective strategy) and the "Updating…" indicator shows
	 * until that response renders. Local display state only.
	 */
	void showStrategyPendingTimeframe(Integer optimisticMinutes)
	{
		this.activeTimeframe = optimisticMinutes;
		buildStrategyPill();
		updatingLabel.setVisible(true);
		revalidateAll();
	}

	/** Optimistic risk-pill highlight (v0.8.10) — same contract as above. */
	void showStrategyPendingRisk(String optimisticRisk)
	{
		this.activeRisk = optimisticRisk;
		buildStrategyPill();
		updatingLabel.setVisible(true);
		revalidateAll();
	}

	/**
	 * Applies the contextual-panel focus (v0.8.5). Contextual mode shows exactly
	 * the selected-item card OR the Top 3 (header/pairing/footer always stay) and
	 * hides the legacy dashboard + GE completed; legacy mode keeps everything.
	 * The visibility rules live in {@link PanelVisibility} (pure, unit-tested).
	 */
	void setContextualMode(boolean contextual)
	{
		this.contextualMode = contextual;
		applyVisibility();
	}

	private void applyVisibility()
	{
		boolean legacy = PanelVisibility.showLegacyDashboard(contextualMode);
		boolean completed = PanelVisibility.showGeCompleted(contextualMode);
		boolean selected =
			PanelVisibility.showSelectedItem(contextualMode, hasSelection);
		boolean topThree =
			PanelVisibility.showTopThree(contextualMode, hasSelection);

		topCard.setVisible(legacy);
		listPanel.setVisible(legacy);
		completedHeader.setVisible(completed);
		completedPanel.setVisible(completed);
		selectedHeader.setVisible(selected);
		selectedCard.setVisible(selected);
		fastFlipHeader.setVisible(topThree);
		fastFlipCard.setVisible(topThree);
		// The header row wraps the Top-3 title + refresh link.
		java.awt.Container headerRow = fastFlipHeader.getParent();
		if (headerRow instanceof JPanel)
		{
			headerRow.setVisible(topThree);
		}

		// v0.8.7 design: pills + session in contextual mode, capital line only
		// in legacy (contextual shows it as the Session "Portfolio" row).
		strategyPill.setVisible(
			PanelVisibility.showStrategyPill(contextualMode, hasSelection));
		sessionPanel.setVisible(
			PanelVisibility.showSessionFull(contextualMode, hasSelection));
		sessionCompactRow.setVisible(
			PanelVisibility.showSessionCompact(contextualMode, hasSelection));
		capitalRow.setVisible(!contextualMode);

		// Footer: the design's fixed compliance line in contextual mode, the
		// original long-form sentence in legacy.
		disclaimer.setText(html(contextualMode
			? safe(FAST_FLIP_FOOTER)
			: "Informational only. RuneFlip never buys, sells or acts — "
				+ "review every flip manually in the official client."));
		revalidateAll();
	}

	// ── StrategyPill (v0.8.7 design) ─────────────────────────────────────────

	/**
	 * Timeframe (5m/30m/2h/8h) + risk (Low/Med/High) pills. Clicking a pill only
	 * changes WHICH read-only fetch the panel makes (a local display preference
	 * handed to the plugin via {@link DesignActions}); clicking the active pill
	 * clears the override. The highlighted pill mirrors the strategy ECHO of the
	 * last overview response — the applied strategy, never a client-side guess.
	 */
	private void buildStrategyPill()
	{
		strategyPill.removeAll();
		strategyPill.setLayout(new BoxLayout(strategyPill, BoxLayout.Y_AXIS));
		strategyPill.setOpaque(false);
		strategyPill.setAlignmentX(LEFT_ALIGNMENT);

		JPanel timeframes = new JPanel(new GridLayout(1, 4, 4, 0));
		timeframes.setOpaque(false);
		timeframes.setAlignmentX(LEFT_ALIGNMENT);
		for (int minutes : StrategyParams.TIMEFRAME_PILLS)
		{
			boolean active =
				activeTimeframe != null && activeTimeframe == minutes;
			int value = minutes;
			timeframes.add(pillButton(timeframeLabel(minutes), active, GOLD,
				() -> designActions.onTimeframePill(value)));
		}
		timeframes.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
		strategyPill.add(timeframes);
		strategyPill.add(Box.createVerticalStrut(4));

		JPanel risks = new JPanel(new GridLayout(1, 4, 4, 0));
		risks.setOpaque(false);
		risks.setAlignmentX(LEFT_ALIGNMENT);
		JLabel riskLabel = new JLabel("RISK");
		riskLabel.setFont(FontManager.getRunescapeSmallFont());
		riskLabel.setForeground(FAINT);
		risks.add(riskLabel);
		for (String grade : StrategyParams.RISK_PILLS)
		{
			boolean active = grade.equals(activeRisk);
			risks.add(pillButton(riskPillLabel(grade), active, ALERT,
				() -> designActions.onRiskPill(grade)));
		}
		risks.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
		strategyPill.add(risks);
	}

	/** "LOW" → "Low" etc. (pill label only; the API keeps the enum). */
	static String riskPillLabel(String grade)
	{
		if (grade == null || grade.isEmpty())
		{
			return "?";
		}
		return grade.charAt(0) + grade.substring(1).toLowerCase();
	}

	// ── SessionPanel (v0.8.7 design) ─────────────────────────────────────────

	/**
	 * Pushes the latest session KPIs (computed by the plugin's SessionTracker
	 * from passively observed offer completions) into the panel. Display only.
	 */
	void updateSession(SessionTracker.Stats stats)
	{
		this.sessionStats = stats;
		rebuildSession();
	}

	/** Rebuilds the full Session block and the collapsed one-liner (1b). */
	private void rebuildSession()
	{
		sessionPanel.removeAll();

		JPanel head = new JPanel(new BorderLayout(6, 0));
		head.setOpaque(false);
		head.setAlignmentX(LEFT_ALIGNMENT);
		JLabel headTitle = new JLabel("SESSION");
		headTitle.setFont(FontManager.getRunescapeSmallFont());
		headTitle.setForeground(MUTED);
		head.add(headTitle, BorderLayout.WEST);
		head.add(linkButton("Reset", MUTED,
			() -> designActions.onSessionReset()), BorderLayout.EAST);
		head.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
		sessionPanel.add(head);
		sessionPanel.add(Box.createVerticalStrut(4));

		SessionTracker.Stats stats = sessionStats;
		long profit = stats == null ? 0 : stats.profit;
		Color profitColor = profit < 0 ? RED : PROFIT;
		String profitText = profitLabel(profit);

		JPanel profitRow = new JPanel(new BorderLayout(6, 0));
		profitRow.setOpaque(false);
		profitRow.setAlignmentX(LEFT_ALIGNMENT);
		JLabel profitKey = new JLabel("Profit");
		profitKey.setFont(FontManager.getRunescapeSmallFont());
		profitKey.setForeground(MUTED);
		profitRow.add(profitKey, BorderLayout.WEST);
		JLabel profitValue = new JLabel(profitText);
		profitValue.setFont(FontManager.getRunescapeBoldFont());
		profitValue.setForeground(profitColor);
		profitValue.setHorizontalAlignment(SwingConstants.RIGHT);
		profitRow.add(profitValue, BorderLayout.EAST);
		profitRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
		sessionPanel.add(profitRow);
		sessionPanel.add(Box.createVerticalStrut(3));

		if (stats == null || !stats.hasActivity)
		{
			JLabel none = new JLabel(html(safe(SESSION_EMPTY_LINE)));
			none.setFont(FontManager.getRunescapeSmallFont());
			none.setForeground(FAINT);
			none.setAlignmentX(LEFT_ALIGNMENT);
			sessionPanel.add(none);
		}
		else
		{
			sessionPanel.add(kpiRow("Flips made",
				String.valueOf(stats.flips), TEXT));
			sessionPanel.add(kpiRow("ROI",
				pctOrDash(stats.roi), stats.roi != null && stats.roi < 0
					? RED : PROFIT));
			sessionPanel.add(kpiRow("Session time",
				SessionTracker.sessionTimeLabel(stats.sessionMillis), ALERT));
			sessionPanel.add(kpiRow("Hourly profit",
				stats.profitPerHour == null
					? "—"
					: profitLabel(Math.round(stats.profitPerHour)) + "/hr",
				stats.profitPerHour != null && stats.profitPerHour < 0
					? RED : PROFIT));
			if (stats.openBuysCost > 0)
			{
				sessionPanel.add(kpiRow("Open buys",
					gpOrDash(stats.openBuysCost), GOLD));
			}
		}
		if (portfolioCoins != null)
		{
			sessionPanel.add(kpiRow("Portfolio (coins)",
				gpOrDash(portfolioCoins), GOLD));
		}

		sessionCompactProfit.setText(profitText);
		sessionCompactProfit.setForeground(profitColor);
		sessionPanel.revalidate();
		sessionPanel.repaint();
		revalidate();
		repaint();
	}

	/** One "label ....... value" KPI row (design: Session rows). */
	private JPanel kpiRow(String key, String value, Color color)
	{
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setOpaque(false);
		row.setAlignmentX(LEFT_ALIGNMENT);
		JLabel k = new JLabel(key);
		k.setFont(FontManager.getRunescapeSmallFont());
		k.setForeground(MUTED);
		row.add(k, BorderLayout.WEST);
		JLabel v = new JLabel(value);
		v.setFont(FontManager.getRunescapeSmallFont());
		v.setForeground(color);
		v.setHorizontalAlignment(SwingConstants.RIGHT);
		row.add(v, BorderLayout.EAST);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 17));
		return row;
	}

	/** Signed gp label for session figures ("+20.2K gp" / "−1,050 gp"). */
	static String profitLabel(long gp)
	{
		String amount = QuantityFormatter.quantityToStackSize(Math.abs(gp)) + " gp";
		return (gp < 0 ? "−" : "+") + amount;
	}

	/** Observed coins (inventory + bank last seen); null when neither exists. */
	static Long portfolioCoinsOf(RuneFlipData.Capital capital)
	{
		if (capital == null
			|| (capital.inventoryCoins == null && capital.bankCoinsLastSeen == null))
		{
			return null;
		}
		long total = 0;
		if (capital.inventoryCoins != null)
		{
			total += capital.inventoryCoins;
		}
		if (capital.bankCoinsLastSeen != null)
		{
			total += capital.bankCoinsLastSeen;
		}
		return total;
	}

	/**
	 * SelectedItemCard (v0.8.7 design, 1b): head (icon + name + risk chip +
	 * confidence), then the WIKI Low/High cells, the TARGET Buy/Sell cells, the
	 * EDGE VS WIKI bullets (backend messages verbatim), the PLAN grid with the
	 * Profit highlight, the ACTION chip + reason and the Open Wiki CTA.
	 * Display only, ending on "Review manually." The v0.8.3 Copy price/qty
	 * block was removed in v0.8.10 — the game accepts no paste, so the buttons
	 * were useless in real play.
	 *
	 * <p>Side-aware since v0.8.12: a SELL setup titles the card "· SELL", adds
	 * the SELL section (safe/quick sell + tax + after-tax edge) and — the fix —
	 * keeps the FULL card when recommended=false as long as sell targets exist,
	 * because the player who already bought needs them to close the flip. A
	 * low/negative after-tax edge shows an explicit amber/red warning instead
	 * of hiding anything.
	 */
	private void buildSelectedCard(
		RuneFlipData.FastFlipItemContextResponse res, String side)
	{
		selectedHeader.setText(selectedHeaderTitle(side));
		// Head: icon + name + risk chip + confidence (all backend data).
		JPanel head = new JPanel(new BorderLayout(8, 0));
		head.setOpaque(false);
		head.setAlignmentX(LEFT_ALIGNMENT);
		if (res.itemId > 0)
		{
			JLabel icon = new JLabel();
			AsyncBufferedImage img = itemManager.getImage(res.itemId);
			if (img != null)
			{
				img.addTo(icon);
			}
			head.add(icon, BorderLayout.WEST);
		}
		String risk = res.riskLevel == null ? "UNKNOWN" : res.riskLevel;
		Color[] riskColors = riskChipColors(risk);
		JPanel headText = new JPanel();
		headText.setLayout(new BoxLayout(headText, BoxLayout.Y_AXIS));
		headText.setOpaque(false);
		JLabel name = new JLabel(sanitizeName(res.itemName));
		name.setFont(FontManager.getRunescapeBoldFont());
		name.setForeground(CREAM);
		name.setAlignmentX(LEFT_ALIGNMENT);
		headText.add(name);
		JPanel headMeta = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
		headMeta.setOpaque(false);
		headMeta.setAlignmentX(LEFT_ALIGNMENT);
		headMeta.add(chip(riskShort(risk), riskColors[0], riskColors[1]));
		if (res.confidence != null)
		{
			JLabel conf = new JLabel("confidence " + res.confidence);
			conf.setFont(FontManager.getRunescapeSmallFont());
			conf.setForeground(FAINT);
			headMeta.add(conf);
		}
		headText.add(headMeta);
		head.add(headText, BorderLayout.CENTER);
		head.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
		selectedCard.add(head);
		selectedCard.add(Box.createVerticalStrut(7));

		// Not recommended (AVOID / not recommended / no data): say so honestly
		// — UNLESS this is a SELL setup with sell targets available (v0.8.12):
		// dropping to "No RuneFlip target yet" right when the player needs the
		// sell target to close the flip was the real-play bug this fixes.
		if (!showFullContextCard(res, side))
		{
			String noTarget = noRuneFlipTargetLine(res);
			JLabel none = new JLabel(html(noTarget != null
				? noTarget : safe(SHORT_DISCLAIMER)));
			none.setFont(FontManager.getRunescapeSmallFont());
			none.setAlignmentX(LEFT_ALIGNMENT);
			selectedCard.add(none);
			selectedCard.add(Box.createVerticalStrut(6));
			selectedCard.add(openWikiRow(res.itemId));
			selectedCard.add(Box.createVerticalStrut(4));
			selectedCard.add(shortDisclaimerLabel());
			return;
		}

		// Full card despite recommended=false (sell side): the honest warning
		// renders ABOVE the targets instead of replacing them.
		if (!Boolean.TRUE.equals(res.recommended))
		{
			String reason = res.notRecommendedReason != null
				&& !res.notRecommendedReason.trim().isEmpty()
				? res.notRecommendedReason.trim()
				: "The model does not stand behind the full flip right now.";
			addSelectedLine("<span style='color:#e8a04a'>" + safe(reason)
				+ "</span>");
			selectedCard.add(Box.createVerticalStrut(6));
		}

		RuneFlipData.TargetComparison tc = res.targetComparison;

		// WIKI: the two raw legs as neutral cells.
		if (tc != null && (tc.wikiLow != null || tc.wikiHigh != null))
		{
			selectedCard.add(sectionLabel("WIKI"));
			selectedCard.add(Box.createVerticalStrut(3));
			selectedCard.add(cellPair(
				"Low", gpOrDash(tc.wikiLow), TEXT, CELL_BG,
				"High", gpOrDash(tc.wikiHigh), TEXT, CELL_BG));
			selectedCard.add(Box.createVerticalStrut(6));
		}

		// TARGET: the RuneFlip buy/sell targets as highlighted cells.
		if (tc != null && (tc.targetBuy != null || tc.targetSell != null))
		{
			selectedCard.add(sectionLabel("TARGET"));
			selectedCard.add(Box.createVerticalStrut(3));
			selectedCard.add(cellPair(
				"Buy", gpOrDash(tc.targetBuy), GOLD, CHIP_GOLD_BG,
				"Sell", gpOrDash(tc.targetSell), PROFIT, CHIP_GREEN_BG));
			selectedCard.add(Box.createVerticalStrut(6));
		}

		// SELL (v0.8.12, sell setups only): safe/quick sell targets, tax and
		// the after-tax edge — everything the player needs to close the flip.
		if ("SELL".equals(side))
		{
			String sellTargets = sellTargetsLine(res.priceEdge);
			String sellTax = sellTaxProfitLine(res.priceEdge);
			if (sellTargets != null || sellTax != null)
			{
				selectedCard.add(sectionLabel("SELL"));
				selectedCard.add(Box.createVerticalStrut(3));
				addSelectedLine(sellTargets);
				addSelectedLine(sellTax);
				selectedCard.add(Box.createVerticalStrut(6));
			}
		}

		// EDGE VS WIKI: the backend's buy/sell messages, verbatim bullets.
		String buyMessage = contextComparisonBuyLine(tc);
		String sellMessage = contextComparisonSellLine(tc);
		String edgeLine = contextExtraProfitLine(tc);
		if (buyMessage != null || sellMessage != null || edgeLine != null)
		{
			selectedCard.add(sectionLabel("EDGE VS WIKI"));
			selectedCard.add(Box.createVerticalStrut(3));
			addSelectedLine(buyMessage);
			addSelectedLine(sellMessage);
			addSelectedLine(edgeLine);
			selectedCard.add(Box.createVerticalStrut(6));
		}

		// PLAN: Qty / Est. time / Cost / ROI grid + the Profit highlight row.
		selectedCard.add(sectionLabel("PLAN"));
		selectedCard.add(Box.createVerticalStrut(3));
		JPanel grid = new JPanel(new GridLayout(0, 2, 8, 2));
		grid.setOpaque(false);
		grid.setAlignmentX(LEFT_ALIGNMENT);
		grid.add(stat("Qty", res.suggestedQuantity == null
			? "—" : QuantityFormatter.formatNumber(res.suggestedQuantity), TEXT));
		grid.add(stat("Est. time",
			durationLabel(res.expectedDurationMinutes), ALERT));
		grid.add(stat("Cost", gpOrDash(res.cost), GOLD));
		grid.add(stat("ROI", pctOrDash(res.roi), BLUE));
		selectedCard.add(grid);
		selectedCard.add(Box.createVerticalStrut(3));
		JPanel profitRow = new JPanel(new BorderLayout(6, 0));
		profitRow.setBackground(CHIP_GREEN_BG);
		profitRow.setBorder(BorderFactory.createEmptyBorder(4, 7, 4, 7));
		profitRow.setAlignmentX(LEFT_ALIGNMENT);
		JLabel profitKey = new JLabel("Profit");
		profitKey.setFont(FontManager.getRunescapeSmallFont());
		profitKey.setForeground(MUTED);
		profitRow.add(profitKey, BorderLayout.WEST);
		JLabel profitValue = new JLabel(profitPerItemLabel(res.expectedProfit));
		profitValue.setFont(FontManager.getRunescapeBoldFont());
		profitValue.setForeground(res.expectedProfit != null
			&& res.expectedProfit < 0 ? RED : PROFIT);
		profitValue.setHorizontalAlignment(SwingConstants.RIGHT);
		profitRow.add(profitValue, BorderLayout.EAST);
		profitRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
		selectedCard.add(profitRow);
		selectedCard.add(Box.createVerticalStrut(6));

		// Low/negative after-tax edge (v0.8.12): an explicit warning — amber
		// at zero, red when negative — instead of hiding the card.
		if (showLowEdgeWarning(res))
		{
			JLabel warn = new JLabel(html(safe(LOW_EDGE_WARNING)));
			warn.setFont(FontManager.getRunescapeSmallFont());
			Long edge = edgeAfterTaxOf(res);
			warn.setForeground(edge != null && edge < 0 ? RED : ALERT);
			warn.setAlignmentX(LEFT_ALIGNMENT);
			selectedCard.add(warn);
			selectedCard.add(Box.createVerticalStrut(6));
		}

		// ACTION: chip + the backend's reason, verbatim + display-only.
		String actionChipText = actionChipLabel(res.action);
		if (actionChipText != null)
		{
			selectedCard.add(sectionLabel("ACTION"));
			selectedCard.add(Box.createVerticalStrut(3));
			JPanel actionRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
			actionRow.setOpaque(false);
			actionRow.setAlignmentX(LEFT_ALIGNMENT);
			Color[] actionColors = actionChipColors(res.action.actionType);
			actionRow.add(chip(actionChipText, actionColors[0], actionColors[1]));
			actionRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
			selectedCard.add(actionRow);
			if (res.action.actionReason != null
				&& !res.action.actionReason.trim().isEmpty())
			{
				addSelectedLine("<span style='color:#8b91a0'>"
					+ safe(res.action.actionReason.trim()) + "</span>");
			}
			selectedCard.add(Box.createVerticalStrut(6));
		}

		// Open Wiki CTA — the card's only external action (v0.8.10: no Copy
		// price/qty; the game accepts no paste, so they assisted nothing).
		selectedCard.add(openWikiRow(res.itemId));

		// Short footer (goal 5): the compliance rule, compact.
		selectedCard.add(Box.createVerticalStrut(4));
		selectedCard.add(shortDisclaimerLabel());
	}

	/** Small tracking-style section label (WIKI / TARGET / PLAN / ACTION). */
	private JLabel sectionLabel(String text)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(FAINT);
		label.setAlignmentX(LEFT_ALIGNMENT);
		return label;
	}

	/** Two side-by-side value cells (design: WIKI Low/High, TARGET Buy/Sell). */
	private JPanel cellPair(
		String leftKey, String leftValue, Color leftColor, Color leftBg,
		String rightKey, String rightValue, Color rightColor, Color rightBg)
	{
		JPanel pair = new JPanel(new GridLayout(1, 2, 4, 0));
		pair.setOpaque(false);
		pair.setAlignmentX(LEFT_ALIGNMENT);
		pair.add(valueCell(leftKey, leftValue, leftColor, leftBg));
		pair.add(valueCell(rightKey, rightValue, rightColor, rightBg));
		pair.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
		return pair;
	}

	private JPanel valueCell(String key, String value, Color color, Color bg)
	{
		JPanel cell = new JPanel();
		cell.setLayout(new BoxLayout(cell, BoxLayout.Y_AXIS));
		cell.setBackground(bg);
		cell.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
		JLabel k = new JLabel(key);
		k.setFont(FontManager.getRunescapeSmallFont());
		k.setForeground(key.equals("Buy") ? GOLD
			: key.equals("Sell") ? PROFIT : FAINT);
		k.setAlignmentX(LEFT_ALIGNMENT);
		cell.add(k);
		JLabel v = new JLabel(value);
		v.setFont(FontManager.getRunescapeBoldFont());
		v.setForeground(color);
		v.setAlignmentX(LEFT_ALIGNMENT);
		cell.add(v);
		return cell;
	}

	/** Adds one small display-only line to the selected card when non-null. */
	private void addSelectedLine(String htmlInner)
	{
		if (htmlInner == null)
		{
			return;
		}
		JLabel label = new JLabel(html(htmlInner));
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setAlignmentX(LEFT_ALIGNMENT);
		selectedCard.add(label);
	}

	private JPanel openWikiRow(int itemId)
	{
		JPanel row = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
		row.setOpaque(false);
		row.setAlignmentX(LEFT_ALIGNMENT);
		row.add(actionButton("Open Wiki", () -> LinkBrowser.browse(
			"https://prices.runescape.wiki/osrs/item/" + itemId)));
		return row;
	}

	/** Short footer for the contextual cards (v0.8.5): the compliance rule in
	 *  three words. The full backend disclaimer stays on web/mobile. */
	private JLabel shortDisclaimerLabel()
	{
		JLabel label = new JLabel(html(safe(SHORT_DISCLAIMER)));
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(MUTED);
		label.setAlignmentX(LEFT_ALIGNMENT);
		return label;
	}

	void updateRecommendations(RuneFlipData.RecommendationsResponse response)
	{
		topCard.removeAll();
		listPanel.removeAll();

		List<RuneFlipData.Recommendation> items =
			response == null ? null : response.items;
		if (items == null || items.isEmpty())
		{
			JLabel none = new JLabel(html("No evaluable flips right now."));
			none.setFont(FontManager.getRunescapeSmallFont());
			none.setForeground(MUTED);
			topCard.add(none);
			revalidateAll();
			return;
		}

		buildTopCard(items.get(0));
		for (int i = 1; i < Math.min(items.size(), 10); i++)
		{
			listPanel.add(compactRow(items.get(i)));
			listPanel.add(Box.createVerticalStrut(3));
		}
		revalidateAll();
	}

	/**
	 * Compact "Fast Flip" card (v0.7.0): renders the backend's top flips
	 * verbatim — name, buy → sell, profit/item, speed labels, risk and
	 * confidence all come pre-computed from GET /fast-flip/overview. The
	 * plugin does no math here (formatting only) and never acts on the data.
	 */
	void updateFastFlip(RuneFlipData.FastFlipOverviewResponse response)
	{
		updateFastFlip(response, false);
	}

	/**
	 * Rebuilds the Fast Flip card from one overview response. `defaultFallback`
	 * (v0.8.6) marks a response the plugin re-fetched with the DEFAULT strategy
	 * after the saved strategy matched nothing — the card then says so instead of
	 * silently presenting default results as the saved-strategy ones.
	 */
	void updateFastFlip(
		RuneFlipData.FastFlipOverviewResponse response,
		boolean defaultFallback)
	{
		fastFlipCard.removeAll();
		// A rendered response ends the in-flight indicator (v0.8.10); stale
		// responses never reach here (the plugin drops them by sequence).
		updatingLabel.setVisible(false);

		// StrategyPill highlight (v0.8.7): mirror the strategy the backend says
		// it APPLIED to this response — never a client-side guess (the optimistic
		// pill value from a click is confirmed or corrected here). A failed
		// fetch keeps the previous highlight.
		if (response != null && response.strategy != null)
		{
			this.activeTimeframe = response.strategy.timeframeMinutes;
			this.activeRisk = response.strategy.riskLevel;
			buildStrategyPill();
		}

		// Choose rows honestly (v0.8.5-c): Top ranking first, then the fast-buy/
		// -sell candidates as "General ideas", then the informative empty state.
		// Fixes the panel showing "Fast flip · 0" whenever a restrictive saved
		// strategy emptied ONLY the Top list while the overview still had ideas.
		FastFlipSelection selection =
			FastFlipSelection.select(response, MAX_FAST_FLIP_ROWS);
		int shown = selection.rows.size();
		fastFlipHeader.setText(headerTitleOf(selection.source, shown).toUpperCase());

		if (selection.source == FastFlipSelection.Source.NONE)
		{
			// A failed fetch is NOT "no matches" (v0.8.6): blaming the strategy
			// for a network/HTTP failure sent users relaxing filters for nothing.
			if (response == null)
			{
				buildFastFlipOfflineState();
			}
			else
			{
				buildFastFlipEmptyState(response);
			}
			revalidateAll();
			return;
		}

		// Saved-strategy fallback note (v0.8.6): these rows come from a default-
		// strategy re-fetch, said explicitly so the strategy line below (the
		// default echo) cannot be mistaken for the saved strategy.
		if (defaultFallback)
		{
			fastFlipCard.add(mutedLine(DEFAULT_FALLBACK_NOTE));
			fastFlipCard.add(Box.createVerticalStrut(4));
		}

		// Compact strategy echo (v0.8.6): "8h · HIGH risk" — the full backend
		// description stays in the empty state, where the filter is the point.
		String strategyLine = compactStrategyLine(
			response == null ? null : response.strategy);
		if (strategyLine != null)
		{
			fastFlipCard.add(mutedLine(strategyLine));
			fastFlipCard.add(Box.createVerticalStrut(4));
		}

		// General-ideas note (v0.8.5-c): the Top ranking matched nothing for
		// the current strategy, so these are liquid fast-buy/-sell candidates —
		// say so, never pass them off as top-ranked flips. The header already
		// reads "General ideas" (v0.8.6); this line explains why.
		if (selection.source == FastFlipSelection.Source.GENERAL)
		{
			fastFlipCard.add(mutedLine(
				"No Top match for your strategy — showing liquid candidates."));
			fastFlipCard.add(Box.createVerticalStrut(4));
		}

		for (int i = 0; i < shown; i++)
		{
			if (i > 0)
			{
				fastFlipCard.add(Box.createVerticalStrut(6));
			}
			RuneFlipData.FastFlipItem flip = selection.rows.get(i);
			fastFlipCard.add(fastFlipEntry(flip, i + 1,
				isPrimaryGeSuggestion(selection.source, i + 1)));
		}

		fastFlipCard.add(Box.createVerticalStrut(6));
		revalidateAll();
	}

	/**
	 * Whether one Fast Flip row is THE primary GE suggestion (v0.8.10): always
	 * — and only — the FIRST rendered row of the current selection, whichever
	 * list it came from (Top ranking or the "General ideas" fallback). #2/#3
	 * are informational and can never be picked as a suggestion; the empty and
	 * offline states have no suggestion at all. The same rule feeds the
	 * plugin's field assist (v0.8.11): only this #1 may ever be offered as the
	 * click-gated "RuneFlip: select …" option — see {@link #GE_SUGGESTION_CHIP}.
	 */
	static boolean isPrimaryGeSuggestion(FastFlipSelection.Source source, int rank)
	{
		return source != FastFlipSelection.Source.NONE && rank == 1;
	}

	/**
	 * EmptyState (v0.8.7 design, 1c): a centered card with the "!" badge, the
	 * "No matches for current strategy" title, the compact strategy chip, the
	 * relax tips and a gold Refresh button — a restrictive saved strategy never
	 * leaves the panel blank and unexplained.
	 */
	private void buildFastFlipEmptyState(
		RuneFlipData.FastFlipOverviewResponse response)
	{
		JPanel card = emptyStateCard();

		JLabel badge = new JLabel("!");
		badge.setFont(FontManager.getRunescapeBoldFont());
		badge.setForeground(ALERT);
		badge.setAlignmentX(CENTER_ALIGNMENT);
		card.add(badge);
		card.add(Box.createVerticalStrut(5));

		JLabel noMatch = new JLabel(safe(NO_MATCH_LINE));
		noMatch.setFont(FontManager.getRunescapeBoldFont());
		noMatch.setForeground(TEXT);
		noMatch.setAlignmentX(CENTER_ALIGNMENT);
		card.add(noMatch);
		card.add(Box.createVerticalStrut(5));

		// Compact strategy chip ("8h · HIGH risk"); the full description (incl.
		// floors) follows when the backend echoed one.
		RuneFlipData.FastFlipStrategy strategy =
			response == null ? null : response.strategy;
		String chipText = compactStrategyLine(strategy);
		JLabel strategyChip = chip(
			chipText != null ? chipText : emptyStrategyLine(null),
			ALERT, CHIP_AMBER_BG);
		strategyChip.setAlignmentX(CENTER_ALIGNMENT);
		card.add(strategyChip);
		card.add(Box.createVerticalStrut(6));

		for (String tip : RELAX_TIPS)
		{
			JLabel line = new JLabel(html(
				"<span style='color:#8b91a0'>· " + safe(tip) + "</span>"));
			line.setFont(FontManager.getRunescapeSmallFont());
			line.setAlignmentX(CENTER_ALIGNMENT);
			card.add(line);
		}
		card.add(Box.createVerticalStrut(7));

		// Refresh only re-fetches the read endpoints (display only) — the exact
		// same action as the header Refresh button. Gold CTA per the design.
		JButton refresh = actionButton("↻ Refresh", onRefresh);
		refresh.setBackground(GOLD);
		refresh.setForeground(new Color(0x11, 0x13, 0x18));
		refresh.setAlignmentX(CENTER_ALIGNMENT);
		card.add(refresh);

		fastFlipCard.add(card);
	}

	/**
	 * Offline state for the Fast Flip card (v0.8.6): the overview fetch failed
	 * (network error, non-2xx, unparseable body), so no strategy verdict exists.
	 * Says exactly that — never "No matches for current strategy", which blamed
	 * the user's filters for a connectivity problem.
	 */
	private void buildFastFlipOfflineState()
	{
		JPanel card = emptyStateCard();

		JLabel offline = new JLabel(safe(OFFLINE_LINE));
		offline.setFont(FontManager.getRunescapeBoldFont());
		offline.setForeground(ALERT);
		offline.setAlignmentX(CENTER_ALIGNMENT);
		card.add(offline);
		card.add(Box.createVerticalStrut(5));

		JLabel hint = new JLabel(html(
			"<div style='text-align:center;color:#8b91a0'>"
				+ safe(OFFLINE_HINT_LINE) + "</div>"));
		hint.setFont(FontManager.getRunescapeSmallFont());
		hint.setAlignmentX(CENTER_ALIGNMENT);
		card.add(hint);
		card.add(Box.createVerticalStrut(7));

		JButton refresh = actionButton("↻ Refresh", onRefresh);
		refresh.setBackground(GOLD);
		refresh.setForeground(new Color(0x11, 0x13, 0x18));
		refresh.setAlignmentX(CENTER_ALIGNMENT);
		card.add(refresh);

		fastFlipCard.add(card);
	}

	/** Shared bordered, centered card container for the 1c/offline states. */
	private JPanel emptyStateCard()
	{
		JPanel card = new JPanel();
		card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
		card.setBackground(CARD_BG);
		card.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(CARD_BORDER),
			BorderFactory.createEmptyBorder(12, 10, 12, 10)));
		card.setAlignmentX(LEFT_ALIGNMENT);
		return card;
	}

	/**
	 * Card heading per row source (v0.8.6): "Top 3 Fast Flips" for the Top
	 * ranking (with the honest count when fewer than 3 arrived), "General ideas"
	 * for the fast-buy/-sell fallback, and the plain zero for the empty/offline
	 * states.
	 */
	static String headerTitleOf(FastFlipSelection.Source source, int shown)
	{
		switch (source)
		{
			case TOP:
				return shown == MAX_FAST_FLIP_ROWS
					? "Top 3 Fast Flips" : "Top Fast Flips · " + shown;
			case GENERAL:
				return "General ideas";
			default:
				return "Fast flip · 0";
		}
	}

	/**
	 * Compact strategy echo (v0.8.6): "8h · HIGH risk" built from the response's
	 * strategy fields. Falls back to the full backend description when either
	 * field is missing, and to null when there is no echo at all (pre-0.8.0) —
	 * nothing is ever derived client-side beyond formatting.
	 */
	static String compactStrategyLine(RuneFlipData.FastFlipStrategy strategy)
	{
		if (strategy == null)
		{
			return null;
		}
		if (strategy.timeframeMinutes == null || strategy.riskLevel == null)
		{
			return strategySummaryLine(strategy);
		}
		return timeframeLabel(strategy.timeframeMinutes) + " · "
			+ strategy.riskLevel + " risk";
	}

	/** Minutes → "30m" / "8h" / "1h 30m" (formatting only). */
	static String timeframeLabel(int minutes)
	{
		if (minutes < 60)
		{
			return minutes + "m";
		}
		int rest = minutes % 60;
		return rest == 0 ? (minutes / 60) + "h" : (minutes / 60) + "h " + rest + "m";
	}

	/** A left-aligned muted line that wraps (html) — used for the strategy and
	 *  relax-hint lines so long text never renders cut off in the sidebar. */
	private JLabel mutedLine(String text)
	{
		JLabel label = new JLabel(html(safe(text)));
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(MUTED);
		label.setAlignmentX(LEFT_ALIGNMENT);
		return label;
	}

	/**
	 * Strategy line for the empty state (v0.8.5-c): the backend's strategy
	 * description when present, or a plain default label when the echo is absent
	 * (pre-0.8.0 backend) — so the user always sees what filter produced no
	 * matches.
	 */
	static String emptyStrategyLine(RuneFlipData.FastFlipStrategy strategy)
	{
		String summary = strategySummaryLine(strategy);
		return summary != null ? summary : "Strategy: default (risk up to HIGH)";
	}

	/**
	 * One TopFastFlipRow (v0.8.7 design): a bordered card — #rank chip + icon +
	 * name + action chip, then buy → sell with the expected profit highlighted
	 * right, then ROI + risk chip + confidence. The #1 row (v0.8.10) carries a
	 * gold-tinted border and the "GE suggestion" chip — the single primary item
	 * to search manually in the GE. Display only — figures come verbatim from
	 * the backend, and no Copy buttons remain (removed in v0.8.10: the game
	 * accepts no paste).
	 */
	private JPanel fastFlipEntry(
		RuneFlipData.FastFlipItem flip,
		int rank,
		boolean primary)
	{
		JPanel entry = new JPanel();
		entry.setLayout(new BoxLayout(entry, BoxLayout.Y_AXIS));
		entry.setBackground(CARD_BG);
		// The primary row is the focus: same gold-tinted border treatment as
		// the selected-item card; the rest keep the neutral card border.
		entry.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(primary
				? new Color(0x4f, 0x44, 0x2f) : CARD_BORDER),
			BorderFactory.createEmptyBorder(5, 7, 5, 7)));
		entry.setAlignmentX(LEFT_ALIGNMENT);

		// Row 1: #rank chip + icon + name (ellipsized) + action chip right.
		JPanel head = new JPanel(new BorderLayout(5, 0));
		head.setOpaque(false);
		head.setAlignmentX(LEFT_ALIGNMENT);
		JPanel headLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		headLeft.setOpaque(false);
		headLeft.add(chip("#" + rank, GOLD, CHIP_GOLD_BG));
		if (flip.itemId > 0)
		{
			JLabel icon = new JLabel();
			AsyncBufferedImage img = itemManager.getImage(flip.itemId);
			if (img != null)
			{
				img.addTo(icon);
			}
			headLeft.add(icon);
		}
		head.add(headLeft, BorderLayout.WEST);
		JLabel name = new JLabel(sanitizeName(flip.itemName));
		name.setFont(FontManager.getRunescapeSmallFont());
		name.setForeground(TEXT);
		head.add(name, BorderLayout.CENTER);
		String actionChipText = actionChipLabel(flip.action);
		if (actionChipText != null)
		{
			Color[] actionColors = actionChipColors(flip.action.actionType);
			head.add(chip(actionChipText, actionColors[0], actionColors[1]),
				BorderLayout.EAST);
		}
		head.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
		entry.add(head);
		entry.add(Box.createVerticalStrut(3));

		// Primary GE suggestion chip (v0.8.10) — DISPLAY ONLY. The chip marks
		// the one item to search; it is not a button and the panel writes
		// nothing into the game. The in-game "RuneFlip: select …" option
		// (v0.8.11, GeFieldAssistService) is where a click may prepare it.
		if (primary)
		{
			JPanel suggestionRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
			suggestionRow.setOpaque(false);
			suggestionRow.setAlignmentX(LEFT_ALIGNMENT);
			JLabel suggestionChip = chip(GE_SUGGESTION_CHIP, GOLD, CHIP_GOLD_BG);
			suggestionChip.setToolTipText(
				"<html>RuneFlip's primary suggestion — search this item in the "
					+ "GE.<br>Tip: right-click while the GE search is open for "
					+ "“RuneFlip: select …” (prepares the text "
					+ "only).<br>RuneFlip never submits or confirms anything "
					+ "for you.</html>");
			suggestionRow.add(suggestionChip);
			suggestionRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
			entry.add(suggestionRow);
			entry.add(Box.createVerticalStrut(3));
		}

		// Row 2: buy → sell left, expected whole-flip profit highlighted right.
		JPanel legs = new JPanel(new BorderLayout(6, 0));
		legs.setOpaque(false);
		legs.setAlignmentX(LEFT_ALIGNMENT);
		JLabel prices = new JLabel(html(
			"<span style='color:#8b91a0'>" + gpOrDash(flip.suggestedBuyPrice)
				+ " → " + gpOrDash(flip.suggestedSellPrice) + "</span>"));
		prices.setFont(FontManager.getRunescapeSmallFont());
		legs.add(prices, BorderLayout.WEST);
		JLabel profit = new JLabel(profitPerItemLabel(flip.estimatedProfit));
		profit.setFont(FontManager.getRunescapeBoldFont());
		profit.setForeground(flip.estimatedProfit != null
			&& flip.estimatedProfit < 0 ? RED : PROFIT);
		profit.setHorizontalAlignment(SwingConstants.RIGHT);
		legs.add(profit, BorderLayout.EAST);
		legs.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
		entry.add(legs);
		entry.add(Box.createVerticalStrut(3));

		// Row 3: ROI + risk chip left, confidence right.
		JPanel meta = new JPanel(new BorderLayout(6, 0));
		meta.setOpaque(false);
		meta.setAlignmentX(LEFT_ALIGNMENT);
		JPanel metaLeft = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
		metaLeft.setOpaque(false);
		JLabel roi = new JLabel("ROI " + pctOrDash(flip.roi));
		roi.setFont(FontManager.getRunescapeSmallFont());
		roi.setForeground(BLUE);
		metaLeft.add(roi);
		String risk = flip.riskLevel == null ? "UNKNOWN" : flip.riskLevel;
		Color[] riskColors = riskChipColors(risk);
		metaLeft.add(chip(riskShort(risk), riskColors[0], riskColors[1]));
		meta.add(metaLeft, BorderLayout.WEST);
		if (flip.confidence != null)
		{
			JLabel conf = new JLabel("conf " + flip.confidence);
			conf.setFont(FontManager.getRunescapeSmallFont());
			conf.setForeground(FAINT);
			meta.add(conf, BorderLayout.EAST);
		}
		meta.setMaximumSize(new Dimension(Integer.MAX_VALUE, 20));
		entry.add(meta);

		return entry;
	}

	/** Action chip text (design): the backend's label, uppercased; null when
	 *  the backend sent no action (pre-0.8.2) — no chip is invented. */
	static String actionChipLabel(RuneFlipData.RecommendedAction action)
	{
		if (action == null || action.actionLabel == null
			|| action.actionLabel.trim().isEmpty())
		{
			return null;
		}
		return action.actionLabel.trim().toUpperCase();
	}

	/** "MEDIUM" → "MED" etc. — the design's compact chip text. */
	static String riskShort(String riskLevel)
	{
		if (riskLevel == null)
		{
			return "?";
		}
		return "MEDIUM".equals(riskLevel) ? "MED" : riskLevel;
	}

	/**
	 * Recommended-action line (v0.8.2): "Action: <label> — <reason>", both
	 * verbatim from the backend, colored by the action type. Null when the
	 * backend sent no action (pre-0.8.2) or an empty label. Display only: this
	 * is a suggestion to REVIEW manually, never a game interaction.
	 */
	static String actionLine(RuneFlipData.RecommendedAction action)
	{
		if (action == null || action.actionLabel == null
			|| action.actionLabel.trim().isEmpty())
		{
			return null;
		}
		String color = actionColorHex(action.actionType);
		StringBuilder sb = new StringBuilder();
		sb.append("<span style='color:#878d9c'>Action:</span> ");
		sb.append("<span style='color:").append(color).append("'><b>");
		sb.append(safe(action.actionLabel.trim())).append("</b></span>");
		if (action.actionReason != null && !action.actionReason.trim().isEmpty())
		{
			sb.append(" <span style='color:#878d9c'>— ");
			sb.append(safe(action.actionReason.trim())).append("</span>");
		}
		return sb.toString();
	}

	/**
	 * Recommended-action type → label color hex. BUY/keep tones green, review
	 * tones gold, abort/avoid tones red; anything unexpected stays neutral —
	 * the plugin never guesses an action itself.
	 */
	static String actionColorHex(String actionType)
	{
		if (actionType == null)
		{
			return "#878d9c";
		}
		switch (actionType)
		{
			case "BUY_NEW":
				return "#4cba86";
			case "SELL_EXISTING":
			case "HOLD":
				return "#9fb6ef";
			case "MODIFY_BUY":
			case "MODIFY_SELL":
				return "#e3b75d";
			case "ABORT_BUY":
			case "ABORT_SELL":
			case "AVOID":
				return "#e26a5e";
			default:
				return "#878d9c";
		}
	}

	/**
	 * Strategy Engine summary line (v0.8.0): "Strategy: <backend description>",
	 * verbatim, or null when the backend sent no strategy echo (pre-0.8.0) or
	 * an empty description — nothing is ever derived client-side.
	 */
	static String strategySummaryLine(RuneFlipData.FastFlipStrategy strategy)
	{
		if (strategy == null || strategy.description == null
			|| strategy.description.trim().isEmpty())
		{
			return null;
		}
		return "Strategy: " + strategy.description.trim();
	}

	/**
	 * The price-edge disclaimer for the card footer: the first backend-sent
	 * one among the shown entries, the fixed fallback when an entry has
	 * targets but no text, and null when no entry has targets at all.
	 */
	static String priceEdgeDisclaimer(
		List<RuneFlipData.FastFlipItem> flips, int shown)
	{
		if (flips == null)
		{
			return null;
		}
		boolean hasTargets = false;
		for (int i = 0; i < Math.min(shown, flips.size()); i++)
		{
			RuneFlipData.PriceEdge edge = flips.get(i).priceEdge;
			if (edge == null)
			{
				continue;
			}
			hasTargets = true;
			if (edge.disclaimer != null && !edge.disclaimer.trim().isEmpty())
			{
				return edge.disclaimer.trim();
			}
		}
		return hasTargets ? PRICE_EDGE_DISCLAIMER : null;
	}

	/**
	 * First price-edge line: "Wiki buy 119 → quick buy 122 · target sell 129"
	 * (or "Targets: not recommended"). Null when the backend sent no edge
	 * block (pre-0.7.1) or no usable targets — the entry simply stays as-is.
	 */
	static String priceEdgeTargetLine(RuneFlipData.PriceEdge edge)
	{
		if (edge == null)
		{
			return null;
		}
		if (edge.recommendedBuyPrice == null || edge.recommendedSellPrice == null)
		{
			return "NOT_RECOMMENDED".equals(edge.recommendation)
				? "<span style='color:#e26a5e'>Targets: not recommended</span>"
				: null;
		}
		return "<span style='color:#878d9c'>Wiki buy</span> "
			+ gpOrDash(edge.wikiLowPrice)
			+ " <span style='color:#9fb6ef'>→ quick buy "
			+ gpOrDash(edge.recommendedBuyPrice) + "</span>"
			+ " <span style='color:#878d9c'>· target sell</span> "
			+ gpOrDash(edge.recommendedSellPrice);
	}

	/** Second price-edge line: profit after tax + confidence at the target. */
	static String priceEdgeProfitLine(RuneFlipData.PriceEdge edge)
	{
		if (edge == null || edge.profitPerItem == null)
		{
			return null;
		}
		return "<span style='color:#4cba86'>"
			+ profitPerItemLabel(edge.profitPerItem)
			+ "/item after tax</span>"
			+ (edge.confidence != null
				? " <span style='color:#878d9c'>· conf " + edge.confidence + "</span>"
				: "");
	}

	// ── Context-aware GE item text (v0.8.4 — verbatim from the backend) ──────

	// ── Sell-context retention (v0.8.12) — pure, unit-tested decisions ──────

	/** Card header per setup side: "SELECTED GE ITEM · SELL" while selling,
	 *  "· BUY" while buying, the plain title when the side is unknown. */
	static String selectedHeaderTitle(String side)
	{
		if ("SELL".equals(side))
		{
			return "SELECTED GE ITEM · SELL";
		}
		if ("BUY".equals(side))
		{
			return "SELECTED GE ITEM · BUY";
		}
		return "SELECTED GE ITEM";
	}

	/**
	 * Whether the FULL context card renders (v0.8.12). Recommended items
	 * always do. A not-recommended response keeps the full card ONLY on the
	 * SELL side and only when sell targets exist — the player who already
	 * bought needs them to close the flip; the buy side keeps the honest
	 * "No RuneFlip target yet" (never open a new flip on a rejected item).
	 */
	static boolean showFullContextCard(
		RuneFlipData.FastFlipItemContextResponse res, String side)
	{
		if (res == null)
		{
			return false;
		}
		if (Boolean.TRUE.equals(res.recommended))
		{
			return true;
		}
		return "SELL".equals(side) && hasSellTargets(res);
	}

	/** Any usable sell target: comparison targetSell, or a recommended/safe/
	 *  quick sell leg in the price edge. */
	static boolean hasSellTargets(RuneFlipData.FastFlipItemContextResponse res)
	{
		if (res == null)
		{
			return false;
		}
		if (res.targetComparison != null && res.targetComparison.targetSell != null)
		{
			return true;
		}
		RuneFlipData.PriceEdge edge = res.priceEdge;
		return edge != null
			&& (edge.recommendedSellPrice != null
				|| edge.safeSellPrice != null
				|| edge.quickSellPrice != null);
	}

	/** "Safe sell 1,049 gp · Quick sell 1,020 gp" — the closing-leg targets;
	 *  null when the edge block has neither. */
	static String sellTargetsLine(RuneFlipData.PriceEdge edge)
	{
		if (edge == null
			|| (edge.safeSellPrice == null && edge.quickSellPrice == null))
		{
			return null;
		}
		return "<span style='color:#878d9c'>Safe sell</span> "
			+ gpOrDash(edge.safeSellPrice)
			+ " <span style='color:#878d9c'>· Quick sell</span> "
			+ gpOrDash(edge.quickSellPrice);
	}

	/** "Tax 21 gp · +5 gp/item after tax" — the closing-leg economics; the
	 *  edge renders red when negative. Null when the block carries neither. */
	static String sellTaxProfitLine(RuneFlipData.PriceEdge edge)
	{
		if (edge == null || (edge.tax == null && edge.profitPerItem == null))
		{
			return null;
		}
		StringBuilder sb = new StringBuilder();
		sb.append("<span style='color:#878d9c'>Tax</span> ")
			.append(gpOrDash(edge.tax));
		if (edge.profitPerItem != null)
		{
			String color = edge.profitPerItem < 0 ? "#e06767" : "#4cba86";
			sb.append(" <span style='color:").append(color).append("'>· ")
				.append(profitPerItemLabel(edge.profitPerItem))
				.append("/item after tax</span>");
		}
		return sb.toString();
	}

	/** After-tax edge per item: the price-edge figure, else the whole-flip
	 *  expected profit; null when neither exists. */
	static Long edgeAfterTaxOf(RuneFlipData.FastFlipItemContextResponse res)
	{
		if (res == null)
		{
			return null;
		}
		if (res.priceEdge != null && res.priceEdge.profitPerItem != null)
		{
			return res.priceEdge.profitPerItem;
		}
		return res.expectedProfit;
	}

	/** The LOW_EDGE_WARNING renders when the after-tax edge is known and does
	 *  not clear zero. An unknown edge shows no warning (nothing invented). */
	static boolean showLowEdgeWarning(RuneFlipData.FastFlipItemContextResponse res)
	{
		Long edge = edgeAfterTaxOf(res);
		return edge != null && edge <= 0;
	}

	/**
	 * "No RuneFlip target yet" line for a selected item the model does not stand
	 * behind (AVOID / not recommended / no data). Null when the backend DID
	 * recommend a target (then the full comparison renders instead). The reason
	 * is the backend's own, rendered verbatim.
	 */
	static String noRuneFlipTargetLine(RuneFlipData.FastFlipItemContextResponse res)
	{
		if (res == null || Boolean.TRUE.equals(res.recommended))
		{
			return null;
		}
		String reason = res.notRecommendedReason != null
			&& !res.notRecommendedReason.trim().isEmpty()
			? res.notRecommendedReason.trim()
			: "This item has no recent price or feature data.";
		return "<span style='color:#e26a5e'>No RuneFlip target yet</span>"
			+ " <span style='color:#878d9c'>— " + safe(reason) + "</span>";
	}

	/**
	 * "Wiki: L 99 · H 109" — the two raw legs, compact (v0.8.5). Low = recent
	 * insta-sell (the buy anchor), High = recent insta-buy (the sell anchor).
	 * Null when the comparison block is absent (pre-0.8.4 backend).
	 */
	static String wikiLegsLine(RuneFlipData.TargetComparison tc)
	{
		if (tc == null || (tc.wikiLow == null && tc.wikiHigh == null))
		{
			return null;
		}
		return "<span style='color:#878d9c'>Wiki:</span> L "
			+ gpOrDash(tc.wikiLow) + " · H " + gpOrDash(tc.wikiHigh);
	}

	/**
	 * "RuneFlip: buy 100 · sell 108" — the recommended targets, compact
	 * (v0.8.5). Null when the backend stands behind no target pair.
	 */
	static String runeFlipTargetsLine(RuneFlipData.TargetComparison tc)
	{
		if (tc == null || (tc.targetBuy == null && tc.targetSell == null))
		{
			return null;
		}
		return "<span style='color:#9fb6ef'>RuneFlip:</span> buy "
			+ gpOrDash(tc.targetBuy) + " · sell " + gpOrDash(tc.targetSell);
	}

	/**
	 * "Safe 119/130 · Quick 122/129" — the safe and quick target pairs (buy/sell)
	 * from the Price Edge block. Null when no edge block or no safe pair exists.
	 */
	static String safeQuickLine(RuneFlipData.PriceEdge edge)
	{
		if (edge == null || edge.safeBuyPrice == null || edge.safeSellPrice == null)
		{
			return null;
		}
		return "<span style='color:#878d9c'>Safe</span> "
			+ gpOrDash(edge.safeBuyPrice) + "/" + gpOrDash(edge.safeSellPrice)
			+ " <span style='color:#878d9c'>· Quick</span> "
			+ gpOrDash(edge.quickBuyPrice) + "/" + gpOrDash(edge.quickSellPrice);
	}

	/**
	 * Top-3 stats line (v0.8.5): "Buy 100 → Sell 108 · +600 gp · ROI 6.0%" — the
	 * suggested legs, the whole-flip expected profit and ROI, all backend
	 * figures rendered verbatim.
	 */
	static String topFlipStatsLine(RuneFlipData.FastFlipItem flip)
	{
		return "<span style='color:#878d9c'>Buy</span> "
			+ gpOrDash(flip.suggestedBuyPrice)
			+ " <span style='color:#878d9c'>→ Sell</span> "
			+ gpOrDash(flip.suggestedSellPrice)
			+ " <span style='color:#4cba86'>· "
			+ profitPerItemLabel(flip.estimatedProfit) + "</span>"
			+ " <span style='color:#878d9c'>· ROI " + pctOrDash(flip.roi)
			+ "</span>";
	}

	/** Buy-side manual-decision line — the backend's buyMessage verbatim (e.g.
	 *  "Try buying 8 gp below Wiki low"). Null when absent. */
	static String contextComparisonBuyLine(RuneFlipData.TargetComparison tc)
	{
		if (tc == null || tc.buyMessage == null || tc.buyMessage.trim().isEmpty())
		{
			return null;
		}
		return "<span style='color:#9fb6ef'>Buy:</span> "
			+ "<span style='color:#e6e6e6'>" + safe(tc.buyMessage.trim()) + "</span>";
	}

	/** Sell-side manual-decision line — the backend's sellMessage verbatim. */
	static String contextComparisonSellLine(RuneFlipData.TargetComparison tc)
	{
		if (tc == null || tc.sellMessage == null || tc.sellMessage.trim().isEmpty())
		{
			return null;
		}
		return "<span style='color:#4cba86'>Sell:</span> "
			+ "<span style='color:#e6e6e6'>" + safe(tc.sellMessage.trim()) + "</span>";
	}

	/**
	 * "Edge: +6 gp/item · +600 for qty" — the recommended flip's profit per item
	 * after tax and the qty-scaled figure (v0.8.5). Null when the backend
	 * computed no edge.
	 */
	static String contextExtraProfitLine(RuneFlipData.TargetComparison tc)
	{
		if (tc == null || tc.extraEdgePerItem == null)
		{
			return null;
		}
		String line = "<span style='color:#4cba86'>Edge:</span> "
			+ profitPerItemLabel(tc.extraEdgePerItem) + "/item";
		if (tc.potentialExtraProfit != null)
		{
			line += " <span style='color:#878d9c'>· "
				+ profitPerItemLabel(tc.potentialExtraProfit) + " for qty</span>";
		}
		return line;
	}

	/** Fixed manual-decision guidance (backend copy), rendered verbatim. Null
	 *  when the backend sent none. */
	static String contextGuidanceLine(RuneFlipData.TargetComparison tc)
	{
		if (tc == null || tc.guidance == null || tc.guidance.trim().isEmpty())
		{
			return null;
		}
		return "<span style='color:#878d9c'>" + safe(tc.guidance.trim()) + "</span>";
	}

	/**
	 * Compact read-only summary of completed offers. Pure display: nothing
	 * here (or anywhere) can search, load, click, type or act on the game —
	 * the alerts only inform the user, who reviews manually.
	 */
	void updateCompleted(RuneFlipData.AlertsResponse response)
	{
		completedPanel.removeAll();
		List<RuneFlipData.Alert> alerts = response == null ? null : response.alerts;
		int count = alerts == null ? 0 : alerts.size();
		completedHeader.setText("GE completed · " + count);

		if (count == 0)
		{
			completedPanel.add(secondaryLine("No completed offers", Color.WHITE));
			completedPanel.add(
				secondaryLine("Completed GE offers will appear here.", MUTED));
		}
		else
		{
			int shown = Math.min(count, MAX_COMPLETED_ROWS);
			for (int i = 0; i < shown; i++)
			{
				completedPanel.add(completedRow(alerts.get(i)));
			}
			if (count > shown)
			{
				completedPanel.add(secondaryLine(
					"+" + (count - shown) + " more ready to review", MUTED));
			}
			completedPanel.add(
				secondaryLine("Review manually in the official client.", MUTED));
		}
		revalidateAll();
	}

	/** One single-line row: sanitized name (ellipsized) + short time. */
	private JPanel completedRow(RuneFlipData.Alert alert)
	{
		JPanel row = new JPanel(new BorderLayout(8, 0));
		row.setOpaque(false);

		// Plain-text JLabel: truncates with an ellipsis instead of wrapping.
		JLabel name = new JLabel(sanitizeName(alert.itemName));
		name.setFont(FontManager.getRunescapeSmallFont());
		name.setForeground(Color.WHITE);
		row.add(name, BorderLayout.CENTER);

		String time = shortTimeAgo(alert.createdAt, System.currentTimeMillis());
		if (!time.isEmpty())
		{
			JLabel when = new JLabel(time);
			when.setFont(FontManager.getRunescapeSmallFont());
			when.setForeground(MUTED);
			row.add(when, BorderLayout.EAST);
		}

		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
		row.setAlignmentX(LEFT_ALIGNMENT);
		return row;
	}

	/** Small single-line helper text (plain text — never wraps). */
	private JPanel secondaryLine(String text, Color color)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(color);
		return wrap(label);
	}

	// ── building blocks ──────────────────────────────────────────────────────

	private void buildTopCard(RuneFlipData.Recommendation rec)
	{
		JPanel head = new JPanel(new BorderLayout(6, 0));
		head.setOpaque(false);
		JLabel icon = new JLabel();
		AsyncBufferedImage img = itemManager.getImage(rec.itemId);
		img.addTo(icon);
		head.add(icon, BorderLayout.WEST);

		JLabel name = new JLabel(html("<b>" + safe(rec.name) + "</b>"
			+ (rec.risk != null ? " <span style='color:#878d9c'>· " + rec.risk + " risk</span>" : "")
			+ " <span style='color:#9fb6ef'>· score " + Math.round(rec.score) + "</span>"));
		name.setFont(FontManager.getRunescapeSmallFont());
		name.setForeground(Color.WHITE);
		head.add(name, BorderLayout.CENTER);
		topCard.add(head);
		topCard.add(Box.createVerticalStrut(6));

		// Manual setup label: DISPLAY ONLY. The name comes verbatim from the
		// backend and is never written into the chatbox, the GE search box or
		// any other game input — the user types it manually.
		JLabel setupLabel = new JLabel(html(
			"<span style='color:#878d9c'>RuneFlip item:</span> <b style='color:#e3b75d'>"
				+ safe(rec.name) + "</b>"));
		setupLabel.setFont(FontManager.getRunescapeSmallFont());
		topCard.add(setupLabel);
		topCard.add(Box.createVerticalStrut(6));

		JPanel grid = new JPanel(new GridLayout(0, 2, 6, 2));
		grid.setOpaque(false);
		grid.add(stat("Buy", gp(rec.buyPrice), Color.WHITE));
		grid.add(stat("Sell", gp(rec.sellPrice), Color.WHITE));
		grid.add(stat("Est. profit", "+" + gp(rec.estimatedProfit), PROFIT));
		grid.add(stat("ROI", pct(rec.roi), Color.WHITE));
		grid.add(stat("Qty", QuantityFormatter.formatNumber(rec.suggestedQuantity), Color.WHITE));
		grid.add(stat("Capital", gp(rec.capitalRequired), GOLD));
		topCard.add(grid);
		topCard.add(Box.createVerticalStrut(6));

		if (rec.reason != null && !rec.reason.isEmpty())
		{
			JLabel reason = new JLabel(html(safe(rec.reason)));
			reason.setFont(FontManager.getRunescapeSmallFont());
			reason.setForeground(MUTED);
			topCard.add(reason);
			topCard.add(Box.createVerticalStrut(6));
		}

		// Clipboard-only conveniences — nothing here can reach the game.
		JPanel actions = new JPanel(new GridLayout(0, 2, 4, 4));
		actions.setOpaque(false);
		// "Search item" is intentionally DISABLED. See disabledSearchButton()
		// and docs/runelite-readonly-contract.md: the only client path to
		// prefill the in-game GE search is synthetic input (setVarcStrValue +
		// runScript), which violates RuneFlip's no-automation contract.
		actions.add(disabledSearchButton());
		actions.add(actionButton("Open Wiki", () -> LinkBrowser.browse(
			"https://prices.runescape.wiki/osrs/item/" + rec.itemId)));
		// v0.8.10: the price/qty Copy buttons were removed everywhere — the
		// game accepts no paste, so they assisted nothing in real play. Copy
		// name stays: it is the documented manual-search aid.
		actions.add(actionButton("Copy name", () -> copy(rec.name)));
		topCard.add(actions);
		topCard.add(Box.createVerticalStrut(6));

		JLabel manualHint = new JLabel(html(
			"Search item only loads the item name. You must manually select "
				+ "the item and enter price/quantity."));
		manualHint.setFont(FontManager.getRunescapeSmallFont());
		manualHint.setForeground(MUTED);
		topCard.add(manualHint);
	}

	/**
	 * The legacy "Search item" affordance, kept DISABLED on purpose.
	 *
	 * <p>Historic research finding (v0.6, RuneLite {@code GrandExchangePlugin},
	 * Plugin Hub flipping tools): prefilling the in-game GE search requires
	 * {@code setVarcStrValue} plus a client script — i.e. synthetic input if
	 * fired from a panel button with no GE state to validate. Since v0.8.11
	 * that prepare exists, but ONLY as the click-gated in-game
	 * "RuneFlip: select …" menu option ({@link GeFieldAssistService}), where
	 * the open editor is verified at the moment of the click. A panel-side
	 * button cannot give that guarantee, so it stays disabled and points the
	 * user at the in-game option instead.
	 */
	private JButton disabledSearchButton()
	{
		JButton b = smallButton("Search item");
		b.setEnabled(false);
		b.setToolTipText(
			"<html>Disabled here. With the GE search open, right-click and "
				+ "choose “RuneFlip: select …” — it prepares the search text "
				+ "after your explicit click and never submits anything.</html>");
		return b;
	}

	private JPanel compactRow(RuneFlipData.Recommendation rec)
	{
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setBackground(CARD_BG);
		row.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));

		JLabel name = new JLabel(html(safe(rec.name)));
		name.setFont(FontManager.getRunescapeSmallFont());
		name.setForeground(Color.WHITE);
		row.add(name, BorderLayout.CENTER);

		JLabel numbers = new JLabel(html(
			"<span style='color:#4cba86'>+" + gp(rec.estimatedProfit) + "</span>"
				+ " <span style='color:#878d9c'>· " + pct(rec.roi) + "</span>"));
		numbers.setFont(FontManager.getRunescapeSmallFont());
		row.add(numbers, BorderLayout.EAST);

		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
		return row;
	}

	private JLabel stat(String label, String value, Color color)
	{
		JLabel l = new JLabel(html(
			"<span style='color:#878d9c'>" + label + "</span> "
				+ "<span style='color:" + hex(color) + "'>" + value + "</span>"));
		l.setFont(FontManager.getRunescapeSmallFont());
		return l;
	}

	private JButton smallButton(String text)
	{
		JButton b = new JButton(text);
		b.setFont(FontManager.getRunescapeSmallFont());
		b.setFocusPainted(false);
		b.setMargin(new java.awt.Insets(1, 6, 1, 6));
		return b;
	}

	private JButton actionButton(String text, Runnable action)
	{
		JButton b = smallButton(text);
		b.addActionListener(e -> action.run());
		return b;
	}

	/** Borderless link-style button (design: "↻ Refresh", "Reset"). Plain
	 *  ActionListener — never a low-level input API. */
	private JButton linkButton(String text, Color color, Runnable action)
	{
		JButton b = new JButton(text);
		b.setFont(FontManager.getRunescapeSmallFont());
		b.setForeground(color);
		b.setFocusPainted(false);
		b.setContentAreaFilled(false);
		b.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
		b.addActionListener(e -> action.run());
		return b;
	}

	/** One StrategyPill segment: active = accent bg + dark text (design). */
	private JButton pillButton(
		String text, boolean active, Color accent, Runnable action)
	{
		JButton b = new JButton(text);
		b.setFont(FontManager.getRunescapeSmallFont());
		b.setFocusPainted(false);
		b.setMargin(new java.awt.Insets(1, 2, 1, 2));
		if (active)
		{
			b.setBackground(accent);
			b.setForeground(new Color(0x11, 0x13, 0x18));
		}
		else
		{
			b.setBackground(CELL_BG);
			b.setForeground(MUTED);
		}
		b.addActionListener(e -> action.run());
		return b;
	}

	/** Small colored chip (design: risk / action chips). */
	private JLabel chip(String text, Color fg, Color bg)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(fg);
		label.setBackground(bg);
		label.setOpaque(true);
		label.setBorder(BorderFactory.createEmptyBorder(1, 5, 1, 5));
		return label;
	}

	/** Risk grade → chip colors (design: LOW green / MED amber / HIGH red). */
	private Color[] riskChipColors(String riskLevel)
	{
		if ("LOW".equals(riskLevel))
		{
			return new Color[] {PROFIT, CHIP_GREEN_BG};
		}
		if ("MEDIUM".equals(riskLevel))
		{
			return new Color[] {ALERT, CHIP_AMBER_BG};
		}
		if ("HIGH".equals(riskLevel) || "AVOID".equals(riskLevel))
		{
			return new Color[] {RED, CHIP_RED_BG};
		}
		return new Color[] {MUTED, CELL_BG};
	}

	/** Action type → chip colors (design: buy green / sell blue / hold amber). */
	private Color[] actionChipColors(String actionType)
	{
		if ("BUY_NEW".equals(actionType))
		{
			return new Color[] {PROFIT, CHIP_GREEN_BG};
		}
		if ("SELL_EXISTING".equals(actionType))
		{
			return new Color[] {BLUE, CHIP_BLUE_BG};
		}
		if ("HOLD".equals(actionType))
		{
			return new Color[] {ALERT, CHIP_AMBER_BG};
		}
		if ("MODIFY_BUY".equals(actionType) || "MODIFY_SELL".equals(actionType))
		{
			return new Color[] {GOLD, CHIP_GOLD_BG};
		}
		if ("ABORT_BUY".equals(actionType) || "ABORT_SELL".equals(actionType)
			|| "AVOID".equals(actionType))
		{
			return new Color[] {RED, CHIP_RED_BG};
		}
		return new Color[] {MUTED, CELL_BG};
	}

	/** Clipboard only — a safe, user-initiated convenience. */
	private static void copy(String text)
	{
		if (text == null)
		{
			return;
		}
		Toolkit.getDefaultToolkit()
			.getSystemClipboard()
			.setContents(new StringSelection(text), null);
	}

	private JPanel wrap(JLabel label)
	{
		JPanel p = new JPanel(new BorderLayout());
		p.setOpaque(false);
		p.add(label, BorderLayout.CENTER);
		p.setMaximumSize(new Dimension(Integer.MAX_VALUE, label.getPreferredSize().height + 4));
		p.setAlignmentX(LEFT_ALIGNMENT);
		return p;
	}

	private void revalidateAll()
	{
		topCard.revalidate();
		topCard.repaint();
		fastFlipCard.revalidate();
		fastFlipCard.repaint();
		listPanel.revalidate();
		listPanel.repaint();
		completedPanel.revalidate();
		completedPanel.repaint();
		revalidate();
		repaint();
	}

	// ── formatting (presentation only — figures come from the backend) ──────

	private static String gp(long value)
	{
		return QuantityFormatter.quantityToStackSize(value) + " gp";
	}

	private static String pct(double ratio)
	{
		return String.format("%.1f%%", ratio * 100);
	}

	/** Nullable backend figure → gp text; "—" when the backend sent null. */
	static String gpOrDash(Long value)
	{
		return value == null ? "—" : gp(value);
	}

	/** Nullable backend ratio → percent text; "—" when the backend sent null. */
	static String pctOrDash(Double ratio)
	{
		return ratio == null ? "—" : pct(ratio);
	}

	/**
	 * Whole-flip duration (minutes, backend figure) → short label: "~14m",
	 * "~2h 05m"; "—" when the backend sent null. Presentation only.
	 */
	static String durationLabel(Double minutes)
	{
		if (minutes == null)
		{
			return "—";
		}
		long total = Math.round(minutes);
		if (total < 60)
		{
			return "~" + total + "m";
		}
		return "~" + (total / 60) + "h " + String.format("%02dm", total % 60);
	}

	/** Backend profit/item verbatim, "+" prefixed when positive; "—" if null. */
	static String profitPerItemLabel(Long profitPerItem)
	{
		if (profitPerItem == null)
		{
			return "—";
		}
		return (profitPerItem >= 0 ? "+" : "") + gp(profitPerItem);
	}

	/**
	 * Backend speed enum → short human label. Anything unexpected renders as
	 * "Unknown" — the plugin never guesses a speed itself.
	 */
	static String speedLabel(String speed)
	{
		if (speed == null)
		{
			return "Unknown";
		}
		switch (speed)
		{
			case "VERY_FAST":
				return "Very fast";
			case "FAST":
				return "Fast";
			case "MODERATE":
				return "Moderate";
			case "SLOW":
				return "Slow";
			default:
				return "Unknown";
		}
	}

	/** Risk enum → label color hex, matching the panel's existing palette. */
	static String riskColorHex(String riskLevel)
	{
		if (riskLevel == null)
		{
			return "#878d9c";
		}
		switch (riskLevel)
		{
			case "LOW":
				return "#4cba86";
			case "MEDIUM":
				return "#e3b75d";
			case "HIGH":
				return "#e8894a";
			case "AVOID":
				return "#e26a5e";
			default:
				return "#878d9c";
		}
	}

	/**
	 * Normalizes an item name coming from an alert payload before rendering:
	 * strips control/undefined/replacement characters, collapses whitespace,
	 * trims, and hard-caps the length so a corrupted or absurd value can
	 * never render as garbage or distort the narrow sidebar.
	 */
	static String sanitizeName(String raw)
	{
		if (raw == null)
		{
			return "Unknown item";
		}
		StringBuilder sb = new StringBuilder(raw.length());
		for (int i = 0; i < raw.length(); i++)
		{
			char c = raw.charAt(i);
			if (Character.isISOControl(c) || !Character.isDefined(c) || c == '�')
			{
				sb.append(' ');
				continue;
			}
			sb.append(c);
		}
		String cleaned = sb.toString().replaceAll("\\s+", " ").trim();
		if (cleaned.isEmpty())
		{
			return "Unknown item";
		}
		if (cleaned.length() > MAX_NAME_CHARS)
		{
			return cleaned.substring(0, MAX_NAME_CHARS - 1).trim() + "…";
		}
		return cleaned;
	}

	/** Ultra-short relative time ("now", "5m", "3h", "2d"); "" on bad input. */
	static String shortTimeAgo(String iso, long nowMs)
	{
		try
		{
			long minutes = Math.max(0,
				(nowMs - Instant.parse(iso).toEpochMilli()) / 60_000);
			if (minutes < 1)
			{
				return "now";
			}
			if (minutes < 60)
			{
				return minutes + "m";
			}
			long hours = minutes / 60;
			return hours < 24 ? hours + "h" : (hours / 24) + "d";
		}
		catch (RuntimeException e)
		{
			return "";
		}
	}

	private static String safe(String s)
	{
		if (s == null)
		{
			return "";
		}
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	private static String html(String inner)
	{
		return "<html>" + inner + "</html>";
	}

	private static String hex(Color c)
	{
		return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
	}
}
