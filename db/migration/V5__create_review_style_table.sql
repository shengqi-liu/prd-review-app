-- V5: 创建评审风格表（含逻辑删除、乐观锁、默认风格唯一性约束）
-- review_style: 评审任务的"深度/态度"开关，与评审员（角色视角）正交
-- 种子数据来源：原型 frontend/app.html 的"评审风格"页面
-- 规则字段以 JSON 数组字符串存储：[{"label":"...","content":"..."}]

CREATE TABLE IF NOT EXISTS `review_style`
(
    `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '评审风格 ID',
    `name`        VARCHAR(50)  NOT NULL COMMENT '风格名称（宽松/务实/标准/严谨等）',
    `icon`        VARCHAR(20)           COMMENT '展示用 emoji 图标',
    `scenario`    VARCHAR(200)          COMMENT '适用场景描述',
    `rules`       TEXT         NOT NULL COMMENT '规则列表 JSON 字符串，4–8 条 {label,content} 对',
    `enabled`     TINYINT      NOT NULL DEFAULT 1 COMMENT '是否启用：0=禁用，1=启用',
    `is_default`  TINYINT      NOT NULL DEFAULT 0 COMMENT '是否默认风格：0=否，1=是（系统恒有 1 个）',
    `sort_order`  INT          NOT NULL DEFAULT 0 COMMENT '排序权重（升序）',
    `version`     INT          NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=正常，1=已删除',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_review_style_name` (`name`, `deleted`),
    KEY `idx_enabled` (`enabled`),
    KEY `idx_is_default` (`is_default`),
    KEY `idx_sort_order` (`sort_order`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = '评审风格';

-- 种子数据：4 个原型风格，全部启用；"标准" 为默认风格
-- rules 字段 JSON 数组，每个对象含 label 与 content 字段
INSERT INTO `review_style` (`name`, `icon`, `scenario`, `rules`, `enabled`, `is_default`, `sort_order`)
VALUES
('宽松',
 '⚡',
 '小迭代 · 紧急优化 · 功能微调',
 '[{"label":"问题级别","content":"仅排查 P0 / P1"},{"label":"输出上限","content":"最多 5 条"},{"label":"忽略项","content":"体验细节、文案、非刚需建议"},{"label":"输出格式","content":"精简扼要"}]',
 1, 0, 10),

('务实',
 '🎯',
 '排期紧 · 快速试错 · 敏捷迭代',
 '[{"label":"问题分档","content":"本期必改 / 下期迭代 / 可接受"},{"label":"成本标注","content":"每条标注整改成本 小/中/大"},{"label":"建议要求","content":"拒绝空泛，贴合落地场景"},{"label":"输出格式","content":"可直接作为排期输入"},{"label":"评审态度","content":"务实导向，优先输出可立即执行项"}]',
 1, 0, 20),

('标准',
 '📋',
 '常规立项 · 正式版本评审',
 '[{"label":"问题级别","content":"全维度，P0/P1 必改，P2 可选"},{"label":"输出要求","content":"描述完整、风险清晰、建议落地"},{"label":"评审态度","content":"中立客观，不宽松不苛刻"},{"label":"输出格式","content":"标准结构化报告"},{"label":"覆盖范围","content":"功能、流程、体验、技术、商业全面覆盖"},{"label":"问题去重","content":"同类问题合并表述"}]',
 1, 1, 30),

('严谨',
 '🔬',
 '大版本 · 核心功能 · 商业化付费功能',
 '[{"label":"问题级别","content":"全量检出，P2 升级为整改项"},{"label":"必填字段","content":"每条附影响范围 + 出现概率"},{"label":"覆盖范围","content":"含隐性体验、技术债、轻度合规"},{"label":"输出格式","content":"超详细整改方案，零容忍"},{"label":"评审态度","content":"高标准严要求，挑战每一处假设"},{"label":"风险标注","content":"标注潜在最坏后果与触发条件"},{"label":"复核机制","content":"对所有关键决策提出反向论证"}]',
 1, 0, 40);
