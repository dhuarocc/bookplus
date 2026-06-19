package com.bookplus.cart.shared.idempotency;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/** Adaptador Redis del IdempotencyStore. Aprovecha el TTL nativo de Redis para caducar claves. */
@Component
public class RedisIdempotencyStore implements IdempotencyStore {

    private final RedisTemplate<String, String> redis;

    public RedisIdempotencyStore(RedisTemplate<String, String> redis) {
        this.redis = redis;
    }

    @Override
    public boolean putIfAbsent(String key, String value, Duration ttl) {
        Boolean ok = redis.opsForValue().setIfAbsent(key, value, ttl);
        return Boolean.TRUE.equals(ok);
    }

    @Override
    public Optional<String> get(String key) {
        return Optional.ofNullable(redis.opsForValue().get(key));
    }

    @Override
    public void set(String key, String value, Duration ttl) {
        redis.opsForValue().set(key, value, ttl);
    }

    @Override
    public void delete(String key) {
        redis.delete(key);
    }
}
