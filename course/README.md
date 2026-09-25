# Udemy course modules

Code that follows the Udemy course *Temporal with Spring Boot in Practice* lecture by lecture. Each module uses the class, method and task-queue names shown on that lecture's slides, so you can put the video and the code side by side.

Lectures that the rest of this repository already covers are mapped in the [root README](../README.md#following-the-udemy-course). This folder holds what was missing.

| Module | Lecture | Port | What you do with it |
|---|---|---|---|
| [`l14-signals-queries-updates`](l14-signals-queries-updates/) | 14 — Signals, Queries, Updates | 8091 | Edit an order while it is pending, change its address, cancel it |
| [`l15-multi-service`](l15-multi-service/) | 15 — Multi-Service Coordination | 8092-8095 | Run four services, route activities by task queue, stop one mid-order |
| [`l17-testing`](l17-testing/) | 17 — Testing Workflows | 8096 | One test class per slide: mocks, signals, time skipping, Spring Boot test, replay |
| [`kata-26-lost-order`](kata-26-lost-order/) | 26 — Kata: The Lost Order | 8097 | Write the saga with compensation |
| [`kata-27-compensation-dance`](kata-27-compensation-dance/) | 27 — Kata: The Compensation Dance | 8098 | Write a five-step saga with separate retry policies for compensation |
| [`kata-28-approval-bottleneck`](kata-28-approval-bottleneck/) | 28 — Kata: The Approval Bottleneck | 8099 | Write the approval wait with a 24 h escalation and a 48 h timeout |

## Build and test

Tests run against Temporal's in-memory test server. You do not need Docker for them.

```bash
# from the repo root
mvn -f course/pom.xml verify
```

## Katas: starter and solution

Each kata has two copies of the same application:

- `starter/` — everything is written except the workflow logic, which is a `TODO`. This is where you work.
- `solution/` — the version shown in the lecture.

Both copies share the same tests. They are skipped in the starter so the build stays green. Run them yourself; they fail until the kata is solved:

```bash
mvn -f course/kata-26-lost-order/starter/pom.xml test -DskipTests=false
```

## Running the applications

Start Temporal once, from the repo root:

```bash
cd order-platform/docker && docker compose up -d && cd ../..
# Temporal UI: http://localhost:8233
```

Then follow the module's README. Every module talks to `localhost:7233` in the `default` namespace.

All modules name order workflows `order-<orderId>`, as the slides do, and share one Temporal namespace. Each README uses its own order ids (1001 for kata 26, 1401 for lecture 14, 1501 for lecture 15, and so on) so they do not collide. If you pick your own ids, do not reuse one that another module already used: the second start is rejected with HTTP 409, and a status query may be routed to the other module's task queue, where no worker is listening.

## Versions

Java 21, Spring Boot 3.4.1, Temporal Java SDK 1.34.0 — the same as the rest of the repository. Some slides were written against older SDK releases; where the code differs from a slide, the module README lists the difference and the reason under "Differences from the slides".
