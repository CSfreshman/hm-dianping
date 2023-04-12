package com.hmdp.config;

import com.hmdp.interceptor.LoginInterceptor;
import com.hmdp.interceptor.RefreshTokenInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurationSupport;

import javax.annotation.Resource;

@Configuration
public class MvcConfig extends WebMvcConfigurationSupport {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    protected void addInterceptors(InterceptorRegistry registry) {
        // 刷新token拦截器 优先级为0（最高）
        registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate)).order(0);
        // 登录状态拦截器 优先级为1
        registry.addInterceptor(new LoginInterceptor(stringRedisTemplate)).excludePathPatterns(
                "/shop/**",
                "/voucher/**",
                "/shop-type/**",
                "/upload/**",
                "/blog/hot",
                "/user/code",
                "/user/login"
        ).order(1);
    }
}
