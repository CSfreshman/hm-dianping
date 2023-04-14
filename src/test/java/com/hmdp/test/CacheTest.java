package com.hmdp.test;

import com.hmdp.service.IShopService;
import com.hmdp.service.impl.ShopServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import javax.annotation.Resource;
import java.util.Set;

@SpringBootTest
public class CacheTest {

    @Resource
    StringRedisTemplate stringRedisTemplate;

    @Test
    public void test() {
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet().reverseRangeByScoreWithScores("feed:1010", 0, 1681463295981L, 0, 2);
        System.out.println(typedTuples.size());

    }
}
