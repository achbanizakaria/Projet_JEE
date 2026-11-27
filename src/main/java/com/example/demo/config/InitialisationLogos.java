package com.example.demo.config;

import com.example.demo.service.EquipeService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InitialisationLogos implements ApplicationRunner {
    private final EquipeService equipes;

    @Override
    public void run(ApplicationArguments args) {
        equipes.regulariserAnciensLogos();
    }
}
