package ru.tyumentsev.cryptopredator.statekeeper.configuration;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@FieldDefaults(level = AccessLevel.PROTECTED, makeFinal = true)
@Slf4j
@SuppressWarnings("unused")
public class SecurityConfig {

    PasswordEncoder pwdEncoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();

    String quantPwd;

    public SecurityConfig(@Value("${applicationconfig.security.adminPassword}") String quantPwd) {
        this.quantPwd = quantPwd;
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests((authz) -> authz.requestMatchers("/**")
                        .hasRole("POWER")
                        .anyRequest().authenticated())
                .httpBasic(Customizer.withDefaults());
        return http.build();
    }

    @Bean
    UserDetailsService authentication() {
        UserDetails user = User.builder()
                .username("predator")
                .password(pwdEncoder.encode(quantPwd))
                .roles("USER", "POWER")
                .build();

        return new InMemoryUserDetailsManager(user);
    }
}
