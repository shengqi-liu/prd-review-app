package com.prdreview.prd;

/**
 * 从文件创建 PRD 命令对象。
 *
 * <p>{@code bytes} 是完整文件内容(已由 Controller 从 MultipartFile 读出);
 * {@code filename} 是原始文件名,供 Tika MIME 检测使用(允许带后缀)。
 */
public record CreatePrdFromFileCommand(
    byte[] bytes,
    String filename,
    Long currentUserId
) {
}
