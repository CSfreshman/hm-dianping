package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIDWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Collections;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;
    @Resource
    private RedisIDWorker redisIDWorker;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    // 加载lua脚本
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    /**
     * 使用异步秒杀
     */
    @Override
    public Result seckillVoucher(Long voucherId) {
        Long userId = UserHolder.getUser().getId();
        // 1.执行lua脚本
        Long execute = stringRedisTemplate.execute(SECKILL_SCRIPT, Collections.emptyList(), voucherId.toString(), userId.toString());
        int res = execute.intValue();

        // 2.判断是否有购买资格，没有就返回(lua返回结果不为0)
        if (res != 0) {
            return Result.fail(res == 1 ? "库存不足" : "不可以重复下单");
        }

        // 3.生成orderId，放到消息队列中
        Long orderId = redisIDWorker.getId("order");
        // TODO:orderId放入消息队列
        // 4.1生成订单
        VoucherOrder order = new VoucherOrder();
        order.setId(orderId);
        order.setVoucherId(voucherId);
        order.setUserId(userId);
        order.setCreateTime(LocalDateTime.now());

        // 5.返回结果
        return Result.ok(orderId);
    }


    /**
     * 秒杀优惠券的功能
     * 添加事务，保证扣减库存、创建订单的动作能够同时完成或者失败
     */
    /*@Override
    public Result seckillVoucher(Long voucherId) {
        // 1.查询优惠券信息
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);

        // 2.判断是否在活动时间内（活动未开始或者活动已经结束）
        LocalDateTime now = LocalDateTime.now();
        if(now.isBefore(seckillVoucher.getBeginTime()) || now.isAfter(seckillVoucher.getEndTime())){
            return Result.fail("不在活动时间内");
        }

        // 3.判断库存是否有剩余
        if(seckillVoucher.getStock() <= 0){
            return Result.fail("库存不足");
        }

        Long userId = UserHolder.getUser().getId();
        // 这里加锁保证锁住的是同一个用户
        // 获得userId的时候，会新建一个Long对象，toString之后仍然会新建String对象
        // 调用intern方法之后，就会使用同一个字面量，也就是对应的userId的字面量，这样就可以保证锁住同一个对象
        *//*synchronized (userId.toString().intern()){
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        }*//*

        //使用分布式锁
        SimpleRedisLock lock = new SimpleRedisLock(stringRedisTemplate, "order" + userId);
        String lockKey = "order" + userId;
        if (!lock.tryLock(100L)) {
            return Result.fail("不可以重复下单");
        }
        try {
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);
        } finally {
            lock.unlock();
        }
    }*/

    @Transactional
    public Result createVoucherOrder(Long voucherId){
        // 6.实现一人一单
        // 根据user_id 和 voucher_id去查询有没有订单记录
        Long userId = UserHolder.getUser().getId();
        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if(count != 0){
            return Result.fail("已经购买了，不可以重复购买");
        }

        // 4.扣减库存
        boolean flag = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock",0)
                .update();
        if(!flag){
            return Result.fail("扣减库存失败");
        }

        // 5.创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        // 5.1订单id（全局唯一ID生成器生成）
        Long orderId = redisIDWorker.getId("order");
        voucherOrder.setId(orderId);
        // 5.2用户id
        voucherOrder.setUserId(UserHolder.getUser().getId());
        // 5.3优惠券id
        voucherOrder.setVoucherId(voucherId);
        save(voucherOrder);

        return Result.ok(orderId);
    }
}
