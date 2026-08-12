package com.example.agent;

import java.util.List;

import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.SignalMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface ResearchAgentWorkflow {

    /**
     * Run the agent to completion.
     *
     * @param goal            what to research
     * @param budgetMinorUnits spend ceiling in cents; crossing it pauses for approval
     */
    @WorkflowMethod
    LlmActivities.Report research(String goal, long budgetMinorUnits);

    /**
     * A human answers the budget request. The workflow may have been waiting for
     * days at this point — it holds no thread and no connection while it waits.
     */
    @SignalMethod
    void approveBudget(boolean approved, long newBudgetMinorUnits);

    /** Live progress, readable at any time without disturbing the run. */
    @QueryMethod
    AgentStatus getStatus();

    record AgentStatus(
            String phase,
            int stepsCompleted,
            int stepsTotal,
            long inputTokens,
            long outputTokens,
            long spentMinorUnits,
            long budgetMinorUnits,
            boolean awaitingApproval,
            List<String> completedStepIds) {}
}
