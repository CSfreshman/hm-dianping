package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
public class RedisIDWorker {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 初始时间戳
     */
    private final Long BEGIN_TIMESTAMP = 1640995200L;

    /**
     * 序列号位数
     */
    private final Integer COUNT_BITS = 32;

    /**
     * 获得全局唯一ID
     * 64位长度的ID，高32位表示时间戳，低32位表示序列号
     * 序列号使用redis的incr实现自增长，每一个业务，每一天都会新建一个序列号；incr:KeyPrefix:today
     * 这样可以保证ID是唯一且递增的同一时间戳下，序列号一定不一致（2^32-1 = 4,294,967,295,42亿的序列号，一天之内不会用完）
     * 这样序列号就是递增的，同时时间戳也是递增的，就可以保证整体的递增性
     */
    public Long getId(String keyPrefix){
        // 获得时间戳
        LocalDateTime now = LocalDateTime.now();
        long timestamp = now.toEpochSecond(ZoneOffset.UTC) - BEGIN_TIMESTAMP;

        // 获得序列号
        String day = now.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        Long increment = stringRedisTemplate.opsForValue().increment("incr:" + keyPrefix + ":" + day);

        // 拼接成唯一ID
        return timestamp << COUNT_BITS | increment;
    }


}
