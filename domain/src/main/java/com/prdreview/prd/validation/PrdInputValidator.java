package com.prdreview.prd.validation;

import com.prdreview.common.exception.BizException;
import com.prdreview.common.exception.ErrorCode;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * PRD 提交评审前的输入门槛校验器。
 *
 * <p>纯函数（静态方法 + 无状态），不依赖 Spring。供 application service 在 submit 用例触发前调用。
 *
 * <p>校验规则:
 * <ul>
 *   <li><b>字数门槛</b>: title 有效字符 ≥ {@link #MIN_TITLE_LENGTH}; content 有效字符 ≥ {@link #MIN_CONTENT_LENGTH}。
 *       不足抛 {@link ErrorCode#PRD_CONTENT_TOO_SHORT}。"有效字符"是去除所有空白后的长度。</li>
 *   <li><b>章节门槛</b>: 抽取 content 中所有 markdown H1/H2 标题文本，与 {@link RequiredSection} 同义词集合比对，
 *       至少匹配 {@link #MIN_REQUIRED_SECTIONS} 个核心章节。不足抛 {@link ErrorCode#PRD_MISSING_REQUIRED_SECTION}，
 *       错误消息列出缺失的章节名。</li>
 * </ul>
 */
public final class PrdInputValidator {

    /** title 最小有效字符数（去空白后） */
    public static final int MIN_TITLE_LENGTH = 5;

    /** content 最小有效字符数（去空白后） */
    public static final int MIN_CONTENT_LENGTH = 200;

    /** 必须匹配的核心章节数下限 */
    public static final int MIN_REQUIRED_SECTIONS = 2;

    /**
     * 匹配 markdown ATX 风格 H1/H2 标题（含行首允许空白），捕获标题文本（含尾部 `#` 装饰符也无所谓，
     * 因为 {@link RequiredSection#matches} 是精确匹配）。
     */
    private static final Pattern HEADING_PATTERN =
        Pattern.compile("^\\s*#{1,2}\\s+(.+?)\\s*$", Pattern.MULTILINE);

    private PrdInputValidator() {
        // 防止误实例化
    }

    /**
     * 提交评审前的总校验入口。
     *
     * @throws BizException 字数不足时抛 PRD_CONTENT_TOO_SHORT，章节不足时抛 PRD_MISSING_REQUIRED_SECTION
     */
    public static void validateForSubmit(String title, String content) {
        validateLength(title, content);
        validateSections(content);
    }

    // ── 字数门槛 ─────────────────────────────────────────────────────

    private static void validateLength(String title, String content) {
        int titleLen = effectiveLength(title);
        if (titleLen < MIN_TITLE_LENGTH) {
            throw new BizException(ErrorCode.PRD_CONTENT_TOO_SHORT,
                "title 太短: 当前 " + titleLen + " 字符, 最少 " + MIN_TITLE_LENGTH + " 字符");
        }
        int contentLen = effectiveLength(content);
        if (contentLen < MIN_CONTENT_LENGTH) {
            throw new BizException(ErrorCode.PRD_CONTENT_TOO_SHORT,
                "content 太短: 当前 " + contentLen + " 字符, 最少 " + MIN_CONTENT_LENGTH + " 字符");
        }
    }

    /** 去除所有空白（空格/换行/tab）后的字符数。null 视为 0。 */
    private static int effectiveLength(String s) {
        return s == null ? 0 : s.replaceAll("\\s+", "").length();
    }

    // ── 章节门槛 ─────────────────────────────────────────────────────

    private static void validateSections(String content) {
        List<String> headings = extractHeadings(content);
        EnumSet<RequiredSection> matched = EnumSet.noneOf(RequiredSection.class);
        for (String heading : headings) {
            for (RequiredSection section : RequiredSection.values()) {
                if (section.matches(heading)) {
                    matched.add(section);
                }
            }
        }
        if (matched.size() < MIN_REQUIRED_SECTIONS) {
            Set<RequiredSection> missing = EnumSet.complementOf(matched);
            String missingNames = missing.stream()
                .map(RequiredSection::getDisplayName)
                .collect(Collectors.joining(", "));
            String matchedNames = matched.isEmpty() ? "无" :
                matched.stream().map(RequiredSection::getDisplayName).collect(Collectors.joining(", "));
            throw new BizException(ErrorCode.PRD_MISSING_REQUIRED_SECTION,
                "缺少必要章节: " + missingNames + " (已识别: " + matchedNames + ")");
        }
    }

    /** 用 HEADING_PATTERN 抽出所有 H1/H2 标题文本，按出现顺序返回。 */
    private static List<String> extractHeadings(String content) {
        List<String> result = new ArrayList<>();
        if (content == null || content.isBlank()) return result;
        Matcher m = HEADING_PATTERN.matcher(content);
        while (m.find()) {
            result.add(m.group(1));
        }
        return result;
    }
}
