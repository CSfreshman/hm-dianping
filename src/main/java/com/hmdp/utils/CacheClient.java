package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.json.JSON;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;

import lombok.extern.slf4j.Slf4j;
import org.redisson.executor.RedissonClassLoader;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

@Slf4j
@Component
public class CacheClient {

    private final StringRedisTemplate stringRedisTemplate;

    public CacheClient(StringRedisTemplate stringRedisTemplate){
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void set(String key, Object value, Long time, TimeUnit unit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value),time,unit);
    }

    public void setWithLogicExpire(String key,Object value,Long time,TimeUnit unit){
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(redisData));
    }

    /**
     * 缓存空对象解决缓存穿透
     */
    public <R,ID> R queryWithPassThrough(String keyPrefix, ID id, Class<R> type, Function<ID,R> dbFallBack,Long time,TimeUnit unit){
        String key = keyPrefix + id;
        // 1.先从redis中查询
        String json = stringRedisTemplate.opsForValue().get(key);

        // 2.判断是否查询到结果
        if(!StringUtils.isEmpty(json)){
            // 3.查询到就返回
            return JSONUtil.toBean(json,type);
        }

        if("".equals(json)){
            // 4.如果查询到的是空对象
            return null;
        }

        // 5.没有查询寻到就去数据库中查询 函数式编程，由调用者去执行
        R r = dbFallBack.apply(id);
        if(r == null){
            // 6.数据库中也没有 就缓存空对象，并设置过期时间
            set(key,"",RedisConstants.CACHE_NULL_TTL,TimeUnit.SECONDS);
            return null;
        }
        // 7.存在数据库中就返回，同时写入redis
        set(key,r,time,unit);
        return r;
    }

    /**
     * 逻辑过期的方式解决缓存击穿
     */
    public <R,ID> R queryWithLogicExpire(String prefix,ID id,Class<R> type, Function<ID,R> dbFallBack,Long time,TimeUnit unit){
        String key = prefix + id;
        // 1.先去redis中查询
        String json = stringRedisTemplate.opsForValue().get(key);

        // 2.如果没查询到或者是空对象  直接返回
        if(StringUtils.isEmpty(json)){
            return null;
        }
        // 3.反序列化
        RedisData redisData = JSONUtil.toBean(json, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(),type);
        LocalDateTime expireTime = redisData.getExpireTime();

        // 3.判断是否过期
        if(expireTime.isAfter(LocalDateTime.now())){
            // 没有过期，返回数据
            return r;
        }

        // 4.过期了，尝试获得互斥锁
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;

        if(!tryLock(lockKey)){
            // 5.加锁失败，返回旧的数据
            return r;
        }

        // 6.加锁成功，新建线程去重建缓存
        ThreadPoolExecutor threadPool = ThreadPoolUtil.getThreadPool();
        threadPool.execute(()->{
            try{
                // 查询数据库，使用函数式编程，由调用者执行
                R newR = dbFallBack.apply(id);
                // 以逻辑过期的方式写入缓存
                setWithLogicExpire(key,newR,time,unit);
            }catch (Exception e){
                e.printStackTrace();
            }finally {
                // 保证一定会释放锁
                unlock(lockKey);
            }
        });
        return r;
    }


    /**
     * 尝试获得互斥锁 （setnx lockKey）
     * @param key 锁的标识
     * @return
     */
    public boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key,"1",RedisConstants.LOCK_SHOP_TTL,TimeUnit.MINUTES);
        return BooleanUtil.isTrue(flag);
    }

    /**
     * 释放互斥锁（del lockKey）
     * @param key 锁的标识
     */
    public void unlock(String key){
        stringRedisTemplate.delete(key);
    }


}

