package ru.tyumentsev.cryptopredator.indicatorvirginbot.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.Getter;
import lombok.Setter;
import okhttp3.OkHttpClient;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import retrofit2.Retrofit;
import retrofit2.converter.jackson.JacksonConverterFactory;
import ru.tyumentsev.cryptopredator.commons.service.CacheServiceClient;
import ru.tyumentsev.cryptopredator.commons.service.DataService;

@Setter
@Getter
@Configuration
@ConfigurationProperties(prefix = "databaseconfig")
public class DatabaseConfig {

    String dataKeeperURL;

    @Bean
    public DataService dataService() {
        return new DataService(new Retrofit.Builder()
                .baseUrl(String.format(dataKeeperURL))
                .addConverterFactory(JacksonConverterFactory.create(new ObjectMapper().registerModule(new JavaTimeModule())))
                .client(new OkHttpClient.Builder().build())
                .build().create(CacheServiceClient.class)
        );
    }
}