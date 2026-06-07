package com.prdreview.auth.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.prdreview.auth.mapper.UserMapper;
import com.prdreview.auth.model.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * UserRepository 基础设施实现，委托 MyBatis-Plus UserMapper。
 */
@Repository
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepository {

    private final UserMapper userMapper;

    @Override
    public Optional<User> findByUsername(String username) {
        return Optional.ofNullable(
            userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username))
        );
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return Optional.ofNullable(
            userMapper.selectOne(new LambdaQueryWrapper<User>()
                .eq(User::getEmail, email))
        );
    }

    @Override
    public Optional<User> findById(Long id) {
        return Optional.ofNullable(userMapper.selectById(id));
    }

    @Override
    public User save(User user) {
        if (user.getId() == null) {
            userMapper.insert(user);
        } else {
            userMapper.updateById(user);
        }
        return user;
    }

    @Override
    public boolean existsByUsername(String username) {
        return userMapper.exists(new LambdaQueryWrapper<User>()
            .eq(User::getUsername, username));
    }

    @Override
    public boolean existsByEmail(String email) {
        return userMapper.exists(new LambdaQueryWrapper<User>()
            .eq(User::getEmail, email));
    }
}
