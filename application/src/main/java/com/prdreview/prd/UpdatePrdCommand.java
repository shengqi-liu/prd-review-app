package com.prdreview.prd;

/**
 * 更新 PRD 草稿命令（Application 层输入）。
 *
 * @param version 前端携带的乐观锁版本号，需与数据库当前版本一致
 */
public record UpdatePrdCommand(Long prdId, String title, String content, Integer version, Long currentUserId) {
}
