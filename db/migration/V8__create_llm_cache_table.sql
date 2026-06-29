-- V8: LLM 非流式响应精确 hash 缓存(#add-llm-response-cache)
-- 相同 provider + model + prompt 输入直接复用上次 LLM 输出,跳过远端调用,降本提速
-- 失效策略:TTL(默认 30 天)+ LRU(默认 max 10000 条)由后端 @Scheduled 清理

CREATE TABLE IF NOT EXISTS `llm_cache`
(
    `cache_key`            VARCHAR(64)  NOT NULL COMMENT 'SHA-256 hex of provider:model:rawText',
    `response`             TEXT         NOT NULL COMMENT 'SummarizeResult JSON',
    `provider`             VARCHAR(50)  NOT NULL COMMENT 'LLM provider (openai-compatible / anthropic ...)',
    `model`                VARCHAR(100) NOT NULL COMMENT 'LLM model id (deepseek-chat / claude-sonnet-4-5 ...)',
    `prompt_preview`       VARCHAR(200)          COMMENT '前 200 字符预览,运维查询用,不含敏感全文',
    `token_count_estimate` INT          NOT NULL DEFAULT 0 COMMENT 'rawText.length / 4 粗略估算',
    `hit_count`            INT          NOT NULL DEFAULT 0,
    `created_at`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `last_hit_at`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`cache_key`),
    KEY `idx_last_hit_at` (`last_hit_at`),
    KEY `idx_created_at` (`created_at`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = 'LLM 响应精确 hash 缓存';
