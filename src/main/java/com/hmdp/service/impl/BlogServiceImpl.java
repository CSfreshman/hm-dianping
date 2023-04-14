package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private IUserService userService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private IFollowService followService;


    /**
     * 根据blog_id查询blog
     */
    @Override
    public Result queryBlogById(Long id) {
        // 根据id查询博库
        Blog blog = getById(id);
        if (blog == null) {
            return Result.fail("博客不存在");
        }
        // 设置当前用户信息
        queryBlogUser(blog);
        // 设置当前用户是否已经给这个博客点赞
        isLikedBlog(blog);
        return Result.ok(blog);
    }

    /**
     * 实现点赞和取消点赞的功能
     */
    @Override
    public Result likeBlog(Long id) {
        // 1.先判断用户有没有点过赞
        Long userId = UserHolder.getUser().getId();
        //Boolean isMember = stringRedisTemplate.opsForSet().isMember(RedisConstants.BLOG_LIKED_KEY + id, userId+"");
        Double score = stringRedisTemplate.opsForZSet().score(RedisConstants.BLOG_LIKED_KEY + id, userId + "");
        if (score == null) {
            // 2.如果没有点过赞
            // 2.1数据库添加一条点赞信息
            boolean isSuccess = update().setSql("liked = liked+1").eq("id", id).update();
            // 2.2在Redis中保存用户点赞记录
            if (isSuccess) {
                //stringRedisTemplate.opsForSet().add(RedisConstants.BLOG_LIKED_KEY + id,userId+"");
                stringRedisTemplate.opsForZSet().add(RedisConstants.BLOG_LIKED_KEY + id, userId + "", System.currentTimeMillis());
            }
        } else {
            // 3.如果已经点过赞
            // 3.1在数据库中删除一条点赞信息
            boolean isSuccess = update().setSql("liked = liked-1").eq("id", id).update();
            // 3.2在Redis中删除用户点赞记录
            if (isSuccess) {
                stringRedisTemplate.opsForZSet().remove(RedisConstants.BLOG_LIKED_KEY + id, userId + "");
            }
        }

        return Result.ok();
    }

    /**
     * 查询热门blog
     */
    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach(blog -> {
            queryBlogUser(blog);
            isLikedBlog(blog);
        });
        return Result.ok(records);
    }

    /**
     * 根据blog_id查询点赞列表
     */
    @Override
    public Result likes(Long id) {
        // 得到top5的值
        Set<String> range = stringRedisTemplate.opsForZSet().range(RedisConstants.BLOG_LIKED_KEY + id, 0, 4);
        if (range == null || range.size() == 0) {
            return Result.ok();
        }

        List<Long> userIds = range.stream().map(Long::parseLong).collect(Collectors.toList());

        String join = StrUtil.join(",", userIds);

        // 根据用户id查询用户信息
        List<UserDTO> userDtos = userService.query()
                .in("id", userIds)
                .last("order by field(id," + join + ")").list()
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());

        return Result.ok(userDtos);
    }

    @Override
    public Result saveBlog(Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 保存探店博文
        boolean isSuccess = save(blog);
        if (!isSuccess) {
            return Result.fail("添加博客失败");
        }

        // 同时要把该博客发送给所有关注了该用户的收信箱中
        // 先查出所有关注了该用户的用户
        List<Follow> fansId = followService.query().eq("follow_user_id", UserHolder.getUser().getId()).list();
        // 将博客id发送给这些用户的信箱
        fansId.forEach((Follow follow) -> {
            Long userId = follow.getUserId();
            // 每一个用户对应一个feed流的key，也就是每一个用户都有一个收信箱
            String key = RedisConstants.FEED_KEY + userId;
            stringRedisTemplate.opsForZSet().add(key, blog.getId() + "", System.currentTimeMillis());
        });

        // 返回id
        return Result.ok(blog.getId());
    }

    /**
     * 查看已经关注了博主新发布的博客
     */
    @Override
    public Result ofFollow(Long max, Integer offset) {
        // 1.得到当前用户
        Long userId = UserHolder.getUser().getId();

        // 2.查询收件箱
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet().reverseRangeByScoreWithScores(RedisConstants.FEED_KEY + userId, 0, max, offset, 2);

        if (typedTuples == null || typedTuples.isEmpty()) {
            return Result.ok();
        }
        System.out.println(typedTuples.size());

        // 3.解析数据 blogList,min(当前查询数据的最小id),offset(与这个最小id相同score的数据的个数)
        List<Long> idList = new ArrayList<>(typedTuples.size());
        long min = 0L;
        int resOffset = 1;
        for (ZSetOperations.TypedTuple<String> tuple : typedTuples) {
            idList.add(Long.valueOf(tuple.getValue()));
            long score = tuple.getScore().longValue();
            // 假设查到的score为 5 4 4 3 3 1
            if (score == min) {
                resOffset += 1;
            } else {
                min = score;
                resOffset = 1;
            }
        }

        // 4.根据id查询blog
        String ids = StrUtil.join(",", idList);
        List<Blog> blogs = query().in("id", idList).last("ORDER BY FIELD(id," + ids + ")").list();
        for (Blog blog : blogs) {
            // 设置当前用户信息
            queryBlogUser(blog);
            // 设置当前用户是否一定给这个博客点赞
            isLikedBlog(blog);
        }
        // 5.返回
        ScrollResult scrollResult = new ScrollResult();
        scrollResult.setList(blogs);
        scrollResult.setMinTime(min);
        scrollResult.setOffset(resOffset);
        return Result.ok(scrollResult);
    }

    /**
     * 给Blog赋值，User信息
     */
    private void queryBlogUser(Blog blog) {
        User user = userService.getById(blog.getUserId());
        blog.setIcon(user.getIcon());
        blog.setName(user.getNickName());
    }

    /**
     * 当前用户是否一定给该blod点赞
     *
     * @param blog
     */
    private void isLikedBlog(Blog blog) {
        Double score = stringRedisTemplate.opsForZSet().score(RedisConstants.BLOG_LIKED_KEY + blog.getId(), UserHolder.getUser().getId() + "");
        // 如果score不为null，就意味着已经有点在记录了
        blog.setIsLike(score != null);
    }
}
