package com.dayclose.voice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan(basePackages = {"com.dayclose.voice", "com.dayclose.web"})
public class VoiceAgentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(VoiceAgentServiceApplication.class, args);
    }
}
