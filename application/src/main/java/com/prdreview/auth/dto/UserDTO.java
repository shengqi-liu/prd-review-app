package com.prdreview.auth.dto;

import com.prdreview.auth.model.UserRole;
import lombok.Builder;
import lombok.Data;

/**
 * 用户 DTO（对外不暴露 password）。
 */
@Data
@Builder
public class UserDTO {
    private Long id;
    private String username;
    private String email;
    private UserRole role;
}
