package com.example.agent;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * A stand-in for a real model provider.
 *
 * <p>This exists so the use case runs with no API key and no spend. Everything the
 * workflow demonstrates — durable replay, retry policy, the budget gate, token
 * accounting — is a property of the orchestration, not of any particular provider,
 * so a simulated provider demonstrates it exactly as well as a real one and costs
 * nothing to run in CI.
 *
 * <p>See the module README for the Spring AI implementation that replaces this class.
 * The workflow does not change: swapping providers means swapping this bean.
 */
@Component
public class SimulatedLlmActivities implements LlmActivities {

    private static final Logger log = LoggerFactory.getLogger(SimulatedLlmActivities.class);

    /** Pretend inference latency, in milliseconds. Kept small so a demo run is watchable. */
    @Value("${agent.simulated.latency-ms:400}")
    private long latencyMs;

    /**
     * Rate-limit the first N analyze() calls, then succeed.
     *
     * <p>Set this to 1 or 2 to watch Temporal's retry policy do its work: the activity
     * fails, backs off, and retries without a single line of retry code in the workflow.
     * Default 0 keeps demo runs deterministic.
     */
    @Value("${agent.simulated.rate-limit-first-n:0}")
    private int rateLimitFirstN;

    private final AtomicInteger analyzeAttempts = new AtomicInteger();

    @Override
    public Plan plan(String goal) {
        pause();
        if (goal == null || goal.isBlank()) {
            // Retrying an empty goal produces an empty goal. Fail fast instead.
            throw new InvalidRequestException("goal must not be blank");
        }
        List<Step> steps = List.of(
                new Step("s1", "Establish current state of: " + goal, goal + " overview 2026"),
                new Step("s2", "Identify the main constraints", goal + " limitations tradeoffs"),
                new Step("s3", "Find production evidence", goal + " case study production"),
                new Step("s4", "Summarise dissenting views", goal + " criticism problems"));
        log.info("Planned {} steps for '{}'", steps.size(), goal);
        return new Plan(steps, new TokenUsage(320, 180, 12));
    }

    @Override
    public List<SearchResult> search(String query) {
        pause();
        return List.of(
                new SearchResult(
                        "Primary source on " + query,
                        "https://example.invalid/a",
                        "A representative passage about " + query + "."),
                new SearchResult(
                        "Secondary analysis of " + query,
                        "https://example.invalid/b",
                        "A second view of " + query + ", partially disagreeing."));
    }

    @Override
    public Finding analyze(String stepDescription, List<SearchResult> sources) {
        pause();
        int attempt = analyzeAttempts.incrementAndGet();
        if (attempt <= rateLimitFirstN) {
            log.warn("Simulating provider rate limit on analyze attempt {}", attempt);
            throw new RateLimitException("429 from simulated provider on attempt " + attempt);
        }
        List<String> citations = sources.stream().map(SearchResult::url).toList();
        return new Finding(
                "step",
                "Based on " + sources.size() + " sources: " + stepDescription
                        + " resolves to a qualified yes, with caveats.",
                citations,
                new TokenUsage(900, 260, 34));
    }

    @Override
    public Report synthesize(String goal, List<Finding> findings) {
        pause();
        String body = findings.stream()
                .map(Finding::conclusion)
                .reduce(new StringBuilder("Report on: ").append(goal).append("\n\n"),
                        (sb, c) -> sb.append("- ").append(c).append('\n'),
                        StringBuilder::append)
                .toString();
        List<String> citations = findings.stream()
                .flatMap(f -> f.citations().stream())
                .distinct()
                .toList();
        return new Report(goal, body, citations, new TokenUsage(1500, 700, 88));
    }

    private void pause() {
        if (latencyMs <= 0) {
            return;
        }
        try {
            Thread.sleep(latencyMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while simulating inference", e);
        }
    }
}
