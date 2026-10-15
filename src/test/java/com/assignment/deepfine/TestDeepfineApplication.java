package com.assignment.deepfine;

import org.springframework.boot.SpringApplication;

public class TestDeepfineApplication {
    public static void main(String[] args) {
        SpringApplication.from(DeepfineApplication::main)
                .with(TestcontainersConfiguration.class)
                .run(args);
    }
}
