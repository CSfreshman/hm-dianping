package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.Follow;
import com.hmdp.mapper.FollowMapper;
import com.hmdp.service.IFollowService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.UserHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class FollowServiceImpl extends ServiceImpl<FollowMapper, Follow> implements IFollowService {

    /**
     * 关注功能
     * followUserId表示要去关注的用户id
     * ifFollow 为 true 表示要去关注
     * 为 false 表示要取消关注
     */
    @Override
    public Result follow(Long followUserId, Boolean isFollow) {
        Long curUserId = UserHolder.getUser().getId();
        if (curUserId == null) {
            return Result.fail("登录过期，请先登录");
        }
        if (followUserId == null) {
            return Result.fail("没有发现要关注的用户");
        }

        if (BooleanUtil.isTrue(isFollow)) {
            if (isFollowed(curUserId, followUserId)) {
                return Result.fail("已经关注了，不可以重复关注");
            }
            Follow follow = new Follow();
            follow.setUserId(curUserId);
            follow.setFollowUserId(followUserId);
            follow.setCreateTime(LocalDateTime.now());
            save(follow);
        } else {
            remove(new QueryWrapper<Follow>().eq("user_id", curUserId).eq("follow_user_id", followUserId));
        }
        return Result.ok();
    }

    /**
     * 判断是否关注了followUserId这个用户
     */
    @Override
    public Result followOrNot(Long followUserId) {
        Long curUserId = UserHolder.getUser().getId();
        if (curUserId == null || followUserId == null) {
            return Result.fail("出错了！");
        }
        //Integer count = query().eq("user_id", curUserId).eq("follow_user_id", followUerId).count();

        return Result.ok(isFollowed(curUserId, followUserId));
    }

    boolean isFollowed(Long curUserId, Long followUserId) {
        return (query().eq("user_id", curUserId).eq("follow_user_id", followUserId).count() > 0);
    }
}
