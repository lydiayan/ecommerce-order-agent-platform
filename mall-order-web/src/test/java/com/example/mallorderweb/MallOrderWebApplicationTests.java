package com.example.mallorderweb;

import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;


import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;

 class MallOrderWebApplicationTests {

    @Resource(name="mallExecutor")
    private Executor executor;
    @Test
    void contextLoads() {


    }

    @Test
    void ThreadPoolTest() {
        CountDownLatch countDownLatch=new CountDownLatch(10);
        List<String> list= Arrays.asList("1","2","3","4","5","6","7","8","9","10");
        list.forEach(item->{
            executor.execute(()->{
                try {
                    Thread.sleep(3000L);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                System.out.println(item);
            });
        });
    }


    public int[] sz(int i,int j,int k){
        if (k==3){
            return new int[]{1,2,3};
        }
        return new int[]{1,2,3};
    }

}
