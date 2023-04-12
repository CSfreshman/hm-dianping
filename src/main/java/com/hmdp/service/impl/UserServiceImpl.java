package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;
import java.sql.Time;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;


    /**
     * 根据用户的手机号发送6位验证码
     * @param phone 手机号
     * @param session 请求对应的session
     * @return
     */
    @Override
    public Result sendCode(String phone, HttpSession session) {
        // 1.检查手机号是否合法
        if(RegexUtils.isPhoneInvalid(phone)){
            // 2.不合法直接返回错误信息
            return Result.fail("手机号不合法");
        }

        // 3.生成验证码
        String code = RandomUtil.randomString(6);

        // 4.保存验证码到Redis中,并设置5分钟过期时间
        //session.setAttribute("code",code);
        stringRedisTemplate.opsForValue().set(RedisConstants.LOGIN_CODE_KEY + phone,code,RedisConstants.LOGIN_CODE_TTL, TimeUnit.MINUTES);

        // 5.发送验证码
        log.info("验证码为  {}",code);

        return Result.ok("发送验证成功");
    }

    /**
     * 实现用户登录
     * @param loginForm
     * @param session
     * @return
     */
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        String phone = loginForm.getPhone();
        String code = loginForm.getCode();
        // 1.检查手机号是否合法
        if(RegexUtils.isPhoneInvalid(phone)){
            return Result.fail("手机号不合法");
        }
        // 2.检查验证码是否正确
        //String cacheCode = (String) session.getAttribute("code");
        String cacheCode = stringRedisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY + phone);
        if(cacheCode == null || !cacheCode.equals(code)){
            return Result.fail("验证码错误");
        }

        // 3.根据手机号查询到用户信息
        User user = query().eq("phone", phone).one();

        // 4.如果是新用户，就进行注册
        if(user == null){
            user = createNewUser(phone);
        }

        // 5.如果是老用户，就将用户信息存放到redis中
        //session.setAttribute("user",user);
        // 6.生成随机token作为key，用户信息保存到redis中，使用Hash结构进行存储
        String token = UUID.randomUUID().toString();
        UserDTO userDTO = new UserDTO();
        userDTO.setId(user.getId());
        userDTO.setNickName(user.getNickName());
        userDTO.setIcon(user.getIcon());
        Map<String, Object> userDtoMap = beanToMap(userDTO);
        // 以hash形式存储userDto，并设置了30分钟有效期
        stringRedisTemplate.opsForHash().putAll(RedisConstants.LOGIN_USER_KEY + token,userDtoMap);
        stringRedisTemplate.expire(RedisConstants.LOGIN_USER_KEY + token,RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);


        // 7.将token返回前端

        return Result.ok(token);
    }


    /**
     * 将UserDTO对象转换为Map对象，
     * @param userDTO
     * @return
     */
    private Map<String,Object> beanToMap(UserDTO userDTO){
        HashMap<String, Object> map = new HashMap<>();
        map.put("id",String.valueOf(userDTO.getId()));
        map.put("nickName",userDTO.getNickName());
        map.put("icno",userDTO.getIcon());
        return map;
    }

    /**
     * 创建一个新用户
     * @param phone 用户手机号
     * @return
     */
    User createNewUser(String phone){
        User user = new User();
        user.setPhone(phone);
        user.setNickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));
        save(user);
        return user;
    }
}
