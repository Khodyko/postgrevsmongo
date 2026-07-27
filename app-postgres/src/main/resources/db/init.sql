-- Кейс A: нормализованная схема
CREATE TABLE IF NOT EXISTS product (
    id          BIGINT PRIMARY KEY,
    name        TEXT NOT NULL,
    price       NUMERIC(12, 2) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL
);

CREATE TABLE IF NOT EXISTS product_tag (
    product_id  BIGINT NOT NULL REFERENCES product (id),
    tag         TEXT NOT NULL,
    PRIMARY KEY (product_id, tag)
);

-- Кейс B: весь продукт в JSONB
CREATE TABLE IF NOT EXISTS product_doc (
    id   BIGINT PRIMARY KEY,
    doc  JSONB NOT NULL
);
