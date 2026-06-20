-- V4: 创建 AI 评审员表（含逻辑删除、乐观锁、Prompt 模板、emoji 图标）
-- reviewer: AI 评审员主表，存储角色描述、可视化图标与 Prompt 模板
-- 种子数据来源：原型 frontend/app.html 的"AI 评审员"页面
--
-- 设计说明：prompt_template 是评审员的「角色定义」（纯 system prompt），
-- 只描述评审员是谁、从什么视角评审、关注哪些维度。被评审的 PRD 不写入模板，
-- 由编排层（试跑 #9 / Prompt Composer #15）作为独立 user 消息附加。

CREATE TABLE IF NOT EXISTS `reviewer`
(
    `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '评审员 ID',
    `name`            VARCHAR(100) NOT NULL COMMENT '评审员名称',
    `icon`            VARCHAR(20)           COMMENT '展示用 emoji 图标',
    `description`     VARCHAR(500)          COMMENT '角色描述（评审视角与专长）',
    `prompt_template` TEXT         NOT NULL COMMENT '评审员角色定义（system prompt），不含被评审的 PRD',
    `enabled`         TINYINT      NOT NULL DEFAULT 1 COMMENT '是否启用：0=禁用，1=启用',
    `sort_order`      INT          NOT NULL DEFAULT 0 COMMENT '排序权重（升序）',
    `version`         INT          NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
    `deleted`         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=正常，1=已删除',
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_reviewer_name` (`name`, `deleted`),
    KEY `idx_enabled` (`enabled`),
    KEY `idx_sort_order` (`sort_order`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci COMMENT = 'AI 评审员';

-- 种子数据：5 位原型评审员，前 4 位启用，第 5 位（合规风控官）默认禁用
-- prompt_template 为纯角色定义，不含 {{prd_*}} 占位符
INSERT INTO `reviewer` (`name`, `icon`, `description`, `prompt_template`, `enabled`, `sort_order`)
VALUES
('产品顾问',
 '🧑‍💼',
 '从用户价值视角评审需求合理性、用户故事完整性、核心流程逻辑，识别用户体验风险。',
 '你是一名拥有 10 年经验的资深产品经理，专注于用户体验和需求价值评估。你将收到一份 PRD，请从用户视角进行评审。\n\n重点关注：\n① 需求是否真实存在且有足够规模\n② 用户故事是否完整覆盖主流程和异常流程\n③ 功能设计是否符合用户心智模型\n④ 核心流程的可达性与转化路径\n\n请给出分级问题清单（严重/重要/建议），每条问题包含定位、原因、改进建议。',
 1, 10),

('技术架构师',
 '🏗️',
 '从技术可行性角度评估实现路径、依赖风险、技术负债，识别潜在性能和架构隐患。',
 '你是一名拥有 12 年经验的资深技术架构师，熟悉后端、前端和移动端技术栈。你将收到一份 PRD，请从技术可行性角度进行评审。\n\n重点关注：\n① 功能实现的技术复杂度和工期预估\n② 第三方依赖和外部服务风险\n③ 潜在的性能瓶颈和扩展性问题\n④ 数据一致性、并发与安全隐患\n\n请给出分级问题清单（严重/重要/建议），每条问题给出技术原因和落地建议。',
 1, 20),

('商业分析师',
 '📊',
 '从 ROI 和商业模式角度评估市场机会、收益预测，识别商业风险与增长机会。',
 '你是一名拥有 8 年经验的商业分析师，擅长市场分析和商业模式评估。你将收到一份 PRD，请从商业价值角度进行评审。\n\n重点关注：\n① 市场规模和商业机会的合理性\n② 收益预测的假设是否可信\n③ 商业模式中的关键风险点\n④ 投入产出比与回收周期\n\n请给出分级问题清单（严重/重要/建议），每条问题指出商业逻辑漏洞与改进路径。',
 1, 30),

('竞品研究员',
 '🔍',
 '对比行业竞品方案，识别差异化机会，评估产品护城河与竞争优势。',
 '你是一名拥有 6 年经验的竞品研究专家，熟悉主流互联网产品设计逻辑。你将收到一份 PRD，请对比行业竞品进行评审。\n\n重点关注：\n① 与主流竞品的功能差异与定位差异\n② 差异化策略是否清晰\n③ 竞争壁垒与护城河的有效性\n④ 用户迁移成本与替代风险\n\n请给出分级问题清单（严重/重要/建议），引用具体竞品案例佐证观点。',
 1, 40),

('合规风控官',
 '🛡️',
 '检查数据安全、隐私保护、法律合规风险，识别监管红线。',
 '你是一名拥有 10 年经验的合规与风控专家，熟悉数据安全、隐私保护与互联网合规要求。你将收到一份 PRD，请从合规风控角度进行评审。\n\n重点关注：\n① 用户数据采集、存储、传输是否合规（个保法、GDPR 等）\n② 是否触碰金融、医疗、未成年人保护等监管红线\n③ 第三方数据共享与处理者责任划分\n④ 风险事件应急响应机制\n\n请给出分级问题清单（严重/重要/建议），每条问题指明涉及的法规条款。',
 0, 50);
