-- ============================================
-- 秒杀引擎MySQL优化：增量迁移脚本
-- 兼容旧数据库，可重复安全执行
-- ============================================

-- 1. 为 event 表补充 tags 列（旧表可能缺失）
SET @tags_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'event'
      AND COLUMN_NAME = 'tags'
);
SET @tags_migration = IF(
    @tags_exists = 0,
    'ALTER TABLE `event` ADD COLUMN `tags` VARCHAR(255) DEFAULT NULL COMMENT ''活动标签JSON数组，如["科技","电竞"]'' AFTER `description`',
    'SELECT 1'
);
PREPARE stmt FROM @tags_migration;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2. 为 event 表补充 has_seats 列（旧表可能缺失）
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
PREPARE stmt FROM @has_seats_migration;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3. 为 ticket 表补充 seat_id 列（旧表可能缺失）
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
PREPARE stmt FROM @seat_id_migration;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 4. 创建 event_stock 表（如果不存在）
CREATE TABLE IF NOT EXISTS `event_stock` (
    `id`             BIGINT    PRIMARY KEY AUTO_INCREMENT COMMENT '主键',
    `event_id`       BIGINT    NOT NULL                  COMMENT '关联活动ID',
    `total_tickets`  INT       NOT NULL DEFAULT 0        COMMENT '门票总数',
    `sold_tickets`   INT       NOT NULL DEFAULT 0        COMMENT '已售门票数',
    `version`        INT       NOT NULL DEFAULT 0        COMMENT '乐观锁版本号（预留）',
    UNIQUE INDEX `uk_event` (`event_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='活动库存表';

SELECT 'Migration completed successfully.' AS result;