package com.example.kata04;

import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class ReconciliationActivitiesImpl implements ReconciliationActivities {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationActivitiesImpl.class);
    private static final int TOTAL_RECORDS = 500;  // demo backlog size

    @Override
    public List<String> fetchBatch(int offset, int limit) {
        if (offset >= TOTAL_RECORDS) return List.of();
        int end = Math.min(offset + limit, TOTAL_RECORDS);
        List<String> batch = new ArrayList<>(end - offset);
        for (int i = offset; i < end; i++) batch.add("rec-" + i);
        log.info("fetched batch offset={} size={}", offset, batch.size());
        return batch;
    }

    @Override
    public void processRecord(String recordId) {
        // Simulate occasional record-level failures (every 73rd one) — the workflow
        // should log and skip these, not abort the whole run.
        if (recordId.hashCode() % 73 == 0) {
            throw new RuntimeException("record " + recordId + " has bad data");
        }
    }

    @Override
    public void sendSummary(int processed, int failed) {
        log.info("[notify] reconciliation summary: processed={} failed={}", processed, failed);
    }
}
