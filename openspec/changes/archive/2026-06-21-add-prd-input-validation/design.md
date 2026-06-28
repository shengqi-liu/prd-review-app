## Context

PRD 的"质量门槛"概念在 #5 阶段已通过错误码 30002/30003 预留,但实现一直空着。本 change 把这层门槛真正落地。

校验逻辑的两个候选位置:
- **API 层 `@Valid` 注解**:声明式,但只能管单字段长度;无法做"必须含某些章节"这种语义规则
- **Domain 层纯函数**:可表达任意业务规则,DDD 范式契合;但需要 application service 主动调用

选 Domain 层。校验是 PRD 的"提交质量"业务规则,本质属于 Prd 聚合的不变量(只是当前在 submit 这个动作上检查)。

## Goals / Non-Goals

**Goals:**
- 提交评审前确认 PRD 内容字数达标、含必要章节
- 错误信息精确指出问题(哪个字段、缺哪个章节、当前字数/阈值),用户能立刻修正
- 校验逻辑独立可测(纯函数,Mockito 都不需要)
- 不影响草稿编辑体验(create/update 仍宽松)

**Non-Goals:**
- 不引入 Markdown 解析库
- 不评估"内容好坏"——这是 AI 评审的职责
- 不做章节顺序校验
- 不在 update 时校验(草稿期允许任意状态)
- 不做异步校验(纯字符串处理,毫秒级,同步调用)

## Decisions

### D1:验证器放 domain 层为 `public final class` + 静态方法

```java
public final class PrdInputValidator {
    public static final int MIN_TITLE_LENGTH = 5;
    public static final int MIN_CONTENT_LENGTH = 200;
    public static final int MIN_REQUIRED_SECTIONS = 2;

    public static void validateForSubmit(String title, String content) {
        validateLength(title, content);
        validateSections(content);
    }
    // 私有辅助方法...
    private PrdInputValidator() {}
}
```

**理由**:
- 纯函数 + 静态方法是验证器经典模式,无状态、易测、易复用
- 不引入 Spring Bean 注入(没必要)
- 阈值是 `public static final` 常量,测试与调用方都可引用
- 私有构造防止误实例化

**备选 A**:做成 Spring `@Component`,阈值通过 `@Value` 注入——过度设计,验证规则不需要环境配置
**备选 B**:做成 Prd 聚合根的 `validateForSubmit()` 实例方法——耦合 domain 与"校验失败抛业务异常"两种关注点,且 Prd 的字段一旦校验通过,后续重新 submit(撤回再提)还要重跑

### D2:章节同义词用枚举 + Set

```java
public enum RequiredSection {
    BACKGROUND("背景", Set.of("背景", "概述", "overview", "background")),
    GOAL("目标", Set.of("目标", "产品目标", "goal", "goals", "objective", "objectives")),
    DESIGN("功能设计", Set.of("功能设计", "功能", "功能方案", "方案", "solution", "design"));

    private final String displayName;
    private final Set<String> aliases;  // 小写形式

    public boolean matches(String heading) {
        return aliases.contains(heading.trim().toLowerCase(Locale.ROOT));
    }
}
```

**理由**:
- 同义词作为枚举的属性,集中维护,新增类别只改一处
- `displayName` 用于错误消息(给用户看的友好名),`aliases` 用于匹配
- 用 Set 而非 List 是为了 O(1) 查找

### D3:章节抽取正则

```java
// 匹配 markdown 一级/二级标题,捕获标题文本
private static final Pattern HEADING_PATTERN =
    Pattern.compile("^\\s*#{1,2}\\s+(.+?)\\s*$", Pattern.MULTILINE);
```

**理由**:
- 只匹配 H1/H2,与 PRD 习惯(顶层结构用一级,子节用二级)契合
- `^\\s*` 允许行首有空白(虽然不规范,但宽容处理)
- 标题尾部的 `#` 装饰符不做特殊处理(`# 背景 #`)——`.+?` 非贪婪后跟 `\\s*$`,会包含尾部 `#`;但 RequiredSection.matches() 用 `aliases.contains`,精确匹配,不会误判
- 不支持 setext 风格标题(`背景\n===`)——markdown 主流是 ATX 风格

**备选**:用 commonmark/flexmark 库——本场景不需要那种保真度,引入依赖反而增加复杂度

### D4:错误消息策略

校验失败时,`BizException` 的 `message` 字段携带具体信息(error code 已存在):

```
title 太短:当前 3 字符,最少 5 字符
content 太短:当前 80 字符,最少 200 字符
缺少必要章节:目标, 功能设计(已识别:背景)
```

**理由**:用户看到错误码能定位类型,看消息能直接修正,不需要再查文档。

`GlobalExceptionHandler.handleBizException` 已经把 `ex.getMessage()` 透出到响应,无需改动。

### D5:校验时机 — 仅 submit

```java
@Transactional
public PrdDTO submit(Long prdId, Long currentUserId) {
    Prd prd = requirePrd(prdId);
    if (!prd.isOwnedBy(currentUserId)) {
        throw new BizException(ErrorCode.FORBIDDEN);
    }
    PrdInputValidator.validateForSubmit(prd.getTitle(), prd.getContent()); // ← 新增
    prd.submit(); // DRAFT → SUBMITTED 状态机
    prdRepository.update(prd);
    // 创建版本快照
    prdVersionRepository.save(PrdVersion.snapshot(prd));
    return PrdDTO.from(prd);
}
```

**理由**:
- 草稿期允许任意修改(用户在打草稿),不能验证
- create 时也不验证(URL 路径 INITIALIZING 状态 title/content 都是空的)
- 只有 submit 是"用户明确说我写好了,送去评审"——这是质量门槛的合理触发点

### D6:有效字符数计算

```java
int effectiveLength(String s) {
    if (s == null) return 0;
    return s.replaceAll("\\s+", "").length();
}
```

只用于"是否过短"的判定,**不修改** PRD 的真实 content(原始内容含换行/缩进对 markdown 渲染重要)。

**理由**:防止用户用空格/换行刷字数(`"           "` 算 11 字符就不合理)。

## Risks / Trade-offs

- **[误杀合法 PRD]** 比如用户的章节叫"方案概要"而不是同义词里任何一个 → 抛 PRD_MISSING_REQUIRED_SECTION。Mitigation:同义词集合宽泛覆盖常见叫法;错误消息明确告知"缺少 xxx",用户可以改章节名绕过。后续如有用户反馈高频别名,扩展枚举即可
- **[正则匹配代码块里的 `#`]** 比如用户写 ```bash ... # comment```,正则会把 `# comment` 当成 H1 标题。Mitigation:`# comment` 不在任何 RequiredSection 同义词里,不会误命中;但会污染"已识别章节"列表。当前阶段可接受,大概率不影响校验结果
- **[阈值定得太死]** 200 字符门槛对某些简单需求过严,5 字符门槛对中文标题刚好(平均 3-4 字)。Mitigation:阈值是 public static final,后续好改;真要改成可配置,加 `@ConfigurationProperties` 即可
- **[英文 PRD 通过门槛但中文同名章节失败]** 比如用户标题写英文 "## Goal" 通过,但中文 "## 目标" 也通过——两种风格都已覆盖在同义词里,不冲突

## Migration Plan

1. **代码**:domain → application 逐层加,无外部依赖
2. **测试**:`PrdInputValidatorTest` 全覆盖边界条件;`PrdApplicationServiceTest.submitPrd` 增加失败用例
3. **DB/前端**:无变更
4. **回滚**:revert commit 即可;线上若已有依赖此校验的 PRD,回滚后退化为旧行为(允许低质内容提交)
5. **验证**:端到端跑 create PRD(短内容)→ submit → 预期 400 + PRD_CONTENT_TOO_SHORT;扩充 content 到 200+ 但只有 1 个章节 → submit → 预期 400 + PRD_MISSING_REQUIRED_SECTION;补齐 2 章节 → submit → 成功
