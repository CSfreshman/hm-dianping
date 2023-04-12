package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result shopTypeList() {
        // 1.现在Redis中查询是否存在该List
        String str = stringRedisTemplate.opsForValue().get(RedisConstants.CACHE_SHOP_TYPE_KEY);
        List<ShopType> list = null;
        // 2.缓存命中直接返回
        if(!StringUtils.isEmpty(str)){
            list = JSONUtil.toList(str,ShopType.class);
            return Result.ok(list);
        }

        // 3.缓存没有命中就查询数据库
        list = query().orderByAsc("sort").list();

        // 4.判断数据库中有没有数据
        if(list == null || list.size() == 0){
            return Result.fail("查询失败");
        }

        // 5.结果写入Redis
        stringRedisTemplate.opsForValue().set(RedisConstants.CACHE_SHOP_TYPE_KEY,JSONUtil.toJsonStr(list));
        return Result.ok(list);
    }
}
