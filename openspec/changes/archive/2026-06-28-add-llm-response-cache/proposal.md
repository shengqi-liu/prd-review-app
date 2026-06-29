## Why

当前每次 PRD 摘要(URL 导入/文件上传)和 AI 评审都直调真后端 LLM(DeepSeek API),每次都消耗 token + 3-5 秒延迟。多个高频场景下的浪费很可见:

- 同一份 PRD 用户先 URL 导入再编辑保存,后续重试摘要 → 相同 rawText 重复花钱
- 同一文件被不同用户上传 → 内容相同但每次都进 LLM
- 同一评审员对同一份 PRD 多次试跑(调参时常见) → 完全相同的 prompt 反复算
- 后续 #17/#18 多 Agent 评审落地后,同 PRD 多次提交评审 N 个 Agent 各跑一遍

引入精确 hash 缓存层:**相同 LLM 输入 → 命中直接复用上次响应**,跳过远端调用。降本 + 提速 + 减轻 LLM Provider 限流压力。

## What Changes

- **新增 `llm_cache` DB 表** 持久化缓存(单机部署用 MySQL 而非 Redis)
- **`AiService.summarizeText(rawText)` 接入缓存层**:头部查缓存 → 命中返回 / miss 走原逻辑 + 写缓存
  - `summarizeFromUrl` / `summarizeFromFile` 间接走 summarizeText,自动覆盖
- **缓存 key**:SHA-256(`provider:model:rawText`)— 精确匹配,换 model 自动失效
- **失效策略**:
  - TTL 默认 30 天(防模型升级后老结果污染)
  - 容量上限 10000 条,超过按 `last_hit_at` LRU 淘汰
  - 1 小时定时清理任务
- **ADMIN 管理 API**(`/api/v1/cache/llm`):统计 / 清空 / 单条删
- **观测**:命中输出 INFO 日志含 hit_count,未命中 DEBUG;ADMIN stats 接口返回命中率
- **配置开关**:`llm.cache.enabled` 默认 true,可关闭一次性走 LLM 用于调试

## Capabilities

### New Capabilities

- `llm-response-cache`:LLM 非流式调用结果的精确 hash 持久化缓存

### Modified Capabilities

- `ai-infrastructure`:`AiService.summarizeText` 接入缓存层(对调用方透明,行为不变)

## Impact

- **DB**:新增 `llm_cache` 表(V8 迁移)
- **依赖**:无新增(SHA-256 用 JDK 自带 `MessageDigest`)
- **domain**:`LlmCacheRepository` 接口、`LlmCacheEntry` 值对象
- **infrastructure**:`LlmCacheRepositoryImpl`(MyBatis-Plus)、`LlmCachePO` + `Mapper`、`LlmCacheCleanupScheduler`(@Scheduled 定时清理 TTL + LRU)
- **application**:`LlmCacheService`(组合查/写/统计/淘汰)
- **infrastructure 的 `AiServiceImpl.summarizeText`**:头部加缓存逻辑(provider/model 从 application.yml 注入)
- **api**:`LlmCacheController`(GET stats / DELETE 清空 / DELETE 单条)— `@RequireRole(ADMIN)`
- **bootstrap**:`application.yml` 新增 `llm.cache.*` 配置;`LlmCacheProperties`
- **测试**:`LlmCacheServiceTest`(命中/未命中/LRU/TTL/SHA-256 一致性)、`AiServiceImplTest.summarizeText_cacheHit` 验证缓存命中跳过 ChatClient、`LlmCacheCleanupSchedulerTest`
- **spec**:新增 `openspec/specs/llm-response-cache/spec.md`
- **无前端变更**(管理 API 暂不接入 UI,运维场景用 curl 即可;后续需要再加)

## Out of Scope

- **不缓存流式响应**(`streamCompletion`)— 试跑评审员需要"流式"体验,缓存会破坏;待 #17/#18 真接通多 Agent 评审时,根据用例再评估批量响应是否值得缓存
- **不引入 Redis / Memcached** — 单机部署的 MySQL 够用,且加 Redis 会让部署多一个依赖
- **不做按用户隔离的缓存** — 同一 prompt 在所有用户间共享是设计目标(成本可量化降低)
- **不做"语义相似缓存"** — 只做精确 hash 匹配;近似匹配等 RAG 链路(#12/#13)落地再说
- **不做缓存预热** — 用户实际命中自然产生即可
- **不缓存失败响应** — 仅成功的 LLM 调用(SummarizeResult title/content 都非空)才写缓存
- **不暴露原始 LLM token 数** — `token_count_estimate` 用字符数估算(rawText.length()/4 粗略),只供运维容量评估,不做计费
