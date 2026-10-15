package com.assignment.deepfine.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfiguration {
    @Bean
    OpenAPI inventoryOpenApi() {
        return new OpenAPI()
                .info(
                        new Info()
                                .title("재고 관리 API")
                                .version("v1")
                                .description(
                                        "입고 → 재고 조회 → 출고 → 이력 조회 순서로 테스트할 수 있습니다. "
                                                + "입출고와 이력 저장은 같은 트랜잭션으로 처리됩니다. "
                                                + "요청 재전송에 대한 멱등성은 지원하지 않습니다."));
    }
}
