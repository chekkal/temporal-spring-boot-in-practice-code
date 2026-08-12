# Agentic Coordination — a durable AI agent

A multi-step research agent that survives losing its process, pauses for a human when it runs out of budget, and does not re-pay for model calls it already made.

Runs with **no API key and no spend** — the model provider is simulated. Everything demonstrated here is a property of the orchestration, not of any particular provider.

```bash
cd use-cases/agentic-coordination
mvn test          # 5 tests, no Docker, no server, no key
mvn spring-boot:run   # needs Temporal running — see below
```

## The problem

An agent plans, searches, calls a model six times, waits for someone to approve a spend, then writes a report. It is a long-running, expensive, partially non-deterministic process that talks to a flaky external API.

Run that in a plain `@Service` and the failure modes are all bad:

- **The pod restarts at step seven.** Without durable state you begin again at step one — and pay for the first six model calls a second time.
- **The provider returns 429.** You add a retry, then backoff, then a circuit breaker, until the retry logic outweighs the agent logic.
- **The agent wants to spend more.** A human must approve, which means a thread waits — or you shred the agent into a state machine, a table, and a polling job.
- **Someone asks what it did.** You read logs.

## What this shows

| Concern | How | Where |
|---|---|---|
| Crash recovery without re-billing | Model calls are activities; results are recorded in history and replayed | `LlmActivities` |
| Rate limits | Declarative retry with exponential backoff | `ActivityOptions` in `ResearchAgentWorkflowImpl` |
| Malformed requests | `setDoNotRetry` — fail fast instead of burning the retry budget | same |
| Human approval on spend | `Workflow.await` with a bounded timeout | `ensureBudgetFor` |
| Live progress | `@QueryMethod`, reading workflow state — no database | `getStatus` |
| Duplicate submissions | Workflow id is the run id | `AgentController` |

## Run it

Temporal must be running. The reference application's compose file covers it:

```bash
cd ../../order-platform/docker && docker compose up -d
cd ../../use-cases/agentic-coordination && mvn spring-boot:run
```

Start a run:

```bash
curl -X POST localhost:8090/api/agents -H 'Content-Type: application/json' -d '{
  "runId": "research-001",
  "goal": "does durable execution help AI agents",
  "budgetMinorUnits": 500
}'
```

Watch it:

```bash
curl localhost:8090/api/agents/research-001
```

```json
{"phase":"RESEARCHING","stepsCompleted":2,"stepsTotal":4,
 "spentMinorUnits":80,"budgetMinorUnits":500,"awaitingApproval":false}
```

### The demonstration that matters

Start a run, then **kill the application mid-flight** (Ctrl-C while `phase` is `RESEARCHING`) and start it again:

```bash
mvn spring-boot:run
curl localhost:8090/api/agents/research-001
```

The run continues from the step it reached. The model calls already made are **not** repeated — the worker replays them from history. Watch the simulated provider's log: it prints nothing for the completed steps, because it is never called again.

That is the whole argument for durable execution in one observation. There is no checkpoint table and no resume logic in the code — the workflow is ordinary sequential Java and Temporal reconstructs its local variables.

### The budget gate

Start a run with a budget too small to finish:

```bash
curl -X POST localhost:8090/api/agents -H 'Content-Type: application/json' -d '{
  "runId": "research-002", "goal": "expensive question", "budgetMinorUnits": 50
}'
```

It completes two steps, then stops at `AWAITING_BUDGET_APPROVAL`. It will wait there for three days holding no thread and no connection. Release it:

```bash
curl -X POST localhost:8090/api/agents/research-002/budget \
  -H 'Content-Type: application/json' -d '{"approved": true, "newBudgetMinorUnits": 500}'
```

If nobody answers within three days the run fails rather than waiting forever. An unbounded `await` is how you leak workflows.

### Watching retries

Set `agent.simulated.rate-limit-first-n: 2` in `application.yml` and start a run. The first two `analyze` calls throw a rate-limit error; Temporal backs off and retries them. **No retry code exists in the workflow** — it is four lines of `RetryOptions`.

## Using a real model

Replace the simulated bean. The workflow does not change.

Add Spring AI to `pom.xml`:

```xml
<dependency>
    <groupId>org.springframework.ai</groupId>
    <artifactId>spring-ai-starter-model-openai</artifactId>
</dependency>
```

Then implement `LlmActivities` against it:

```java
@Component
@Primary
public class OpenAiLlmActivities implements LlmActivities {

    private final ChatClient chat;

    public OpenAiLlmActivities(ChatClient.Builder builder) {
        this.chat = builder.build();
    }

    @Override
    public Finding analyze(String stepDescription, List<SearchResult> sources) {
        ChatResponse response = chat.prompt()
                .user(u -> u.text("Analyse these sources for: {step}")
                        .param("step", stepDescription))
                .call()
                .chatResponse();

        Usage usage = response.getMetadata().getUsage();
        return new Finding(
                "step",
                response.getResult().getOutput().getText(),
                sources.stream().map(SearchResult::url).toList(),
                new TokenUsage(
                        usage.getPromptTokens(),
                        usage.getCompletionTokens(),
                        costInMinorUnits(usage)));   // your rate card
    }

    // plan(), search(), synthesize() follow the same shape
}
```

Two things to keep right when you do:

**Keep non-determinism inside activities.** Model output varies between identical calls. If the workflow branched on a value it computed itself rather than one an activity returned, replay would take a different path and Temporal would fail the execution for non-determinism.

**Keep money in integer minor units.** `TokenUsage.costMinorUnits` is a `long` of cents. Token counts are large, rates are small, and runs are many — floating point drift here becomes a conversation with finance.

## Tests

```bash
mvn test
```

Five tests against Temporal's in-memory environment — no server, no Docker, no key. Timers are skipped, so the three-day approval timeout is exercised in milliseconds:

| Test | Asserts |
|---|---|
| `completesEveryStepWhenBudgetIsSufficient` | one `analyze` per planned step; spend totals 236 |
| `pausesForApprovalWhenBudgetRunsOutThenResumes` | blocks at step 3, resumes on signal, finishes all four |
| `failsWhenAdditionalSpendIsDeclined` | stops after the two affordable steps |
| `silenceOnTheApprovalRequestEventuallyFailsTheRun` | an unanswered request does not leak a workflow |
| `malformedRequestIsNotRetried` | `plan()` runs exactly once for a non-retryable error |

## Files

| File | What it is for |
|---|---|
| `ResearchAgentWorkflowImpl.java` | The agent. Read this first — it is ordinary sequential code. |
| `LlmActivities.java` | The provider boundary, and which errors are retryable |
| `SimulatedLlmActivities.java` | Stand-in provider so this runs with no key |
| `AgentController.java` | Start, query status, answer a budget request |
| `TemporalConfig.java` | Worker and client wiring |
