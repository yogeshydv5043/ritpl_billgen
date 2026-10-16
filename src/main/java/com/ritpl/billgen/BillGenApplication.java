package com.ritpl.billgen;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BillGenApplication {
    public static void main(String[] args) {
        SpringApplication.run(BillGenApplication.class, args);
    }
}
