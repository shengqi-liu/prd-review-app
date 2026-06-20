package com.prdreview.knowledgebase.git.dto;

import com.prdreview.knowledgebase.git.model.AuthType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.hibernate.validator.constraints.URL;

/**
 * 创建知识库仓库请求 DTO。
 */
public record CreateKbRepositoryRequest(
    @NotBlank(message = "name 不能为空")
    @Size(max = 100, message = "name 长度不能超过 100")
    String name,

    @NotBlank(message = "remoteUrl 不能为空")
    @Size(max = 500, message = "remoteUrl 长度不能超过 500")
    String remoteUrl,

    @Size(max = 100, message = "branch 长度不能超过 100")
    String branch,

    AuthType authType,

    @Size(max = 1000, message = "authSecret 长度不能超过 1000")
    String authSecret,

    Long pollIntervalMs
) {
}
