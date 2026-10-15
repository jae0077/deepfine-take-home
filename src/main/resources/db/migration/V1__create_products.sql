-- 상품 재고 / 상품 ID는 API 요청에서 받으며 수량은 DB에서도 음수를 거부한다.
CREATE TABLE products (
    id BIGINT PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    quantity BIGINT NOT NULL,
    CONSTRAINT products_name_not_blank CHECK (char_length(btrim(name)) > 0),
    CONSTRAINT products_quantity_nonnegative CHECK (quantity >= 0)
);

-- 입출고 이력 / quantity_after는 변경 직후의 재고
CREATE TABLE inventory_history (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_id BIGINT NOT NULL REFERENCES products (id),
    type VARCHAR(8) NOT NULL,
    amount BIGINT NOT NULL,
    quantity_after BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT inventory_history_type_valid CHECK (type IN ('INBOUND', 'OUTBOUND')),
    CONSTRAINT inventory_history_amount_positive CHECK (amount > 0),
    CONSTRAINT inventory_history_quantity_nonnegative CHECK (quantity_after >= 0)
);

CREATE INDEX inventory_history_product_id_id_idx ON inventory_history (product_id, id DESC);
