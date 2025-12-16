package com.medsoft.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.nio.file.Paths;

@Configuration
public class VoskConfig {

    @Bean
    public String voskModelPath() {
        String modelPath = Paths.get("src", "main", "resources",
                "vosk-model-small-ru-0.22").toAbsolutePath().toString();

        System.out.println("Модель Vosk загружена из: " + modelPath);
        return modelPath;
    }
}
