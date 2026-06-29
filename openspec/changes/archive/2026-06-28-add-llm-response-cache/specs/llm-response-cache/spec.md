## ADDED Requirements

### Requirement: LLM 非流式响应精确 hash 缓存
系统 SHALL 为 `AiService.summarizeText(rawText)` 调用提供精确 hash 缓存层,相同的 `(provider, model, rawText)` 输入 SHALL 直接复用上次的 `SummarizeResult` 输出,跳过远端 LLM 调用。

**缓存 key**:`SHA-256(provider + ':' + model + ':' + (rawText ?? ''))`,固定 64 字符十六进制串。
**缓存值**:`SummarizeResult` 的 JSON 序列化(`{"title":"...","content":"..."}`)。
**生效范围**:`summarizeText` / `summarizeFromUrl` / `summarizeFromFile`(后两者间接走 summarizeText)。
**不缓存**:流式调用 `streamCompletion`、fallback 响应(LLM 返回非预期 JSON 时的降级输出)。

#### Scenario: 首次调用 — miss,写入缓存
- **WHEN** `summarizeText("hello world")` 第一次调用,缓存表无对应 key
- **THEN** 系统 MUST 调用真后端 LLM,得到 `SummarizeResult`,然后插入一条 `llm_cache` 记录:cache_key=SHA256(...)、response=JSON、provider="openai-compatible"、model=配置值、hit_count=0、created_at=now

#### Scenario: 二次调用 — hit,跳过 LLM
- **WHEN** 相同 rawText 第二次调用 `summarizeText`
- **THEN** 系统 MUST 不调用 ChatClient,直接从 `llm_cache` 取出 response,反序列化为 SummarizeResult 返回;同时 `hit_count + 1`,`last_hit_at = now`

#### Scenario: 同 rawText 不同 model 不命中
- **WHEN** 同一 rawText 用 model=A 调一次,再切到 model=B 调
- **THEN** 第二次 MUST miss(key 不同),独立写一条新缓存

#### Scenario: fallback 响应不写缓存
- **WHEN** LLM 返回非预期 JSON,`summarizeText` 触发 fallback(title=前 20 字 + "..."、content=前 500 字 + "...")
- **THEN** 系统 MUST 不写缓存(避免低质量响应被永久固定),下次调用仍走 LLM

---

### Requirement: 缓存层失败不阻塞主流程
缓存查询或写入失败 SHALL NOT 影响 LLM 主流程。任何 SQLException / 序列化错误 SHALL 被捕获并降级为 WARN 日志,继续走原 LLM 调用。

#### Scenario: 缓存查询失败降级
- **WHEN** `LlmCacheService.get(key)` 抛 SQLException
- **THEN** `summarizeText` MUST 捕获后调真 LLM,不向调用方传播缓存异常;日志 WARN 含 "缓存查询失败"

#### Scenario: 缓存写入失败降级
- **WHEN** LLM 调用成功但 `LlmCacheService.put(...)` 抛错
- **THEN** `summarizeText` MUST 仍返回真实 LLM 结果给调用方;日志 WARN 含 "缓存写入失败"

---

### Requirement: 缓存定时清理(TTL + LRU)
系统 SHALL 通过 `LlmCacheCleanupScheduler` 定时清理缓存,默认 1 小时执行一次(`@Scheduled(fixedDelayString = "${llm.cache.cleanup-interval-ms:3600000}")`)。

**TTL 清理**:删除 `created_at < NOW() - INTERVAL N DAY` 的条目(N = `llm.cache.ttl-days`,默认 30)。
**LRU 淘汰**:若条目数 > `llm.cache.max-entries`(默认 10000),按 `last_hit_at ASC` 删除最久未命中的若干条,使剩余条目数等于 max-entries。

#### Scenario: TTL 清理超过 30 天的条目
- **WHEN** 调度器触发,DB 中有 `created_at = NOW - 31 day` 的条目
- **THEN** 该条目 MUST 被 DELETE

#### Scenario: LRU 淘汰超出容量
- **WHEN** 条目数 = 10001,触发清理
- **THEN** 系统 MUST 删除 `last_hit_at` 最早的 1 条,使总数恢复 10000

#### Scenario: 未超容量不动手
- **WHEN** 条目数 = 5000,未到 max-entries
- **THEN** 调度器 MUST 不执行 LRU(仍执行 TTL)

---

### Requirement: ADMIN 缓存管理 REST API
系统 SHALL 提供 `/api/v1/cache/llm` 资源接口供 ADMIN 查询统计 / 手动失效缓存。所有接口 `@RequireRole(ADMIN)`。

- `GET /api/v1/cache/llm/stats` → 返回 `{ totalEntries, totalHits, hitRate, oldestCreatedAt, newestCreatedAt }`
- `DELETE /api/v1/cache/llm` → 清空全部条目,返回 `{ deletedCount }`
- `DELETE /api/v1/cache/llm/{key}` → 按 cache_key 删单条,返回 `{ deleted: true/false }`

#### Scenario: ADMIN 查询统计
- **WHEN** ADMIN 调 `GET /api/v1/cache/llm/stats`
- **THEN** 系统 MUST 返回当前缓存表的总条目数、累计命中数、估算命中率(hits / (hits + entries),粗略)、最老/最新条目时间戳

#### Scenario: 非 ADMIN 被拒绝
- **WHEN** SUBMITTER 调任何 `/api/v1/cache/llm` 接口
- **THEN** 系统 MUST 返回 FORBIDDEN(20002)

#### Scenario: ADMIN 清空全部
- **WHEN** ADMIN 调 `DELETE /api/v1/cache/llm`
- **THEN** 系统 MUST 删除 `llm_cache` 表所有条目,返回 `{ deletedCount: N }`

---

### Requirement: 配置开关与可观测性
系统 SHALL 通过 `llm.cache.*` 配置控制缓存行为:

- `llm.cache.enabled`(默认 true)— false 时缓存层完全跳过(get/put 都不做),所有调用直接走 LLM
- `llm.cache.ttl-days`(默认 30)— TTL 清理阈值
- `llm.cache.max-entries`(默认 10000)— LRU 淘汰阈值
- `llm.cache.cleanup-interval-ms`(默认 3600000 = 1 小时)— 清理调度间隔

每次命中 SHALL 输出 INFO 日志含 `[LLM-Cache] hit key=<前 8 位> hits=<新 hit_count>`,便于运维观察命中率;未命中输出 DEBUG 日志。

#### Scenario: enabled=false 时绕过缓存
- **WHEN** `llm.cache.enabled=false` 启动应用,调用 `summarizeText`
- **THEN** 系统 MUST 不查不写缓存,直接走 LLM,且不输出 `[LLM-Cache]` 日志

#### Scenario: 命中输出 INFO 日志
- **WHEN** 缓存命中
- **THEN** 系统 MUST 输出 INFO 日志,包含 cache_key 前 8 位、新的 hit_count
