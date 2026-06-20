package com.prdreview.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 全局错误码枚举。
 *
 * <p>分段编排：
 * <ul>
 *   <li>0           — 成功</li>
 *   <li>10000–19999 — 通用错误（参数、资源等）</li>
 *   <li>20000–29999 — 鉴权与权限</li>
 *   <li>30000–39999 — PRD 域</li>
 *   <li>40000–49999 — 评审域</li>
 *   <li>50000–59999 — 知识库域</li>
 *   <li>60000–69999 — Reviewer / Style 域</li>
 *   <li>90000–99999 — 系统级错误</li>
 * </ul>
 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // ── 成功 ──────────────────────────────────────────────
    SUCCESS(0, "success"),

    // ── 通用错误 10000–19999 ──────────────────────────────
    RESOURCE_NOT_FOUND(10001, "资源不存在"),
    PARAM_INVALID(10002, "请求参数不合法"),
    PARAM_MISSING(10003, "缺少必要参数"),
    OPERATION_NOT_ALLOWED(10004, "操作不允许"),
    DATA_CONFLICT(10005, "数据冲突"),

    // ── 鉴权与权限 20000–29999 ───────────────────────────
    UNAUTHORIZED(20001, "未登录或登录已过期"),
    FORBIDDEN(20002, "无权限执行此操作"),
    TOKEN_INVALID(20003, "Token 无效"),
    TOKEN_EXPIRED(20004, "Token 已过期"),
    USERNAME_EXISTS(20010, "用户名已存在"),
    EMAIL_EXISTS(20011, "邮箱已被注册"),
    LOGIN_FAILED(20012, "用户名或密码错误"),
    ACCOUNT_DISABLED(20013, "账号已禁用"),

    // ── PRD 域 30000–39999 ───────────────────────────────
    PRD_NOT_FOUND(30001, "PRD 方案不存在"),
    PRD_CONTENT_TOO_SHORT(30002, "PRD 内容过短，请补充后再提交"),
    PRD_MISSING_REQUIRED_SECTION(30003, "PRD 缺少必要章节（背景/目标/功能设计）"),
    PRD_VERSION_CONFLICT(30004, "PRD 版本冲突，请刷新后重试"),
    PRD_OPERATION_NOT_ALLOWED(30005, "当前状态不允许该操作"),

    // ── 评审域 40000–49999 ───────────────────────────────
    REVIEW_NOT_FOUND(40001, "评审记录不存在"),
    REVIEW_ALREADY_RUNNING(40002, "该方案已有评审正在进行中"),
    REVIEW_AGENT_UNAVAILABLE(40003, "评审员不可用，请检查配置"),
    REVIEW_STYLE_NOT_FOUND(40004, "评审风格不存在"),

    // ── 知识库域 50000–59999 ─────────────────────────────
    KB_INDEX_FAILED(50001, "知识库索引失败"),
    KB_RETRIEVAL_FAILED(50002, "知识库检索失败"),
    KB_GIT_REPO_NOT_FOUND(50003, "Git 知识库路径不存在"),
    KB_GIT_CLONE_FAILED(50004, "Git 仓库 clone 失败"),
    KB_GIT_PULL_FAILED(50005, "Git 仓库拉取失败"),
    KB_GIT_AUTH_FAILED(50006, "Git 凭据无效或被拒绝"),
    KB_REPO_ALREADY_CONFIGURED(50007, "知识库仓库已配置（系统至多 1 个）"),

    // ── Reviewer / Style 域 60000–69999 ─────────────────
    REVIEWER_NOT_FOUND(60001, "AI 评审员不存在"),
    REVIEWER_PROMPT_INVALID(60002, "评审员 Prompt 模板不合法"),
    STYLE_NOT_FOUND(60003, "评审风格不存在"),
    STYLE_DEFAULT_NOT_DELETABLE(60004, "默认风格不可删除"),
    STYLE_RULE_INVALID(60005, "评审风格规则配置非法"),
    STYLE_DEFAULT_NOT_DISABLABLE(60006, "默认风格不可禁用"),

    // ── 系统级错误 90000–99999 ───────────────────────────
    SYSTEM_ERROR(99999, "系统内部错误，请稍后重试"),
    EXTERNAL_SERVICE_ERROR(99998, "外部服务调用失败"),
    AI_SERVICE_ERROR(99997, "AI 服务调用失败");

    /** 错误码数值 */
    private final int code;

    /** 默认错误描述 */
    private final String message;
}
