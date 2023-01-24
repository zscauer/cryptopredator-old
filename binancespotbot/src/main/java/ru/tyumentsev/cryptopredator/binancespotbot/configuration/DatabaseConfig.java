package ru.tyumentsev.cryptopredator.binancespotbot.configuration;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Scope;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.jedis.JedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.Collections;

@Setter
@Getter
@Configuration
@ConfigurationProperties(prefix = "databaseconfig")
public class DatabaseConfig {

    String redisHost;
    int redisPort;
    String redisPassword;
    String dataKeeperAddress;
    String dataKeeperPort;

    @Bean(name = "jedisConnectionFactory")
    JedisConnectionFactory jedisConnectionFactory() {
        RedisStandaloneConfiguration redisStandaloneConfiguration = new RedisStandaloneConfiguration(redisHost, redisPort);
        if (!redisPassword.isBlank()) {
            redisStandaloneConfiguration.setPassword(RedisPassword.of(redisPassword));
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

//    @Bean(name = "dataKeeperWebClient")
//    @Scope("Prototype")
//    public WebClient dataKeeperWebClient() {
//        return WebClient.builder()
//                .baseUrl(String.format("http://%s:%s", dataKeeperAddress, dataKeeperPort))
////                .defaultCookie("cookieKey", "cookieValue")
//                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
//                .defaultUriVariables(Collections.singletonMap("url", String.format("http://%s:%s", dataKeeperAddress, dataKeeperPort)))
//                .build();
//    }
}
