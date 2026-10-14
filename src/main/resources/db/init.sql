-- ============================================
-- 创建数据库（如果不存在）
-- ============================================
CREATE DATABASE IF NOT EXISTS ticket_system DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE ticket_system;

-- ============================================
-- 表1: user — 用户
-- ============================================
CREATE TABLE IF NOT EXISTS `user` (
    `id`          BIGINT                                   PRIMARY KEY AUTO_INCREMENT  COMMENT '主键',
    `username`    VARCHAR(50)                              NOT NULL UNIQUE             COMMENT '用户名',
    `password`    VARCHAR(255)                             NOT NULL                    COMMENT '加密后的密码',
    `role`        ENUM('attendee', 'organizer')            NOT NULL                    COMMENT '角色：参会者/举办方（注册后不可改）',
    `gender`      ENUM('male', 'female', 'other')          DEFAULT NULL                COMMENT '性别（举办方为NULL，参会者可修改）',
    `tags`        VARCHAR(255)                             DEFAULT NULL                COMMENT '偏好标签JSON数组，如["科技","电竞"]',
    `avatar_url`  VARCHAR(255)                             NOT NULL DEFAULT 'default.png' COMMENT '头像URL（暂不支持上传，使用默认值）',
    `created_at`  DATETIME                                 NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
    INDEX `idx_role` (`role`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- ============================================
-- 表2: event — 活动
-- ============================================
CREATE TABLE IF NOT EXISTS `event` (
    `id`            BIGINT                                 PRIMARY KEY AUTO_INCREMENT  COMMENT '主键',
    `organizer_id`  BIGINT                                 NOT NULL                    COMMENT '举办方用户ID（逻辑关联user.id）',
    `title`         VARCHAR(100)                           NOT NULL                    COMMENT '活动标题',
    `description`   TEXT                                                               COMMENT '活动描述（支持AI生成填充）',
    `location`      VARCHAR(255)                           DEFAULT NULL                COMMENT '活动地点（纯文本，如"人文馆主厅"）',
    `start_time`    DATETIME                               NOT NULL                    COMMENT '活动开始时间',
    `end_time`      DATETIME                               DEFAULT NULL                COMMENT '活动结束时间（为NULL表示无固定结束时间，不会自动变为finished）',
    `total_tickets` INT                                    NOT NULL DEFAULT 0          COMMENT '门票总数',
    `sold_tickets`  INT                                    NOT NULL DEFAULT 0          COMMENT '已售门票数（应用层保证 sold_tickets <= total_tickets）',
    `tags`          VARCHAR(255)                           DEFAULT NULL                COMMENT '活动标签JSON数组，如["科技","电竞"]',
    `has_seats`     TINYINT(1)                             NOT NULL DEFAULT 0          COMMENT '是否开启选座：0=不开启，1=开启',
    `status`        ENUM('draft', 'online', 'offline', 'finished') NOT NULL DEFAULT 'draft' COMMENT '状态：draft→online→offline↔online→finished（自动到达end_time）',
    `created_at`    DATETIME                               NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发布时间',
    `updated_at`    DATETIME                               NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最近更新时间',
    INDEX `idx_organizer` (`organizer_id`),
    INDEX `idx_status` (`status`),
    INDEX `idx_start_time` (`start_time`),
    INDEX `idx_end_time` (`end_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活动表';

-- ============================================
-- 表3: ticket — 门票
-- ============================================
CREATE TABLE IF NOT EXISTS `ticket` (
    `id`          BIGINT                                   PRIMARY KEY AUTO_INCREMENT  COMMENT '主键',
    `event_id`    BIGINT                                   NOT NULL                    COMMENT '关联活动ID（逻辑关联event.id）',
    `user_id`     BIGINT                                   NOT NULL                    COMMENT '票主用户ID（逻辑关联user.id）',
    `ticket_no`   VARCHAR(64)                              NOT NULL UNIQUE             COMMENT '票号，生成规则: {eventId}_{userId}_{timestamp}',
    `seat_id`     BIGINT                                   DEFAULT NULL                COMMENT '关联座位ID，NULL=无选座',
    `status`      ENUM('pending', 'checked', 'invalid', 'refunded') NOT NULL DEFAULT 'pending' COMMENT '状态：待签到/已签到/已失效/已退票',
    `created_at`  DATETIME                                 NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '购票时间',
    `checked_at`  DATETIME                                 DEFAULT NULL                COMMENT '核销时间（签到成功时记录）',
    UNIQUE INDEX `uk_event_user` (`event_id`, `user_id`),
    INDEX `idx_event` (`event_id`),
    INDEX `idx_user` (`user_id`),
    INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='门票表';

-- ============================================
-- 表4: seat — 座位表
-- ============================================
CREATE TABLE IF NOT EXISTS `seat` (
    `id`          BIGINT                                   PRIMARY KEY AUTO_INCREMENT  COMMENT '主键',
    `event_id`    BIGINT                                   NOT NULL                    COMMENT '关联活动ID',
    `seat_row`    INT                                      NOT NULL                    COMMENT '排号',
    `seat_col`    INT                                      NOT NULL                    COMMENT '列号',
    `seat_label`  VARCHAR(20)                              NOT NULL                    COMMENT '座位标签，如"1-1"',
    `status`      ENUM('available','sold')                 NOT NULL DEFAULT 'available' COMMENT '座位状态：available=可选, sold=已售出',
    UNIQUE INDEX `uk_event_seat` (`event_id`, `seat_row`, `seat_col`),
    INDEX `idx_event_status` (`event_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='座位表';

-- ============================================
-- 兼容旧数据库的增量迁移
--
-- CREATE TABLE IF NOT EXISTS 不会更新已经存在的表，因此选座功能加入后，
-- 旧数据库可能缺少 event.has_seats 和 ticket.seat_id。
-- 通过 information_schema 判断字段是否存在，只在缺失时执行 ALTER TABLE，
-- 保证脚本可以在每次应用启动时安全、重复执行，并保留已有业务数据。
-- ============================================

SET @has_seats_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'event'
      AND COLUMN_NAME = 'has_seats'
);
SET @has_seats_migration = IF(
    @has_seats_exists = 0,
    'ALTER TABLE `event` ADD COLUMN `has_seats` TINYINT(1) NOT NULL DEFAULT 0 COMMENT ''是否开启选座：0=不开启，1=开启'' AFTER `tags`',
    'SELECT 1'
);
PREPARE migration_statement FROM @has_seats_migration;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

SET @seat_id_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'ticket'
      AND COLUMN_NAME = 'seat_id'
);
SET @seat_id_migration = IF(
    @seat_id_exists = 0,
    'ALTER TABLE `ticket` ADD COLUMN `seat_id` BIGINT DEFAULT NULL COMMENT ''关联座位ID，NULL=无选座'' AFTER `ticket_no`',
    'SELECT 1'
);
PREPARE migration_statement FROM @seat_id_migration;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

-- ============================================
-- 表5: event_stock — 活动库存独立表（秒杀优化）
-- ============================================
CREATE TABLE IF NOT EXISTS `event_stock` (
    `id`             BIGINT    PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    `event_id`       BIGINT    NOT NULL                  COMMENT '关联活动ID',
    `total_tickets`  INT       NOT NULL DEFAULT 0        COMMENT '门票总数',
    `sold_tickets`   INT       NOT NULL DEFAULT 0        COMMENT '已售门票数',
    `version`        INT       NOT NULL DEFAULT 0        COMMENT '乐观锁版本号（预留）',
    UNIQUE INDEX `uk_event` (`event_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活动库存表';
