package com.dayclose.commitment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.dayclose.commitment", "com.dayclose.web"})
public class CommitmentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CommitmentServiceApplication.class, args);
    }
}
