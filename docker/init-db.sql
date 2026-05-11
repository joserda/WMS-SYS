-- WMS 数据库初始化脚本 (PostgreSQL)
-- 容器首次启动时由 docker-entrypoint-initdb.d 自动执行

-- 商品表
CREATE TABLE IF NOT EXISTS products (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    sku VARCHAR(50) NOT NULL UNIQUE,
    unit VARCHAR(20) DEFAULT '个',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 仓库表
CREATE TABLE IF NOT EXISTS warehouses (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL
);

-- 库位表
CREATE TABLE IF NOT EXISTS locations (
    id BIGSERIAL PRIMARY KEY,
    warehouse_id BIGINT NOT NULL,
    code VARCHAR(50) NOT NULL UNIQUE,
    status VARCHAR(20) DEFAULT 'FREE',
    CONSTRAINT fk_locations_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses(id)
);

-- 库存表
CREATE TABLE IF NOT EXISTS inventory (
    id BIGSERIAL PRIMARY KEY,
    product_id BIGINT NOT NULL,
    location_code VARCHAR(50) NOT NULL,
    quantity INT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_inventory_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT uk_product_location UNIQUE (product_id, location_code)
);

-- 入库单主表
CREATE TABLE IF NOT EXISTS inbound_orders (
    id BIGSERIAL PRIMARY KEY,
    order_no VARCHAR(50) NOT NULL UNIQUE,
    supplier_name VARCHAR(200),
    status VARCHAR(20) DEFAULT 'DRAFT',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 入库单明细表
CREATE TABLE IF NOT EXISTS inbound_order_items (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    location_code VARCHAR(50) NOT NULL,
    CONSTRAINT fk_inbound_items_order FOREIGN KEY (order_id) REFERENCES inbound_orders(id),
    CONSTRAINT fk_inbound_items_product FOREIGN KEY (product_id) REFERENCES products(id)
);

-- 出库单主表
CREATE TABLE IF NOT EXISTS outbound_orders (
    id BIGSERIAL PRIMARY KEY,
    order_no VARCHAR(50) NOT NULL UNIQUE,
    customer_name VARCHAR(200),
    status VARCHAR(20) DEFAULT 'DRAFT',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 出库单明细表
CREATE TABLE IF NOT EXISTS outbound_order_items (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    location_code VARCHAR(50) NOT NULL,
    CONSTRAINT fk_outbound_items_order FOREIGN KEY (order_id) REFERENCES outbound_orders(id),
    CONSTRAINT fk_outbound_items_product FOREIGN KEY (product_id) REFERENCES products(id)
);
