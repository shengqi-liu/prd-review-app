## 1. Domain 层

- [x] 1.1 创建 `domain/.../prd/validation/RequiredSection.java` 枚举：
  ```java
  public enum RequiredSection {
      BACKGROUND("背景", Set.of("背景","概述","overview","background")),
      GOAL("目标", Set.of("目标","产品目标","goal","goals","objective","objectives")),
      DESIGN("功能设计", Set.of("功能设计","功能","功能方案","方案","solution","design"));
      private final String displayName;
      private final Set<String> aliases; // 全小写
      RequiredSection(String displayName, Set<String> aliases) { ... }
      public String getDisplayName() { ... }
      public boolean matches(String heading) {
          return aliases.contains(heading == null ? "" : heading.trim().toLowerCase(Locale.ROOT));
      }
  }
  ```

- [x] 1.2 创建 `domain/.../prd/validation/PrdInputValidator.java`：
  - `public static final int MIN_TITLE_LENGTH = 5;`
  - `public static final int MIN_CONTENT_LENGTH = 200;`
  - `public static final int MIN_REQUIRED_SECTIONS = 2;`
  - `private static final Pattern HEADING_PATTERN = Pattern.compile("^\\s*#{1,2}\\s+(.+?)\\s*$", Pattern.MULTILINE);`
  - 公开方法 `validateForSubmit(String title, String content)`：
    - 调 `validateLength(title, content)` → 不足抛 `BizException(PRD_CONTENT_TOO_SHORT, "title 太短:当前 N 字符,最少 5 字符")`
    - 调 `validateSections(content)` → 至少 2 个 RequiredSection,否则抛 `PRD_MISSING_REQUIRED_SECTION`,消息含**缺失**项 + 可选 "已识别:..."
  - 私有方法 `effectiveLength(String s)`：`s == null ? 0 : s.replaceAll("\\s+", "").length()`
  - 私有方法 `extractSections(String content)`：用 HEADING_PATTERN matcher 抽出所有 H1/H2 标题文本,返回 `List<String>`
  - 私有构造函数 `private PrdInputValidator() {}` 防止实例化

## 2. Application 层

- [x] 2.1 修改 `application/.../prd/service/PrdApplicationService.submit(Long, Long)`：
  - 在 `prd.submit()` 状态转移之前增加 `PrdInputValidator.validateForSubmit(prd.getTitle(), prd.getContent());`
  - 校验抛出的 `BizException` 由 `GlobalExceptionHandler` 自动处理为 HTTP 200 + 错误码响应,无需 try-catch

## 3. 测试 - 验证器

- [x] 3.1 创建 `domain/src/test/java/.../prd/validation/PrdInputValidatorTest.java`,覆盖以下用例:
  - **字数边界**:
    - title 4 字符 → 抛 PRD_CONTENT_TOO_SHORT,消息含 "title" 与 "4" "5"
    - title 5 字符 → 通过(配合合法 content)
    - content 199 字符 + 含两章节 → 抛 PRD_CONTENT_TOO_SHORT,消息含 "content"
    - content 200 字符 + 含两章节 → 通过
  - **空白处理**:
    - title="     "(5 空格)→ 有效 0 字符,抛 PRD_CONTENT_TOO_SHORT
    - content 含大量换行/缩进但实质字数足够 + 章节 → 通过
    - title=null → 抛 PRD_CONTENT_TOO_SHORT(有效 0 字符)
    - content=null → 抛 PRD_CONTENT_TOO_SHORT
  - **同义词识别**:
    - `# Background` + `# Goals` → 识别 BACKGROUND/GOAL
    - `# 背景` + `# 功能设计` → 识别 BACKGROUND/DESIGN
    - `# BACKGROUND` + `# objective` → 大小写不敏感,识别 BACKGROUND/GOAL
    - `## 概述` + `## 方案` → 识别 BACKGROUND/DESIGN
  - **缺章节**:
    - 仅 `# 背景` + 内容达标 → 抛 PRD_MISSING_REQUIRED_SECTION,消息含 "目标" 与 "功能设计"
    - 仅 `# 其他章节名` + 内容达标 → 抛 PRD_MISSING_REQUIRED_SECTION,消息含三个 displayName
    - 三个都有(背景/目标/功能设计)→ 通过
  - **正则边界**:
    - `#背景`(无空格)→ 不匹配为标题(正则要求 `#` 后跟空格)
    - `### 背景`(H3)→ 不匹配为目标章节(只识别 H1/H2)
    - 行首空格 `  # 背景` → 应被识别(正则允许 `^\\s*`)

## 4. 测试 - 服务层

- [x] 4.1 修改 `application/src/test/java/.../prd/PrdApplicationServiceTest`,在现有 `submitPrd_*` 测试用例中确保 mock 的 PRD content 是合法的(达标 + 含两章节),否则之前的"成功"用例会因新校验失败
- [x] 4.2 新增用例 `submitPrd_titleTooShort_throwsContentTooShort`:
  - Mock PrdRepository.findById 返回 title 太短的 DRAFT Prd
  - 调 submit
  - 断言抛 BizException + errorCode=PRD_CONTENT_TOO_SHORT
  - 断言 prd 状态保持 DRAFT(prdRepository.update 未被调用)
- [x] 4.3 新增用例 `submitPrd_contentTooShort_throwsContentTooShort`:同上,content 太短
- [x] 4.4 新增用例 `submitPrd_missingSections_throwsMissingSection`:同上,content 达标但只 1 个章节,断言错误消息含缺失章节名

## 5. 集成验证(手工)

- [x] 5.1 启动服务,登录 admin
- [x] 5.2 创建一个 DRAFT PRD:title="测",content="hi"(短内容)
- [x] 5.3 调 submit → 预期 400 + code=30002 + msg 含 "title 太短"
- [x] 5.4 update content 到 300 字符但只 `# 背景` 一个章节 → submit → 预期 400 + code=30003 + msg 含 "目标" "功能设计"
- [x] 5.5 update content 加上 `# 目标` `# 功能设计` → submit → 预期成功,状态变 SUBMITTED,有快照
- [x] 5.6 走 URL 路径再验证一次:`POST /api/v1/prds/from-url` 传一个真实公开文档 URL → AI 摘要后转 DRAFT(可能内容短/缺章节)→ 调 submit → 预期被 #6 校验拦截,与手动路径行为一致;扩充后再 submit → 成功

## 6. 归档准备

- [x] 6.1 跑全量测试 `mvn clean test`,确认无回归
- [x] 6.2 把 spec 增量合并到 `openspec/specs/prd-storage/spec.md`(替换"提交 PRD 评审" requirement + 末尾追加"PRD 提交输入门槛" requirement)
- [x] 6.3 归档:`mv openspec/changes/add-prd-input-validation openspec/changes/archive/$(date +%Y-%m-%d)-add-prd-input-validation`
- [x] 6.4 更新 `openspec/roadmap.md`,把 #6 行状态改为 ✅ DONE + 完成日期
