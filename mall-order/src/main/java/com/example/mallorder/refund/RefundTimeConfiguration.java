package com.example.mallorder.refund;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class RefundTimeConfiguration {

    @Bean
    Clock refundClock() {
        return Clock.system(ZoneId.of("Asia/Shanghai"));
    }
}
