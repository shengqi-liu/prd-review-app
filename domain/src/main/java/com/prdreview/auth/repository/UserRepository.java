package com.prdreview.auth.repository;

import com.prdreview.auth.model.User;

import java.util.Optional;

/**
 * 用户 Repository 接口（领域层定义，基础设施层实现）。
 */
public interface UserRepository {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    Optional<User> findById(Long id);

    User save(User user);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);
}
