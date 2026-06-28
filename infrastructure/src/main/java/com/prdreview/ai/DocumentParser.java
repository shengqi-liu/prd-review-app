package com.prdreview.ai;

import com.prdreview.common.exception.BizException;
import com.prdreview.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Set;

/**
 * 文件解析器:基于 Apache Tika 检测 MIME 类型并抽取纯文本。
 *
 * <p>支持 PDF / Word / Markdown / 纯文本。其他类型抛 {@link ErrorCode#PRD_FILE_TYPE_UNSUPPORTED}。
 *
 * <p>MIME 检测策略:Tika 的 {@code AutoDetectParser} 同时看文件内容魔数与文件名(双重判定),
 * 防止用户改后缀绕过白名单。
 */
@Slf4j
@Component
public class DocumentParser {

    /** 支持的 MIME 白名单。 */
    public static final Set<String> SUPPORTED_MIME = Set.of(
        "application/pdf",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/msword",
        "text/plain",
        "text/markdown",
        "text/x-markdown",
        "text/x-web-markdown"  // Tika 对部分 .md 的识别结果
    );

    /** 解析后文本长度阈值:低于此值疑似扫描件(无文字层)。 */
    private static final int MIN_PARSED_TEXT_LENGTH = 10;

    private final Tika tika = new Tika();

    /**
     * 检测文件 MIME 类型(基于内容 + 文件名双重判定)。
     */
    public String detectMime(byte[] bytes, String filename) {
        return tika.detect(bytes, filename);
    }

    /**
     * 解析文件为纯文本。检查白名单 → 调 Tika 解析 → 校验非空 → 返回。
     *
     * @throws BizException PRD_FILE_TYPE_UNSUPPORTED 类型不在白名单
     * @throws BizException PRD_FILE_PARSE_FAILED 解析失败或文本过短(疑似扫描件)
     */
    public String parseText(byte[] bytes, String filename) {
        String mime = detectMime(bytes, filename);
        if (!SUPPORTED_MIME.contains(mime)) {
            log.warn("[DocParser] 不支持的文件类型 filename={} detectedMime={}", filename, mime);
            throw new BizException(ErrorCode.PRD_FILE_TYPE_UNSUPPORTED,
                "不支持的文件类型: " + mime);
        }
        try (ByteArrayInputStream input = new ByteArrayInputStream(bytes)) {
            String text = tika.parseToString(input);
            if (text == null || text.isBlank() || text.length() < MIN_PARSED_TEXT_LENGTH) {
                log.warn("[DocParser] 解析后文本过短 filename={} len={}",
                    filename, text == null ? 0 : text.length());
                throw new BizException(ErrorCode.PRD_FILE_PARSE_FAILED,
                    "解析后内容过短,可能是扫描件或损坏文件,请提供文字版");
            }
            log.info("[DocParser] 解析成功 filename={} mime={} textLen={}",
                filename, mime, text.length());
            return text;
        } catch (BizException ex) {
            throw ex;
        } catch (IOException | TikaException ex) {
            log.warn("[DocParser] 解析失败 filename={} err={}", filename, ex.getMessage());
            throw new BizException(ErrorCode.PRD_FILE_PARSE_FAILED,
                "文件解析失败: " + ex.getMessage());
        }
    }
}
