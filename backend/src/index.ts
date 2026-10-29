import { Hono } from "hono";
import type { AppEnv } from "./env";
import { auth } from "./routes/auth";
import { dev } from "./routes/dev";
import { company } from "./routes/company";
import { lookup } from "./routes/lookup";
import { media, removeOrphanedMedia } from "./routes/media";
import { pruneLimits } from "./auth/rateLimit";
import { me } from "./routes/me";
import { pruneSync, sync } from "./routes/sync";
import { tenants } from "./routes/tenants";

const app = new Hono<AppEnv>();

app.get("/health", (c) => c.json({ ok: true, environment: c.env.ENVIRONMENT }));
app.route("/auth", auth);
app.use("/dev/*", async (c, next) => (c.env.ENVIRONMENT === "dev" ? next() : c.json({ error: "not_found" }, 404)));
app.route("/dev", dev);
app.route("/me", me);
app.route("/", company);
app.route("/", tenants);
app.route("/", sync);
app.route("/", lookup);
app.route("/", media);

app.notFound((c) => c.json({ error: "not_found" }, 404));
app.onError((err, c) => {
  console.error(err);
  return c.json({ error: "server_error" }, 500);
});

// Keeps `app.request` for tests; the Workers runtime also calls `scheduled` for the cron trigger.
export default Object.assign(app, {
  scheduled: (_event: ScheduledEvent, env: AppEnv["Bindings"], ctx: ExecutionContext) => {
    ctx.waitUntil(removeOrphanedMedia(env));
    ctx.waitUntil(pruneLimits(env.DB));
    ctx.waitUntil(pruneSync(env.DB));
  },
});
