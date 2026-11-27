package com.example.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class HorlogeConfig {
    @Bean
    Clock horloge() {
        return Clock.system(ZoneId.of("Africa/Casablanca"));
    }
}
