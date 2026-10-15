package com.investops;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class InvestOpsApplication {

    public static void main(String[] args) {
        SpringApplication.run(InvestOpsApplication.class, args);
    }
}
