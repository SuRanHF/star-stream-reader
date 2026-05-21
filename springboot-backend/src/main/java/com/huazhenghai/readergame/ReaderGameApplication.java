package com.huazhenghai.readergame;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 全知读者视角 - 文字冒险游戏后端
 * <p>
 * Spring Boot 3 启动入口.
 * 使用 MyBatis-Plus 操作 MySQL, 提供 RESTful API 给前端调用.
 * </p>
 *
 * @author Hua Zheng Hai
 */
@SpringBootApplication
@MapperScan("com.huazhenghai.readergame.mapper")
public class ReaderGameApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReaderGameApplication.class, args);
    }
}
