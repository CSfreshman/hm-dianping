package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.BooleanUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import javax.print.attribute.standard.MediaSize;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

public class SimpleRedisLock implements ILock {

    private StringRedisTemplate stringRedisTemplate;
    private String LOCK_NAME;
    private static final String LOCK_PREFIX = "lock:";
    private static final String ID_PREFIX = UUID.randomUUID().toString(true) + "-";
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;

    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

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
        String value = ID_PREFIX + Thread.currentThread().getId();
        Boolean success = stringRedisTemplate.opsForValue().setIfAbsent(LOCK_PREFIX + LOCK_NAME, value, timeoutSec, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(success);
    }

    /**
     * 释放锁
     */
    @Override
    public void unlock() {
        /*String value = stringRedisTemplate.opsForValue().get(LOCK_NAME);
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        if(threadId.equals(value)){
            stringRedisTemplate.delete(LOCK_PREFIX + LOCK_NAME);
        }*/

        // 执行lua脚本，实现判断锁标识和删除锁的原子性
        stringRedisTemplate.execute(UNLOCK_SCRIPT, Collections.singletonList(LOCK_PREFIX + LOCK_NAME), Collections.singletonList(ID_PREFIX + Thread.currentThread().getId()));
    }
}
