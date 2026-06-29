## 1. 数据库迁移

- [x] 1.1 创建 `db/migration/V8__create_llm_cache_table.sql`:
  ```sql
  CREATE TABLE IF NOT EXISTS `llm_cache` (
      `cache_key`            VARCHAR(64) NOT NULL COMMENT 'SHA-256 hex of provider:model:rawText',
      `response`             TEXT        NOT NULL COMMENT 'SummarizeResult JSON',
      `provider`             VARCHAR(50) NOT NULL,
      `model`                VARCHAR(100) NOT NULL,
      `prompt_preview`       VARCHAR(200) COMMENT '前 200 字符,运维查询用,不含敏感全文',
      `token_count_estimate` INT          DEFAULT 0 COMMENT 'rawText.length / 4 粗略估算',
      `hit_count`            INT NOT NULL DEFAULT 0,
      `created_at`           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
      `last_hit_at`          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
      PRIMARY KEY (`cache_key`),
      KEY `idx_last_hit_at` (`last_hit_at`),
      KEY `idx_created_at` (`created_at`)
  ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='LLM 响应精确 hash 缓存';
  ```
- [x] 1.2 执行迁移:`mysql -uroot -p<pwd> --default-character-set=utf8mb4 prd_review < db/migration/V8__create_llm_cache_table.sql`

## 2. Domain 层

- [x] 2.1 创建 `domain/.../ai/cache/LlmCacheEntry.java` 值对象(record):cacheKey、response(JSON)、provider、model、promptPreview、tokenCountEstimate、hitCount、createdAt、lastHitAt
- [x] 2.2 创建 `domain/.../ai/cache/LlmCacheRepository.java` 接口:
  - `Optional<LlmCacheEntry> findByKey(String key)`
  - `void save(LlmCacheEntry entry)`
  - `void incrementHit(String key)` — UPDATE hit_count+1, last_hit_at=NOW WHERE cache_key=?
  - `int deleteByKey(String key)` — 返回影响行数(0/1)
  - `int deleteAll()` — 清空,返回行数
  - `int deleteByCreatedAtBefore(LocalDateTime cutoff)` — TTL 清理
  - `int deleteLeastRecentlyHit(int keepCount)` — LRU 淘汰,保留前 keepCount 条
  - `long count()` — 当前条目数
  - `CacheStats stats()` — 总条目 / 总命中 / 最老/最新 created_at

## 3. Infrastructure 层

- [x] 3.1 创建 `infrastructure/.../ai/cache/LlmCachePO.java`(`@TableName("llm_cache")`)— 字段映射,无逻辑删除,无乐观锁
- [x] 3.2 创建 `LlmCacheMapper.java`(继承 `BaseMapper<LlmCachePO>`)
- [x] 3.3 创建 `LlmCacheAssembler.java`:PO ↔ LlmCacheEntry 双向转换
- [x] 3.4 创建 `LlmCacheRepositoryImpl.java` 实现接口:
  - `findByKey`:`mapper.selectById(key)` → Optional
  - `save`:`mapper.insert(po)` 或 `mapper.updateById(po)`(`existsById` 判断;但更常见是 insert,因为 key 已 hash 重复极少)
  - `incrementHit`:用 `mapper.update(null, new LambdaUpdateWrapper<>().eq(...).setSql("hit_count=hit_count+1, last_hit_at=NOW()"))`
  - `deleteByCreatedAtBefore`:`mapper.delete(new LambdaQueryWrapper<>().lt(LlmCachePO::getCreatedAt, cutoff))`
  - `deleteLeastRecentlyHit(keepCount)`:子查询保留最近命中的 keepCount,删其余;MyBatis-Plus 不便表达,可用 `@Select` 自定义 SQL 或先 select id 再 delete
  - `stats`:聚合查询 `SELECT COUNT(*) AS total, SUM(hit_count) AS hits, MIN(created_at), MAX(created_at) FROM llm_cache`,用 `@Select` 自定义

## 4. Application 层

- [x] 4.1 创建 `application/.../ai/cache/LlmCacheService.java`:
  - 注入 `LlmCacheRepository` + `LlmCacheProperties`(infrastructure 层 `@ConfigurationProperties`)
  - `Optional<String> get(String key)`:enabled=false 直接返回 empty;否则查 Repository,命中后异步 `incrementHit`(不阻塞返回) + INFO 日志
  - `void put(String key, String response, String provider, String model, String preview, int tokenEstimate)`:enabled=false 跳过;否则构造 LlmCacheEntry 调 save
  - `CacheStatsDTO stats()` / `int clearAll()` / `boolean clearOne(String key)`
  - 所有方法 try-catch SQLException,记 WARN 不传播
- [x] 4.2 创建 `LlmCacheService` 用的工具 `LlmCacheKeys`:
  - `static String compute(String provider, String model, String rawText)` — SHA-256 hex
  - `static String preview(String rawText)` — 取前 200 字符,replace 换行为空格

## 5. AiServiceImpl 接入缓存

- [x] 5.1 修改 `infrastructure/.../ai/AiServiceImpl.java`:
  - 构造函数注入 `LlmCacheService llmCacheService`(用 `@Autowired(required = false)` 允许测试 mock null)
  - `@Value("${spring.ai.openai.chat.options.model:unknown}")` 注入 `llmModel`
  - 类常量 `private static final String LLM_PROVIDER = "openai-compatible";`
  - `summarizeText(rawText)` 头部:
    ```java
    String key = LlmCacheKeys.compute(LLM_PROVIDER, llmModel, rawText);
    Optional<String> cached = llmCacheService != null ? llmCacheService.get(key) : Optional.empty();
    if (cached.isPresent()) {
        return objectMapper.readValue(cached.get(), SummarizeResult.class);
    }
    ```
  - parseResult 改为 `parseResultIfValid(aiResponse)` 返回 `Optional<SummarizeResult>`:JSON 解析成功且 title/content 非空才 present
  - 主流程:
    ```java
    Optional<SummarizeResult> parsed = parseResultIfValid(aiResponse);
    SummarizeResult result = parsed.orElseGet(() -> fallback(rawText));
    if (parsed.isPresent() && llmCacheService != null) {
        try {
            String json = objectMapper.writeValueAsString(result);
            llmCacheService.put(key, json, LLM_PROVIDER, llmModel,
                LlmCacheKeys.preview(rawText), rawText.length() / 4);
        } catch (Exception e) { log.warn("[LLM-Cache] 写入失败: {}", e.getMessage()); }
    }
    return result;
    ```

## 6. 定时清理调度

- [x] 6.1 创建 `infrastructure/.../ai/cache/LlmCacheCleanupScheduler.java`(`@Component`):
  - `@Scheduled(fixedDelayString = "${llm.cache.cleanup-interval-ms:3600000}")`
  - 调 `llmCacheRepository.deleteByCreatedAtBefore(NOW - ttlDays)` + 若 `count() > maxEntries` 调 `deleteLeastRecentlyHit(maxEntries)`
  - 日志 INFO 输出清理统计

## 7. API 层

- [x] 7.1 创建 `api/.../ai/cache/dto/CacheStatsResponse.java` record(totalEntries / totalHits / hitRate(double) / oldestCreatedAt / newestCreatedAt)
- [x] 7.2 创建 `api/.../ai/cache/dto/CacheDeleteResponse.java` record(deletedCount 或 deleted)
- [x] 7.3 创建 `api/.../ai/cache/controller/LlmCacheController.java`:
  - 类级 `@RequireRole(UserRole.ADMIN)`
  - `GET /api/v1/cache/llm/stats` → CacheStatsResponse(调 service.stats)
  - `DELETE /api/v1/cache/llm` → 返回 `{ deletedCount: N }`
  - `DELETE /api/v1/cache/llm/{key}` → 返回 `{ deleted: true/false }`

## 8. 配置

- [x] 8.1 创建 `infrastructure/.../ai/cache/LlmCacheProperties.java`(`@ConfigurationProperties(prefix = "llm.cache")`):
  - boolean enabled = true
  - int ttlDays = 30
  - int maxEntries = 10000
  - long cleanupIntervalMs = 3600000
- [x] 8.2 `bootstrap/src/main/resources/application.yml` 加配置段:
  ```yaml
  llm:
    cache:
      enabled: true
      ttl-days: 30
      max-entries: 10000
      cleanup-interval-ms: 3600000
  ```

## 9. 测试

- [x] 9.1 `LlmCacheKeysTest`(domain or infrastructure):
  - SHA-256 一致性:同输入同输出
  - 不同 provider/model/rawText 产生不同 key
  - preview 截断与换行替换
- [x] 9.2 `LlmCacheServiceTest`(application,Mock LlmCacheRepository):
  - enabled=false 时 get/put 直接跳过(repo 未被调用)
  - get 命中:返回 Optional present + 异步触发 incrementHit
  - get miss:返回 Optional empty,无副作用
  - put 调用 save 并填充 LlmCacheEntry 各字段
  - stats / clearAll / clearOne 透传
  - 异常路径:repo 抛错时 service 不传播,返回降级值
- [x] 9.3 `AiServiceImplTest.summarizeText_cacheHit_skipsLlm`:
  - Mock `LlmCacheService.get(key)` 返回 present(JSON)
  - 调 `summarizeText` 应返回反序列化结果,ChatClient 不被调用
- [x] 9.4 `AiServiceImplTest.summarizeText_cacheMiss_invokesLlmAndCaches`:
  - Mock `get` 返回 empty
  - ChatClient 返回合法 JSON
  - 验证 `put` 被调用 + 参数正确
- [x] 9.5 `AiServiceImplTest.summarizeText_fallback_doesNotCache`:
  - Mock ChatClient 返回非 JSON 字符串(触发 fallback)
  - 验证 `put` 未被调用
- [x] 9.6 `LlmCacheCleanupSchedulerTest`(Mock repo):
  - TTL 清:验证 `deleteByCreatedAtBefore(NOW - 30day)` 被调
  - 超容量:`count` 返回 10001 → 验证 `deleteLeastRecentlyHit(10000)` 被调
  - 未超容量:不调 LRU

## 10. 集成验证(手工)

- [x] 10.1 启动应用,登录 admin
- [x] 10.2 上传一个 txt/md 文件 → 等摘要完成 → 看日志含 `[LLM-Cache] miss + put` 类似
- [x] 10.3 删除该 PRD,重新上传同样内容的文件 → 日志含 `[LLM-Cache] hit hits=1`,响应明显更快(< 1s 直接返回)
- [x] 10.4 调 `GET /api/v1/cache/llm/stats`(用 admin token)→ 看 totalEntries / totalHits
- [x] 10.5 调 `DELETE /api/v1/cache/llm` → 再次上传 → 又是 miss

## 11. 归档准备

- [x] 11.1 跑全量测试 `mvn clean test`,确认无回归
- [x] 11.2 spec 合并:本 change 是新 capability,把 `specs/llm-response-cache/spec.md` 直接拷到主 spec 目录
- [x] 11.3 归档:`mv openspec/changes/add-llm-response-cache openspec/changes/archive/$(date +%Y-%m-%d)-add-llm-response-cache`
- [x] 11.4 更新 `openspec/roadmap.md`:#16 行(注意 roadmap 老名是 `add-claude-integration`,改写为 `add-llm-response-cache`)状态 ✅ DONE
