import { z } from "zod";

/** AC-B32: Open-Meteo answers are cached 6 h per rounded location and day. */
export const WEATHER_CACHE_SECONDS = 6 * 3600;
const FORECAST_URL = "https://api.open-meteo.com/v1/forecast";
const ARCHIVE_URL = "https://archive-api.open-meteo.com/v1/archive";
/** The forecast API serves the last ~92 days and the next 16; older days come from the archive. */
const FORECAST_PAST_DAYS = 80;
const FORECAST_FUTURE_DAYS = 15;
const DAY_MS = 86_400_000;

const isoDay = z.string().regex(/^\d{4}-\d{2}-\d{2}$/).refine((s) => !Number.isNaN(Date.parse(`${s}T00:00:00Z`)));

export const weatherQuerySchema = z.object({
  lat: z.coerce.number().min(-90).max(90),
  lng: z.coerce.number().min(-180).max(180),
  date: isoDay,
});

/** Condition keys the apps turn into words (strings stay in the apps, per docs/voice.md). */
export type Conditions =
  | "clear" | "partly_cloudy" | "cloudy" | "fog" | "drizzle" | "rain" | "freezing_rain" | "snow" | "showers" | "thunderstorm" | "unknown";

export interface DayWeather {
  date: string;
  highF: number | null;
  lowF: number | null;
  precipIn: number | null;
  code: number | null;
  conditions: Conditions;
}

/** WMO weather interpretation codes as Open-Meteo documents them. */
export function conditionsFor(code: number | null): Conditions {
  if (code === null) return "unknown";
  if (code === 0) return "clear";
  if (code === 1 || code === 2) return "partly_cloudy";
  if (code === 3) return "cloudy";
  if (code === 45 || code === 48) return "fog";
  if (code >= 51 && code <= 55) return "drizzle";
  if (code === 56 || code === 57 || code === 66 || code === 67) return "freezing_rain";
  if (code >= 61 && code <= 65) return "rain";
  if ((code >= 71 && code <= 77) || code === 85 || code === 86) return "snow";
  if (code >= 80 && code <= 82) return "showers";
  if (code >= 95 && code <= 99) return "thunderstorm";
  return "unknown";
}

/** About 1 km: crews on the same job share one cache entry and one upstream call. */
export function weatherCacheKey(lat: number, lng: number, date: string): string {
  return `weather:${lat.toFixed(2)}:${lng.toFixed(2)}:${date}`;
}

/** Null when the day is too far ahead for any forecast. */
export function weatherUrl(lat: number, lng: number, date: string, now = Date.now()): string | null {
  const day = Date.parse(`${date}T00:00:00Z`);
  const today = Date.parse(`${new Date(now).toISOString().slice(0, 10)}T00:00:00Z`);
  const offset = Math.round((day - today) / DAY_MS);
  if (offset > FORECAST_FUTURE_DAYS) return null;
  const base = offset < -FORECAST_PAST_DAYS ? ARCHIVE_URL : FORECAST_URL;
  const q = new URLSearchParams({
    latitude: lat.toFixed(4),
    longitude: lng.toFixed(4),
    daily: "temperature_2m_max,temperature_2m_min,precipitation_sum,weather_code",
    temperature_unit: "fahrenheit",
    precipitation_unit: "inch",
    timezone: "auto",
    start_date: date,
    end_date: date,
  });
  return `${base}?${q}`;
}

interface OpenMeteoDaily {
  daily?: {
    time?: string[];
    temperature_2m_max?: (number | null)[];
    temperature_2m_min?: (number | null)[];
    precipitation_sum?: (number | null)[];
    weather_code?: (number | null)[];
  };
}

const num = (v: unknown): number | null => (typeof v === "number" && Number.isFinite(v) ? v : null);
const round1 = (v: number | null) => (v === null ? null : Math.round(v * 10) / 10);

/** Picks the requested day out of an Open-Meteo daily answer; null when the day is missing. */
export function parseDaily(raw: OpenMeteoDaily, date: string): DayWeather | null {
  const d = raw.daily;
  const i = d?.time?.indexOf(date) ?? -1;
  if (!d || i < 0) return null;
  const code = num(d.weather_code?.[i]);
  const high = num(d.temperature_2m_max?.[i]);
  const low = num(d.temperature_2m_min?.[i]);
  if (high === null && low === null && code === null) return null;
  return {
    date,
    highF: high === null ? null : Math.round(high),
    lowF: low === null ? null : Math.round(low),
    precipIn: round1(num(d.precipitation_sum?.[i])),
    code,
    conditions: conditionsFor(code),
  };
}

export async function fetchWeather(
  lat: number, lng: number, date: string, fetchFn: typeof fetch = fetch, now = Date.now(),
): Promise<DayWeather | null> {
  const url = weatherUrl(lat, lng, date, now);
  if (!url) return null;
  const res = await fetchFn(url);
  if (!res.ok) return null;
  const raw = (await res.json().catch(() => null)) as OpenMeteoDaily | null;
  return raw ? parseDaily(raw, date) : null;
}
