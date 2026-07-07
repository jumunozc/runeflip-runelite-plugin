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
	private static final Color PANEL_BG = ColorScheme.DARKER_GRAY_COLOR;
	private static final Color CARD_BG = ColorScheme.DARK_GRAY_COLOR;
	private static final Color PROFIT = new Color(0x4c, 0xba, 0x86);
	private static final Color GOLD = new Color(0xe3, 0xb7, 0x5d);
	private static final Color ALERT = new Color(0xe8, 0x89, 0x4a);
	private static final Color MUTED = new Color(0x87, 0x8d, 0x9c);

	private final ItemManager itemManager;
	private final Runnable onRefresh;
	private final PairingActions pairingActions;

	private final JLabel statusLabel = new JLabel("Offline");
	private final JLabel capitalLabel = new JLabel(" ");
	private final JPanel topCard = new JPanel();
	private final JPanel listPanel = new JPanel();
	private final JLabel completedHeader = new JLabel("GE completed · 0");
	private final JPanel completedPanel = new JPanel();
	private final JLabel disclaimer = new JLabel();

	private final JLabel pairingStatus = new JLabel("Not paired");
	private final javax.swing.JTextField pairingCodeField = new javax.swing.JTextField();
	private final JButton pairButton;
	private final JButton unpairButton;
	private final JPanel pairingInputRow = new JPanel(new BorderLayout(6, 0));
	private final JLabel pairingHint = new JLabel();

	/** Rows shown in the compact completed summary; the rest is "+n more". */
	private static final int MAX_COMPLETED_ROWS = 3;
	/** Hard cap so one absurd name can never distort the narrow sidebar. */
	private static final int MAX_NAME_CHARS = 40;

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

	public RuneFlipPanel(
		ItemManager itemManager,
		Runnable onRefresh,
		PairingActions pairingActions,
		boolean initiallyPaired)
	{
		this.itemManager = itemManager;
		this.onRefresh = onRefresh;
		this.pairingActions = pairingActions;
		this.pairButton = smallButton("Pair");
		this.unpairButton = smallButton("Unpair");

		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(PANEL_BG);
		setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

		// ── Header: title + status + refresh ────────────────────────────────
		JPanel header = new JPanel(new BorderLayout());
		header.setOpaque(false);
		JLabel title = new JLabel("RuneFlip");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(GOLD);
		header.add(title, BorderLayout.WEST);

		JPanel headerRight = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
		headerRight.setOpaque(false);
		statusLabel.setFont(FontManager.getRunescapeSmallFont());
		statusLabel.setForeground(MUTED);
		headerRight.add(statusLabel);
		JButton refresh = smallButton("Refresh");
		refresh.addActionListener(e -> onRefresh.run());
		headerRight.add(refresh);
		header.add(headerRight, BorderLayout.EAST);
		header.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		add(header);
		add(Box.createVerticalStrut(6));

		// ── Capital (read-only observation) ─────────────────────────────────
		capitalLabel.setFont(FontManager.getRunescapeSmallFont());
		capitalLabel.setForeground(MUTED);
		JPanel capitalRow = wrap(capitalLabel);
		add(capitalRow);
		add(Box.createVerticalStrut(8));

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
		if (capital == null)
		{
			capitalLabel.setText(html(
				"Capital: manual / default (enable Capital sync in the plugin "
					+ "settings for a read-only estimate)"));
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
		actions.add(actionButton("Copy name", () -> copy(rec.name)));
		actions.add(actionButton("Copy buy price", () -> copy(String.valueOf(rec.buyPrice))));
		actions.add(actionButton("Copy sell price", () -> copy(String.valueOf(rec.sellPrice))));
		actions.add(actionButton("Copy qty", () -> copy(String.valueOf(rec.suggestedQuantity))));
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
	 * The requested "Search item" affordance, kept DISABLED on purpose.
	 *
	 * <p>Research finding (RuneLite {@code GrandExchangePlugin}, Plugin Hub
	 * flipping tools): RuneLite exposes no <em>safe, client-supported</em> way
	 * to place an item name into the <b>in-game</b> GE search box. Its own
	 * {@code search(name)} opens RuneLite's sidebar price lookup, not the game
	 * widget; prefilling the real search requires
	 * {@code client.setVarcStrValue(VarClientStr.INPUT_TEXT, …)} plus a client
	 * script — synthetic input into the game client. That is exactly what
	 * RuneFlip's no-automation contract forbids (no synthetic input, no
	 * keyboard automation, no widget mutation that drives the client).
	 *
	 * <p>So the button stays disabled with an explanatory tooltip; the working
	 * manual path is "Copy name" + the user typing it in the official client.
	 * If a genuinely safe public API ever appears, wire it here — user-click
	 * only, item name only, never selection/price/quantity/offer/submit.
	 */
	private JButton disabledSearchButton()
	{
		JButton b = smallButton("Search item");
		b.setEnabled(false);
		b.setToolTipText(
			"<html>Disabled: RuneLite has no safe way to fill the in-game GE "
				+ "search without synthetic input, which RuneFlip never does. "
				+ "Use Copy name and type it in the official client.</html>");
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
