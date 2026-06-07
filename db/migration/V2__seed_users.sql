-- V2__seed_users.sql
-- 初始化系统用户（禁止自助注册，统一由此脚本管理）
--
-- 默认密码说明：
--   admin   → Admin@2025!
--   pm_demo → Review@2025!
--
-- 密码均使用 BCrypt(strength=10) 哈希，可通过以下命令重新生成：
--   spring shell: new BCryptPasswordEncoder().encode("your-password")
--   htpasswd -bnBC 10 "" "your-password" | tr -d ':\n'

INSERT INTO `user` (username, email, password, role, status, created_at)
VALUES
  -- 管理员账号
  ('admin',
   'admin@prdreview.internal',
   '$2b$10$dXaIqEak.qkH55Bm4oFZjuKCpaJsZK1x3F36D7nwa2vnD7CbwmBlK',
   'ADMIN',
   1,
   NOW()),

  -- 产品经理示例账号
  ('pm_demo',
   'pm@prdreview.internal',
   '$2b$10$xuriDXPfP7UOjqBolvkrfet7KU4N2aB0MWU5nr0j24cHuFYOMN5Tu',
   'PM',
   1,
   NOW())

ON DUPLICATE KEY UPDATE id = id;  -- 幂等：重复执行不报错
