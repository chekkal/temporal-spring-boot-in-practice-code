package com.example.agent;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.failure.ApplicationFailure;
import io.temporal.workflow.Workflow;
import org.slf4j.Logger;

/**
 * A research agent that survives losing its process.
 *
 * <p>Read this as ordinary sequential code, because that is what it is. There is no
 * state machine, no checkpoint table, and no resume logic — Temporal reconstructs the
 * local variables by replaying history. Kill the worker at step four and restart it:
 * execution continues at step five, and the four model calls already made are not
 * repeated or re-billed.
 */
public class ResearchAgentWorkflowImpl implements ResearchAgentWorkflow {

    private static final Logger log = Workflow.getLogger(ResearchAgentWorkflowImpl.class);

    /**
     * Model calls are slow, occasionally rate-limited, and cost money.
     *
     * <p>startToClose is generous because inference is slow. Backoff is deliberately
     * wide, since retrying a rate limit immediately just earns another one. Only
     * InvalidRequestException is non-retryable: the same malformed request will fail
     * identically every time, so spending three attempts on it wastes wall-clock and
     * delays the failure the operator needs to see.
     */
    private final LlmActivities llm = Workflow.newActivityStub(
            LlmActivities.class,
            ActivityOptions.newBuilder()
                    .setStartToCloseTimeout(Duration.ofMinutes(3))
                    .setRetryOptions(RetryOptions.newBuilder()
                            .setInitialInterval(Duration.ofSeconds(2))
                            .setBackoffCoefficient(2.0)
                            .setMaximumInterval(Duration.ofMinutes(1))
                            .setMaximumAttempts(5)
                            .setDoNotRetry(
                                    LlmActivities.InvalidRequestException.class.getName())
                            .build())
                    .build());

    // Workflow state. Durable across process death — this is the point of the exercise.
    private String phase = "STARTING";
    private int stepsTotal = 0;
    private final List<String> completedStepIds = new ArrayList<>();
    private LlmActivities.TokenUsage usage = LlmActivities.TokenUsage.none();
    private long budgetMinorUnits;

    private boolean awaitingApproval = false;
    private Boolean approvalDecision = null;

    @Override
    public LlmActivities.Report research(String goal, long budgetMinorUnits) {
        this.budgetMinorUnits = budgetMinorUnits;

        phase = "PLANNING";
        LlmActivities.Plan plan = llm.plan(goal);
        record(plan.usage());
        stepsTotal = plan.steps().size();
        log.info("Planned {} steps for goal: {}", stepsTotal, goal);

        phase = "RESEARCHING";
        List<LlmActivities.Finding> findings = new ArrayList<>();
        for (LlmActivities.Step step : plan.steps()) {

            // Check the budget BEFORE spending, not after. Asking a human to approve
            // money already spent is not an approval, it is a notification.
            if (!ensureBudgetFor(step)) {
                throw ApplicationFailure.newFailure(
                        "Budget of " + budgetMinorUnits + " minor units exhausted and additional "
                                + "spend was declined after " + completedStepIds.size() + " of "
                                + stepsTotal + " steps",
                        "BudgetExceeded");
            }

            List<LlmActivities.SearchResult> sources = llm.search(step.query());
            LlmActivities.Finding finding = llm.analyze(step.description(), sources);

            record(finding.usage());
            findings.add(finding);
            completedStepIds.add(step.id());
            log.info("Completed step {} ({}/{}), spent {} so far",
                    step.id(), completedStepIds.size(), stepsTotal, usage.costMinorUnits());
        }

        phase = "SYNTHESISING";
        LlmActivities.Report report = llm.synthesize(goal, findings);
        record(report.usage());

        phase = "COMPLETED";
        return report;
    }

    /**
     * Returns true if there is budget to continue.
     *
     * <p>When the estimate would breach the ceiling the workflow blocks on a human.
     * {@link Workflow#await} consumes no thread and no connection, so a run can sit
     * here for days at zero cost and still resume the instant someone signals — the
     * same mechanism as the approval use case, applied to spend rather than risk.
     */
    private boolean ensureBudgetFor(LlmActivities.Step step) {
        if (usage.costMinorUnits() < budgetMinorUnits) {
            return true;
        }

        phase = "AWAITING_BUDGET_APPROVAL";
        awaitingApproval = true;
        approvalDecision = null;
        log.warn("Budget {} reached before step {}; waiting for a human",
                budgetMinorUnits, step.id());

        // Bounded wait: an unbounded one leaks a workflow forever when nobody answers.
        boolean answered = Workflow.await(Duration.ofDays(3), () -> approvalDecision != null);

        awaitingApproval = false;
        phase = "RESEARCHING";

        if (!answered) {
            log.warn("No budget decision within 3 days; treating silence as a refusal");
            return false;
        }
        return approvalDecision;
    }

    private void record(LlmActivities.TokenUsage delta) {
        usage = usage.plus(delta);
    }

    @Override
    public void approveBudget(boolean approved, long newBudgetMinorUnits) {
        if (approved && newBudgetMinorUnits > budgetMinorUnits) {
            budgetMinorUnits = newBudgetMinorUnits;
        }
        approvalDecision = approved;
    }

    @Override
    public AgentStatus getStatus() {
        return new AgentStatus(
                phase,
                completedStepIds.size(),
                stepsTotal,
                usage.inputTokens(),
                usage.outputTokens(),
                usage.costMinorUnits(),
                budgetMinorUnits,
                awaitingApproval,
                List.copyOf(completedStepIds));
    }
}
