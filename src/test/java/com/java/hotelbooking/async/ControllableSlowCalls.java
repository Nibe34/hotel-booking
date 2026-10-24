package com.java.hotelbooking.async;

import com.java.hotelbooking.integration.SlowCallSimulator;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Replaces the sleeping simulator in tests. Nothing here depends on wall-clock timing:
 * an operation can be held until the test releases it, and a group of operations can be required to
 * meet at a barrier, which only succeeds if they really run at the same time.
 */
class ControllableSlowCalls implements SlowCallSimulator {

    private static final long SAFETY_SECONDS = 5;

    private final Map<String, String> threads = new ConcurrentHashMap<>();
    private final Map<String, CountDownLatch> gates = new ConcurrentHashMap<>();
    private final Map<String, CountDownLatch> finished = new ConcurrentHashMap<>();
    private volatile CyclicBarrier barrier;
    private volatile Set<String> barrierOperations = Set.of();

    @Override
    public void simulate(String operation, Duration typicalDuration) {
        threads.put(operation, Thread.currentThread().getName());
        try {
            CountDownLatch gate = gates.get(operation);
            if (gate != null && !gate.await(SAFETY_SECONDS, TimeUnit.SECONDS)) {
                throw new IllegalStateException(operation + " was never released");
            }
            if (barrierOperations.contains(operation)) {
                barrier.await(2, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(operation + " interrupted", e);
        } catch (BrokenBarrierException | TimeoutException e) {
            throw new IllegalStateException(operation + " did not run at the same time as " + barrierOperations, e);
        } finally {
            finishedLatch(operation).countDown();
        }
    }

    void block(String operation) {
        gates.put(operation, new CountDownLatch(1));
    }

    void release(String operation) {
        CountDownLatch gate = gates.remove(operation);
        if (gate != null) {
            gate.countDown();
        }
    }

    void mustRunConcurrently(String... operations) {
        barrier = new CyclicBarrier(operations.length);
        barrierOperations = Set.of(operations);
    }

    boolean hasFinished(String operation) {
        return finishedLatch(operation).getCount() == 0;
    }

    boolean awaitFinished(String operation) throws InterruptedException {
        return finishedLatch(operation).await(SAFETY_SECONDS, TimeUnit.SECONDS);
    }

    String threadOf(String operation) {
        return threads.get(operation);
    }

    void releaseAll() {
        gates.keySet().forEach(this::release);
    }

    /** Call only when no simulated operation is running any more, otherwise stale results leak into the next test. */
    void clear() {
        releaseAll();
        threads.clear();
        finished.clear();
        barrierOperations = Set.of();
        barrier = null;
    }

    private CountDownLatch finishedLatch(String operation) {
        return finished.computeIfAbsent(operation, k -> new CountDownLatch(1));
    }
}
