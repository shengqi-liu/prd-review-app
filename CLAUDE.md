# CLAUDE.md — AI 编辑器项目导航

## 项目概要

AI 产品方案评审系统：用户提交 PRD → 选择 AI 评审员 + 评审风格 → 系统结合业务知识库（RAG）执行评审 → 输出三态结论（通过/修改后通过/不通过）+ 分级问题报告。

## 模块映射

| 模块 | 路径 | 放什么 |
|------|------|-------|
| `domain` | `domain/src/main/java/com/prdreview/<ctx>/` | 聚合根、实体、值对象、Repository 接口、领域事件 |
| `application` | `application/src/main/java/com/prdreview/<ctx>/` | ApplicationService（用例编排）、事务注解 |
| `infrastructure` | `infrastructure/src/main/java/com/prdreview/<ctx>/` | Repository 实现（MyBatis-Plus）、外部客户端 |
| `api` | `api/src/main/java/com/prdreview/<ctx>/` | Controller、Request/Response DTO |
| `bootstrap` | `bootstrap/src/main/java/com/prdreview/bootstrap/` | 主类、全局 Config Bean |

## 关键公共类（api/com.prdreview.common）

| 类 | 用途 |
|----|------|
| `Result<T>` | 统一响应包装，`Result.success(data)` / `Result.error(ErrorCode)` |
| `ErrorCode` | 错误码枚举，按段位分域 |
| `BizException` | 业务异常，`throw new BizException(ErrorCode.XXX)` |
| `GlobalResponseAdvice` | 自动包装 Controller 返回值，勿在 Controller 手动包 Result |
| `GlobalExceptionHandler` | 全局异常捕获，勿在 Controller try-catch 业务异常 |
| `TraceIdFilter` | 请求入口注入 traceId 到 MDC |

## 开发约定

### 新增 Controller
```java
// api 模块，com.prdreview.<ctx>.controller 包
@Tag(name = "XXX")
@RestController
@RequestMapping("/api/v1/<resource>")
public class XxxController {
    @Operation(summary = "xxx")
    @GetMapping("/{id}")
    public XxxResponse get(@PathVariable Long id) {
        // 直接返回业务对象，GlobalResponseAdvice 自动包装
        return service.get(id);
    }
}
```

### 抛业务异常
```java
throw new BizException(ErrorCode.PRD_NOT_FOUND);
throw new BizException(ErrorCode.PARAM_INVALID, "字段 title 不能为空");
```

### 新增错误码
在 `ErrorCode` 枚举对应段位加一行，然后跑 `ErrorCodeTest` 验证唯一性。

### 日志
```java
@Slf4j
// log.info/warn/error — traceId 自动携带，无需手动加
log.info("处理 PRD id={}", prdId);
```

## 架构约束（ArchUnit 守护）

- `domain` 包内禁止 `@RestController`、`@Repository` 实现、Spring Web 注解
- 不同 Bounded Context 包之间禁止直接引用（`common`、`system` 除外）
- 跨上下文调用走 `application` 层事件 or `api` 层接口契约

## 配置文件位置

| 文件 | 路径 |
|------|------|
| 公共配置 | `bootstrap/src/main/resources/application.yml` |
| Dev 配置 | `bootstrap/src/main/resources/application-dev.yml` |
| Prod 配置 | `bootstrap/src/main/resources/application-prod.yml` |
| 日志配置 | `bootstrap/src/main/resources/logback-spring.xml` |

## OpenSpec 路线图

详见 `openspec/roadmap.md`，共 24 个 change，当前进行中：`add-project-bootstrap`。
