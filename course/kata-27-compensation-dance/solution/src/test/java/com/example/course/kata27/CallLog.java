package com.example.course.kata27;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Records every activity invocation (including failed attempts) in the order it happened. */
class CallLog {

    private final List<String> calls = new CopyOnWriteArrayList<>();

    void add(String call) {
        calls.add(call);
    }

    List<String> all() {
        return List.copyOf(calls);
    }

    long count(String call) {
        return calls.stream().filter(call::equals).count();
    }
}
