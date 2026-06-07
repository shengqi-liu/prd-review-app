# Audit: add-rbac-permissions

完成日期：2026-06-07

## 自检结果

| 检查项 | 结果 |
|--------|------|
| 单元测试 10/10 通过 | ✅ |
| 全量测试 30/30 通过（含 ArchUnit、ErrorCode、Result、bootstrap 启动） | ✅ |
| ADMIN 可访问 ADMIN 专属接口 | ✅ |
| SUBMITTER/TEAM_MEMBER 访问 ADMIN 专属接口 → FORBIDDEN(20002) | ✅ |
| 多角色 OR 语义正确 | ✅ |
| 未登录访问 → UNAUTHORIZED(20001) | ✅ |
| 方法级注解覆盖类级注解 | ✅ |
| AccessDeniedHandler 接入 SecurityConfig | ✅ |
| api 模块 pom 添加 spring-boot-starter-aop | ✅ |

## Spec 验收

- [x] @RequireRole 注解可标注在方法和类上
- [x] AOP 拦截角色不匹配时抛 BizException(FORBIDDEN)
- [x] 未登录时抛 BizException(UNAUTHORIZED)
- [x] 方法级注解优先于类级注解
- [x] AccessDeniedExceptionHandler 返回标准 JSON，HTTP 200
