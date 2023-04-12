package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import com.hmdp.utils.ThreadPoolUtil;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private ThreadPoolUtil threadPoolUtil;

    @Override
    public Result queryById(Long id) {
        Shop shop = cacheWithMutex(id);
        return Result.ok(shop);
    }

    /**
     * 逻辑过期解决缓存击穿
     * @param id
     * @return
     */
    public Shop cacheWithLogicExpire(Long id){
        // 1.从redis中查询店铺信息
        String shopStr = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id);
        Shop shop = null;
        // 2.缓存命中了？
        if(!(shopStr != null && !shopStr.equals(""))){
            // 3.没命中直接返回空
           return null;
        }

        // 4.判断数据是否过期
        RedisData redisData = JSONUtil.toBean(shopStr, RedisData.class);
        shop = JSONUtil.toBean((JSONObject) redisData.getData(),Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        if(!LocalDateTime.now().isAfter(expireTime)){
            // 5.当前时间不晚于过期时间，也就是说数据没过期
            return shop;
        }
        // 6.过期了
        if(!tryLock(RedisConstants.LOCK_SHOP_KEY + id)){
            // 7.尝试获取锁失败
            return shop;
        }
        // 8.获取锁成功，新建线程执行缓存重构
        // 新建线程进行缓存重构
        ThreadPoolUtil.getThreadPool().execute(()->{
            System.out.println("执行缓存重建:" + Thread.currentThread().getName());
            saveShop2Redis(id,30L);
            unlock(RedisConstants.LOCK_SHOP_KEY + id);
        });
        // 9.当前线程返回旧的信息
        return shop;
    }

    /**
     * 使用互斥锁解决缓存击穿
     * @param id
     * @return
     */
    public Shop cacheWithMutex(Long id){
        // 1.从redis中查询店铺信息
        String shopStr = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id);
        Shop shop = null;
        // 2.缓存命中了？
        if(shopStr != null && !shopStr.equals("")){
            // 3.命中了直接返回
            shop = JSONUtil.toBean(shopStr,Shop.class);
            return shop;
        }
        // 补充：命中之后判断是不是空对象
        if("".equals(shopStr)){
            return null;
        }

        // 4.没有命中就去数据库中查询
        // 4.1先尝试获取互斥锁
        String lockKey = RedisConstants.LOCK_SHOP_KEY + id;
        try {
            if (!tryLock(lockKey)) {
                // 4.2获取锁失败，休眠一段时间后重试
                Thread.sleep(5 * 1000);
                cacheWithMutex(id);
            }
            // 4.3获取锁成功，去查询数据库
            shop = getById(id);

            // 5.数据库命中了？
            if (shop == null) {
                // 6.数据库中也没有，返回错误信息
                // 补充：缓存空对象
                stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, "", RedisConstants.CACHE_NULL_TTL, TimeUnit.MINUTES);
                return null;
            }

            // 7.数据库中查到了就写入redis
            stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop), RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        }catch (Exception e){
            e.printStackTrace();
        }finally {
            // 补充：记得释放锁
            unlock(lockKey);
        }


        // 8.结束
        return shop;
    }

    /**
     * 缓存空对象解决缓存穿透
     * @param id
     * @return
     */
    public Shop cacheWithPassThrough(Long id){
        // 1.从redis中查询店铺信息
        String shopStr = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_KEY + id);
        Shop shop = null;
        // 2.缓存命中了？
        if(shopStr != null && !shopStr.equals("")){
            // 3.命中了直接返回
            shop = JSONUtil.toBean(shopStr,Shop.class);
            return shop;
        }
        // 补充：命中之后判断是不是空对象
        if("".equals(shopStr)){
            return null;
        }

        // 4.没有命中就去数据库中查询
        shop = getById(id);

        // 5.数据库命中了？
        if(shop == null){
            // 6.数据库中也没有，返回错误信息
            // 补充：缓存空对象
            stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id,"",RedisConstants.CACHE_NULL_TTL,TimeUnit.MINUTES);
            return null;
        }

        // 7.数据库中查到了就写入redis
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY + id,JSONUtil.toJsonStr(shop),RedisConstants.CACHE_SHOP_TTL, TimeUnit.MINUTES);
        // 8.结束
        return shop;
    }




    @Transactional
    public Result update(Shop shop){
        if(shop == null || shop.getId() == null){
            return Result.fail("更新出错");
        }
        // 1.更新数据库
        updateById(shop);

        // 2.删除缓存
        stringRedisTemplate.delete(RedisConstants.CACHE_SHOP_KEY + shop.getId());

        return Result.ok();
    }


    public boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key,"1",RedisConstants.LOCK_SHOP_TTL,TimeUnit.MINUTES);
        return BooleanUtil.isTrue(flag);
    }

    public void unlock(String key){
        stringRedisTemplate.delete(key);
    }

    /**
     * 封装将店铺信息存储到Redis中的方法
     * @param id 店铺id
     * @param expireSeconds 逻辑过期时间
     */
    public void saveShop2Redis(Long id,Long expireSeconds){
        Shop shop = getById(id);
        RedisData data = new RedisData();
        data.setData(shop);
        // 当前时间加上逻辑过期时间
        data.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_KEY+id,JSONUtil.toJsonStr(data));
    }
}
