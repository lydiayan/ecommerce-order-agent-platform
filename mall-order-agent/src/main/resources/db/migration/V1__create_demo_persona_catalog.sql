CREATE TABLE demo_persona (
    actor_user_id VARCHAR(64) NOT NULL,
    category VARCHAR(32) NOT NULL,
    display_name VARCHAR(64) NOT NULL,
    job_title VARCHAR(100) NOT NULL,
    department VARCHAR(100) NOT NULL,
    description VARCHAR(255) NOT NULL,
    welcome_message VARCHAR(500) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (actor_user_id),
    INDEX idx_demo_persona_category_sort (category, sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE demo_persona_rag_scope (
    actor_user_id VARCHAR(64) NOT NULL,
    scope_type VARCHAR(32) NOT NULL,
    scope_value VARCHAR(100) NOT NULL,
    PRIMARY KEY (actor_user_id, scope_type, scope_value),
    CONSTRAINT fk_demo_scope_persona FOREIGN KEY (actor_user_id)
        REFERENCES demo_persona(actor_user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE demo_persona_capability (
    actor_user_id VARCHAR(64) NOT NULL,
    capability VARCHAR(64) NOT NULL,
    PRIMARY KEY (actor_user_id, capability),
    CONSTRAINT fk_demo_capability_persona FOREIGN KEY (actor_user_id)
        REFERENCES demo_persona(actor_user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE demo_persona_suggestion (
    actor_user_id VARCHAR(64) NOT NULL,
    sort_order INT NOT NULL,
    suggestion VARCHAR(255) NOT NULL,
    PRIMARY KEY (actor_user_id, sort_order),
    CONSTRAINT fk_demo_suggestion_persona FOREIGN KEY (actor_user_id)
        REFERENCES demo_persona(actor_user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE sales_customer_assignment (
    sales_actor_user_id VARCHAR(64) NOT NULL,
    customer_user_id VARCHAR(64) NOT NULL,
    PRIMARY KEY (sales_actor_user_id, customer_user_id),
    CONSTRAINT fk_sales_assignment_persona FOREIGN KEY (sales_actor_user_id)
        REFERENCES demo_persona(actor_user_id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO demo_persona
    (actor_user_id, category, display_name, job_title, department, description, welcome_message, sort_order)
VALUES
    ('HR001', 'HR', '林悦', 'HRBP', 'HumanResources', '负责员工政策、福利与组织协作', '你好，我是林悦。可以演示员工制度、福利政策和 HR 协作场景。', 10),
    ('HR002', 'HR', '陈晨', '招聘专员', 'HumanResources', '负责招聘流程与新员工入职', '你好，我是陈晨。可以演示招聘、入职和试用期相关知识问答。', 20),
    ('DEV001', 'ENGINEERING', '周航', '后端工程师', 'Engineering', '关注研发规范、接口与代码质量', '你好，我是周航。可以演示技术开发规范和订单系统研发知识。', 30),
    ('DEV002', 'ENGINEERING', '赵宁', '平台工程师', 'Platform', '负责 Agent 平台运行与故障排查', '你好，我是赵宁。可以演示开发规范、Agent 运维和平台排障。', 40),
    ('SALES001', 'SALES', '王磊', '华东区销售', 'Sales', '负责华东客户跟进与订单协同', '你好，我是王磊。可以演示销售规则和已分配客户的订单跟进。', 50),
    ('SALES002', 'SALES', '刘婷', '大客户销售', 'Sales', '负责重点客户经营与订单协同', '你好，我是刘婷。可以演示大客户销售规则和订单跟进。', 60),
    ('USER1001', 'CUSTOMER', '张伟', '个人客户', 'Customer', '有两笔进行中订单，适合演示退款与物流场景', '你好，我是张伟。可以查看我的订单、物流进度并发起售后操作。', 70),
    ('USER1002', 'CUSTOMER', '李娜', '个人客户', 'Customer', '有一笔已完成订单，适合演示历史订单场景', '你好，我是李娜。可以查看我的已完成订单和售后规则。', 80);

INSERT INTO demo_persona_rag_scope (actor_user_id, scope_type, scope_value) VALUES
    ('HR001', 'ROLE', 'public'), ('HR001', 'ROLE', 'hr'),
    ('HR002', 'ROLE', 'public'), ('HR002', 'ROLE', 'hr'),
    ('DEV001', 'ROLE', 'public'), ('DEV001', 'ROLE', 'developer'),
    ('DEV002', 'ROLE', 'public'), ('DEV002', 'ROLE', 'developer'), ('DEV002', 'ROLE', 'admin'),
    ('SALES001', 'ROLE', 'public'), ('SALES001', 'ROLE', 'sales'),
    ('SALES002', 'ROLE', 'public'), ('SALES002', 'ROLE', 'sales'),
    ('USER1001', 'ROLE', 'public'), ('USER1001', 'ROLE', 'customer_service'),
    ('USER1002', 'ROLE', 'public'), ('USER1002', 'ROLE', 'customer_service');

INSERT INTO demo_persona_capability (actor_user_id, capability) VALUES
    ('HR001', 'KNOWLEDGE_SEARCH'), ('HR002', 'KNOWLEDGE_SEARCH'),
    ('DEV001', 'KNOWLEDGE_SEARCH'), ('DEV002', 'KNOWLEDGE_SEARCH'),
    ('SALES001', 'KNOWLEDGE_SEARCH'), ('SALES001', 'ASSIGNED_ORDER_READ'),
    ('SALES002', 'KNOWLEDGE_SEARCH'), ('SALES002', 'ASSIGNED_ORDER_READ'),
    ('USER1001', 'KNOWLEDGE_SEARCH'), ('USER1001', 'OWN_ORDER_READ'),
    ('USER1001', 'ORDER_CANCEL'), ('USER1001', 'AFTER_SALES_CREATE'),
    ('USER1002', 'KNOWLEDGE_SEARCH'), ('USER1002', 'OWN_ORDER_READ'),
    ('USER1002', 'ORDER_CANCEL'), ('USER1002', 'AFTER_SALES_CREATE');

INSERT INTO demo_persona_suggestion (actor_user_id, sort_order, suggestion) VALUES
    ('HR001', 1, '员工年假如何计算？'), ('HR001', 2, '公司有哪些员工福利？'), ('HR001', 3, '跨部门协作有哪些规范？'),
    ('HR002', 1, '新员工入职需要准备什么？'), ('HR002', 2, '试用期管理有哪些要求？'), ('HR002', 3, '招聘面试流程是什么？'),
    ('DEV001', 1, '订单接口开发要遵循哪些规范？'), ('DEV001', 2, '代码评审有哪些要求？'), ('DEV001', 3, '如何处理接口幂等？'),
    ('DEV002', 1, 'Agent 服务异常如何排查？'), ('DEV002', 2, '知识库导入失败怎么办？'), ('DEV002', 3, '服务上线前要检查什么？'),
    ('SALES001', 1, '销售跟进订单时要注意什么？'), ('SALES001', 2, '查看我负责客户的订单'), ('SALES001', 3, '客户退款诉求如何协同？'),
    ('SALES002', 1, '大客户订单如何跟进？'), ('SALES002', 2, '查看我负责客户的订单'), ('SALES002', 3, '销售报价有哪些边界？'),
    ('USER1001', 1, '查看我的订单'), ('USER1001', 2, 'ORD20260810001 可以退款吗？'), ('USER1001', 3, '我的订单什么时候发货？'),
    ('USER1002', 1, '查看我的订单'), ('USER1002', 2, '已完成订单还能申请售后吗？'), ('USER1002', 3, '介绍一下售后处理规则');

INSERT INTO sales_customer_assignment (sales_actor_user_id, customer_user_id) VALUES
    ('SALES001', 'USER1001'),
    ('SALES002', 'USER1002');
