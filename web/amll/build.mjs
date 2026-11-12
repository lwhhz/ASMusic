import { build } from "esbuild";
import { fileURLToPath } from "node:url";
import { dirname, resolve } from "node:path";

const here = dirname(fileURLToPath(import.meta.url));
const outDir = resolve(here, "../../androidApp/src/main/assets/amll");

await build({
  entryPoints: [resolve(here, "src/entry.mjs")],
  outfile: resolve(outDir, "bundle.js"),
  bundle: true,
  format: "iife",
  platform: "browser",
  target: ["chrome100"],
  minify: true,
  sourcemap: false,
  legalComments: "none",
  define: { "process.env.NODE_ENV": '"production"' },
  logLevel: "info",
});

console.log("built ->", outDir);
