import { cloudflareTest, readD1Migrations } from "@cloudflare/vitest-pool-workers";
import { defineConfig } from "vitest/config";

export default defineConfig(async () => {
  const migrations = await readD1Migrations("migrations");
  return {
    plugins: [
      cloudflareTest({
        main: "./src/index.ts",
        wrangler: { configPath: "./wrangler.toml" },
        miniflare: {
          bindings: { TEST_MIGRATIONS: migrations, JWT_SECRET: "test-secret-test-secret-test-secret-1234", ENVIRONMENT: "test" },
        },
      }),
    ],
    test: { setupFiles: ["./test/setup.ts"] },
  };
});
