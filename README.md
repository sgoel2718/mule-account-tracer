# Mule Account Tracer

**Follow scam money hop by hop, and freeze it before it's cashed out.**

[![CI](https://github.com/sgoel2718/mule-account-tracer/actions/workflows/ci.yml/badge.svg)](https://github.com/sgoel2718/mule-account-tracer/actions/workflows/ci.yml)
![Java 17](https://img.shields.io/badge/Java-17-orange) ![deps](https://img.shields.io/badge/runtime%20deps-0-brightgreen) ![tests](https://img.shields.io/badge/tests-31%20JUnit-blue)

### 🔴 [Live demo → sgoel2718.github.io/mule-account-tracer](https://sgoel2718.github.io/mule-account-tracer/)

---

## The problem

In a **"digital arrest"** scam, a fake CBI or police officer keeps the victim on a video call for hours and pressures them into transferring their savings to a "verification account". The same playbook drives UPI and investment scams. Within minutes the money is **layered**: split across dozens of rented "mule" accounts, then withdrawn at ATMs or sold for crypto.

Banks *can* freeze money that is still sitting in mule accounts. The hard part is finding it fast enough: an investigator has to follow the money through several layers of accounts, at several banks, in time order, while the gang keeps cashing out.

**Mule Account Tracer** takes a fraud complaint (the victim's transaction UTRs) and in **under a millisecond** returns:

1. the full **money trail**: every account the victim's money reached, layer by layer;
2. how much is **still freezable** in each account, and how much is already gone;
3. a **prioritised freeze list** with an explainable risk score, so banks freeze mules and only place a lien on innocent shopkeepers who received a few rupees;
4. **mule rings**: accounts run from the same phones or networks, including *dormant* ones the gang hasn't used yet;
5. **chokepoints**: the minimum set of accounts that every cash-out route passed through.

## Demo scenario (synthetic)

A retired professor in Chennai makes 4 transfers totalling **₹18.5 lakh** over 52 minutes. 21 mule accounts layer the money and cash it out in Jamtara, Nuh and Bharatpur and through USDT P2P desks. Ordinary banking traffic around it: 5,000 accounts and 60,000 transactions.

| Complaint received (after last transfer) | Still freezable | Recovery |
|---|---|---|
| immediately | ₹14.17 L | **77%** |
| 30 min | ₹9.80 L | 53% |
| 1 hour | ₹7.18 L | 39% |
| 4 hours | ₹4.36 L | 24% |

Precision of hard freezes against the simulator's ground truth: **100%** (0 innocent accounts frozen, innocent recipients get a lien only), and **6/6** dormant mule accounts watch-listed.
*These numbers come from synthetic data. Real-world performance depends on data quality and needs validation.*

## Algorithms and data structures

| Component | Technique | Complexity |
|---|---|---|
| `TemporalGraph` | adjacency lists sorted by time; binary search (lower bound) for "first debit after money arrived" | insert O(1) amortised · lookup O(log d) |
| `TaintTracer` | **time-respecting taint propagation** as an event-driven k-way merge: a min-heap holds one cursor per tainted account; a debit carries `min(amount, taintedBalance)` | O((S + E′) log K) |
| `MuleRingDetector` | **incremental union-find** (path compression + union by rank) over device fingerprints and IP hashes; member lists merged on union | O(α(n)) per account |
| `PageRank` | weighted power iteration on the money-flow graph to spot aggregator ("boss") mules | O(iter · (V+E)) |
| `MuleScorer` | 7 explainable signals: concentration, pass-through, speed, ring, account age, hub rank, fan-out | O(degree) per account |
| `ChokepointFinder` + `Dinic` | **minimum vertex cut** via node splitting and Dinic max-flow (Menger's theorem) | O(E √V) on unit capacities |
| `Investigator` | freeze orders drained from a **max-heap** on `held × risk` | O(k log k) |

**Correctness invariant** (tested at every delay): `stolen = cashedOut + freezable + beyondHopLimit`. Every rupee is accounted for.

Why time-respecting and not plain BFS? If a mule paid a shop at 9:00 and received the victim's money at 10:00, BFS would blame the shop. The tracer only follows debits that happened *after* the money arrived, and it caps each one at the tainted balance (the conservative "tainted-first" rule investigators use).

## Benchmark

`java -jar target/mule-account-tracer-1.0.0.jar bench` on a 2-vCPU cloud VM, with a graph of **200K accounts and 2M transactions**:

| Operation | p50 | p99 |
|---|---|---|
| Trace only | **35 µs** | 129 µs |
| Full investigation (trace + rings + scores + min-cut + freeze list) | **0.26 ms** | 2.8 ms |
| Ingestion | **1.19M txns/sec** | |

(A simple `System.nanoTime` harness with warm-up. Use JMH for rigorous numbers.)

## Architecture

```
complaint (UTRs, report time)
        │
        ▼
┌──────────────┐   ┌──────────────────┐   ┌─────────────┐   ┌─────────────────┐   ┌──────────────┐
│ TemporalGraph│──▶│ TaintTracer      │──▶│ MuleRing    │──▶│ MuleScorer      │──▶│ Chokepoint   │
│ time-sorted  │   │ min-heap, k-way  │   │ Detector    │   │ + PageRank      │   │ Finder       │
│ adjacency    │   │ merge, taint     │   │ union-find  │   │ explainable     │   │ Dinic min-cut│
└──────────────┘   └──────────────────┘   └─────────────┘   └─────────────────┘   └──────┬───────┘
                                                                                         ▼
                                       REST API (JDK HttpServer)  ◀── Investigator: max-heap freeze list
```

```
src/main/java/com/muletracer/
├── model/        Account, Transaction, Channel
├── graph/        TemporalGraph
├── trace/        TaintTracer, TraceConfig, TraceResult, AccountTaint, TaintedEdge
├── cluster/      UnionFind, MuleRingDetector, MuleRing
├── score/        PageRank, MuleScorer, MuleScore
├── cut/          Dinic, ChokepointFinder
├── investigate/  Investigator, Investigation, FreezeOrder
├── sim/          ScamScenarioGenerator (seeded, deterministic)
├── io/           Json, InvestigationJson, DemoExporter
├── api/          TracerServer
├── bench/        Benchmark
└── App.java      CLI
docs/             GitHub Pages demo (draws docs/data/scenario.json exported by the Java engine)
```

## Run it

Requires JDK 17+ and Maven.

```bash
mvn -q package                                            # builds + runs 31 tests
java -jar target/mule-account-tracer-1.0.0.jar demo 30    # freeze list 30 min after the complaint
java -jar target/mule-account-tracer-1.0.0.jar serve 8080 # REST API
java -jar target/mule-account-tracer-1.0.0.jar bench      # 200K accounts / 2M txns
java -jar target/mule-account-tracer-1.0.0.jar export     # regenerate docs/data/scenario.json
```

REST API:

```bash
curl "http://localhost:8080/api/investigate?utr=UTR000060015,UTR000060041,UTR000060062,UTR000060082&reportTime=1795153920"
curl  http://localhost:8080/api/health
```

## Design decisions and trade-offs

- **Tainted-first rule:** when a mule mixes its own money with the victim's, debits are assumed to carry victim money first. This over-traces rather than under-traces, and the risk score plus lien-vs-freeze split limits the harm to innocent recipients.
- **Report time as a horizon:** the tracer never looks at transactions after the complaint time, so the demo shows what an investigator could actually know at that moment.
- **Unknown behaviour isn't innocence:** an account that received victim money but hasn't moved it yet is scored only on the signals that exist (age, ring, concentration), so it isn't let off because pass-through can't be measured yet.
- **In-memory, single node:** a real deployment would partition the graph by account hash across nodes (Kafka for the transaction stream, cross-partition hops as messages) and persist the union-find. The core algorithms stay the same.
- **No frameworks:** the JDK `HttpServer` and a tiny JSON writer keep the jar dependency-free and the algorithms in plain sight.

## Possible extensions

- Kafka ingestion with partitioned graph shards and cross-shard taint messages
- Inter-bank API simulation (NPCI-style freeze requests with acknowledgements)
- An ML model trained on the scorer's features for mule detection
- Haircut (proportional) taint as an alternative policy

## Disclaimer

All data is synthetic, generated by `ScamScenarioGenerator` (seed 1930, after India's cyber-fraud helpline). If you have been defrauded in India, call **1930** or report at **cybercrime.gov.in** immediately. As the demo shows, the first hour matters most.

---

Built by [Shubham](https://github.com/sgoel2718) · MIT License
