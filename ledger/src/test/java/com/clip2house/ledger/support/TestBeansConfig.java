package com.clip2house.ledger.support;

import java.util.random.RandomGenerator;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class TestBeansConfig {

    @Bean
    @Primary
    public RandomGenerator testRandom() {
        return new TestRandom();
    }
}
