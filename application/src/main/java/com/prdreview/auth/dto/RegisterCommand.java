package com.prdreview.auth.dto;

import lombok.Data;

/**
 * 注册命令。
 */
@Data
public class RegisterCommand {
    private String username;
    private String email;
    private String password;
}
