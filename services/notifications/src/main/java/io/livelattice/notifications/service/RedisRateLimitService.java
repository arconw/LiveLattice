package io.livelattice.notifications.service;

import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class RedisRateLimitService {

    private final StringRedisTemplate redisTemplate;

    public RedisRateLimitService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean allow(String key, int max, Duration window) {
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                redisTemplate.expire(key, window);
            }
            return count == null || count <= max;
        } catch (Exception ex) {
            return true;
        }
    }
}
