package com.example.agent;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowException;
import io.temporal.client.WorkflowOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.testing.TestWorkflowExtension;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * These run against Temporal's in-memory test environment: no server, no Docker, and
 * no API key. Timers are skipped rather than waited on, so the three-day budget
 * timeout is exercised in milliseconds.
 */
class ResearchAgentWorkflowTest {

    private static final String TASK_QUEUE = "test-agent";

    @RegisterExtension
    static final TestWorkflowExtension extension = TestWorkflowExtension.newBuilder()
            .setWorkflowTypes(ResearchAgentWorkflowImpl.class)
            .setDoNotStart(true)
            .build();

    // --- Test double -------------------------------------------------------
    // Same costs as the simulated provider: plan 12, each analyze 34, synthesize 88.

    static class FakeLlm implements LlmActivities {
        final AtomicInteger analyzeCalls = new AtomicInteger();
        final AtomicInteger planCalls = new AtomicInteger();
        private final int steps;
        private final RuntimeException planFailure;

        FakeLlm(int steps) {
            this(steps, null);
        }

        FakeLlm(int steps, RuntimeException planFailure) {
            this.steps = steps;
            this.planFailure = planFailure;
        }

        @Override
        public Plan plan(String goal) {
            planCalls.incrementAndGet();
            if (planFailure != null) {
                throw planFailure;
            }
            List<Step> list = new java.util.ArrayList<>();
            for (int i = 1; i <= steps; i++) {
                list.add(new Step("s" + i, "step " + i, "query " + i));
            }
            return new Plan(List.copyOf(list), new TokenUsage(320, 180, 12));
        }

        @Override
        public List<SearchResult> search(String query) {
            return List.of(new SearchResult("t", "https://example.invalid/" + query, "snippet"));
        }

        @Override
        public Finding analyze(String stepDescription, List<SearchResult> sources) {
            analyzeCalls.incrementAndGet();
            return new Finding("step", "concluded: " + stepDescription,
                    List.of("https://example.invalid/c"), new TokenUsage(900, 260, 34));
        }

        @Override
        public Report synthesize(String goal, List<Finding> findings) {
            return new Report(goal, "report over " + findings.size() + " findings",
                    List.of("https://example.invalid/c"), new TokenUsage(1500, 700, 88));
        }
    }

    private ResearchAgentWorkflow newAgent(TestWorkflowEnvironment env, Worker worker,
                                           FakeLlm llm, String workflowId) {
        worker.registerActivitiesImplementations(llm);
        env.start();
        return env.getWorkflowClient().newWorkflowStub(
                ResearchAgentWorkflow.class,
                WorkflowOptions.newBuilder()
                        .setWorkflowId(workflowId)
                        .setTaskQueue(worker.getTaskQueue())
                        .build());
    }

    // --- Tests -------------------------------------------------------------

    @Test
    void completesEveryStepWhenBudgetIsSufficient(TestWorkflowEnvironment env, Worker worker) {
        FakeLlm llm = new FakeLlm(4);
        ResearchAgentWorkflow agent = newAgent(env, worker, llm, "run-happy");

        LlmActivities.Report report = agent.research("does durable execution help agents", 500);

        assertEquals(4, llm.analyzeCalls.get(), "one analyze per planned step");
        assertTrue(report.body().contains("4 findings"));

        ResearchAgentWorkflow.AgentStatus status = agent.getStatus();
        assertEquals("COMPLETED", status.phase());
        assertEquals(4, status.stepsCompleted());
        // plan 12 + 4 x 34 + synthesize 88
        assertEquals(236, status.spentMinorUnits());
        assertFalse(status.awaitingApproval());
    }

    @Test
    void pausesForApprovalWhenBudgetRunsOutThenResumes(TestWorkflowEnvironment env, Worker worker) {
        FakeLlm llm = new FakeLlm(4);
        ResearchAgentWorkflow agent = newAgent(env, worker, llm, "run-approve");

        // Budget 50: spend reaches 46 after step 1 and 80 after step 2, so the guard
        // trips before step 3 and the run blocks on a human.
        WorkflowClient.start(agent::research, "expensive goal", 50L);

        // Answer an hour into the (simulated) three-day wait.
        env.registerDelayedCallback(Duration.ofHours(1), () -> agent.approveBudget(true, 500));
        env.sleep(Duration.ofHours(2));

        LlmActivities.Report report = env.getWorkflowClient()
                .newUntypedWorkflowStub("run-approve")
                .getResult(LlmActivities.Report.class);

        assertEquals(4, llm.analyzeCalls.get(), "all four steps ran after approval");
        assertTrue(report.body().contains("4 findings"));
    }

    @Test
    void failsWhenAdditionalSpendIsDeclined(TestWorkflowEnvironment env, Worker worker) {
        FakeLlm llm = new FakeLlm(4);
        ResearchAgentWorkflow agent = newAgent(env, worker, llm, "run-declined");

        WorkflowClient.start(agent::research, "expensive goal", 50L);
        env.registerDelayedCallback(Duration.ofHours(1), () -> agent.approveBudget(false, 0));
        env.sleep(Duration.ofHours(2));

        WorkflowException thrown = assertThrows(WorkflowException.class,
                () -> env.getWorkflowClient()
                        .newUntypedWorkflowStub("run-declined")
                        .getResult(LlmActivities.Report.class));

        assertTrue(thrown.getCause().getMessage().contains("Budget"),
                "failure should name the budget as the cause");
        assertEquals(2, llm.analyzeCalls.get(), "stopped after the two affordable steps");
    }

    @Test
    void silenceOnTheApprovalRequestEventuallyFailsTheRun(TestWorkflowEnvironment env, Worker worker) {
        FakeLlm llm = new FakeLlm(4);
        ResearchAgentWorkflow agent = newAgent(env, worker, llm, "run-ignored");

        WorkflowClient.start(agent::research, "expensive goal", 50L);
        // Nobody answers. Skip past the three-day bound.
        env.sleep(Duration.ofDays(4));

        assertThrows(WorkflowException.class,
                () -> env.getWorkflowClient()
                        .newUntypedWorkflowStub("run-ignored")
                        .getResult(LlmActivities.Report.class),
                "an unanswered budget request must not leak a workflow forever");
    }

    @Test
    void malformedRequestIsNotRetried(TestWorkflowEnvironment env, Worker worker) {
        FakeLlm llm = new FakeLlm(4,
                new LlmActivities.InvalidRequestException("goal must not be blank"));
        ResearchAgentWorkflow agent = newAgent(env, worker, llm, "run-invalid");

        assertThrows(WorkflowException.class, () -> agent.research("", 500));

        assertEquals(1, llm.planCalls.get(),
                "InvalidRequestException is non-retryable, so plan() runs exactly once");
    }
}
