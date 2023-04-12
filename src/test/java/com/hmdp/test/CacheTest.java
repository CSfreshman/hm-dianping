package com.hmdp.test;

import com.hmdp.service.IShopService;
import com.hmdp.service.impl.ShopServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import javax.annotation.Resource;

@SpringBootTest
public class CacheTest {
    @Resource
    ShopServiceImpl shopService;

    @Test
    public void test(){
        shopService.saveShop2Redis(1L,30L);
    }
}
