package com.example.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/css/**", "/js/**", "/images/**", "/error").permitAll()
                        .requestMatchers("/connexion", "/inscription").permitAll()
                        .requestMatchers("/organisateur/**").hasRole("ORGANISATEUR")
                        .requestMatchers("/capitaine/**").hasRole("CAPITAINE")
                        .requestMatchers("/joueur/**").hasRole("JOUEUR")
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/connexion")
                        .loginProcessingUrl("/connexion")
                        .usernameParameter("email")
                        .passwordParameter("motDePasse")
                        .defaultSuccessUrl("/", true)
                        .failureUrl("/connexion?erreur")
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/deconnexion")
                        .logoutSuccessUrl("/connexion?deconnecte")
                        .permitAll())
                .addFilterBefore(new ChangementMotDePasseFilter(), AuthorizationFilter.class);
        return http.build();
    }
}