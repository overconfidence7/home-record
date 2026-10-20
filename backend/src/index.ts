import { Hono } from "hono";
import type { AppEnv } from "./env";
import { auth } from "./routes/auth";
import { company } from "./routes/company";
import { me } from "./routes/me";
import { sync } from "./routes/sync";
import { tenants } from "./routes/tenants";

const app = new Hono<AppEnv>();

app.get("/health", (c) => c.json({ ok: true, environment: c.env.ENVIRONMENT }));
app.route("/auth", auth);
app.route("/me", me);
app.route("/", company);
app.route("/", tenants);
app.route("/", sync);

app.notFound((c) => c.json({ error: "not_found" }, 404));
app.onError((err, c) => {
  console.error(err);
  return c.json({ error: "server_error" }, 500);
});

export default app;
