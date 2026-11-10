package com.clip2house.ledger.support;

import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

public class TestRandom implements RandomGenerator {

    private volatile Double fixedDouble;

    public void fixNextDouble(double value) {
        this.fixedDouble = value;
    }

    public void unfix() {
        this.fixedDouble = null;
    }

    @Override
    public double nextDouble() {
        Double fixed = fixedDouble;
        return fixed != null ? fixed : ThreadLocalRandom.current().nextDouble();
    }

    @Override
    public long nextLong() {
        return ThreadLocalRandom.current().nextLong();
    }
}
