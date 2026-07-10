package com.runeflip.companion;

import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.ScriptID;
import net.runelite.api.VarClientInt;
import net.runelite.api.VarClientStr;
import net.runelite.api.Varbits;
import net.runelite.api.vars.InputType;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;

/**
 * COMPLIANCE — Explicit GE Field Assist (v0.8.11). This class is the ONLY
 * place in the plugin allowed to write into a game input field, and the
 * ComplianceScanTest enforces exactly that: {@code setVarcStrValue} /
 * {@code runScript} anywhere else fails the build.
 *
 * <p>Official rule: <b>RuneFlip can
 * prepare GE fields after explicit user action,
 * but must never submit or execute the offer.</b>
 *
 * <p>What "prepare" means here — and all it means: the pending chatbox INPUT
 * TEXT (a client-side typing buffer, {@link VarClientStr#INPUT_TEXT}) is set
 * to the suggested value and redrawn, exactly as if the user had typed it.
 * The user still presses Enter to apply the field, still reviews the offer,
 * and still clicks Confirm themselves. Guardrails, all enforced before any
 * write:
 * <ul>
 *   <li><b>Explicit user action only.</b> Every method requires
 *       {@link GeFieldAssist.ActionSource#USER_CLICK} — the click on a
 *       "RuneFlip: …" menu option. {@code AUTOMATIC} (timers, events,
 *       background code) is rejected by {@link GeFieldAssist#canPrepare};
 *       nothing here can run headless or in a loop.</li>
 *   <li><b>Right editor, verified.</b> Writes happen only while the exact GE
 *       editor is open: the GE offer-setup container plus the item-search
 *       input for {@link #prepareItemSearch}, or the chatbox value input for
 *       {@link #prepareQuantity}/{@link #preparePrice}. Wrong state = no-op.</li>
 *   <li><b>Never submit, never execute.</b> No method here (or anywhere in
 *       the plugin) confirms, submits, cancels, aborts or collects an offer,
 *       presses Enter, clicks a widget, or invokes a menu action. The only
 *       scripts ever run are the input-redraw ones for the text just
 *       prepared. Do not add anything of the sort — the scan will fail the
 *       build, and the project rule forbids it.</li>
 * </ul>
 *
 * <p>Must be called on the client thread (menu-option {@code onClick}
 * callbacks already are).
 */
class GeFieldAssistService
{
	private final Client client;

	GeFieldAssistService(Client client)
	{
		this.client = client;
	}

	/**
	 * Prepares the GE item-search text with the suggested item's name, so the
	 * user sees it as the typed search and picks the result themselves. Only
	 * on an explicit click and only while the GE search is open.
	 */
	boolean prepareItemSearch(String itemName, GeFieldAssist.ActionSource source)
	{
		if (itemName == null || itemName.trim().isEmpty()
			|| !GeFieldAssist.canPrepare(source, isItemSearchOpen()))
		{
			return false;
		}
		prepareInputText(itemName.trim());
		return true;
	}

	/** Prepares the GE quantity editor's pending input. Click-only. */
	boolean prepareQuantity(long quantity, GeFieldAssist.ActionSource source)
	{
		if (quantity <= 0
			|| !GeFieldAssist.canPrepare(source, isValueEditorOpen()))
		{
			return false;
		}
		prepareInputText(String.valueOf(quantity));
		return true;
	}

	/** Prepares the GE price editor's pending input. Click-only. */
	boolean preparePrice(long price, GeFieldAssist.ActionSource source)
	{
		if (price <= 0
			|| !GeFieldAssist.canPrepare(source, isValueEditorOpen()))
		{
			return false;
		}
		prepareInputText(String.valueOf(price));
		return true;
	}

	/**
	 * The one write path: sets the pending chatbox input text and redraws it.
	 * {@link ScriptID#CHAT_TEXT_INPUT_REBUILD} only re-renders the input line
	 * the user is already editing — it applies nothing and submits nothing.
	 */
	private void prepareInputText(String value)
	{
		client.setVarcStrValue(VarClientStr.INPUT_TEXT, value);
		client.runScript(ScriptID.CHAT_TEXT_INPUT_REBUILD, "");
		client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
			GeFieldAssist.PREPARED_MESSAGE, null);
	}

	// ── Read-only GE state checks (also used by the plugin to decide which
	//    "RuneFlip: …" menu OPTION to display — displaying an option is not an
	//    action; the write above still re-validates on the actual click) ─────

	/** GE offer setup open AND the item-search chatbox is the active input. */
	boolean isItemSearchOpen()
	{
		return isOfferSetupOpen()
			&& client.getVarcIntValue(VarClientInt.INPUT_TYPE)
				== InputType.SEARCH.getType();
	}

	/** GE offer setup open AND a chatbox value input (qty/price) is open. */
	boolean isValueEditorOpen()
	{
		if (!isOfferSetupOpen())
		{
			return false;
		}
		Widget input = client.getWidget(ComponentID.CHATBOX_FULL_INPUT);
		return input != null && !input.isHidden();
	}

	/** The chatbox prompt text ("How many do you wish to buy?" …), or null. */
	String chatboxPrompt()
	{
		Widget title = client.getWidget(ComponentID.CHATBOX_TITLE);
		return title == null || title.isHidden() ? null : title.getText();
	}

	/** Whether the offer being set up is a SELL (read-only varbit). */
	boolean isSellOffer()
	{
		return client.getVarbitValue(Varbits.GE_OFFER_CREATION_TYPE) == 1;
	}

	private boolean isOfferSetupOpen()
	{
		Widget container =
			client.getWidget(ComponentID.GRAND_EXCHANGE_OFFER_CONTAINER);
		return container != null && !container.isHidden();
	}
}
