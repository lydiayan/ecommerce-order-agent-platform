package com.css.mallorderagent.security;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Qualifier;

import java.time.Duration;

@Component
public class LoginRateLimiter {

    private final StringRedisTemplate redisTemplate;
    private final AuthProperties properties;

    public LoginRateLimiter(@Qualifier("stringRedisTemplate") StringRedisTemplate redisTemplate,
                            AuthProperties properties) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
    }

    public boolean isBlocked(String ip) {
        String value = redisTemplate.opsForValue().get(key(ip));
        return value != null && Integer.parseInt(value) >= properties.getLoginMaxFailures();
    }

    public void recordFailure(String ip) {
        String key = key(ip);
        Long failures = redisTemplate.opsForValue().increment(key);
        if (failures != null && failures == 1) {
            redisTemplate.expire(key, Duration.ofSeconds(properties.getLoginLockSeconds()));
        }
    }

    public void clear(String ip) {
        redisTemplate.delete(key(ip));
    }

    private static String key(String ip) {
        return "auth:login:ip:" + TokenHash.sha256(ip).substring(0, 24);
    }
}
