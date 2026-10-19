package com.example.droolspoc.config;

import leads.ruleengine.core.approval.BearerTokenPassthroughFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

/**
 * Spring Security arrives transitively with the changeboard approval library. Without this
 * config Boot would lock every endpoint behind a generated password.
 *
 * <p>Everything stays open, exactly as before. The only addition is the bearer-token pass-through:
 * when a caller sends {@code Authorization: Bearer ...} the token is placed in the security
 * context (not verified, never rejected) so changeboard can forward it to Centrino.</p>
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a.anyRequest().permitAll())
                .addFilterBefore(new BearerTokenPassthroughFilter(), AnonymousAuthenticationFilter.class)
                .build();
    }
}
