package com.studypilot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.nio.file.Files;
import java.nio.file.Path;

@SpringBootApplication
public class StudyPilotApplication {

    public static void main(String[] args) throws Exception {
        // SQLite 不会自动创建父目录，先确保 data 目录存在
        Files.createDirectories(Path.of("./data"));
        SpringApplication.run(StudyPilotApplication.class, args);
    }
}
