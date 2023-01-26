package ru.tyumentsev.cryptopredator.datakeeper.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;

@Setter
@Getter
@Configuration
@ConfigurationProperties(prefix = "redis")
public class RedisConfig {

    String host;
    int port;
    String password;

    @Bean(name = "jedisConnectionFactory")
    JedisConnectionFactory jedisConnectionFactory() {
        RedisStandaloneConfiguration redisStandaloneConfiguration = new RedisStandaloneConfiguration(host, port);
        if (!password.isBlank()) {
            redisStandaloneConfiguration.setPassword(RedisPassword.of(password));
        }
        return new JedisConnectionFactory(redisStandaloneConfiguration);

    }

    @Bean
    @DependsOn("jedisConnectionFactory")
    public RedisTemplate<String, Object> redisTemplate() {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(jedisConnectionFactory());
        return template;
    }
}