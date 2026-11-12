package com.openexchange.spot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SpotTradingServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(SpotTradingServiceApplication.class, args);
    }
}
