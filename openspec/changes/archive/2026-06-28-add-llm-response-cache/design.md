## Context

`AiServiceImpl.summarizeText` 是当前 LLM 调用的核心入口(URL/文件路径都汇集到此)。本 change 在该方法头部加一层"查缓存 → 命中复用 / miss 走 LLM + 写缓存"逻辑,对调用方完全透明。

技术选型核心权衡:
- **存储**:MySQL(已有)vs 引入 Redis → 单机够用,选 MySQL
- **粒度**:粗粒度缓存 SummarizeResult JSON,而非细粒度 prompt 模板替换。理由:缓存语义就是"完全相同的 LLM 输入直接复用输出"
- **失效**:TTL(防模型升级)+ LRU(防容量爆炸)双策略,1 小时定时清

## Goals / Non-Goals

**Goals:**
- 相同 LLM 输入(provider + model + rawText)的重复调用 → 命中缓存,跳过远端
- 缓存层失败/超时 → 自动 fallback 到原 LLM 调用(缓存永不阻塞主流程)
- ADMIN 能查命中率、手动失效条目
- 不需要修改 `AiService` 接口签名(对所有现有调用方透明)

**Non-Goals:**
- 不缓存流式响应
- 不引入新基础设施(Redis 等)
- 不做按用户隔离 / 按租户隔离
- 不做语义相似匹配(精确 hash only)

## Decisions

### D1:缓存 key 由 SHA-256(provider + ':' + model + ':' + rawText) 组成

```java
String composite = provider + ":" + model + ":" + (rawText == null ? "" : rawText);
String key = sha256Hex(composite); // 64 字符
```

**理由**:
- 64 字符固定长度,作为 DB 主键索引高效
- provider 和 model 作为 key 的一部分:换 provider(DeepSeek → OpenAI)或换 model(deepseek-chat → gpt-4)自动失效,不会复用旧结果
- rawText 作为正文部分:同一文本命中
- SHA-256 几乎不会碰撞(相同 key = 99.999...% 真是相同输入)

**备选 A**:MD5 — 速度快但已知不抗碰撞,即便缓存场景影响小,选 SHA-256 更稳
**备选 B**:用 prompt 全文作 key — 太长,VARCHAR(65535) 索引不友好

### D2:缓存值存 SummarizeResult JSON 字符串

```java
String json = objectMapper.writeValueAsString(result); // {"title":"...","content":"..."}
```

**理由**:
- 已经有 ObjectMapper,序列化零成本
- 反序列化时直接 `objectMapper.readValue(json, SummarizeResult.class)`
- TEXT 类型存,够长

### D3:只缓存成功响应,失败不缓存

`summarizeText` 内部如果 ChatClient 返回非预期 JSON 触发 fallback(title=前 20 字,content=前 500 字),fallback 结果**不写缓存**——避免低质量响应被永久固定。

```java
SummarizeResult result = parseResult(aiResponse, rawText);
// 只在解析成功(非 fallback)时写缓存
if (!isFallback(result, rawText)) {
    cacheService.put(key, result, ...);
}
```

如何判定 fallback?最简单:`fallback()` 返回的结果 title 以 "..." 结尾且长度恰为 23(或 content 以 "..." 结尾且长度恰为 503)。但这个判断脆弱。

**更稳的方案**:在 `parseResult` 内部加一个出参 / 让 fallback 抛 sentinel exception,或者 fallback 单独路径触发(parseResult 返回 Optional)。本 change 加一个 `parseResultIfValid` 返回 `Optional<SummarizeResult>`,只在 present 时写缓存。

### D4:LlmCacheService 失败不阻塞主流程

```java
SummarizeResult result;
String key = ...;
try {
    Optional<SummarizeResult> cached = cacheService.get(key);
    if (cached.isPresent()) {
        log.info("[LLM-Cache] hit key={} ...", key.substring(0, 8));
        return cached.get();
    }
} catch (Exception ex) {
    log.warn("[LLM-Cache] 查询失败,跳过缓存走 LLM: {}", ex.getMessage());
}
// 走 LLM
result = callLlm(rawText);
try {
    cacheService.put(key, result, ...);
} catch (Exception ex) {
    log.warn("[LLM-Cache] 写入失败,不影响响应: {}", ex.getMessage());
}
return result;
```

**理由**:缓存是优化层,任何故障(DB 慢、磁盘满)都不该影响 AI 调用本身的可用性。

### D5:LRU 淘汰 + TTL 清理用单一 Scheduler

`LlmCacheCleanupScheduler`:`@Scheduled(fixedDelayString = "${llm.cache.cleanup-interval-ms:3600000}")`,默认 1 小时一次,执行两步:

1. **TTL 清**:`DELETE FROM llm_cache WHERE created_at < NOW() - INTERVAL N DAY`(N = ttl-days,默认 30)
2. **LRU 淘汰**:`SELECT COUNT(*)`,超过 max-entries(默认 10000)时 `DELETE WHERE cache_key IN (SELECT cache_key ORDER BY last_hit_at ASC LIMIT (count - max))`

**理由**:
- 清理频率低(1 小时),DB 影响可忽略
- TTL 优先(老条目几乎不再命中)
- LRU 只在容量真超时才动手,日常无负担

### D6:hit_count + last_hit_at 用乐观锁不重要,直接 UPDATE 即可

每次命中要 `UPDATE llm_cache SET hit_count=hit_count+1, last_hit_at=NOW() WHERE cache_key=?`。并发命中可能两个事务都 +1 但只有一个生效——不影响功能,只影响统计准确性(误差 ±1),可接受。

不引入乐观锁版本字段,降低复杂度。

### D7:provider/model 怎么知道?

`AiServiceImpl` 当前没有显式持有 provider/model 信息(它从 ChatClient.Builder 拿 ChatClient,provider/model 是 Spring AI 自动配置的)。

方案:
- 让 `AiServiceImpl` 通过 `@Value("${spring.ai.openai.chat.options.model:unknown}")` 注入 model 名;provider 用常量 "openai-compatible"(因为我们走的是 OpenAI 兼容协议指向 DeepSeek)
- 不依赖 Spring AI 内部 API(可能不稳定)
- 后续换 provider 直接改 properties 即可

```java
@Value("${spring.ai.openai.chat.options.model:unknown}")
private String llmModel;
private static final String LLM_PROVIDER = "openai-compatible";
```

### D8:API 设计

```
GET    /api/v1/cache/llm/stats        → CacheStatsResponse(total_entries, total_hits, hit_rate?, oldest, newest)
DELETE /api/v1/cache/llm              → 清空全部,返回 deleted_count
DELETE /api/v1/cache/llm/{key}        → 删单条,返回 success/notfound
```

所有接口 `@RequireRole(ADMIN)`。无 POST/PUT — 缓存条目由 AiServiceImpl 自动写入,不暴露手写。

stats 的 `hit_rate` 计算:`sum(hit_count) / (sum(hit_count) + cumulative_writes)`,但 cumulative_writes 没存,简化为 `sum(hit_count) / total_entries`(每个条目至少经过一次 miss 才进缓存,所以分母至少等于 total_entries,粗略可用)。

## Risks / Trade-offs

- **[缓存值过时(模型升级)]** → TTL 30 天保底失效;ADMIN 紧急清空 API 兜底
- **[LRU 删除策略偶尔删错"高价值低频"条目]** → 可接受,反正 miss 一次重新算就有
- **[并发命中 hit_count 误差]** → 仅影响统计,不影响功能;不引入乐观锁是务实选择
- **[缓存命中误判]** → 用 SHA-256 几乎无碰撞;同 provider 同 model 同 rawText 复用响应是设计目的
- **[fallback 响应被误缓存]** → D3 解决,fallback 不写缓存
- **[缓存表无限增长 → 影响数据库性能]** → max-entries 10000 + LRU 清理保底,1 小时一次

## Migration Plan

1. **DB 迁移**:执行 V8 创建 `llm_cache` 表
2. **代码**:domain → infrastructure → application → api → 配置,无对外接口契约变更
3. **验证**:启动后调一次 `summarizeText("hello world")`(URL 导入触发) → 看日志 INFO 含 `[LLM-Cache] miss + put`;再次相同输入调用 → 看 INFO 含 `[LLM-Cache] hit`
4. **回滚**:revert commit + `DROP TABLE llm_cache`;`llm.cache.enabled=false` 也可在线关闭缓存功能但不删表
