package com.prdreview.auth.model;

/**
 * 用户角色枚举。
 */
public enum UserRole {
    /** 方案提交人（默认） */
    SUBMITTER,
    /** 团队成员（可查看共享报告） */
    TEAM_MEMBER,
    /** 管理员 */
    ADMIN
}
