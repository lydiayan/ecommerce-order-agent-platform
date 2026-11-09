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

    /**
     * 判断来源 IP 在当前登录失败统计窗口内是否已达到锁定阈值。
     *
     * @param ip 客户端来源 IP；仅以哈希摘要参与 Redis key 构造
     * @return 达到最大失败次数时返回 {@code true}
     */
    public boolean isBlocked(String ip) {
        String value = redisTemplate.opsForValue().get(key(ip));
        return value != null && Integer.parseInt(value) >= properties.getLoginMaxFailures();
    }

    /**
     * 累加来源 IP 的登录失败次数，并在首次失败时设置限流窗口。
     *
     * @param ip 客户端来源 IP
     */
    public void recordFailure(String ip) {
        String key = key(ip);
        Long failures = redisTemplate.opsForValue().increment(key);
        if (failures != null && failures == 1) {
            redisTemplate.expire(key, Duration.ofSeconds(properties.getLoginLockSeconds()));
        }
    }

    /**
     * 登录成功后清除来源 IP 的失败计数。
     *
     * @param ip 客户端来源 IP
     */
    public void clear(String ip) {
        redisTemplate.delete(key(ip));
    }

    private static String key(String ip) {
        return "auth:login:ip:" + TokenHash.sha256(ip).substring(0, 24);
    }
}
