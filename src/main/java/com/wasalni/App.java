package com.wasalni;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class App {
    public static void main(String[] args) {
        System.setProperty("spring.datasource.url", 
            "jdbc:mysql://roundhouse.proxy.rlwy.net:43646/railway?useSSL=false&allowPublicKeyRetrieval=true");
        System.setProperty("spring.datasource.username", "root");
        System.setProperty("spring.datasource.password", "Wasalni2030");
        System.setProperty("spring.datasource.driver-class-name", "com.mysql.cj.jdbc.Driver");
        System.setProperty("spring.jpa.hibernate.ddl-auto", "none");
        SpringApplication.run(App.class, args);
    }
}