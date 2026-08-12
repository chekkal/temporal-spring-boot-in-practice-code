package com.example.agent;

import java.util.List;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

/**
 * Every call that leaves the process — model inference, web search — is an activity.
 *
 * <p>That is the whole trick. Activity results are written to workflow history, so a
 * replay after a crash reads the recorded result instead of calling the model again.
 * The agent resumes where it stopped and pays nothing to get back there.
 *
 * <p>Anything non-deterministic belongs here rather than in the workflow: model output
 * varies between identical calls, and a workflow must produce the same decisions on
 * replay or Temporal will detect the divergence and fail the execution.
 */
@ActivityInterface
public interface LlmActivities {

    /** Ask the model to decompose a goal into ordered research steps. */
    @ActivityMethod
    Plan plan(String goal);

    /** Retrieve source material for one step. */
    @ActivityMethod
    List<SearchResult> search(String query);

    /** Ask the model to draw a conclusion from retrieved sources. */
    @ActivityMethod
    Finding analyze(String stepDescription, List<SearchResult> sources);

    /** Ask the model to combine findings into a final report. */
    @ActivityMethod
    Report synthesize(String goal, List<Finding> findings);

    // --- Types -------------------------------------------------------------
    // Records, so payloads serialise to JSON without extra configuration.

    record Plan(List<Step> steps, TokenUsage usage) {}

    record Step(String id, String description, String query) {}

    record SearchResult(String title, String url, String snippet) {}

    record Finding(String stepId, String conclusion, List<String> citations, TokenUsage usage) {}

    record Report(String goal, String body, List<String> citations, TokenUsage usage) {}

    /**
     * Token counts and cost in minor units (cents), never floating point.
     * Money in a double is how you end up explaining a rounding drift to finance.
     */
    record TokenUsage(long inputTokens, long outputTokens, long costMinorUnits) {

        static TokenUsage none() {
            return new TokenUsage(0, 0, 0);
        }

        TokenUsage plus(TokenUsage other) {
            return new TokenUsage(
                    inputTokens + other.inputTokens,
                    outputTokens + other.outputTokens,
                    costMinorUnits + other.costMinorUnits);
        }
    }

    /**
     * The provider rate-limited us. This IS retryable — it is the textbook case for
     * exponential backoff, and Temporal's retry policy handles it without any code
     * in the workflow. Marking rate limits non-retryable is a common mistake: it
     * turns a two-second delay into a failed agent run.
     */
    class RateLimitException extends RuntimeException {
        public RateLimitException(String message) {
            super(message);
        }
    }

    /**
     * The request itself is wrong — malformed prompt, context-length overflow, rejected
     * by content policy. Retrying sends the identical request and gets the identical
     * error, so this one is configured as non-retryable and fails the run immediately
     * instead of burning the retry budget.
     */
    class InvalidRequestException extends RuntimeException {
        public InvalidRequestException(String message) {
            super(message);
        }
    }
}
