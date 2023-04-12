package com.hmdp.interceptor;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class RefreshTokenInterceptor implements HandlerInterceptor {
    private StringRedisTemplate stringRedisTemplate;

    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate){
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        System.out.println("===== 刷新token拦截器 =====");
        // 1.获得token
        //HttpSession session = request.getSession();
        String token = request.getHeader("authorization");
        if(StringUtils.isEmpty(token)){
            //token为空，直接放行，交给后面的拦截器去处理
            return true;
        }
        // 2.从Redis中获得用户信息
        //User user = (User) session.getAttribute("user");
        Map<Object, Object> userDtoMap = stringRedisTemplate.opsForHash().entries(RedisConstants.LOGIN_USER_KEY + token);

        System.out.println(userDtoMap);
        // 3.判断用户是否存在
        if(userDtoMap == null){
            // 4.不存在就放行，后面的拦截器会处理的
            return true;
        }

        // 5.保存用户信息到ThreadLocal
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userDtoMap, new UserDTO(), true);
//        userDTO.setId(user.getId());
//        userDTO.setNickName(user.getNickName());
//        userDTO.setIcon(user.getIcon());
        System.out.println(userDTO.toString());
        UserHolder.saveUser(userDTO);

        // 6.刷新token有效期
        stringRedisTemplate.expire(RedisConstants.LOGIN_USER_KEY+token, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);

        // 7.放行
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        UserHolder.removeUser();
    }
}
