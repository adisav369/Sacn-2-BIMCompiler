# The WMS, Logistics & Robot Lenses — one verb, all the way to the actuator

*Extracted from my iDempiere Warehousing / Manufacturing / AutoBOMOrder / Android-Scanner plugins and the DSD
van work, ported to the browser and the signed op-log. The warehouse-and-movement half of [The POS Lens](POSLens.md);
both are the same [Distributed ERP](DistributedERP.md) doctrine, and they meet at one record: **location**.*

---

## 0. Why this document

The POS lens proved the doctrine at the till: *one source act, the rest a fold, no central control.* This document
follows the goods *behind* the till — into the warehouse, onto the truck, and out to the robot — and shows it is all
**one primitive**. Picking, putaway, replenishment, delivery, even an automated forklift are not separate systems.
They are the same signed, scanned **movement**, folded over the same log, shown through different lenses.

The lineage is real and tested: iDempiere already models stock by `M_Locator`, moves it with `M_Movement`,
reconciles it with `M_Inventory` (physical count), and derives reorder quantities from the Replenishment Report;
my [AutoBOMOrder](https://wiki.idempiere.org/en/Plugin:_AutoBOMOrder) plugin backflushed recipes and generated the
replenishment, and the [Unicenta POS](https://wiki.idempiere.org/en/Plugin:_Unicenta_POS) ring fed it. What changes
here is only the plumbing: server → browser + SQLite (faster, local), and the bus → email (guaranteed). The logic
is the iDempiere logic I worked in and noticed.

## 1. The one verb — `MOVE(item, from, to, scan)`

Strip every warehouse operation to its atom and they are the same op: a **signed, scanned movement between two
locations or parties.**

| Operation | from → to |
|---|---|
| Sale (POS) | shelf → customer |
| Pick | bin → tote / order |
| Putaway | dock → bin |
| Receipt | supplier → dock |
| Replenishment | a *fold* that **proposes** the next movement |
| Cycle count | a *fold vs scan* that **reconciles** a location |
| Delivery | van → destination |

`QtyOnHand` at any location is never a stored cell — it is the **fold** of the movements over that locator (the
root truth of Distributed ERP: *the fact is a fold over a signed sequence*). And every "list" in the building —
picking list, putaway list, replenishment, route sheet — is **a fold telling a worker which move to make next.**
The list is derived, deterministic, regenerable; nobody keys it.

## 2. Picking & putaway — mirror images, both scan-serialized

A picker scans **item + bin** ("this left here, for that order"); a putaway scans **item + bin** ("this now lives
here"). Same act, opposite direction. Physics serializes exactly as at the till: *you cannot scan a box you don't
hold, or a bin you aren't standing at.* The worklist is the fold — orders folded against stock-by-location for
picking, receipts folded against slotting rules for putaway — and the **mobile worker is a lens**: show the fold,
capture the scans, hold no truth. The handheld is the warehouse's dumb terminal, exactly as the POS is the shop's.

Directed work falls out for free: the lens lights the next bin, the worker goes, scans, the loop closes. **Literacy-
free** — follow the highlight, scan, done; no reading. The list *suggests* the path; the *scan* is authoritative. If
the item isn't in the expected bin, the scan records reality and the fold corrects it (a relocation/variance surfaces).

## 3. Cycle count & shrinkage — exact, at bin granularity

Because no one keys stock, there is **no lever to falsify the book** — so loss is unhideable, exactly as at the
till. A bin whose fold says 10 but scans to 8 is a 2-unit variance, computed to the unit and pinned to a locator.
**Cycle counting becomes the occasional audit, not a chore**: the book is always known (perpetual, derived, zero-
effort), so you count when you like, and the gap is waiting — bracketed to a bin and a window. Frequency buys
*attribution*, never *visibility*. Honest boundary: the fold tells you the shortfall is exactly 2; *whether* it was
theft, breakage, or a misplacement still needs a human — but the quantum is exact and located.

## 4. The spatial floor — where BIM and ERP become one record

A bin is not two things. In BIM it is a **spatial object** (Aisle 3, Bin 12, coordinates, volume); in iDempiere it
is **`M_Locator`** — the stock dimension the fold sums over. *They are the same record.* So the stock-by-location
fold renders straight onto the spatial model — color a bin by fill, light the pick path, flash a variance hotspot.
Location is the primary key that marries the two halves of this project.

Two lenses, same fold:
- **Schematic Aisle/Bin/Lot** — 2D, phone, ships first. The *worker's* lens: go to the lit bin, scan, done.
- **BIM walk-through** — 3D, big screen, later. The *orchestrator's* lens: see the floor, the routes, the leaks.
  **It reuses the existing BIM viewer** — not a new build; point the viewer at `M_Locator`s and overlay the fold.

This is the project's deep unification: the **recursive BOM** is the spine of *both* domains — building → floor →
room → leaf (space) is the same verb as burger → bun → flour and warehouse → aisle → bin → lot → stock
(operations). One recursive fold, two domains, joined at location. `bim-compiler` and the ERP doctrine were never
two efforts.

## 5. Logistics & delivery — GPS is the third serializer

Reality gives us natural serializers, and delivery adds the third:
- the **shelf/scan** locks the *goods* (one item, one hand),
- the **payer** locks the *payment* (paid once),
- **GPS** locks *presence* (you are in one place, and it witnesses where).

The route/schedule is a fold (orders → destinations → sequence). Arrival is confirmed by **scan + GPS-vicinity** —
*you cannot confirm a delivery you are not physically at.* Proof-of-delivery becomes a multi-factor party-act: the
scan (item handed over) × the GPS (presence) × the recipient's counter-sign. It cannot be faked — you would have to
be physically there, holding the item, with the receiver ratifying. This is the DSD/van pattern ("you can't scan a
box you don't hold") extended: *you can't confirm an arrival you're not at.*

**Route management — a fold, navigated by a killer app.** "Arrange trips along a route" is the vehicle-routing
problem (VRP): the day's deliveries (open orders + addresses) fold into stops, sequenced into a route. The driver
gets the sequenced worklist with **tap-to-navigate** (`📍 → Google Maps intent`); the orchestrator sees all routes
on a map. Doctrine-ordered, cheapest first:
1. **Navigate — free deep-link, no integration:** `maps/dir/?api=1&waypoints=…` hands the route to the driver's own
   Maps app (no key, no cost, every phone). Caveat: the free URL caps at ~10 waypoints — chunk or solve for more.
2. **Optimize — local, no server:** run the VRP sequencing **on-device** (OR-Tools / nearest-neighbour heuristic);
   only *navigation* rides Maps. The whole thing stays serverless.
3. **Traffic-aware / large fleet — the one optional paid tier:** Google Routes / Route-Optimization API (key, quota,
   bill, ToS) — a *metered cloud dependency*, taken only when it genuinely buys traffic-aware ETAs or fleet-scale
   optimization. Keep the stops/orders **sovereign** so the optimizer is swappable (Google ↔ Mapbox ↔ OSRM/Valhalla ↔
   local OR-Tools): depend on its *quality*, never its *permission*.
Each stop's scan + GPS-vicinity folds progress back (stops done/remaining), so the route is self-tracking from the
proof-of-delivery acts — no separate status-keying.

## 6. Robots & automation — the actuator is just another lens

A robot is not a new system; it is **another actuator on the same worklist-fold.** A human gets the worklist on a
phone and confirms with a scan; a robot forklift gets the *same* worklist as machine instructions and confirms with
its sensors. The fold doesn't know or care which executed the move — it folds the confirmation either way. *Dumb
actuator, smart fold* — the dumb-POS pattern, with wheels.

The **stub** is the lens made thin for a machine: a small driver translating *worklist → robot API* one way and
*sensor → signed op* the other. This is the provider pattern extended from data to devices — alongside
`providerFromSqlite` / `providerFromExcel`, a `stubForDevice(spec)`. Because AI code generation has collapsed the
cost of that glue to near zero, the marginal cost of supporting a new commodity actuator (a Raspberry-Pi forklift,
a conveyor, a scanner) ≈ the cost of generating its stub ≈ nothing. **That democratizes automation** — a small
operation with cheap hardware and a generated stub, instead of a six-figure systems-integration project. The
integration cost that kept robotics for the big players evaporates.

**Two hard lines — non-negotiable:**

1. **Generate the integration glue — never the safety-critical motion control.** A forklift is a heavy machine that
   can kill someone. "Cheap AI code" is fine for *worklist ↔ signed-op* translation; it is **not** fine for collision
   avoidance, emergency-stop, or motion. Those come from the robot's own **certified safety firmware**. The stub
   commands *intent* ("go pick bin 12") and reads *confirmation*; the certified layer owns *how it moves without
   hurting anyone.* Keep this line bright — it is where the doctrine touches liability.
2. **Provenance requires a real sensor act, not a software flag.** The robot's "done" must be anchored to actual
   physical sensing (scan, weight, position), exactly as the human's is anchored to a scan. A stub that merely
   *asserts* "moved" mints a **partyless orderline** — the robot lying. Bind confirm-to-sensor as you bind confirm-
   to-scan, and the sacred transaction holds even when the party is a machine.

**Design law (now, so the robot is free later):** keep the worklist a **clean, actuator-agnostic fold output** that a
human phone *or* a robot stub can consume identically. Do that from day one and the robot is a drop-in lens (fold-
not-fork). Don't build the forklift before the human picks; just don't foreclose it.

## 7. Transport — use what is guaranteed

The comm layer is not a thing to build. DistributedERP §6 describes a "dumb async post office that only sequences
and relays" — that is **email**, already installed worldwide, free, tested (I ran it through iDempiere's email API).
Branches/tills/vans/robots email their signed ops; the admin's inbox collects them; a lens folds the inbox onto the
**kanban dashboard**. The inbox *is* the incoming op-log; the kanban is a fold of it; an email *arriving* moves a
card — `SEND == arrive == VERB`.

Email is poor — latency, spam, deliverability — but it is the **least poor of the *guaranteed* options**, and its
failure modes are precisely what the fold already tolerates, by construction:
- **duplicate** → harmless (the fold is idempotent, UUID-keyed),
- **out-of-order** → harmless (the merge is commutative),
- **late** → harmless (business-cadence, Truth 2).

You didn't make the architecture tolerate email; you made it tolerate the real world, and email *is* the real world.
Security follows *secure the fact, not the container*: a spoofed email carries an invalid signature → the fold
rejects it. The channel can be wide open because the fact is sealed.

**Piggy-ride the killer app — and double up.** The guarantee comes from *hyperscale*, not self-hosting: a private
mail server goes down and gets compromised (your 3am problem); Gmail going down is *global news*, fixed by Google in
minutes (not your job). You inherit planet-scale reliability for free by riding what billions already depend on. For
extra safety, **send each signed op over two independent channels** (e.g. Gmail + Telegram/WhatsApp/SMS) — and this
costs *nothing* in correctness, because the fold is **idempotent**: the same op arriving twice dedupes by UUID. So
redundancy is free; independent failure domains make simultaneous loss near-impossible; and the signature still
guarantees integrity, so a *compromised* channel can't forge an op. Redundancy buys **availability**; the signature
buys **truth**. (Email is the frictionless automatable default; the second channel is whatever independent guaranteed
app fits — Telegram has the cleanest bot API, WhatsApp the widest reach but tighter automation limits.)

**The root principle: *use what is guaranteed.*** It is the same rule as *no server setup* — a server is the one
thing that **isn't** guaranteed (you provide it, maintain it, pay for it, it can fail). Commit to building only from
guaranteed parts and the server deletes itself, leaving: **browser** (everyone has one), **SQLite** (the most-
deployed database on earth — here *guaranteed and faster* than the old server round-trip), **email** (the universal
post office), **the scan / physical act** (reality serializes), **the user's own devices** (they own them). The
infrastructure list is empty. The one carve-out is the real-time op-class (§5, customer-global entitlements), which
by nature can't ride a high-latency guaranteed channel — the named exception, not a competing default.

## 8. The lens family — features ready to adapt

Everything above is one of three thin things over the *same* model — never a new engine. That is why they are
*ready to adapt* rather than *ready to build*: adapting is glue, not architecture.

| Feature | Kind | Status |
|---|---|---|
| Picking / putaway worklist | lens (mobile worker) | shelf — adapt on demand |
| Schematic Aisle/Bin/Lot | lens (2D) | shelf — first warehouse view |
| BIM walk-through floor | lens (3D) | shelf — reuses existing viewer |
| Delivery route + GPS PoD | lens + provider (GPS) | shelf — adapt on demand |
| Robot forklift / conveyor | stub (`stubForDevice`) | shelf — generate per device |
| Cycle count / shrinkage | fold (read-only) | shelf — derived, no new input |
| Email bus + kanban pickup | provider (transport) | shelf — the guaranteed default |
| Excel / SQLite catalog | provider (input) | built (`providerFrom*`) |

**This is a parts shelf, not a build queue.** The value is that you pull the one piece a real customer needs, when
they need it. One proven door first — the one-item POS sale, the only validated demand — and the rest stay genuinely
ready until a named person reaches for one. Breadth as *optionality*, not as unfinished surface.

## 9. Honest scope

- **Proven and real:** the iDempiere warehousing/manufacturing logic, AutoBOMOrder backflush + replenishment, the
  DSD scan-commit, and email comm through iDempiere's API — deployed and tested historically. The arithmetic here is
  that logic, not a new claim.
- **Specified, not yet built:** the browser *lenses* themselves (WMS handheld, spatial floor, delivery, robot stub),
  each over the frozen engine seam (`window.ERP`), gated on the front-end foundation's write-path. They sit on the
  staircase *after* the POS front door — proof the architecture extends to the whole goods lifecycle and to actuators,
  not the thing to build first.

The promise, plainly: **one verb — a scanned movement folded over a signed log — carries goods from supplier to
shelf to customer to truck to robot, with no server, no central control, and no number a human had to key; and every
new surface is a lens, a provider, or a generated stub over the same model.**

---

*Sources / lineage:* [Plugin: AutoBOMOrder](https://wiki.idempiere.org/en/Plugin:_AutoBOMOrder) ·
[Plugin: Unicenta POS](https://wiki.idempiere.org/en/Plugin:_Unicenta_POS) ·
[Plugin: Wanda POS](https://wiki.idempiere.org/en/Plugin:_Wanda_POS) · iDempiere standard `M_Locator` / `M_Movement`
/ `M_Inventory` / Replenishment Report · companions: [POSLens.md](POSLens.md) · [SocialPlatformLens.md](SocialPlatformLens.md) · [DistributedERP.md](DistributedERP.md).
