package com.prdreview.auth.service;

import com.prdreview.auth.dto.LoginCommand;
import com.prdreview.auth.dto.LoginResult;
import com.prdreview.auth.dto.RegisterCommand;
import com.prdreview.auth.dto.UserDTO;
import com.prdreview.auth.model.User;
import com.prdreview.auth.model.UserStatus;
import com.prdreview.auth.repository.UserRepository;
import com.prdreview.common.exception.BizException;
import com.prdreview.common.exception.ErrorCode;
import com.prdreview.common.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 认证应用服务。
 *
 * <p>编排注册和登录用例，不包含具体持久化或加密实现。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthApplicationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    /**
     * 用户注册。
     */
    @Transactional
    public UserDTO register(RegisterCommand cmd) {
        if (userRepository.existsByUsername(cmd.getUsername())) {
            throw new BizException(ErrorCode.USERNAME_EXISTS);
        }
        if (userRepository.existsByEmail(cmd.getEmail())) {
            throw new BizException(ErrorCode.EMAIL_EXISTS);
        }

        User user = User.builder()
            .username(cmd.getUsername())
            .email(cmd.getEmail())
            .password(passwordEncoder.encode(cmd.getPassword()))
            .build();

        userRepository.save(user);
        log.info("[Auth] 用户注册成功 username={}", user.getUsername());

        return toDTO(user);
    }

    /**
     * 用户登录。
     */
    public LoginResult login(LoginCommand cmd) {
        // 防止用户名枚举：统一返回"用户名或密码错误"
        User user = userRepository.findByUsername(cmd.getUsername())
            .orElseThrow(() -> new BizException(ErrorCode.LOGIN_FAILED));

        // 先校验账号状态，再验密（disabled 账号给出明确提示）
        if (user.getStatus() == UserStatus.DISABLED) {
            throw new BizException(ErrorCode.ACCOUNT_DISABLED);
        }

        if (!passwordEncoder.matches(cmd.getPassword(), user.getPassword())) {
            throw new BizException(ErrorCode.LOGIN_FAILED);
        }

        String token = jwtUtil.generateToken(user);
        log.info("[Auth] 用户登录成功 username={}", user.getUsername());

        return LoginResult.builder()
            .accessToken(token)
            .expiresIn(jwtUtil.getExpirationSeconds())
            .build();
    }

    private UserDTO toDTO(User user) {
        return UserDTO.builder()
            .id(user.getId())
            .username(user.getUsername())
            .email(user.getEmail())
            .role(user.getRole())
            .build();
    }
}
