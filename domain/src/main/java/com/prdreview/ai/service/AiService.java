package com.prdreview.ai.service;

import com.prdreview.ai.dto.SummarizeResult;
import reactor.core.publisher.Flux;

/**
 * AI 能力接口（应用层）：屏蔽底层 Spring AI / provider 细节。
 * <p>实现类 {@code AiServiceImpl} 位于 infrastructure 层，符合依赖倒置原则。
 */
public interface AiService {

    /**
     * 从 URL 读取文档内容，调用 AI 摘要出 title 和 content。
     *
     * @param url 可访问的文档 URL（HTML 或纯文本）
     * @return 摘要结果，title / content 均非空
     */
    SummarizeResult summarizeFromUrl(String url);

    /**
     * 对已有文本内容摘要（供其他 change 复用）。
     *
     * @param rawText 原始文本（去 HTML 后的正文）
     * @return 摘要结果，title / content 均非空
     */
    SummarizeResult summarizeText(String rawText);

    /**
     * 调用 LLM 流式补全接口，逐 token 返回输出 chunk。
     *
     * <p>底层基于 Spring AI {@code ChatClient.stream()}，订阅 Flux 即可逐元素消费。
     * 底层异常通过 {@code onError} 信号传播（包装为
     * {@link com.prdreview.common.exception.AiServiceException}）。
     *
     * @param prompt 完整的 user prompt 文本
     * @return token chunk 流；流终止表示 LLM 已输出完毕
     */
    Flux<String> streamCompletion(String prompt);

    /**
     * 调用 LLM 流式补全接口，分离 system / user 两段消息。
     *
     * <p>{@code systemPrompt} 是固定的角色定义（如评审员模板），
     * {@code userMessage} 是变量输入（如被评审的 PRD）。这是 chat 模型的标准用法，
     * 也是评审员 × PRD 解耦后的推荐调用方式。
     *
     * @param systemPrompt system 角色定义，非空
     * @param userMessage  user 输入内容，非空
     * @return token chunk 流；流终止表示 LLM 已输出完毕
     */
    Flux<String> streamCompletion(String systemPrompt, String userMessage);

    /**
     * 解析上传文件(PDF/Word/Markdown/纯文本) → AI 摘要为 title + content。
     *
     * <p>底层用 Tika 检测 MIME 与解析为纯文本,然后复用 {@link #summarizeText(String)}。
     *
     * @param bytes    文件字节流(完整内容)
     * @param filename 原始文件名(供 Tika MIME 检测使用,允许带后缀)
     * @return 摘要结果,title / content 均非空
     * @throws com.prdreview.common.exception.BizException PRD_FILE_TYPE_UNSUPPORTED 类型不在白名单;
     *                                                    PRD_FILE_PARSE_FAILED 解析失败或文本过短
     */
    SummarizeResult summarizeFromFile(byte[] bytes, String filename);
}
