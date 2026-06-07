-- V1: 创建用户表
-- 字符集：utf8mb4，排序规则：utf8mb4_unicode_ci

CREATE TABLE IF NOT EXISTS `user`
(
    `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '用户 ID',
    `username`   VARCHAR(32)  NOT NULL COMMENT '用户名，2-32 字符，全局唯一',
    `email`      VARCHAR(128) NOT NULL COMMENT '邮箱，全局唯一',
    `password`   VARCHAR(128) NOT NULL COMMENT '密码（BCrypt 加密）',
    `role`       VARCHAR(20)  NOT NULL DEFAULT 'SUBMITTER' COMMENT '角色：SUBMITTER/TEAM_MEMBER/ADMIN',
    `status`     VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/DISABLED',
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`),
    UNIQUE KEY `uk_email` (`email`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = '用户表';
