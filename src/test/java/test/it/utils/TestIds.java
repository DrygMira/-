package test.it.utils;

import java.util.concurrent.atomic.AtomicLong;

/** Генератор уникальных requestId для IT тестов (в пределах одной JVM). */
public final class TestIds {
    private static final AtomicLong SEQ = new AtomicLong(0);

    private TestIds() {}

    public static String next(String prefix) {
        long n = SEQ.incrementAndGet();
        return "it-" + (prefix == null ? "req" : prefix) + "-" + n;
    }
}