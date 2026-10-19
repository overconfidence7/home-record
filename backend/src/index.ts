import { Hono } from "hono";
import type { AppEnv } from "./env";
import { me } from "./routes/me";
import { tenants } from "./routes/tenants";

const app = new Hono<AppEnv>();

app.get("/health", (c) => c.json({ ok: true, environment: c.env.ENVIRONMENT }));
app.route("/me", me);
app.route("/", tenants);

app.notFound((c) => c.json({ error: "not_found" }, 404));
app.onError((err, c) => {
  console.error(err);
  return c.json({ error: "server_error" }, 500);
});

export default app;
