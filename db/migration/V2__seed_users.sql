-- V2__seed_users.sql
-- 初始化系统用户（禁止自助注册，统一由此脚本管理）
--
-- 默认密码：
--   admin → Admin@123
--   alice → Alice@123
--   bob   → Bob@1234

INSERT INTO `user` (username, email, password, role, status, created_at)
VALUES
  ('admin',
   'admin@prdreview.internal',
   '$2a$10$/7AMVJ6r1DfZ83QcgEQmKOJUUFsZ6go9Vwm4Mp/NmEUApTEv9pb3u',
   'ADMIN',
   1,
   NOW()),

  ('alice',
   'alice@prdreview.internal',
   '$2a$10$NvxEKJna.A5mK/UHUakoxuqOUV.Np5osR4X.GYAD6U494.mU2uPKa',
   'SUBMITTER',
   1,
   NOW()),

  ('bob',
   'bob@prdreview.internal',
   '$2a$10$ACQGiWERFFzKpkJmiTvQIuGCZDjsY0acQ6RuxZM.cRtzJ0FVp26Pq',
   'TEAM_MEMBER',
   1,
   NOW())

ON DUPLICATE KEY UPDATE id = id;
