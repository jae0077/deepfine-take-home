package com.assignment.deepfine;

import static org.assertj.core.api.Assertions.assertThat;

import com.assignment.deepfine.exception.InventoryException;
import com.assignment.deepfine.service.InventoryService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class InventoryApiTests {
    @LocalServerPort int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired InventoryService service;

    private final HttpClient client =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final JsonMapper mapper = JsonMapper.builder().build();

    @BeforeEach
    void 테스트_데이터를_격리한다() {
        jdbc.update("DELETE FROM inventory_history");
        jdbc.update("DELETE FROM products");
    }

    @Test
    void 신규_상품_입고_성공_상품을_등록하고_재고와_이력을_저장한다() throws Exception {
        var response = inbound(1, "상품 A", 10);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(json(response).get("id").asLong()).isEqualTo(1);
        assertThat(json(response).get("name").asString()).isEqualTo("상품 A");
        assertThat(json(response).get("quantity").asLong()).isEqualTo(10);
        assertThat(service.stock(1).quantity()).isEqualTo(10);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM products", Long.class)).isEqualTo(1);
        assertThat(getHistoryCount()).isEqualTo(1);
    }

    @Test
    void 기존_상품_입고_성공_동일한_상품코드와_상품명이면_재고를_증가시킨다() throws Exception {
        assertThat(inbound(1, "상품 A", 10).statusCode()).isEqualTo(200);
        var response = inbound(1, "상품 A", 5);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(json(response).get("quantity").asLong()).isEqualTo(15);
        assertThat(json(response).get("name").asString()).isEqualTo("상품 A");
        assertThat(getHistoryCount()).isEqualTo(2);
    }

    @Test
    void 입고_실패_수량이_0_음수_null_또는_누락이면_400을_반환한다() throws Exception {
        for (String amount : List.of("0", "-1", "null")) {
            assertError(
                    post(
                            "/inventory/inbounds",
                            "{\"id\":1,\"name\":\"상품\",\"amount\":" + amount + "}"),
                    400,
                    "INVALID_AMOUNT");
        }
        assertError(
                post("/inventory/inbounds", "{\"id\":1,\"name\":\"상품\"}"), 400, "INVALID_AMOUNT");
        assertThat(getHistoryCount()).isZero();
    }

    @Test
    void 입고_실패_상품_ID나_상품명이_유효하지_않으면_400을_반환한다() throws Exception {
        for (String id : List.of("0", "-1", "null")) {
            assertError(
                    post("/inventory/inbounds", "{\"id\":" + id + ",\"name\":\"상품\",\"amount\":1}"),
                    400,
                    "INVALID_REQUEST");
        }
        for (String name : List.of("\"\"", "\"   \"", "null", "\"" + "가".repeat(201) + "\"")) {
            assertError(
                    post("/inventory/inbounds", "{\"id\":1,\"name\":" + name + ",\"amount\":1}"),
                    400,
                    "INVALID_REQUEST");
        }
    }

    @Test
    void 입고_실패_입고_수량_타입이_잘못되면_400을_반환한다() throws Exception {
        for (String amount : List.of("1.5", "\"1\"", "true")) {
            assertError(
                    post(
                            "/inventory/inbounds",
                            "{\"id\":1,\"name\":\"상품\",\"amount\":" + amount + "}"),
                    400,
                    "INVALID_REQUEST");
        }
    }

    @Test
    void 입고_실패_누적_재고가_BIGINT_최댓값을_넘으면_409이고_기존_재고를_유지한다() throws Exception {
        assertThat(inbound(1, "상품", Long.MAX_VALUE).statusCode()).isEqualTo(200);
        assertError(inbound(1, "상품", 1), 409, "STOCK_LIMIT_EXCEEDED");
        assertThat(service.stock(1).quantity()).isEqualTo(Long.MAX_VALUE);
        assertThat(getHistoryCount()).isEqualTo(1);
    }

    @Test
    void 입고_이력_저장_실패_신규_상품_등록과_기존_재고_증가를_롤백한다() throws Exception {
        inbound(1, "상품", 1000);
        // 임시 제약을 추가해 이력 저장을 실패시키고 재고 변경도 롤백되는지 확인한다.
        jdbc.execute(
                "ALTER TABLE inventory_history ADD CONSTRAINT test_reject_amount CHECK (amount <> 777)");
        try {
            assertError(inbound(2, "신규", 777), 500, "INTERNAL_ERROR");
            assertError(inbound(1, "상품", 777), 500, "INTERNAL_ERROR");
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT count(*) FROM products WHERE id = 2", Long.class))
                    .isZero();
            assertThat(service.stock(1).quantity()).isEqualTo(1000);
            assertThat(getHistoryCount()).isEqualTo(1);
        } finally {
            // 테스트용 임시 제약 제거
            jdbc.execute("ALTER TABLE inventory_history DROP CONSTRAINT test_reject_amount");
        }
    }

    @Test
    void 신규_상품_동시_입고_상품은_하나만_등록되고_모든_입고를_반영한다() throws Exception {
        parallel(
                20,
                index -> {
                    service.inbound(1, "상품", 1);
                    return true;
                });
        assertThat(service.stock(1).quantity()).isEqualTo(20);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM products", Long.class)).isEqualTo(1);
        assertThat(getHistoryCount()).isEqualTo(20);
    }

    @Test
    void 기존_상품_동시_입고_모든_입고_수량과_이력을_누락_없이_저장한다() throws Exception {
        service.inbound(1, "상품", 20);
        parallel(
                20,
                index -> {
                    service.inbound(1, "상품", 1);
                    return true;
                });
        assertThat(service.stock(1).quantity()).isEqualTo(40);
        assertThat(getHistoryCount()).isEqualTo(21);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT sum(amount) FROM inventory_history WHERE product_id = 1",
                                Long.class))
                .isEqualTo(40);
    }

    @Test
    void 입고_실패_동일_ID의_상품명이_다르면_409이고_재고와_이력을_유지한다() throws Exception {
        inbound(1, "상품 A", 10);
        assertError(inbound(1, "상품 B", 5), 409, "PRODUCT_NAME_MISMATCH");
        assertError(inbound(1, "상품 A ", 5), 409, "PRODUCT_NAME_MISMATCH");
        assertThat(service.stock(1).name()).isEqualTo("상품 A");
        assertThat(service.stock(1).quantity()).isEqualTo(10);
        assertThat(getHistoryCount()).isEqualTo(1);
    }

    @Test
    void 신규_상품_동시_입고_동일_ID에_다른_이름이면_등록된_이름의_요청만_성공한다() throws Exception {
        List<Boolean> results =
                parallel(
                        20,
                        index -> {
                            try {
                                service.inbound(1, index % 2 == 0 ? "상품 A" : "상품 B", 1);
                                return true;
                            } catch (InventoryException exception) {
                                assertThat(exception.reason())
                                        .isEqualTo(InventoryException.Reason.PRODUCT_NAME_MISMATCH);
                                return false;
                            }
                        });
        assertThat(results.stream().filter(Boolean::booleanValue).count()).isEqualTo(10);
        assertThat(service.stock(1).quantity()).isEqualTo(10);
        assertThat(service.stock(1).name()).isIn("상품 A", "상품 B");
        assertThat(getHistoryCount()).isEqualTo(10);
    }

    @Test
    void 출고_성공_재고를_차감하고_출고_이력을_저장한다() throws Exception {
        inbound(1, "상품 A", 10);
        var response = outbound(1, 3);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(json(response).get("id").asLong()).isEqualTo(1);
        assertThat(json(response).get("name").asString()).isEqualTo("상품 A");
        assertThat(json(response).get("quantity").asLong()).isEqualTo(7);
        assertThat(service.stock(1).quantity()).isEqualTo(7);
        assertThat(getHistoryCount()).isEqualTo(2);
        var history = service.history(1, null, 1).getFirst();
        assertThat(history.type()).isEqualTo("OUTBOUND");
        assertThat(history.amount()).isEqualTo(3);
        assertThat(history.quantityAfter()).isEqualTo(7);
        assertThat(history.createdAt()).isNotNull();
    }

    @Test
    void 전량_출고_성공_출고하면_재고가_0이_된다() throws Exception {
        inbound(1, "상품", Long.MAX_VALUE);
        var response = outbound(1, Long.MAX_VALUE);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(json(response).get("quantity").asLong()).isZero();
        assertThat(service.stock(1).quantity()).isZero();
        assertThat(getHistoryCount()).isEqualTo(2);
    }

    @Test
    void 출고_실패_재고가_부족하면_409이고_재고와_이력을_유지한다() throws Exception {
        inbound(1, "상품", 2);
        assertError(outbound(1, 3), 409, "INSUFFICIENT_STOCK");
        assertThat(service.stock(1).quantity()).isEqualTo(2);
        assertThat(getHistoryCount()).isEqualTo(1);
    }

    @Test
    void 출고_실패_미등록_상품이면_404이고_상품과_이력을_생성하지_않는다() throws Exception {
        assertError(outbound(1, 1), 404, "PRODUCT_NOT_FOUND");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM products", Long.class)).isZero();
        assertThat(getHistoryCount()).isZero();
    }

    @Test
    void 출고_실패_수량이_0_음수_null_또는_누락이면_400을_반환한다() throws Exception {
        inbound(1, "상품", 10);
        for (String amount : List.of("0", "-1", "null")) {
            assertError(
                    post("/inventory/outbounds", "{\"id\":1,\"amount\":" + amount + "}"),
                    400,
                    "INVALID_AMOUNT");
        }
        assertError(post("/inventory/outbounds", "{\"id\":1}"), 400, "INVALID_AMOUNT");
        assertThat(service.stock(1).quantity()).isEqualTo(10);
        assertThat(getHistoryCount()).isEqualTo(1);
    }

    @Test
    void 출고_실패_상품_ID가_0_음수_null_또는_누락이면_400을_반환한다() throws Exception {
        inbound(1, "상품", 10);
        for (String id : List.of("0", "-1", "null")) {
            assertError(
                    post("/inventory/outbounds", "{\"id\":" + id + ",\"amount\":1}"),
                    400,
                    "INVALID_REQUEST");
        }
        assertError(post("/inventory/outbounds", "{\"amount\":1}"), 400, "INVALID_REQUEST");
        assertThat(service.stock(1).quantity()).isEqualTo(10);
        assertThat(getHistoryCount()).isEqualTo(1);
    }

    @Test
    void 출고_실패_수량_타입이_잘못되면_400을_반환한다() throws Exception {
        inbound(1, "상품", 10);
        for (String amount : List.of("1.5", "\"1\"", "true", "9223372036854775808")) {
            assertError(
                    post("/inventory/outbounds", "{\"id\":1,\"amount\":" + amount + "}"),
                    400,
                    "INVALID_REQUEST");
        }
        assertThat(service.stock(1).quantity()).isEqualTo(10);
        assertThat(getHistoryCount()).isEqualTo(1);
    }

    @Test
    void 출고_이력_저장_실패_재고_차감을_롤백한다() throws Exception {
        inbound(1, "상품", 1000);
        // 출고는 가능하지만 이력 INSERT만 실패하도록 테스트 DB에 임시 제약을 추가한다.
        jdbc.execute(
                "ALTER TABLE inventory_history ADD CONSTRAINT test_reject_outbound CHECK (amount <> 777)");
        try {
            assertError(outbound(1, 777), 500, "INTERNAL_ERROR");
            assertThat(service.stock(1).quantity()).isEqualTo(1000);
            assertThat(getHistoryCount()).isEqualTo(1);
        } finally {
            // 테스트용 임시 제약 제거
            jdbc.execute("ALTER TABLE inventory_history DROP CONSTRAINT test_reject_outbound");
        }
    }

    @Test
    void 동시_출고_재고를_초과하면_가능한_요청만_성공하고_음수가_되지_않는다() throws Exception {
        service.inbound(1, "상품", 10);
        List<Boolean> results =
                parallel(
                        20,
                        index -> {
                            try {
                                service.outbound(1, 1);
                                return true;
                            } catch (InventoryException exception) {
                                assertThat(exception.reason())
                                        .isEqualTo(InventoryException.Reason.INSUFFICIENT_STOCK);
                                return false;
                            }
                        });
        assertThat(results.stream().filter(Boolean::booleanValue).count()).isEqualTo(10);
        assertThat(results.stream().filter(result -> !result).count()).isEqualTo(10);
        assertThat(service.stock(1).quantity()).isZero();
        assertThat(getHistoryCount()).isEqualTo(11);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM inventory_history WHERE type = 'OUTBOUND'",
                                Long.class))
                .isEqualTo(10);
    }

    @Test
    void 동시_입출고_성공한_변경_합계와_최종_재고와_이력이_일치한다() throws Exception {
        service.inbound(1, "상품", 20);
        parallel(
                20,
                index -> {
                    if (index % 2 == 0) service.inbound(1, "상품", 1);
                    else service.outbound(1, 1);
                    return true;
                });
        assertThat(service.stock(1).quantity()).isEqualTo(20);
        assertThat(getHistoryCount()).isEqualTo(21);
        assertThat(
                        jdbc.queryForObject(
                                """
                SELECT sum(CASE WHEN type = 'INBOUND' THEN amount ELSE -amount END)
                FROM inventory_history WHERE product_id = 1
                """,
                                Long.class))
                .isEqualTo(20);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM inventory_history WHERE type = 'OUTBOUND'",
                                Long.class))
                .isEqualTo(10);
    }

    // 모든 작업을 시작 신호로 함께 풀어 경합을 만들고 Future로 작업 스레드의 실패를 회수한다.
    private List<Boolean> parallel(int count, java.util.function.IntFunction<Boolean> action)
            throws Exception {
        var executor = Executors.newFixedThreadPool(count);
        // 커넥션을 얻기 전에 대기하므로 스레드 수가 커넥션 풀보다 커도 시작 대기가 풀을 점유하지 않는다.
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < count; i++) {
                int index = i;
                futures.add(
                        executor.submit(
                                () -> {
                                    if (!start.await(10, TimeUnit.SECONDS))
                                        throw new IllegalStateException("start timeout");
                                    return action.apply(index);
                                }));
            }
            start.countDown();
            List<Boolean> results = new ArrayList<>();
            // get에 시간 제한을 두어 교착이나 예상치 못한 대기로 테스트가 무한히 멈추지 않게 한다.
            for (Future<Boolean> future : futures) results.add(future.get(30, TimeUnit.SECONDS));
            return results;
        } finally {
            start.countDown();
            // 성공·실패와 무관하게 작업을 종료해 다음 테스트에 스레드를 남기지 않는다.
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private long getHistoryCount() {
        return jdbc.queryForObject("SELECT count(*) FROM inventory_history", Long.class);
    }

    private HttpResponse<String> inbound(long id, String name, long amount) throws Exception {
        return post(
                "/inventory/inbounds",
                "{\"id\":" + id + ",\"name\":\"" + name + "\",\"amount\":" + amount + "}");
    }

    private HttpResponse<String> outbound(long id, long amount) throws Exception {
        return post("/inventory/outbounds", "{\"id\":" + id + ",\"amount\":" + amount + "}");
    }

    // Mock Controller 호출이 아니라 실제 서버에 JSON 요청을 보낸다.
    private HttpResponse<String> post(String path, String body) throws Exception {
        return client.send(
                HttpRequest.newBuilder(uri(path))
                        .timeout(Duration.ofSeconds(20))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://localhost:" + port + "/api/v1" + path);
    }

    private JsonNode json(HttpResponse<String> response) {
        return mapper.readTree(response.body());
    }

    private void assertError(HttpResponse<String> response, int status, String code) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        assertThat(json(response).get("code").asString()).isEqualTo(code);
        assertThat(json(response).get("message").asString()).isNotBlank();
    }
}
