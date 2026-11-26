// Swiftdrop live tracking server.
//
// Streams order status, courier location and ETA over a WebSocket using the same
// JSON protocol the Android app decodes (see TrackingProtocol.kt):
//
//   { "type": "status",   "status": "PICKED_UP", "at": 1764170000000 }
//   { "type": "location", "x": 450.0, "y": 300.0, "bearing": 90.0, "at": ... }
//   { "type": "eta",      "seconds": 240, "at": ... }
//
// Connect to   ws://<host>:8080/orders/<orderId>?restaurant=r2c1&home=r11c6
// From the Android emulator the host is 10.0.2.2.
//
// The street graph (city.json) is exported from the Kotlin :delivery-core module,
// so client and server always agree on the map.

const { WebSocketServer } = require("ws");
const city = require("./city.json");

const PORT = Number(process.env.PORT || 8080);
const SPEED = Number(process.env.SPEED || 9); // metres per second (sped up for demos)
const TICK = 1000;
const PREP_SECONDS = Number(process.env.PREP || 18);
const COURIER_START = "r5c4";

const dist = (a, b) => Math.hypot(b[0] - a[0], b[1] - a[1]);
const bearing = (a, b) => ((Math.atan2(b[0] - a[0], -(b[1] - a[1])) * 180) / Math.PI + 360) % 360;

/** A* over the exported street graph. Returns an array of [x, y] points. */
function shortestPath(from, to) {
  const goal = city.nodes[to];
  const g = { [from]: 0 };
  const prev = {};
  const open = new Map([[from, dist(city.nodes[from], goal)]]);
  const closed = new Set();
  while (open.size) {
    let current = null;
    let best = Infinity;
    for (const [id, f] of open) if (f < best) { best = f; current = id; }
    open.delete(current);
    if (current === to) break;
    closed.add(current);
    for (const next of city.adjacency[current] || []) {
      if (closed.has(next)) continue;
      const tentative = g[current] + dist(city.nodes[current], city.nodes[next]);
      if (tentative < (g[next] ?? Infinity)) {
        g[next] = tentative;
        prev[next] = current;
        open.set(next, tentative + dist(city.nodes[next], goal));
      }
    }
  }
  if (from !== to && !(to in prev)) return null;
  const ids = [to];
  while (ids[0] !== from) ids.unshift(prev[ids[0]]);
  return ids.map((id) => city.nodes[id]);
}

class Route {
  constructor(points) {
    this.points = points;
    this.cum = [0];
    for (let i = 1; i < points.length; i++) this.cum.push(this.cum[i - 1] + dist(points[i - 1], points[i]));
    this.length = this.cum[this.cum.length - 1];
  }
  at(d) {
    if (this.points.length === 1) return { x: this.points[0][0], y: this.points[0][1], bearing: 0 };
    d = Math.max(0, Math.min(d, this.length));
    let i = 1;
    while (i < this.points.length - 1 && this.cum[i] < d) i++;
    const a = this.points[i - 1];
    const b = this.points[i];
    const seg = this.cum[i] - this.cum[i - 1];
    const t = seg === 0 ? 0 : (d - this.cum[i - 1]) / seg;
    return { x: a[0] + (b[0] - a[0]) * t, y: a[1] + (b[1] - a[1]) * t, bearing: bearing(a, b) };
  }
}

/** Runs one order's lifecycle, calling send(message) on a timer. Returns a stop() function. */
function runOrder({ restaurant, home }, send) {
  const toRestaurant = new Route(shortestPath(COURIER_START, restaurant) || [city.nodes[restaurant]]);
  const toHome = new Route(shortestPath(restaurant, home));
  const start = Date.now();
  const readyAt = 3 + PREP_SECONDS;
  let second = 0;
  let travelled = 0;
  let leg = 1;
  let sent = new Set();

  const status = (s) => {
    if (!sent.has(s)) { sent.add(s); send({ type: "status", status: s, at: Date.now() }); }
  };

  const timer = setInterval(() => {
    second++;
    if (second === 1) return status("ACCEPTED");
    if (second === 3) status("PREPARING");
    if (second < 3) return;

    if (leg === 1) {
      if (second >= readyAt) status("READY_FOR_PICKUP");
      const p = toRestaurant.at(travelled);
      send({ type: "location", ...p, at: Date.now() });
      const rideLeft = (toRestaurant.length - travelled) / SPEED;
      send({ type: "eta", seconds: Math.round(Math.max(readyAt - second, rideLeft, 0) + toHome.length / SPEED), at: Date.now() });
      travelled = Math.min(toRestaurant.length, travelled + SPEED);
      if (travelled >= toRestaurant.length && second >= readyAt) { leg = 2; travelled = 0; status("PICKED_UP"); }
      return;
    }
    if (leg === 2) {
      travelled = Math.min(toHome.length, travelled + SPEED);
      send({ type: "location", ...toHome.at(travelled), at: Date.now() });
      const remaining = toHome.length - travelled;
      send({ type: "eta", seconds: Math.round(remaining / SPEED), at: Date.now() });
      if (remaining <= 180) status("ARRIVING");
      if (remaining <= 0) { leg = 3; setTimeout(() => status("DELIVERED"), 2000); }
    }
  }, TICK);

  return () => clearInterval(timer);
}

function start(port = PORT) {
  const wss = new WebSocketServer({ port });
  wss.on("connection", (socket, request) => {
    const url = new URL(request.url, "http://localhost");
    const match = url.pathname.match(/^\/orders\/([\w-]+)$/);
    const restaurant = url.searchParams.get("restaurant");
    const home = url.searchParams.get("home");
    if (!match || !city.nodes[restaurant] || !city.nodes[home]) {
      socket.close(4400, "Expected /orders/<id>?restaurant=<node>&home=<node>");
      return;
    }
    console.log(`order ${match[1]} connected: ${restaurant} → ${home}`);
    const stop = runOrder({ restaurant, home }, (msg) => {
      if (socket.readyState === socket.OPEN) socket.send(JSON.stringify(msg));
    });
    socket.on("close", stop);
  });
  console.log(`Swiftdrop tracking server on ws://localhost:${port}`);
  return wss;
}

if (require.main === module) start();

module.exports = { start, shortestPath, Route };
