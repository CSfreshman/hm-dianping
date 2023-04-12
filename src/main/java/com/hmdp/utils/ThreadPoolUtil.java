package com.hmdp.utils;

import org.springframework.stereotype.Component;

import java.util.concurrent.*;

/**
 * 自定义单例线程池，5个核心线程，10个最大线程
 * 使用双重检验锁的懒汉式创建，保证线程安全
 */
@Component
public class ThreadPoolUtil {

    private static volatile ThreadPoolExecutor threadPoolExecutor;

    private ThreadPoolUtil(){}

    public static ThreadPoolExecutor getThreadPool(){
        if(threadPoolExecutor == null){
            synchronized (ThreadPoolUtil.class){
                if(threadPoolExecutor == null){
                    threadPoolExecutor = new ThreadPoolExecutor(
                            5, 10, 1, TimeUnit.MINUTES, new LinkedBlockingQueue<>()
                    );
                }
            }
        }
        return threadPoolExecutor;
    }
}
