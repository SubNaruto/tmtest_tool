package com.example.tm_test;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
@MapperScan("com.example.tm_test.mapper")
public class TmTestApplication {
    public static void main(String[] args) {
        //SpringApplication.run(TmTestApplication.class, args);
        ConfigurableApplicationContext context = SpringApplication.run(TmTestApplication.class, args);

        // 从Spring容器中获取StartController实例
        StartController startController = context.getBean(StartController.class);

        // 调用StartController实例的start()方法
        startController.start();
    }

}
