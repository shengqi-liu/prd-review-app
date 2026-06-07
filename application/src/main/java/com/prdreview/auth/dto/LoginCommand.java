package com.prdreview.auth.dto;

import lombok.Data;

/**
 * 登录命令。
 */
@Data
public class LoginCommand {
    private String username;
    private String password;
}
