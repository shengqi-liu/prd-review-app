package com.prdreview.bootstrap.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 3 文档配置。
 *
 * <p>访问地址：
 * <ul>
 *   <li>Swagger UI：<a href="http://localhost:8080/swagger-ui/index.html">/swagger-ui/index.html</a></li>
 *   <li>OpenAPI JSON：<a href="http://localhost:8080/v3/api-docs">/v3/api-docs</a></li>
 * </ul>
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("AI 产品方案评审系统")
                .version("v1.0.0")
                .description("基于多角色 Agent + 业务知识库的智能 PRD 评审平台")
                .contact(new Contact()
                    .name("产品团队")
                    .email("product@company.com")));
    }
}
