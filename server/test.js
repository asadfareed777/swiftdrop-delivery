// End-to-end check: start the server, place an order, and verify the event stream.
// Run with:  npm test   (uses short prep time and high speed so it finishes in seconds)
process.env.PREP = process.env.PREP || "2";
process.env.SPEED = process.env.SPEED || "150";

const assert = require("node:assert");
const { WebSocket } = require("ws");
const { start, shortestPath, Route } = require("./tracking-server");

// Routing parity with the Kotlin core (DeliveryCoreTest): river crossing via a bridge.
const r = new Route(shortestPath("r5c4", "r8c4"));
assert.strictEqual(r.length, 1050, "bridge detour length should match Kotlin core");

const wss = start(18080);
const ws = new WebSocket("ws://localhost:18080/orders/demo-1?restaurant=r2c1&home=r11c6");
const statuses = [];
let locations = 0;
let lastAt = 0;

const timeout = setTimeout(() => { console.error("timed out", statuses); process.exit(1); }, 30000);

ws.on("message", (data) => {
  const msg = JSON.parse(data);
  assert.ok(msg.at >= lastAt, "events must be time ordered");
  lastAt = msg.at;
  if (msg.type === "status") statuses.push(msg.status);
  if (msg.type === "location") locations++;
  if (msg.status === "DELIVERED") {
    assert.deepStrictEqual(statuses, ["ACCEPTED", "PREPARING", "READY_FOR_PICKUP", "PICKED_UP", "ARRIVING", "DELIVERED"]);
    assert.ok(locations > 5, "expected live courier locations");
    console.log(`ok: ${statuses.length} statuses, ${locations} location fixes`);
    clearTimeout(timeout);
    ws.close();
    wss.close();
  }
});

// Bad request is rejected with a clear close code.
const bad = new WebSocket("ws://localhost:18080/nope");
bad.on("close", (code) => assert.strictEqual(code, 4400));
