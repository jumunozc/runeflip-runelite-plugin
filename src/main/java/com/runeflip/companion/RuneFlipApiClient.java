package com.runeflip.companion;

import com.google.gson.Gson;
import java.io.IOException;
import java.util.function.Consumer;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Read-only fetches for the sidebar panel. GET requests only, against the
 * user's own backend; the read endpoints need no token, so none is ever
 * attached here. Private endpoints (capital, alerts) carry the anonymous
 * X-RuneFlip-Client-Id so the backend serves only THIS install's data.
 * Responses are parsed and displayed — never acted on.
 */
public class RuneFlipApiClient
{
	static final String CLIENT_ID_HEADER = "X-RuneFlip-Client-Id";

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
