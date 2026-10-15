package com.assignment.deepfine.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.type.LogicalType;

@Configuration(proxyBeanMethods = false)
public class JsonConfiguration {
    @Bean
    JsonMapperBuilderCustomizer strictRequestTypes() {
        // amount=1.5를 1로 자르거나 amount="1"을 숫자로 자동변환 하지 않는다.
        // 상품명도 숫자·불리언을 문자열로 자동 변환하지 않는다.
        return builder ->
                builder.withCoercionConfig(
                                LogicalType.Integer,
                                config ->
                                        config.setCoercion(
                                                        CoercionInputShape.Float,
                                                        CoercionAction.Fail)
                                                .setCoercion(
                                                        CoercionInputShape.String,
                                                        CoercionAction.Fail))
                        .withCoercionConfig(
                                LogicalType.Textual,
                                config ->
                                        config.setCoercion(
                                                        CoercionInputShape.Integer,
                                                        CoercionAction.Fail)
                                                .setCoercion(
                                                        CoercionInputShape.Float,
                                                        CoercionAction.Fail)
                                                .setCoercion(
                                                        CoercionInputShape.Boolean,
                                                        CoercionAction.Fail));
    }
}
