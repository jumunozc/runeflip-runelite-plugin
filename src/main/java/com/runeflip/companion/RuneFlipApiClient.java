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
		// Public market data: no client id required. The response is
		// display-only — the plugin renders it and never acts on it.
		get(backendUrl, "/fast-flip/overview?limit=3", null,
			RuneFlipData.FastFlipOverviewResponse.class, onSuccess, onFailure);
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
		Request request = builder.build();
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
