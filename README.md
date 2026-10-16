# 재고 관리 시스템 MVP

Java / Spring Boot / PostgreSQL 기반 재고 관리 API 사전 과제입니다.

## 구조

```text
src/main/java/com/assignment/deepfine/
├── controller  # HTTP 요청 검증과 응답
├── service     # 업무 오류 분류, 트랜잭션, 엔티티 → DTO 변환
├── repository  # JPA 조회·이력 저장과 원자적 재고 변경 SQL
├── entity      # 상품·입출고 이력
├── dto         # API 요청·응답
├── exception   # 업무 예외와 일관된 오류 응답
└── config      # JSON 타입 검증과 Swagger
```

## 실행

```sh
docker compose up -d
docker compose logs -f app
```

DB healthcheck 성공 후 앱을 시작하고 Flyway가 DDL을 적용합니다.  
`Started DeepfineApplication` 로그를 확인한 후 접속하세요.  
컨테이너의 `Started` 표시는 HTTP 준비 완료를 의미하지 않습니다.

- API: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui/index.html
- OpenAPI JSON: http://localhost:8080/v3/api-docs

Redis·Kafka 및 외부 서비스는 필요하지 않습니다.

### 환경 변수

기본값으로 바로 실행할 수 있습니다

| 변수 | 기본값 | 의미 |
|---|---|---|
| APP_PORT | 8080 | 호스트 API 포트 |
| DB_PORT | 5432 | 호스트 PostgreSQL 포트 |
| DB_NAME | inventory | DB 이름 |
| DB_USERNAME | inventory | DB 계정 |
| DB_PASSWORD | inventory_dev | 개발 전용 비밀번호 |

### 데이터 유지·초기화

```sh
docker compose ps -a
docker compose logs --tail=100 app db

# 컨테이너 종료, 데이터 유지
docker compose down

# 해당 프로젝트의 DB 데이터 전체 삭제 후 재실행
docker compose down -v
docker compose up -d
```
데이터는 Compose의 `postgres_data` 볼륨에 보존됩니다.

## API

상품 ID는 클라이언트가 지정하는 상품코드로 양수만 입력 가능합니다.  
상품명은 공백만 입력할 수 없으며 최대 200자입니다.  
입출고 수량은 1부터 9,223,372,036,854,775,807까지의 JSON 정수입니다.  
소수·문자열 수량은 자동 변환하지 않습니다.  
이름은 trim 없이 정확히 비교하므로 끝 공백도 불일치입니다.  

| 메서드 | 경로 | 기능 |
|---|---|---|
| POST | `/api/v1/inventory/inbounds` | 신규/기존 상품 입고 |
| POST | `/api/v1/inventory/outbounds` | 출고 |
| GET | `/api/v1/products/stock` | 상품 ID 오름차순 재고 목록 |
| GET | `/api/v1/products/{id}/stock` | 개별 재고 |
| GET | `/api/v1/products/{id}/history` | 이력 ID 내림차순 이력 |
| PATCH | `/api/v1/products/{id}/name` | 상품명 변경 |

### 입고·출고·재고 조회

```sh
curl -i -X POST http://localhost:8080/api/v1/inventory/inbounds \
  -H 'Content-Type: application/json' \
  -d '{"id":1,"name":"상품 A","amount":10}'
# 200: {"id":1,"name":"상품 A","quantity":10}

curl -i -X POST http://localhost:8080/api/v1/inventory/outbounds \
  -H 'Content-Type: application/json' \
  -d '{"id":1,"amount":3}'
# 200: {"id":1,"name":"상품 A","quantity":7}

curl -i http://localhost:8080/api/v1/products/1/stock
# 200: {"id":1,"name":"상품 A","quantity":7}

curl -i -X PATCH http://localhost:8080/api/v1/products/1/name \
  -H 'Content-Type: application/json' -d '{"name":"변경한 상품명"}'
# 200: {"id":1,"name":"변경한 상품명","quantity":7}
```

### 페이지·이력 조회

```sh
curl 'http://localhost:8080/api/v1/products/stock?size=20'
# {
#   "items":[{"id":1,"name":"변경한 상품명","quantity":7}],
#   "nextCursor": null
# }

curl 'http://localhost:8080/api/v1/products/1/history?size=20'
# {
#   "items": [{
#     "id":2,
#     "productId":1,
#     "type":"OUTBOUND",
#     "amount":3,
#     "quantityAfter":7,
#     "createdAt":"2026-10-15T21:25:36.965137Z"
#   }, {
#     "id":1,
#     "productId":1,
#     "type":"INBOUND",
#     "amount":10,
#     "quantityAfter":10,
#     "createdAt":"2026-10-15T21:25:30.000000Z"
#   }],
#   "nextCursor": null
# }
```

### 오류

```json
{"code":"INSUFFICIENT_STOCK","message":"재고가 부족합니다."}
```

| HTTP | code | 원인 |
|---|---|---|
| 400 | INVALID_AMOUNT | 수량 누락·null·0·음수 |
| 400 | INVALID_REQUEST | ID·이름·페이지 검증 실패, JSON 타입·BIGINT 범위 오류 |
| 404 | PRODUCT_NOT_FOUND | 미등록 상품 출고·조회·이름 변경 |
| 409 | PRODUCT_NAME_MISMATCH | 기존 ID와 다른 이름으로 입고 |
| 409 | STOCK_LIMIT_EXCEEDED | 누적 재고 BIGINT 상한 초과 |
| 409 | INSUFFICIENT_STOCK | 출고 시점의 재고 부족 |
| 500 | INTERNAL_ERROR | 예기치 않은 서버/DB 처리 실패 |

## 동시성 제어

PostgreSQL의 원자적 SQL과 트랜잭션을 사용합니다.

### 방식별 비교

| 방식 | 특징 | 검토 내용 |
|---|---|---|
| 원자적 SQL (채택) | 조건 검사와 변경을 한 SQL로 처리 | 현재의 단일 상품 재고 변경에 직접적이며 PostgreSQL에 종속됨 |
| 낙관적 락 | 버전으로 충돌을 감지 | 경합이 적을 때 적합하지만 인기 상품에서는 충돌과 재시도 비용이 증가 |
| 비관적 락 | 조회 시 행을 잠근 뒤 검증·변경 | 복잡한 검증에 적합하지만 조회가 추가되고 미등록 상품 생성 경쟁은 별도 처리 필요 |
| Redis 분산 락 | 여러 서버에서 상품별 작업 진입을 조정 | DB 접근 전 요청을 조정할 수 있으나 추가 네트워크 비용과 만료·소유권·장애 처리 필요 |

### 원자적 SQL을 선택한 이유

현재는 재고 관리의 핵심 기능을 구현하는 MVP 단계이므로,  
요구사항을 충족하면서 구현과 운영이 단순한 원자적 SQL을 선택했습니다.

별도의 락 저장소나 낙관적 락 충돌 재시도 로직을 추가하지 않아도 되어,  
구현 복잡도를 낮추고 핵심 기능과 정합성 검증에 집중할 수 있습니다.

- 조회와 변경 사이에 다른 요청이 개입하여 잘못된 값을 저장하는 문제를 방지합니다.
- 별도 인프라 없이 실행할 수 있어 개발 환경과 운영 의존성이 단순합니다.

### 인기 상품 요청 집중 시 한계

동일 상품의 변경은 행 잠금으로 직렬화됩니다.  
요청이 몰리면 DB 커넥션을 점유하고 잠금 해제를 기다릴 수 있습니다.  
커넥션 풀이 모두 점유되면 추가 요청은 앱에서 커넥션을 기다리게 됩니다.  
상품 A의 잠금이 상품 B를 직접 잠그지는 않지만, 공용 커넥션 풀 부족으로 다른 상품 요청도 지연될 수 있습니다.

## 추후 개선 사항

### Redisson Pub/Sub 기반 분산 락 처리

향후 Redisson의 Pub/Sub 기반 `RLock`으로 변경하여 기존 원자적 SQL 방식을 대체합니다. 

상품 ID를 공통 락 키로 사용하고, 입고·출고·상품명 변경 등 같은 상품을 수정하는 모든 경로에 동일한 락 규칙을 적용합니다.
락을 얻지 못한 요청은 Pub/Sub 해제 알림을 기다렸다가 획득을 다시 시도합니다.

```text
상품별 Redis 락 획득
  → DB 트랜잭션 시작
  → 상품 조회 및 상품명·수량 검증
  → 신규 상품 생성 또는 기존 엔티티의 재고 변경
  → 입출고 이력 저장
  → 커밋 또는 롤백 완료
  → Redis 락 해제 및 대기 요청 알림
```

락을 DB 트랜잭션 시작 전에 획득하여 경쟁 요청이 DB 커넥션을 점유하고 대기하는 상황을 줄이는 것이 목적입니다.  

### 입고 및 출고 멱등성 처리

현재 같은 요청을 재전송하면 별개의 입출고로 처리됩니다.  
향후 멱등 키와 처리 결과를 재고 변경과 같은 DB 트랜잭션에 저장하여 중복 처리를 방지할 수 있습니다.
