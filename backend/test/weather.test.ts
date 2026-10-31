// AC-B32: GET /weather proxies Open-Meteo (no key) with a 6 h cache
import { env } from "cloudflare:workers";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import app from "../src/index";
import { WEATHER_CACHE_SECONDS, conditionsFor, parseDaily, weatherCacheKey, weatherUrl } from "../src/lookup/weather";
import { seedUser } from "./helpers";

const today = new Date().toISOString().slice(0, 10);
const daily = (date: string) => ({
  daily: {
    time: [date],
    temperature_2m_max: [61.6],
    temperature_2m_min: [44.2],
    precipitation_sum: [0.234],
    weather_code: [63],
  },
});
const hdr = (t: string) => ({ Authorization: `Bearer ${t}` });

describe("AC-B32 GET /weather", () => {
  let upstream: ReturnType<typeof vi.fn>;
  beforeEach(() => {
    upstream = vi.fn(async (url: string) => {
      const date = new URL(url).searchParams.get("start_date") ?? today;
      return new Response(JSON.stringify(daily(date)), { status: 200 });
    });
    vi.stubGlobal("fetch", upstream);
  });
  afterEach(() => vi.unstubAllGlobals());
  const get = (t: string, qs: string) => app.request(`/weather?${qs}`, { headers: hdr(t) }, env);

  it("requires a session and valid lat, lng and date", async () => {
    expect((await app.request(`/weather?lat=40&lng=-75&date=${today}`, {}, env)).status).toBe(401);
    const t = await seedUser("wx0", "wx0@example.com");
    expect((await get(t, `lat=40&date=${today}`)).status).toBe(400);
    expect((await get(t, `lat=95&lng=-75&date=${today}`)).status).toBe(400);
    expect((await get(t, "lat=40&lng=-75&date=Oct-31")).status).toBe(400);
    expect(upstream).not.toHaveBeenCalled();
  });

  it("returns high, low, precipitation and conditions in Fahrenheit and inches from Open-Meteo, without a key", async () => {
    const t = await seedUser("wx1", "wx1@example.com");
    const res = await get(t, `lat=40.0583&lng=-75.4001&date=${today}`);
    expect(res.status).toBe(200);
    expect(await res.json()).toEqual({
      date: today, highF: 62, lowF: 44, precipIn: 0.2, code: 63, conditions: "rain", cached: false,
    });
    const url = new URL(upstream.mock.calls[0][0] as string);
    expect(url.host).toBe("api.open-meteo.com");
    expect(url.searchParams.get("temperature_unit")).toBe("fahrenheit");
    expect(url.searchParams.get("precipitation_unit")).toBe("inch");
    expect(url.searchParams.get("start_date")).toBe(today);
    expect(url.searchParams.has("apikey")).toBe(false);
  });

  it("serves the same job's later requests from the 6 h cache", async () => {
    const t = await seedUser("wx2", "wx2@example.com");
    const date = new Date(Date.now() - 2 * 86_400_000).toISOString().slice(0, 10);
    expect(((await (await get(t, `lat=39.9526&lng=-75.1652&date=${date}`)).json()) as { cached: boolean }).cached).toBe(false);
    const again = (await (await get(t, `lat=39.95261&lng=-75.16518&date=${date}`)).json()) as { cached: boolean; highF: number };
    expect(again.cached).toBe(true);
    expect(again.highF).toBe(62);
    expect(upstream).toHaveBeenCalledTimes(1);
    expect(WEATHER_CACHE_SECONDS).toBe(6 * 3600);
  });

  it("answers 502 when Open-Meteo fails and does not cache the failure", async () => {
    const t = await seedUser("wx3", "wx3@example.com");
    upstream.mockResolvedValueOnce(new Response("busy", { status: 503 }));
    expect((await get(t, `lat=41.1&lng=-74.1&date=${today}`)).status).toBe(502);
    expect(await env.CACHE.get(weatherCacheKey(41.1, -74.1, today))).toBeNull();
    expect((await get(t, `lat=41.1&lng=-74.1&date=${today}`)).status).toBe(200);
  });

  it("answers 404 for a day too far ahead for a forecast", async () => {
    const t = await seedUser("wx4", "wx4@example.com");
    const far = new Date(Date.now() + 40 * 86_400_000).toISOString().slice(0, 10);
    expect((await get(t, `lat=40&lng=-75&date=${far}`)).status).toBe(404);
    expect(upstream).not.toHaveBeenCalled();
  });

  it("uses the archive for old days and the forecast API for recent ones", () => {
    const now = Date.parse("2026-10-31T15:00:00Z");
    expect(new URL(weatherUrl(40, -75, "2026-10-30", now) ?? "").host).toBe("api.open-meteo.com");
    expect(new URL(weatherUrl(40, -75, "2026-03-02", now) ?? "").host).toBe("archive-api.open-meteo.com");
    expect(weatherUrl(40, -75, "2026-11-20", now)).toBeNull();
  });

  it("maps WMO codes to condition keys and skips a missing day", () => {
    expect([0, 2, 3, 45, 53, 57, 65, 75, 81, 95, 42].map(conditionsFor)).toEqual([
      "clear", "partly_cloudy", "cloudy", "fog", "drizzle", "freezing_rain", "rain", "snow", "showers", "thunderstorm", "unknown",
    ]);
    expect(parseDaily(daily("2026-10-30"), "2026-10-31")).toBeNull();
    expect(parseDaily({ daily: { time: ["2026-10-31"], weather_code: [null], temperature_2m_max: [null] } }, "2026-10-31")).toBeNull();
  });
});
