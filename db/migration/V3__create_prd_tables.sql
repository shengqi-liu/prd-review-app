-- V3: 创建 PRD 相关表
-- prd: PRD 主表（含逻辑删除、乐观锁）
-- prd_version: 提交评审时的版本快照

CREATE TABLE IF NOT EXISTS `prd`
(
    `id`         BIGINT        NOT NULL AUTO_INCREMENT COMMENT 'PRD ID',
    `title`      VARCHAR(200)  NOT NULL DEFAULT '' COMMENT 'PRD 标题',
    `content`    MEDIUMTEXT             COMMENT 'PRD 正文内容',
    `source_url` VARCHAR(2048)          COMMENT '原始文档 URL（URL 路径创建时填充）',
    `author_id`  BIGINT        NOT NULL COMMENT '作者用户 ID',
    `status`     VARCHAR(20)   NOT NULL DEFAULT 'DRAFT' COMMENT '状态：INITIALIZING/DRAFT/SUBMITTED/UNDER_REVIEW/APPROVED/REJECTED',
    `version`    INT           NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    `deleted`    TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=正常，1=已删除',
    `created_at` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_author_id` (`author_id`),
    KEY `idx_status` (`status`),
    KEY `idx_created_at` (`created_at`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = 'PRD 方案主表';

CREATE TABLE IF NOT EXISTS `prd_version`
(
    `id`         BIGINT        NOT NULL AUTO_INCREMENT COMMENT '版本快照 ID',
    `prd_id`     BIGINT        NOT NULL COMMENT '关联 PRD ID',
    `version`    INT           NOT NULL COMMENT '快照对应的版本号',
    `title`      VARCHAR(200)  NOT NULL COMMENT '快照标题',
    `content`    MEDIUMTEXT             COMMENT '快照正文内容',
    `source_url` VARCHAR(2048)          COMMENT '快照时记录的原始文档 URL',
    `created_at` DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '快照创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_prd_id` (`prd_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = 'PRD 版本快照表';
