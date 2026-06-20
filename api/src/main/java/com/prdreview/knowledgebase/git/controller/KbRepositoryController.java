package com.prdreview.knowledgebase.git.controller;

import com.prdreview.auth.model.UserRole;
import com.prdreview.common.security.CurrentUser;
import com.prdreview.common.security.RequireRole;
import com.prdreview.knowledgebase.git.CreateKbRepositoryCommand;
import com.prdreview.knowledgebase.git.KbRepositoryDTO;
import com.prdreview.knowledgebase.git.UpdateKbRepositoryCommand;
import com.prdreview.knowledgebase.git.dto.CreateKbRepositoryRequest;
import com.prdreview.knowledgebase.git.dto.KbRepositoryResponse;
import com.prdreview.knowledgebase.git.dto.UpdateKbRepositoryRequest;
import com.prdreview.knowledgebase.git.service.KbRepositoryApplicationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 知识库 Git 仓库管理 Controller。
 *
 * <p>所有接口需登录；写操作仅 ADMIN，读操作所有登录用户但凭据按角色 mask。
 */
@Slf4j
@Tag(name = "KbRepository", description = "知识库 Git 仓库管理")
@RestController
@RequestMapping("/api/v1/kb/repositories")
@RequiredArgsConstructor
public class KbRepositoryController {

    private final KbRepositoryApplicationService kbService;

    @Operation(summary = "创建知识库仓库（仅 ADMIN，至多 1 个）")
    @RequireRole(UserRole.ADMIN)
    @PostMapping
    public KbRepositoryResponse create(@Valid @RequestBody CreateKbRepositoryRequest req) {
        KbRepositoryDTO dto = kbService.create(new CreateKbRepositoryCommand(
            req.name(), req.remoteUrl(), req.branch(),
            req.authType(), req.authSecret(), req.pollIntervalMs()
        ));
        return KbRepositoryResponse.from(dto);
    }

    @Operation(summary = "更新知识库仓库（仅 ADMIN）")
    @RequireRole(UserRole.ADMIN)
    @PutMapping("/{id}")
    public KbRepositoryResponse update(@PathVariable Long id,
                                       @Valid @RequestBody UpdateKbRepositoryRequest req) {
        KbRepositoryDTO dto = kbService.update(new UpdateKbRepositoryCommand(
            id, req.name(), req.remoteUrl(), req.branch(),
            req.authType(), req.authSecret(), req.pollIntervalMs(), req.version()
        ));
        return KbRepositoryResponse.from(dto);
    }

    @Operation(summary = "删除知识库仓库（逻辑删除 + 清理本地工作区，仅 ADMIN）")
    @RequireRole(UserRole.ADMIN)
    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        kbService.delete(id);
    }

    @Operation(summary = "触发立即同步（仅 ADMIN）")
    @RequireRole(UserRole.ADMIN)
    @PostMapping("/{id}/sync")
    public KbRepositoryResponse triggerSync(@PathVariable Long id) {
        return KbRepositoryResponse.from(
            kbService.triggerSync(id, CurrentUser.getCurrentUserRole())
        );
    }

    @Operation(summary = "查看仓库详情（所有登录用户；凭据对非 ADMIN mask）")
    @RequireRole({UserRole.SUBMITTER, UserRole.TEAM_MEMBER, UserRole.ADMIN})
    @GetMapping("/{id}")
    public KbRepositoryResponse getById(@PathVariable Long id) {
        return KbRepositoryResponse.from(
            kbService.getById(id, CurrentUser.getCurrentUserRole())
        );
    }

    @Operation(summary = "查询仓库列表（所有登录用户）")
    @RequireRole({UserRole.SUBMITTER, UserRole.TEAM_MEMBER, UserRole.ADMIN})
    @GetMapping
    public List<KbRepositoryResponse> list() {
        return kbService.listRepositories(CurrentUser.getCurrentUserRole()).stream()
            .map(KbRepositoryResponse::from)
            .toList();
    }
}
