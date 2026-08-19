CREATE DATABASE IF NOT EXISTS products CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS agent_memory CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

GRANT ALL PRIVILEGES ON products.* TO 'portfolio'@'%';
GRANT ALL PRIVILEGES ON agent_memory.* TO 'portfolio'@'%';

USE products;

CREATE TABLE IF NOT EXISTS orders (
    order_id VARCHAR(32) NOT NULL,
    user_id VARCHAR(32) NOT NULL,
    order_time DATETIME NOT NULL,
    total_amount DECIMAL(10, 2) NOT NULL,
    order_status TINYINT NOT NULL DEFAULT 0,
    payment_method VARCHAR(32),
    shipping_address VARCHAR(255),
    contact_phone VARCHAR(32),
    PRIMARY KEY (order_id),
    INDEX idx_orders_user_time (user_id, order_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS order_details (
    detail_id BIGINT NOT NULL AUTO_INCREMENT,
    order_id VARCHAR(32) NOT NULL,
    product_id VARCHAR(32) NOT NULL,
    product_name VARCHAR(128) NOT NULL,
    product_type TINYINT NOT NULL DEFAULT 0 COMMENT '商品类型：0普通商品，1定制商品，2生鲜类，3虚拟商品',
    quantity INT NOT NULL,
    unit_price DECIMAL(10, 2) NOT NULL,
    total_price DECIMAL(10, 2) NOT NULL,
    specification VARCHAR(128),
    PRIMARY KEY (detail_id),
    INDEX idx_order_details_order (order_id),
    CONSTRAINT fk_order_details_order FOREIGN KEY (order_id) REFERENCES orders(order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS after_sales_request (
    ticket_id VARCHAR(32) NOT NULL,
    order_id VARCHAR(32) NOT NULL,
    user_id VARCHAR(32) NOT NULL,
    operation_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (ticket_id),
    INDEX idx_after_sales_user_time (user_id, created_at),
    CONSTRAINT fk_after_sales_order FOREIGN KEY (order_id) REFERENCES orders(order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO orders
    (order_id, user_id, order_time, total_amount, order_status, payment_method, shipping_address, contact_phone)
VALUES
    ('ORD20260810001', 'USER1001', '2026-08-10 09:15:00', 5999.00, 1, 'DEMO_PAY', 'DEMO_ADDRESS_001', '13800000001'),
    ('ORD20260810002', 'USER1001', '2026-08-09 14:30:00', 3299.00, 2, 'DEMO_PAY', 'DEMO_ADDRESS_001', '13800000001'),
    ('ORD20260810003', 'USER1002', '2026-08-08 18:20:00', 8999.00, 3, 'DEMO_PAY', 'DEMO_ADDRESS_002', '13800000002')
ON DUPLICATE KEY UPDATE order_id = VALUES(order_id);

INSERT INTO order_details
    (order_id, product_id, product_name, product_type, quantity, unit_price, total_price, specification)
SELECT 'ORD20260810001', 'DEMO-P1001', 'Demo Phone Pro', 0, 1, 5999.00, 5999.00, '16GB+512GB'
WHERE NOT EXISTS (SELECT 1 FROM order_details WHERE order_id = 'ORD20260810001');

INSERT INTO order_details
    (order_id, product_id, product_name, product_type, quantity, unit_price, total_price, specification)
SELECT 'ORD20260810002', 'DEMO-P1002', 'Demo Tablet', 0, 1, 3299.00, 3299.00, '256GB'
WHERE NOT EXISTS (SELECT 1 FROM order_details WHERE order_id = 'ORD20260810002');

INSERT INTO order_details
    (order_id, product_id, product_name, product_type, quantity, unit_price, total_price, specification)
SELECT 'ORD20260810003', 'DEMO-P1003', 'Demo Laptop', 0, 1, 8999.00, 8999.00, '32GB+1TB'
WHERE NOT EXISTS (SELECT 1 FROM order_details WHERE order_id = 'ORD20260810003');

USE agent_memory;

CREATE TABLE IF NOT EXISTS user_profile (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id VARCHAR(64) NOT NULL,
    occupation VARCHAR(100),
    department VARCHAR(100),
    city VARCHAR(100),
    language VARCHAR(50) DEFAULT 'zh-CN',
    response_style VARCHAR(100),
    profile_json JSON,
    confidence DECIMAL(4, 3) DEFAULT 0.500,
    version INT DEFAULT 1,
    source VARCHAR(50) DEFAULT 'conversation',
    last_conversation_id VARCHAR(64),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_profile_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
