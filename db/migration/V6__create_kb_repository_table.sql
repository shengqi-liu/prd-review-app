-- V6: 创建知识库 Git 仓库表（监听本地 Git 仓库元数据 + 同步状态）
-- kb_repository: 知识库源仓库配置，单仓库约束在 Application 层强制
-- 同步状态机：HEALTHY → SYNCING → HEALTHY/ERROR → SYNCING

CREATE TABLE IF NOT EXISTS `kb_repository`
(
    `id`                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '知识库仓库 ID',
    `name`                VARCHAR(100) NOT NULL COMMENT '仓库展示名',
    `remote_url`          VARCHAR(500) NOT NULL COMMENT 'Git 远端 URL',
    `branch`              VARCHAR(100) NOT NULL DEFAULT 'main' COMMENT '监听分支',
    `local_path`          VARCHAR(500) NOT NULL COMMENT '本地 clone 绝对路径',
    `auth_type`           VARCHAR(20)  NOT NULL DEFAULT 'NONE' COMMENT '凭据类型：NONE/HTTPS_TOKEN/SSH_KEY_PATH',
    `auth_secret`         VARCHAR(1000)         COMMENT '凭据内容（token 或 SSH 私钥路径），日志中始终 mask',
    `poll_interval_ms`    BIGINT       NOT NULL DEFAULT 3600000 COMMENT '轮询间隔毫秒（默认 1 小时）',
    `sync_status`         VARCHAR(20)  NOT NULL DEFAULT 'HEALTHY' COMMENT '同步状态：HEALTHY/SYNCING/ERROR',
    `last_synced_commit`  VARCHAR(40)           COMMENT '最后一次成功同步的 commit hash',
    `last_synced_at`      DATETIME              COMMENT '最后一次成功同步时间',
    `last_error_message`  VARCHAR(1000)         COMMENT '最后一次错误信息',
    `version`             INT          NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    `deleted`             TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=正常，1=已删除',
    `created_at`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_kb_repository_name` (`name`, `deleted`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = '知识库 Git 仓库';
