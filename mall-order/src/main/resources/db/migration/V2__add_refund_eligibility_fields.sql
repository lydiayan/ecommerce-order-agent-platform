ALTER TABLE orders
    ADD COLUMN delivery_status TINYINT NULL COMMENT '物流状态：0未发货，1运输中，2已签收，3已拒收' AFTER order_status,
    ADD COLUMN shipped_at DATETIME NULL AFTER delivery_status,
    ADD COLUMN signed_at DATETIME NULL AFTER shipped_at,
    ADD COLUMN production_status TINYINT NULL COMMENT '定制履约：0未开始，1生产中，2已完成' AFTER signed_at,
    ADD COLUMN production_started_at DATETIME NULL AFTER production_status,
    ADD COLUMN digital_delivery_status TINYINT NULL COMMENT '虚拟履约：0未交付，1已交付，2已核销' AFTER production_started_at,
    ADD COLUMN digital_delivered_at DATETIME NULL AFTER digital_delivery_status,
    ADD COLUMN redeemed_at DATETIME NULL AFTER digital_delivered_at,
    ADD CONSTRAINT chk_orders_delivery_status
        CHECK (delivery_status IS NULL OR delivery_status BETWEEN 0 AND 3),
    ADD CONSTRAINT chk_orders_production_status
        CHECK (production_status IS NULL OR production_status BETWEEN 0 AND 2),
    ADD CONSTRAINT chk_orders_digital_delivery_status
        CHECK (digital_delivery_status IS NULL OR digital_delivery_status BETWEEN 0 AND 2);

ALTER TABLE order_details
    ADD CONSTRAINT chk_order_details_product_type
        CHECK (product_type BETWEEN 0 AND 3);

ALTER TABLE orders
    ADD CONSTRAINT chk_orders_order_status
        CHECK (order_status BETWEEN 0 AND 4);

ALTER TABLE after_sales_request
    ADD COLUMN reason_type VARCHAR(32) NULL AFTER operation_type,
    ADD COLUMN reason_description VARCHAR(500) NULL AFTER reason_type,
    ADD COLUMN evidence_urls JSON NULL AFTER reason_description,
    ADD COLUMN customer_opened BOOLEAN NULL AFTER evidence_urls,
    ADD COLUMN customer_used BOOLEAN NULL AFTER customer_opened,
    ADD COLUMN customer_condition_status VARCHAR(32) NULL AFTER customer_used,
    ADD COLUMN inspection_result VARCHAR(32) NULL AFTER customer_condition_status,
    ADD COLUMN inspection_note VARCHAR(500) NULL AFTER inspection_result,
    ADD COLUMN eligibility_decision VARCHAR(32) NULL AFTER inspection_note,
    ADD COLUMN policy_version VARCHAR(32) NULL AFTER eligibility_decision,
    ADD COLUMN active_request_key VARCHAR(96) NULL AFTER policy_version,
    ADD UNIQUE INDEX uk_after_sales_active_request (active_request_key);

UPDATE after_sales_request
SET status = 'PENDING_REVIEW'
WHERE status = 'PENDING';

ALTER TABLE after_sales_request
    ADD CONSTRAINT chk_after_sales_status
        CHECK (status IN ('PENDING_REVIEW', 'APPROVED', 'REJECTED', 'WAITING_RETURN',
                          'RETURNING', 'RECEIVED', 'REFUNDING', 'REFUNDED', 'CLOSED'));

UPDATE orders
SET delivery_status = 0,
    shipped_at = NULL,
    signed_at = NULL
WHERE order_id = 'ORD20260810001';

UPDATE orders
SET delivery_status = 1,
    shipped_at = '2026-08-17 10:00:00',
    signed_at = NULL
WHERE order_id = 'ORD20260810002';

UPDATE orders
SET delivery_status = 2,
    shipped_at = '2026-08-08 20:00:00',
    signed_at = '2026-08-10 09:00:00'
WHERE order_id = 'ORD20260810003';

UPDATE order_details
SET product_type = 0
WHERE order_id IN ('ORD20260810001', 'ORD20260810002', 'ORD20260810003');
