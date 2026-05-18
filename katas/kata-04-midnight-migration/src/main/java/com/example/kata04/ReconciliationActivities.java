package com.example.kata04;

import java.util.List;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface ReconciliationActivities {

    @ActivityMethod
    List<String> fetchBatch(int offset, int limit);

    @ActivityMethod
    void processRecord(String recordId);

    @ActivityMethod
    void sendSummary(int processed, int failed);
}
