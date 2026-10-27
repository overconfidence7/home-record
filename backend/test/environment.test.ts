import { env } from "cloudflare:workers";
import { describe, expect, it } from "vitest";
import app from "../src/index";
import wranglerToml from "../wrangler.toml?raw";
import pkg from "../package.json";

// Q005 / AC-B2: /dev/* exists only in a dev environment, and a deploy must fail safe (cr:dev-env-default).
describe("AC-B2 environment defaults", () => {
  it("wrangler.toml defaults ENVIRONMENT to production", () => {
    const vars = wranglerToml.split(/^\[vars\]\s*$/m)[1]?.split(/^\[/m)[0] ?? "";
    expect(vars).toMatch(/^ENVIRONMENT\s*=\s*"production"\s*$/m);
    expect(wranglerToml).not.toMatch(/^ENVIRONMENT\s*=\s*"dev"/m);
  });
  it("npm run dev turns dev on and npm run deploy names production", () => {
    expect(pkg.scripts.dev).toContain("--var ENVIRONMENT:dev");
    expect(pkg.scripts.deploy).toContain("--var ENVIRONMENT:production");
  });
  it("dev routes stay closed when ENVIRONMENT is missing or not exactly dev", async () => {
    const { ENVIRONMENT: _drop, ...noEnv } = env as unknown as Record<string, unknown>;
    for (const e of [noEnv, { ...env, ENVIRONMENT: "Dev" }, { ...env, ENVIRONMENT: "" }]) {
      expect((await app.request("/dev/reset", { method: "POST" }, e)).status).toBe(404);
      expect((await app.request("/dev/last-code?email=a@b.co", {}, e)).status).toBe(404);
    }
  });
});
