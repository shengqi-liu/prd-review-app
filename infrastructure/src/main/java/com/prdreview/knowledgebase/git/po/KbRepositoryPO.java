package com.prdreview.knowledgebase.git.po;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库 Git 仓库持久化对象。
 * <ul>
 *   <li>{@code @Version} — 乐观锁</li>
 *   <li>{@code @TableLogic} — 逻辑删除</li>
 * </ul>
 */
@Data
@TableName("kb_repository")
public class KbRepositoryPO {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    @TableField("remote_url")
    private String remoteUrl;

    private String branch;

    @TableField("local_path")
    private String localPath;

    /** AuthType 枚举，存 String */
    @TableField("auth_type")
    private String authType;

    @TableField("auth_secret")
    private String authSecret;

    @TableField("poll_interval_ms")
    private Long pollIntervalMs;

    /** SyncStatus 枚举，存 String */
    @TableField("sync_status")
    private String syncStatus;

    @TableField("last_synced_commit")
    private String lastSyncedCommit;

    @TableField("last_synced_at")
    private LocalDateTime lastSyncedAt;

    @TableField("last_error_message")
    private String lastErrorMessage;

    @Version
    private Integer version;

    @TableLogic
    private Integer deleted;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
