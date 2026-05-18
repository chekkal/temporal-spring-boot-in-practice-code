# Companion code — *Temporal with Spring Boot in Practice*

This directory holds runnable code that accompanies the book. The book itself is at `../BOOK_TEMPORAL_SPRING_BOOT_AMAZON.md`.

## What's here

```
code/
├── order-platform/        ← Reference application (Chapters 13–18, 25–27)
│   ├── order-api/          # workflow + activity contracts, DTOs
│   ├── order-service/      # Spring Boot app — impls, REST, TemporalConfig, tests
│   └── docker/             # Postgres + Temporal server + UI
│
└── katas/                 ← 6 exercises (Part 6 of the book)
    ├── kata-01-lost-order/             Difficulty 2/5 — basic saga + manual compensation
    ├── kata-02-compensation-dance/     Difficulty 3/5 — 5-step saga with reverse-order rollback
    ├── kata-03-approval-bottleneck/    Difficulty 3/5 — Workflow.await + escalation
    ├── kata-04-midnight-migration/     Difficulty 4/5 — cron + Continue-As-New
    ├── kata-05-cascading-failure/      Difficulty 4/5 — retry + fallback to secondary provider
    └── kata-06-schema-evolution/       Difficulty 5/5 — Workflow.getVersion + forward-compatible DTOs
```

## Stack

- Java 21 LTS
- Spring Boot 3.4
- Temporal Java SDK 1.34
- Maven 3.9+
- A container runtime exposing the `docker` CLI + Compose v2 (see below)

### Container runtime — pick one

The scripts and `docker-compose.yml` only require a working `docker` CLI and `docker compose` (v2, the plugin form). Any of the following works:

| Runtime | Platform | Install / start |
|---|---|---|
| **Docker Desktop** | macOS / Windows / Linux | Install the app and launch it. Default for most setups. |
| **Colima** *(recommended on macOS if you don't want Docker Desktop)* | macOS / Linux | `brew install colima docker docker-compose` → `colima start --cpu 4 --memory 6` |
| **OrbStack** | macOS | `brew install orbstack` → launch; ships the `docker` CLI |
| **Rancher Desktop** | macOS / Windows / Linux | Install the app; select the `dockerd (moby)` engine in Preferences |
| **Podman** | macOS / Windows / Linux | `brew install podman podman-compose` (or distro equivalent) → `podman machine init && podman machine start`. Then either install `podman-mac-helper` for a transparent `docker` CLI, or replace `docker compose` calls with `podman compose` |

On Linux, the native Docker Engine (`apt install docker.io docker-compose-plugin` or your distro's equivalent) works too — no daemon-mode wrapper needed.

**Memory floor:** Temporal + Postgres + the Temporal UI together want **≥ 4 GB** of RAM allocated to the runtime VM. Colima's default of 2 GB is too low — use `colima start --memory 6` (or adjust in Docker Desktop → Settings → Resources). If `test-all.sh` hangs at *"Temporal gRPC not ready on :7233 after 120s"*, this is almost always the cause.

Verify the runtime is working before running anything else:

```bash
docker info >/dev/null && echo "OK"
docker compose version
```

## Quick start

```bash
# 1. Start Temporal once (covers both the reference app and the katas)
cd order-platform/docker && docker compose up -d
# Temporal UI: http://localhost:8233

# 2. Run the reference app
cd ../  # back into order-platform/
mvn install
cd order-service && mvn spring-boot:run
# REST: http://localhost:8080

# 3. (Or) Run any kata
cd ../../katas && mvn install
cd kata-01-lost-order && mvn spring-boot:run
# Each kata exposes a different port: 8081 (kata-01) ... 8086 (kata-06)
```

## Verify everything works — `test-all.sh`

A single script verifies that every piece of the companion code compiles, boots, and talks to Temporal correctly. Run it after cloning, after pulling updates, or after making changes:

```bash
./test-all.sh
```

Expected runtime: **~3–4 minutes on first run** (Maven downloads + Docker pulls), **~90 seconds on subsequent runs**.

### What it checks

| # | Phase | What's verified | On pass, you know... |
|---|---|---|---|
| 1 | Prereqs | `java 21+`, `mvn`, `docker`, `curl`, `nc` are on `PATH` and the container runtime is reachable (Docker Desktop, Colima, OrbStack, Podman, etc. — see the *Container runtime* section above) | Your environment can build and run the stack |
| 2 | Docker infra | `docker compose up -d` from `order-platform/docker/`; waits for ports `7233` (gRPC) and `8233` (UI) | Temporal Postgres + server + UI are live |
| 3 | Ref-app build | `mvn install` in `order-platform/` — compiles `order-api` + `order-service`, runs unit tests | All code compiles; **`OrderFulfillmentWorkflowTest` passes against `TestWorkflowExtension`** (no live server needed for that step) |
| 4 | Ref-app boot | `java -jar order-service.jar`; polls `/actuator/health` until `"status":"UP"` | Spring context, `TemporalConfig`, and worker registration all clean |
| 5 | Happy path | POSTs an order with `paymentMethod=card-good`, polls `/api/orders/{id}/status` until `COMPLETED` | The full saga (validate → authorize → reserve → ship → notify) runs end-to-end against a real Temporal server |
| 6 | Decline path | POSTs an order with `paymentMethod=card-decline-test`, polls until `FAILED` | The error path returns the workflow to `FAILED` as designed (compensation logic is exercised in code paths that have side effects further down the saga; this case fails on `authorize` before any compensation is registered) |
| 7 | Katas build | `mvn install` in `katas/` — builds all 6 modules | Every kata's starter scaffolding compiles |
| 8 | Katas bootup | For each kata in turn: `java -jar`, wait for port to bind, kill | Each kata's Spring context starts, its `TemporalConfig` wires, and its worker registers with Temporal. Workflow bodies are deliberately `TODO`, so business endpoints are NOT exercised here |

### Usage

```bash
./test-all.sh                  # full run
./test-all.sh --skip-infra     # assume Temporal is already up on :7233
./test-all.sh --skip-katas     # verify the reference app only
./test-all.sh --teardown       # stop the Temporal Docker stack at the end
./test-all.sh --keep-infra     # leave Temporal running (the default)
./test-all.sh --help
```

### Logs and troubleshooting

Per-app stdout/stderr lands in `.test-logs/`:

```
.test-logs/
├── order-service.log
├── kata-01-lost-order.log
├── kata-02-compensation-dance.log
├── kata-03-approval-bottleneck.log
├── kata-04-midnight-migration.log
├── kata-05-cascading-failure.log
└── kata-06-schema-evolution.log
```

When something fails, the script tails ~30–50 lines of the offending log to your terminal and exits non-zero. Open the full log file for the complete trace.

**Common failure modes:**

| Symptom | Likely cause | Fix |
|---|---|---|
| `Temporal gRPC not ready on :7233 after 120s` | Port already in use, container runtime not started, or runtime out of memory | `lsof -i:7233`; `docker compose down`; ensure your runtime is started (`colima start`, `podman machine start`, or launch Docker/Rancher/OrbStack); increase the runtime's memory to ≥ 4 GB (`colima start --memory 6`, or Docker Desktop → Settings → Resources) |
| `Java 21+ required (detected: 17)` | Wrong JDK on `PATH` | `sdk use java 21.0.4-tem` (or whichever 21+ distro you have) |
| `order-service crashed during startup` | Usually a port conflict on 8080 | `lsof -i:8080`; kill the offender, re-run |
| `katas/kata-NN-... didn't bind :808N within 90s` | Worker can't reach Temporal (DNS/network) or the kata's `application.yml` has a bad `spring.temporal.connection.target` | Check `.test-logs/kata-NN-….log` for the connection error |
| `decline path unexpected status: AUTHORIZING_PAYMENT` | Workflow hasn't progressed yet — Temporal under load on first run | Re-run; the script polls but only twice. If persistent, increase the `sleep 4` in the script |

### Reliability notes

- The script uses **executable jars** (`java -jar target/*-SNAPSHOT.jar`), not `mvn spring-boot:run`, so each app is a single JVM with one PID — no orphan processes from Maven's fork chain.
- A `trap EXIT` handler kills every background JVM the script started, even on Ctrl-C or unexpected exit. Your machine won't accumulate stale processes between runs.
- The infra step is **idempotent**: re-running with the stack already up is a no-op (docker compose just confirms the services are healthy).
- The script does **not** assert workflow correctness inside the katas — their bodies are intentionally `TODO`. The kata phase confirms the surrounding scaffolding is wired correctly; once you implement a kata, you can run its specific endpoint manually (see the kata's own `README.md`).

## Reading order

| If you're working through... | Open this code |
|---|---|
| Chapter 13 (Order Fulfillment Saga) | `order-platform/order-service/.../workflow/OrderFulfillmentWorkflowImpl.java` |
| Chapter 14 (Payment + Compensation) | `order-platform/order-service/.../activity/PaymentActivityImpl.java` |
| Chapter 15 (Human-in-the-Loop) | `order-platform/order-service/.../workflow/OrderApprovalWorkflowImpl.java` |
| Chapter 17 (Signals / Queries / Updates) | The `cancel` signal + `getStatus` query on `OrderFulfillmentWorkflow` |
| Chapter 21 (Testing) | `order-platform/order-service/src/test/.../OrderFulfillmentWorkflowTest.java` |
| Chapter 25 (Reference App Walkthrough) | The whole of `order-platform/` |
| Chapter 26 (Retry & Timeouts) | `ActivityOptions` in `OrderFulfillmentWorkflowImpl` + `kata-05` |
| Chapter 27 (Idempotency & Business Keys) | `setWorkflowIdReusePolicy` in `OrderController` |
| Chapter 28 (Migrating Scheduled Jobs) | `kata-04-midnight-migration/` |
| Chapter 32 (Versioning) | `kata-06-schema-evolution/` |
| Part 6 (Katas) | `katas/kata-NN-*/` — one per challenge |

## How the katas work

Each kata is a complete, runnable Spring Boot app with:

- Workflow + activity interfaces (provided)
- Activity implementations with deliberate failure triggers (provided)
- `TemporalConfig` and a REST controller (provided)
- **A workflow implementation with the body deliberately stubbed out as a `TODO`** — *that's your part*

The challenge text from the book is mirrored in each kata's `README.md`, with a solution outline at the bottom for when you're stuck. Open the `*WorkflowImpl.java` file and follow the TODO block — they're 5-15 lines of business logic each.

## A note on the package namespace

All companion code uses `com.example.*` — the same neutral namespace the book uses. No third-party branding or organization prefixes.
