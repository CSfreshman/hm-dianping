package com.hmdp.utils;

public interface ILock {

    /**
     * 获取锁
     */
    public boolean tryLock(Long timeout);

    /**
     * 释放锁
     */
    public void unlock();
}
