package com.huazhenghai.readergame.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Knife4j / Swagger 接口文档配置.
 * <p>
 * 前端可通过 /doc.html 访问增强版 Swagger UI,
 * 也可通过 /swagger-ui.html 访问原生 Swagger UI.
 * </p>
 */
@Configuration
public class Knife4jConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("全知读者视角 Reader Game API")
                        .description("文字冒险游戏后端 RESTful API 文档")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Hua Zheng Hai")));
    }
}
