package com.prdreview.prd.validation;

import com.prdreview.common.exception.BizException;
import com.prdreview.common.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PrdInputValidator 全场景单元测试。
 *
 * <p>覆盖：字数边界 / 空白处理 / 章节同义词识别（中英）/ 缺章节错误消息 / 正则边界。
 */
@DisplayName("PrdInputValidator 单元测试")
class PrdInputValidatorTest {

    /** 一份"够格"的 content：长度足(≥200 有效字符)且含两个中文章节。后续测试可以这份做基底。 */
    private static final String LEGAL_CONTENT = """
        # 背景
        本项目旨在为产品评审团队提供 AI 辅助评审能力，让 PM 提交 PRD 后能在数十秒内获得多角色评审反馈，
        显著缩短评审周期。当前手工评审平均耗时 3 天且覆盖维度有限，本平台希望通过多 Agent 并行评审解决这两个痛点。
        预期受益方包括产品经理、设计师、技术 Lead、合规审查官。系统目标用户规模约 200 人，单日峰值评审请求 50 次。

        # 目标
        - 评审平均耗时降至 5 分钟以内（含 AI 调用、报告生成、用户阅读）
        - 评审维度覆盖产品/技术/商业/合规，每个维度至少一名 AI 评审员
        - 评审报告含明确分级问题清单（严重/重要/建议）和改进建议
        - 用户对评审有用度的反馈率 ≥ 60%
        """;

    private static final String LEGAL_TITLE = "会员订阅功能 v1";

    // ────────────────────────────────────────────────
    // 字数边界
    // ────────────────────────────────────────────────

    @Nested
    @DisplayName("字数边界")
    class LengthBoundary {

        @Test
        @DisplayName("title 4 字符 → PRD_CONTENT_TOO_SHORT")
        void titleTooShort() {
            assertThatThrownBy(() ->
                PrdInputValidator.validateForSubmit("abcd", LEGAL_CONTENT))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> {
                    BizException be = (BizException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.PRD_CONTENT_TOO_SHORT);
                    assertThat(be.getMessage())
                        .contains("title").contains("4").contains("5");
                });
        }

        @Test
        @DisplayName("title 恰好 5 字符 → 通过")
        void titleAtThreshold() {
            assertThatCode(() ->
                PrdInputValidator.validateForSubmit("abcde", LEGAL_CONTENT))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("content 199 字符 → PRD_CONTENT_TOO_SHORT")
        void contentTooShort() {
            // 200 - 1 = 199 个有效字符，再加两个章节标题
            String shortContent = "# 背景\n" + "x".repeat(99) + "\n# 目标\n" + "y".repeat(90);
            assertThatThrownBy(() ->
                PrdInputValidator.validateForSubmit(LEGAL_TITLE, shortContent))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> {
                    BizException be = (BizException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.PRD_CONTENT_TOO_SHORT);
                    assertThat(be.getMessage()).contains("content");
                });
        }

        @Test
        @DisplayName("content 恰好 200 字符 + 两章节 → 通过")
        void contentAtThreshold() {
            // 200 个 'x' + 两章节标题(背景/目标)。effectiveLength 去空白后 = 200 + (#背景=3) + (#目标=3) = 206
            // 但有效字符数算法是 replaceAll("\\s+",""),所以 "# 背景" 去空格 = "#背景" = 3 字符
            String content = "# 背景\n" + "x".repeat(200) + "\n# 目标";
            assertThatCode(() ->
                PrdInputValidator.validateForSubmit(LEGAL_TITLE, content))
                .doesNotThrowAnyException();
        }
    }

    // ────────────────────────────────────────────────
    // 空白处理
    // ────────────────────────────────────────────────

    @Nested
    @DisplayName("空白处理")
    class WhitespaceHandling {

        @Test
        @DisplayName("title 全空格(5 个空格) → 有效 0 字符 → 抛异常")
        void titleAllWhitespace() {
            assertThatThrownBy(() ->
                PrdInputValidator.validateForSubmit("     ", LEGAL_CONTENT))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.PRD_CONTENT_TOO_SHORT));
        }

        @Test
        @DisplayName("title 含空格但实质字符足够 → 通过")
        void titleWhitespaceMixed() {
            assertThatCode(() ->
                PrdInputValidator.validateForSubmit("  会员订阅 v2  ", LEGAL_CONTENT))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("title=null → 抛异常")
        void titleNull() {
            assertThatThrownBy(() ->
                PrdInputValidator.validateForSubmit(null, LEGAL_CONTENT))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.PRD_CONTENT_TOO_SHORT));
        }

        @Test
        @DisplayName("content=null → 抛异常")
        void contentNull() {
            assertThatThrownBy(() ->
                PrdInputValidator.validateForSubmit(LEGAL_TITLE, null))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.PRD_CONTENT_TOO_SHORT));
        }
    }

    // ────────────────────────────────────────────────
    // 章节同义词识别
    // ────────────────────────────────────────────────

    @Nested
    @DisplayName("章节同义词识别")
    class SectionRecognition {

        private final String filler = "x".repeat(200);

        @Test
        @DisplayName("英文章节 Background + Goals → 通过")
        void englishHeadings() {
            String c = "## Background\n" + filler + "\n## Goals\n";
            assertThatCode(() -> PrdInputValidator.validateForSubmit(LEGAL_TITLE, c))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("中文章节 背景 + 功能设计 → 通过")
        void chineseHeadings() {
            String c = "# 背景\n" + filler + "\n# 功能设计\n";
            assertThatCode(() -> PrdInputValidator.validateForSubmit(LEGAL_TITLE, c))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("大小写不敏感 BACKGROUND + objective → 通过")
        void caseInsensitive() {
            String c = "# BACKGROUND\n" + filler + "\n# objective\n";
            assertThatCode(() -> PrdInputValidator.validateForSubmit(LEGAL_TITLE, c))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("H2 同义词 概述 + 方案 → 通过")
        void h2Synonyms() {
            String c = "## 概述\n" + filler + "\n## 方案\n";
            assertThatCode(() -> PrdInputValidator.validateForSubmit(LEGAL_TITLE, c))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("三章节都有 → 通过")
        void allThreeSections() {
            String c = "# 背景\n" + filler + "\n# 目标\n# 功能设计\n";
            assertThatCode(() -> PrdInputValidator.validateForSubmit(LEGAL_TITLE, c))
                .doesNotThrowAnyException();
        }
    }

    // ────────────────────────────────────────────────
    // 缺章节错误消息
    // ────────────────────────────────────────────────

    @Nested
    @DisplayName("缺章节错误消息")
    class MissingSectionMessage {

        private final String filler = "x".repeat(200);

        @Test
        @DisplayName("只有 1 个章节 → 错误消息列出 2 个缺失章节")
        void onlyBackground() {
            String c = "# 背景\n" + filler;
            assertThatThrownBy(() -> PrdInputValidator.validateForSubmit(LEGAL_TITLE, c))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> {
                    BizException be = (BizException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.PRD_MISSING_REQUIRED_SECTION);
                    assertThat(be.getMessage())
                        .contains("目标").contains("功能设计")
                        .contains("已识别").contains("背景");
                });
        }

        @Test
        @DisplayName("0 个章节(只是其他标题) → 错误消息列出全部 3 个缺失")
        void noRequiredSection() {
            String c = "# 其他无关章节\n" + filler;
            assertThatThrownBy(() -> PrdInputValidator.validateForSubmit(LEGAL_TITLE, c))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> {
                    BizException be = (BizException) ex;
                    assertThat(be.getErrorCode()).isEqualTo(ErrorCode.PRD_MISSING_REQUIRED_SECTION);
                    assertThat(be.getMessage())
                        .contains("背景").contains("目标").contains("功能设计")
                        .contains("无"); // 已识别: 无
                });
        }
    }

    // ────────────────────────────────────────────────
    // 正则边界
    // ────────────────────────────────────────────────

    @Nested
    @DisplayName("正则边界")
    class HeadingRegexBoundary {

        private final String filler = "x".repeat(200);

        @Test
        @DisplayName("'#背景'(无空格) → 不识别为标题")
        void noSpaceAfterHash() {
            String c = "#背景\n" + filler + "\n# 目标";
            // 只识别到 "# 目标"，缺背景 + 功能设计两个,应抛异常
            assertThatThrownBy(() -> PrdInputValidator.validateForSubmit(LEGAL_TITLE, c))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.PRD_MISSING_REQUIRED_SECTION));
        }

        @Test
        @DisplayName("'### 背景'(H3) → 不识别为目标章节(只识别 H1/H2)")
        void h3NotRecognized() {
            String c = "### 背景\n" + filler + "\n# 目标";
            assertThatThrownBy(() -> PrdInputValidator.validateForSubmit(LEGAL_TITLE, c))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                    .isEqualTo(ErrorCode.PRD_MISSING_REQUIRED_SECTION));
        }

        @Test
        @DisplayName("行首带空格的标题 '  # 背景' → 识别")
        void leadingWhitespaceHeading() {
            String c = "  # 背景\n" + filler + "\n  ## 目标";
            assertThatCode(() -> PrdInputValidator.validateForSubmit(LEGAL_TITLE, c))
                .doesNotThrowAnyException();
        }
    }
}
