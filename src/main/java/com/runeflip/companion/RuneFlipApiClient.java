package com.runeflip.companion;

import com.google.gson.Gson;
import java.io.IOException;
import java.util.function.Consumer;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Read-only fetches for the sidebar panel, plus the v0.6.3 pairing exchange.
 * The read endpoints need no token, so none is ever attached to them.
 * Private endpoints (capital, alerts) carry the anonymous
 * X-RuneFlip-Client-Id so the backend serves only THIS install's data.
 * Pairing is the one user-initiated POST here: it sends the pasted code
 * (never game data) and receives the clientId + ingest token. Responses are
 * parsed and displayed or stored in config — never acted on in-game.
 */
public class RuneFlipApiClient
{
	static final String CLIENT_ID_HEADER = "X-RuneFlip-Client-Id";
	static final String TOKEN_HEADER = "X-RuneFlip-Token";
	private static final MediaType JSON =
		MediaType.parse("application/json; charset=utf-8");

	private final OkHttpClient http;
	private final Gson gson;

	public RuneFlipApiClient(OkHttpClient http, Gson gson)
	{
		this.http = http;
		this.gson = gson;
	}

	public void fetchRecommendations(
		String backendUrl,
		int limit,
		Consumer<RuneFlipData.RecommendationsResponse> onSuccess,
		Runnable onFailure)
	{
		// Public market data: no client id required.
		get(backendUrl, "/flips/recommendations?limit=" + limit, null,
			RuneFlipData.RecommendationsResponse.class, onSuccess, onFailure);
	}

	public void fetchFastFlipOverview(
		String backendUrl,
		Consumer<RuneFlipData.FastFlipOverviewResponse> onSuccess,
		Runnable onFailure)
	{
		fetchFastFlipOverview(backendUrl, "", null, onSuccess, onFailure);
	}

	/**
	 * Overview with optional Strategy Engine params (v0.8.1) appended — the
	 * query string comes from {@link #strategyQueryOf} (saved preferences) and
	 * is empty for the default strategy. The client id is a v0.8.2 opt-in:
	 * fast-flip is public, but sending this install's anonymous id lets the
	 * backend's recommended actions know about THIS client's existing GE
	 * offers (the slotInsights block). The response is display-only — the
	 * plugin renders it, never acts on it.
	 */
	public void fetchFastFlipOverview(
		String backendUrl,
		String strategyQuery,
		String clientId,
		Consumer<RuneFlipData.FastFlipOverviewResponse> onSuccess,
		Runnable onFailure)
	{
		String query = strategyQuery == null ? "" : strategyQuery;
		// limit=12 (v0.8.18): fills the paginated suggestion list (4 pages of
		// 3). Older backends that cap lower simply yield fewer pages.
		get(backendUrl, "/fast-flip/overview?limit=12" + query, clientId,
			RuneFlipData.FastFlipOverviewResponse.class, onSuccess, onFailure);
	}

	/**
	 * Full RuneFlip context for ONE item (v0.8.4) — GET /fast-flip/item/{itemId}
	 * with the optional Strategy Engine params appended (same source as
	 * {@link #strategyQueryOf}). Used by the contextual side panel when the user
	 * opens an item in the GE Buy/Sell setup. The client id is forwarded (v0.8.2
	 * opt-in) so the recommended action can reflect this install's existing offer
	 * on the item. The backend never 404/500s here: an unknown or not-recommended
	 * item still returns 200 with recommended=false. Display-only — the response
	 * is rendered verbatim, never acted on.
	 */
	public void fetchFastFlipItem(
		String backendUrl,
		int itemId,
		String strategyQuery,
		String clientId,
		Consumer<RuneFlipData.FastFlipItemContextResponse> onSuccess,
		Runnable onFailure)
	{
		String query = strategyQuery == null || strategyQuery.isEmpty()
			? ""
			: "?" + strategyQuery.replaceFirst("^&", "");
		get(backendUrl, "/fast-flip/item/" + itemId + query, clientId,
			RuneFlipData.FastFlipItemContextResponse.class, onSuccess, onFailure);
	}

	/**
	 * This install's saved strategy preferences (v0.8.1) — private, scoped by
	 * the anonymous client id. A failing fetch (offline, or a pre-0.8.1
	 * backend answering 404) simply means the default strategy applies.
	 */
	public void fetchStrategyPreferences(
		String backendUrl,
		String clientId,
		Consumer<RuneFlipData.StrategyPreferencesResponse> onSuccess,
		Runnable onFailure)
	{
		get(backendUrl, "/strategy/preferences", clientId,
			RuneFlipData.StrategyPreferencesResponse.class, onSuccess, onFailure);
	}

	/**
	 * Saved preferences → overview query params. Defensive: only whitelisted
	 * risk values and in-range numbers ever reach the URL, and anything else
	 * (nothing saved, malformed, pre-0.8.1 shapes) yields the empty string —
	 * i.e. the backend's default strategy.
	 */
	static String strategyQueryOf(RuneFlipData.StrategyPreferencesResponse response)
	{
		if (response == null || response.preferences == null
			|| !Boolean.TRUE.equals(response.saved))
		{
			return "";
		}
		RuneFlipData.StrategyPreferences p = response.preferences;
		StringBuilder query = new StringBuilder();
		if (p.timeframeMinutes != null && p.timeframeMinutes >= 1
			&& p.timeframeMinutes <= 1440)
		{
			query.append("&timeframeMinutes=").append(p.timeframeMinutes);
		}
		if ("LOW".equals(p.riskLevel) || "MEDIUM".equals(p.riskLevel)
			|| "HIGH".equals(p.riskLevel))
		{
			query.append("&riskLevel=").append(p.riskLevel);
		}
		if (p.minPredictedProfit != null && p.minPredictedProfit >= 0)
		{
			query.append("&minPredictedProfit=").append(p.minPredictedProfit);
		}
		if (p.minRoi != null && p.minRoi >= 0 && p.minRoi <= 1)
		{
			query.append("&minRoi=").append(p.minRoi);
		}
		return query.toString();
	}

	public void fetchCapital(
		String backendUrl,
		String clientId,
		Consumer<RuneFlipData.CapitalLatestResponse> onSuccess,
		Runnable onFailure)
	{
		get(backendUrl, "/player-capital/latest", clientId,
			RuneFlipData.CapitalLatestResponse.class, onSuccess, onFailure);
	}

	public void fetchCompletedAlerts(
		String backendUrl,
		String clientId,
		Consumer<RuneFlipData.AlertsResponse> onSuccess,
		Runnable onFailure)
	{
		get(backendUrl, "/alerts?type=GE_SLOT_COMPLETED&limit=20", clientId,
			RuneFlipData.AlertsResponse.class, onSuccess, onFailure);
	}

	/**
	 * Exchanges a user-pasted pairing code for the paired clientId + scoped
	 * ingest token (v0.6.3). User-click only. The token in the response is
	 * handed to the caller to store as secret config — it is never logged.
	 */
	public void completePairing(
		String backendUrl,
		String code,
		Consumer<RuneFlipData.PairingResponse> onSuccess,
		Consumer<String> onFailure)
	{
		String endpoint = BackendUrl.pairingCompleteEndpoint(backendUrl);
		if (endpoint == null)
		{
			onFailure.accept("Set a valid Backend URL first.");
			return;
		}

		RuneFlipData.PairingRequest payload =
			new RuneFlipData.PairingRequest(code.trim());
		Request request = new Request.Builder()
			.url(endpoint)
			.post(RequestBody.create(JSON, gson.toJson(payload)))
			.build();

		http.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				onFailure.accept("Network error — is the backend reachable?");
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (Response r = response)
				{
					if (!r.isSuccessful())
					{
						onFailure.accept(describePairingFailure(r.code()));
						return;
					}
					ResponseBody body = r.body();
					RuneFlipData.PairingResponse parsed = body == null
						? null
						: gson.fromJson(body.string(), RuneFlipData.PairingResponse.class);
					if (!isValidPairingResponse(parsed))
					{
						onFailure.accept("Pairing failed: unexpected backend response.");
						return;
					}
					onSuccess.accept(parsed);
				}
				catch (IOException | RuntimeException e)
				{
					onFailure.accept("Pairing failed: unreadable backend response.");
				}
			}
		});
	}

	/**
	 * Best-effort token revoke on unpair (DELETE /pairing/token). The local
	 * config is cleared regardless of the outcome, so this always calls
	 * onDone and never blocks the unpair.
	 */
	public void revokeToken(String backendUrl, String token, Runnable onDone)
	{
		String endpoint = BackendUrl.pairingTokenEndpoint(backendUrl);
		if (endpoint == null || token == null || token.trim().isEmpty())
		{
			onDone.run();
			return;
		}

		Request request = new Request.Builder()
			.url(endpoint)
			.header(TOKEN_HEADER, token.trim())
			.delete()
			.build();

		http.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				onDone.run();
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				response.close();
				onDone.run();
			}
		});
	}

	/**
	 * Account pairing start (v0.9.1). Sends this install's anonymous client
	 * id + a friendly device name; receives the userCode to display and the
	 * deviceCode polling secret (kept in memory only — never logged).
	 */
	public void startDevicePairing(
		String backendUrl,
		String clientId,
		String deviceName,
		Consumer<RuneFlipData.DevicePairingStartResponse> onSuccess,
		Consumer<String> onFailure)
	{
		String endpoint = BackendUrl.pairingStartEndpoint(backendUrl);
		if (endpoint == null)
		{
			onFailure.accept("Set a valid Backend URL first.");
			return;
		}
		Request request = new Request.Builder()
			.url(endpoint)
			.header(CLIENT_ID_HEADER, clientId)
			.post(RequestBody.create(JSON, gson.toJson(
				new RuneFlipData.DevicePairingStartRequest(deviceName))))
			.build();
		http.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				onFailure.accept("Network error — is the backend reachable?");
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (Response r = response)
				{
					if (!r.isSuccessful())
					{
						onFailure.accept(r.code() == 404
							? "This backend has no account pairing yet (needs v0.9.1+)."
							: "Pairing start failed (HTTP " + r.code() + ").");
						return;
					}
					ResponseBody body = r.body();
					RuneFlipData.DevicePairingStartResponse parsed = body == null
						? null
						: gson.fromJson(body.string(),
							RuneFlipData.DevicePairingStartResponse.class);
					if (parsed == null
						|| parsed.deviceCode == null || parsed.deviceCode.trim().isEmpty()
						|| parsed.userCode == null || parsed.userCode.trim().isEmpty())
					{
						onFailure.accept("Pairing start failed: unexpected backend response.");
						return;
					}
					onSuccess.accept(parsed);
				}
				catch (IOException | RuntimeException e)
				{
					onFailure.accept("Pairing start failed: unreadable backend response.");
				}
			}
		});
	}

	/**
	 * Account pairing poll (v0.9.1): POST /pairing/token with the deviceCode.
	 * Always yields a DevicePollResponse (statuses drive the state machine);
	 * network problems surface as onFailure so the poller can retry.
	 */
	public void pollDevicePairing(
		String backendUrl,
		String deviceCode,
		Consumer<RuneFlipData.DevicePollResponse> onSuccess,
		Runnable onFailure)
	{
		String endpoint = BackendUrl.pairingPollEndpoint(backendUrl);
		if (endpoint == null)
		{
			onFailure.run();
			return;
		}
		Request request = new Request.Builder()
			.url(endpoint)
			.post(RequestBody.create(JSON, gson.toJson(
				new RuneFlipData.DevicePollRequest(deviceCode))))
			.build();
		http.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				onFailure.run();
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (Response r = response)
				{
					ResponseBody body = r.body();
					RuneFlipData.DevicePollResponse parsed =
						!r.isSuccessful() || body == null
							? null
							: gson.fromJson(body.string(),
								RuneFlipData.DevicePollResponse.class);
					if (parsed == null || parsed.status == null)
					{
						onFailure.run();
						return;
					}
					onSuccess.accept(parsed);
				}
				catch (IOException | RuntimeException e)
				{
					onFailure.run();
				}
			}
		});
	}

	/** Both fields present and non-blank; anything else is rejected. */
	static boolean isValidPairingResponse(RuneFlipData.PairingResponse response)
	{
		return response != null
			&& response.clientId != null && !response.clientId.trim().isEmpty()
			&& response.token != null && !response.token.trim().isEmpty();
	}

	/**
	 * Panel-friendly failure text per pairing response code. Never includes
	 * the code or any token — safe to display and log.
	 */
	static String describePairingFailure(int code)
	{
		switch (code)
		{
			case 400:
				return "That does not look like a pairing code.";
			case 401:
				return "Code invalid, expired or already used — generate a new one.";
			case 404:
				return "This backend has no pairing support yet (needs v0.6.3+).";
			case 429:
				return "Too many attempts — wait a minute and try again.";
			default:
				return "Pairing failed (HTTP " + code + ").";
		}
	}

	/**
	 * Plan & entitlements of this install (v0.9.2) — GET /entitlements with
	 * the stored credential (X-RuneFlip-Token: v0.9.1 rfd1_ device credential
	 * or legacy rfp_ token) plus the anonymous client id. The backend resolves
	 * the owning account's plan; an anonymous or unverifiable caller simply
	 * receives the FREE bundle — the endpoint never rejects. Display/gating
	 * input only: nothing here reads or acts on the game.
	 */
	public void fetchEntitlements(
		String backendUrl,
		String clientId,
		String token,
		Consumer<RuneFlipData.EntitlementsResponse> onSuccess,
		Runnable onFailure)
	{
		String base = BackendUrl.normalize(backendUrl);
		if (base == null)
		{
			onFailure.run();
			return;
		}
		Request.Builder builder =
			new Request.Builder().url(base + "/entitlements").get();
		if (clientId != null && !clientId.trim().isEmpty())
		{
			builder.header(CLIENT_ID_HEADER, clientId.trim());
		}
		if (token != null && !token.trim().isEmpty())
		{
			builder.header(TOKEN_HEADER, token.trim());
		}
		enqueue(builder.build(), RuneFlipData.EntitlementsResponse.class,
			onSuccess, onFailure);
	}

	private <T> void get(
		String backendUrl,
		String path,
		String clientId,
		Class<T> type,
		Consumer<T> onSuccess,
		Runnable onFailure)
	{
		String base = BackendUrl.normalize(backendUrl);
		if (base == null)
		{
			onFailure.run();
			return;
		}

		Request.Builder builder = new Request.Builder().url(base + path).get();
		if (clientId != null && !clientId.trim().isEmpty())
		{
			builder.header(CLIENT_ID_HEADER, clientId.trim());
		}
		enqueue(builder.build(), type, onSuccess, onFailure);
	}

	private <T> void enqueue(
		Request request,
		Class<T> type,
		Consumer<T> onSuccess,
		Runnable onFailure)
	{
		http.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				onFailure.run();
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (Response r = response)
				{
					ResponseBody body = r.body();
					if (!r.isSuccessful() || body == null)
					{
						onFailure.run();
						return;
					}
					T parsed = gson.fromJson(body.string(), type);
					if (parsed == null)
					{
						onFailure.run();
						return;
					}
					onSuccess.accept(parsed);
				}
				catch (IOException | RuntimeException e)
				{
					onFailure.run();
				}
			}
		});
	}
}
