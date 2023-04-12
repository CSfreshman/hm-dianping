package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock {

    private StringRedisTemplate stringRedisTemplate;
    private String LOCK_NAME;
    private final String LOCK_PREFIX = "lock:";

    public SimpleRedisLock(StringRedisTemplate stringRedisTemplate, String LOCK_NAME) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.LOCK_NAME = LOCK_NAME;
    }

    /**
     * 尝试获取锁
     *
     * @param timeoutSec 锁的超时过期时间
     */
    @Override
    public boolean tryLock(Long timeoutSec) {
        String value = Thread.currentThread().getId() + "";
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(LOCK_PREFIX + LOCK_NAME, value, timeoutSec, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(success);
    }

    /**
     * 释放锁
     */
    @Override
    public void unlock() {
        stringRedisTemplate.delete(LOCK_PREFIX + LOCK_NAME);
    }
}
