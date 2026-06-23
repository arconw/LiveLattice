package io.livelattice.notifications.service;

import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class RedisDeduplicationService {

    private final StringRedisTemplate redisTemplate;

    public RedisDeduplicationService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean claim(String key, Duration ttl) {
        try {
            Boolean result = redisTemplate.opsForValue().setIfAbsent(key, "1", ttl);
            return Boolean.TRUE.equals(result);
        } catch (Exception ex) {
            return true;
        }
    }
}
