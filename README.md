# AI 产品方案评审系统

基于多角色 Agent + 业务知识库的智能 PRD 评审平台。

## 技术栈

| 层级 | 技术 |
|------|------|
| 语言 & 构建 | Java 21 + Maven 3.9+ |
| Web 框架 | Spring Boot 3.2.x（虚拟线程） |
| AI 框架 | Spring AI 1.0 + Claude API |
| ORM | MyBatis-Plus |
| 数据库 | MySQL 8.0（utf8mb4） |
| 向量库 | Chroma 1.5+ |
| 文档 | SpringDoc OpenAPI 3 |

## 模块结构（DDD 分层）

```
prd-review-app/
├── domain/           领域层：聚合根、实体、值对象、Repository 接口
├── application/      应用层：用例编排、事务边界、领域事件
├── infrastructure/   基础设施层：Repository 实现、外部服务集成
├── api/              接口层：Controller、DTO、通用响应/异常/traceId
└── bootstrap/        启动层：主类、全局配置、日志、OpenAPI
```

### 依赖方向

```
bootstrap → api → application → domain ← infrastructure
```

**禁止反向依赖**，由 Maven Enforcer + ArchUnit 双重保障。

### Bounded Context 包约定

```
com.prdreview.<context>.<layer>

context 列表：
  auth          鉴权上下文
  prd           PRD 方案上下文
  reviewer      AI 评审员上下文
  reviewstyle   评审风格上下文
  knowledgebase 知识库上下文
  review        评审编排上下文
  common        横切关注点（Result、ErrorCode、Filter 等）
  system        系统级（健康探针等）
```

## 本地启动

### 前置条件

| 依赖 | 版本 | 说明 |
|------|------|------|
| JDK | 21+ | `java -version` 确认 |
| Maven | 3.9+ | `mvn -v` 确认 |
| MySQL | 8.0+ | 默认 `localhost:3306` |
| Chroma | 1.5+ | 默认 `localhost:8000` |

### 1. 准备数据库

```sql
CREATE DATABASE prd_review CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 2. 准备环境变量

```bash
cp .env.example .env
# 编辑 .env，填入真实密码
```

### 3. 启动 Chroma

```bash
chroma run --path ./chroma-data --port 8000
```

### 4. 启动应用

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
mvn -pl bootstrap spring-boot:run
```

### 5. 验证

| 地址 | 说明 |
|------|------|
| http://localhost:8080/api/v1/ping | 返回 `{"code":0,"data":"pong",...}` |
| http://localhost:8080/actuator/health | 返回 `{"status":"UP"}` |
| http://localhost:8080/swagger-ui/index.html | OpenAPI 文档 |

## 错误码分段

| 段位 | 用途 |
|------|------|
| 0 | 成功 |
| 10000–19999 | 通用错误（参数、资源等） |
| 20000–29999 | 鉴权与权限 |
| 30000–39999 | PRD 域 |
| 40000–49999 | 评审域 |
| 50000–59999 | 知识库域 |
| 60000–69999 | Reviewer / Style 域 |
| 90000–99999 | 系统级错误 |

## 常用命令

```bash
# 构建全项目
mvn clean install

# 仅跑测试
mvn test

# 启动应用
mvn -pl bootstrap spring-boot:run

# 指定 profile 启动
mvn -pl bootstrap spring-boot:run -Dspring-boot.run.profiles=prod
```

## 开发规范

- 业务异常统一用 `throw new BizException(ErrorCode.XXX)`
- Controller 直接返回业务对象，`GlobalResponseAdvice` 自动包装 `Result<T>`
- 日志自动携带 `traceId`（MDC），无需手动写入
- 新增错误码在 `ErrorCode` 枚举按段位添加，并运行 `ErrorCodeTest` 验证唯一性
