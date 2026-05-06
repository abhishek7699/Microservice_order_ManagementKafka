package com.orderSystem.gateway.RateLimiter;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

@Configuration
public class RateLimiterConfig {

    @Bean
    public KeyResolver customerResolver() {
        return exchange -> {
            String customerId = exchange.getRequest()
                    .getHeaders()
                    .getFirst("X-Customer-Id");

            if (customerId != null) {
                return Mono.just(customerId);
            }
            return Mono.just(exchange.getRequest()
                    .getRemoteAddress()
                    .getAddress()
                    .getHostAddress());
        };
    }

    @Bean
    public RedisRateLimiter redisRateLimiter() {
        return new RedisRateLimiter(10, 20, 1);
        // replenishRate=10, burstCapacity=20, requestedTokens=1
    }
}