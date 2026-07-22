import fs from "node:fs";
import path from "node:path";
import { pathToFileURL } from "node:url";

const [webPath, wasmPath, hostPath] = process.argv.slice(2);
if (!webPath || !wasmPath || !hostPath) throw new Error("missing conformance paths");

const bi = (n) => BigInt(n);
const allBullseyeCorrectPerfect = [
  42, 42, 42, 42,
  0, 0, 0, 0,
  0, 1, 2, 3,
  0, 1, 2, 3,
  0, 1, 2, 3,
  0, 1, 2, 3,
].map(bi);
const mixed = [
  42, 47, 37, 100,
  0, 1, 0, 3,
  0, 1, 2, 3,
  1, 0, 2, 3,
  3, 2, 1, 0,
  2, 3, 0, 1,
].map(bi);
const cases = [
  [allBullseyeCorrectPerfect, 0n],
  [mixed, 1n],
];
const rejected = [[1n, 2n, 3n], Array(23).fill(0n), Array(25).fill(0n)];

const web = await import(pathToFileURL(path.resolve(webPath)));
if (web.kotobaArtifact.requiredCapabilities.length !== 0)
  throw new Error("Vamos Web graph requested a capability");
if (web.instantiateKotoba().main() !== 42n) throw new Error("Vamos Web main mismatch");
for (const [values, caseId] of cases)
  if (web.instantiateKotoba()["tally-check"](values, caseId) !== 42n)
    throw new Error("Vamos Web tally mismatch");
for (const values of rejected)
  if (web.instantiateKotoba()["reject-check"](values) !== 42n)
    throw new Error("Vamos Web accepted a malformed input vector");

const host = await import(pathToFileURL(path.resolve(hostPath)));
const wasmBytes = fs.readFileSync(path.resolve(wasmPath));
for (const [values, caseId] of cases) {
  const wasm = await host.instantiateKotoba(wasmBytes);
  if (wasm.instance.exports["tally-check"](
      wasm.typedValues.vectorI64(values), caseId) !== 42n)
    throw new Error("Vamos Wasm tally mismatch");
}
for (const values of rejected) {
  const wasm = await host.instantiateKotoba(wasmBytes);
  if (wasm.instance.exports["reject-check"](
      wasm.typedValues.vectorI64(values)) !== 42n)
    throw new Error("Vamos Wasm accepted a malformed input vector");
}
const wasmMain = await host.instantiateKotoba(wasmBytes);
if (wasmMain.instance.exports.main() !== 42n) throw new Error("Vamos Wasm main mismatch");
console.log("ghosthacker-vamos: bounded party-tally Web/Wasm conformance passed");
