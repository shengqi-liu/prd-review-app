# AI 产品方案评审系统 · 项目路线图

> 本文档登记所有 OpenSpec change，作为项目的导航地图。每个 change 完成审计归档后必须更新对应状态。

## 项目简介

AI 产品方案评审系统：用户提交 PRD → 选择 AI 评审员 + 评审风格 → 系统结合业务知识库（RAG）执行评审 → 输出三态结论（通过 / 修改后通过 / 不通过）+ 分级问题报告。

技术栈：Java 21 + Maven + Spring Boot 3.x + Spring AI 1.0 + Claude API + MySQL 8.0 + MyBatis-Plus + Chroma 向量库 + Next.js 前端。

## 状态图例

| 标记 | 含义 |
|------|------|
| 🔲 `NOT_STARTED` | 未开始 |
| 🟡 `IN_PROGRESS` | 实现中 |
| 🔵 `IN_REVIEW` | 审计中 |
| ✅ `DONE` | 已归档 |

## 阶段总览

```
阶段一 地基（串行） ──→ 阶段二 基础领域（可并行） ──→ 阶段三 知识库（独立） ──→ 阶段四 评审闭环（依赖前置）
   4 个 change            6 个 change                4 个 change                10 个 change
```

合计 **24 个 change**。

---

## 阶段一 · 地基（必须串行完成）

| # | Change ID | 名称 | 依赖 | 状态 | 完成日期 |
|---|-----------|------|------|------|---------|
| 1 | `add-project-bootstrap` | 项目脚手架 + 路线图 | — | ✅ DONE | 2026-06-07 |
| 2 | `add-infra-compose` | Docker Compose 编排（MySQL 8.0 + Chroma） | #1 | ⏸ DEFERRED | — |
| 3 | `add-auth-foundation` | 用户实体 + 登录会话（JWT） | #1 | ✅ DONE | 2026-06-07 |
| 4 | `add-rbac-permissions` | 角色与权限 AOP 拦截 | #3 | ✅ DONE | 2026-06-07 |

## 阶段二 · 基础领域（可并行）

| # | Change ID | 名称 | 依赖 | 状态 | 完成日期 |
|---|-----------|------|------|------|---------|
| 5 | `add-prd-storage` | PRD 实体 + CRUD + 版本表（MyBatis-Plus） | #4 | 🔲 NOT_STARTED | — |
| 6 | `add-prd-input-validation` | 最低输入门槛 + 章节解析 | #5 | 🔲 NOT_STARTED | — |
| 7 | `add-document-parsing` | PDF/Word → 结构化文本（Tika） | #5 | 🔲 NOT_STARTED | — |
| 8 | `add-reviewer-management` | AI 评审员 CRUD + Prompt 模板 | #4 | 🔲 NOT_STARTED | — |
| 9 | `add-reviewer-test-endpoint` | Prompt 试跑接口 | #8 | 🔲 NOT_STARTED | — |
| 10 | `add-review-styles` | 评审风格 + 规则配置 | #4 | 🔲 NOT_STARTED | — |

## 阶段三 · 知识库与 RAG（独立链路）

| # | Change ID | 名称 | 依赖 | 状态 | 完成日期 |
|---|-----------|------|------|------|---------|
| 11 | `add-kb-git-watcher` | JGit 轮询 + 变更检测 | #1 | 🔲 NOT_STARTED | — |
| 12 | `add-kb-markdown-indexing` | Markdown 分块 + BGE Embedding + Chroma 写入 | #11 | 🔲 NOT_STARTED | — |
| 13 | `add-kb-retrieval` | 实体提取 + 向量检索 + 降级提示 | #12 | 🔲 NOT_STARTED | — |
| 14 | `add-kb-management-api` | 索引状态 + 检索测试 + 文档列表 API | #13 | 🔲 NOT_STARTED | — |

## 阶段四 · 评审闭环（按顺序推进）

| # | Change ID | 名称 | 依赖 | 状态 | 完成日期 |
|---|-----------|------|------|------|---------|
| 15 | `add-prompt-composer` | 评审员 × 风格 × RAG 上下文 Prompt 拼装 | #8, #10, #13 | 🔲 NOT_STARTED | — |
| 16 | `add-claude-integration` | Spring AI Anthropic + 缓存 + 流式 | #1 | 🔲 NOT_STARTED | — |
| 17 | `add-internal-review` | 内审：简化 Prompt + SSE + 不归档 | #5, #15, #16 | 🔲 NOT_STARTED | — |
| 18 | `add-formal-review-orchestration` | 多 Agent 虚拟线程并行 + 状态机 | #17 | 🔲 NOT_STARTED | — |
| 19 | `add-finding-aggregation` | 多 Agent 结果去重 + 冲突标注 | #18 | 🔲 NOT_STARTED | — |
| 20 | `add-review-conclusion` | 三态判定 + 内部评分 | #19 | 🔲 NOT_STARTED | — |
| 21 | `add-report-storage` | 报告归档 + 版本绑定 + 历史查询 | #20 | 🔲 NOT_STARTED | — |
| 22 | `add-followup-chat` | 评审意见追问（10 轮上限） | #21 | 🔲 NOT_STARTED | — |
| 23 | `add-feedback-collection` | 有用/没用反馈采集 | #21 | 🔲 NOT_STARTED | — |
| 24 | `add-report-export` | PDF/Word 导出 | #21 | 🔲 NOT_STARTED | — |

---

## 依赖关系示意

```
add-project-bootstrap (#1)
        │
        ├──→ add-infra-compose (#2) ──→ add-prd-storage (#5) ──→ #6, #7
        │           │
        │           └──→ add-kb-git-watcher (#11) ──→ #12 ──→ #13 ──→ #14
        │
        ├──→ add-auth-foundation (#3) ──→ add-rbac-permissions (#4)
        │                                       │
        │                                       ├──→ #5 (PRD 域)
        │                                       ├──→ add-reviewer-management (#8) ──→ #9
        │                                       └──→ add-review-styles (#10)
        │
        └──→ add-claude-integration (#16)
                    │
                    └──┬──→ #8, #10, #13 汇合 ──→ add-prompt-composer (#15)
                       │                                  │
                       └──────────────────────────────────┴──→ add-internal-review (#17)
                                                                    │
                                                                    └──→ add-formal-review-orchestration (#18)
                                                                              │
                                                                              └──→ #19 ──→ #20 ──→ #21
                                                                                                    │
                                                                                                    ├──→ #22 (追问)
                                                                                                    ├──→ #23 (反馈)
                                                                                                    └──→ #24 (导出)
```

## 关键里程碑

| 里程碑 | 触达 change | 可演示能力 |
|--------|------------|-----------|
| M1：骨架可运行 | #1 完成 | 启动应用，访问健康检查与 Swagger |
| M2：登录可用 | #4 完成 | 用户登录 + 权限控制 |
| M3：PRD 可提交 | #7 完成 | 创建/上传/版本管理 PRD |
| M4：RAG 可检索 | #14 完成 | 业务知识库检索 |
| M5：MVP 评审走通 | #18 完成 | 多 Agent 并行评审一份 PRD |
| M6：完整产品就绪 | #24 完成 | 全流程上线 |

## 审计闭环

每个 change 完成实现后，必须依次执行：

1. **自检**：单元测试 + 集成测试 + 本地启动验证
2. **代码审计**：`/code-review`（medium 档），结果记入 `audit.md`
3. **安全审计**：`/security-review`（仅涉及鉴权、外部输入、密钥的 change）
4. **Spec 验收**：对照 spec 逐条 requirement 检查
5. **同步归档**：`openspec-sync-specs` + `openspec-archive-change`，更新本文档状态

## 后续维护

- 新增 change 时在对应阶段表格追加一行
- 状态变更立即更新（避免离线累积）
- 每月做一次依赖关系复核，识别可并行优化的边
