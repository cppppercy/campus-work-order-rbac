INSERT INTO roles(code,name) VALUES
('ADMIN','系统管理员'),('MANAGER','业务主管'),('STAFF','普通员工'),('VIEWER','只读用户');
INSERT INTO permissions(code,name,type) VALUES
('menu:workorders','工单菜单','MENU'),
('page:workorders:view','工单页面','PAGE'),
('workorder:list','查看工单','ACTION'),
('workorder:create','新增工单','ACTION'),
('workorder:update','编辑工单及状态','ACTION'),
('workorder:delete','删除工单','ACTION'),
('workorder:assign','分派负责人','ACTION'),
('workorder:export','导出工单','ACTION'),
('menu:reports','统计菜单','MENU'),
('page:reports:view','统计页面','PAGE'),
('report:view','查看统计','ACTION'),
('menu:users','用户菜单','MENU'),
('page:users:view','用户页面','PAGE'),
('user:list','查看用户','ACTION'),
('user:manage','管理用户与角色分配','ACTION'),
('menu:roles','角色菜单','MENU'),
('page:roles:view','角色页面','PAGE'),
('role:list','查看角色','ACTION'),
('role:manage','管理角色','ACTION'),
('permission:grant','配置角色权限','ACTION'),
('menu:audit','审计菜单','MENU'),
('page:audit:view','审计页面','PAGE'),
('audit:view','查看审计日志','ACTION');
INSERT INTO user_roles(user_id,role_id)
SELECT u.id,r.id FROM users u JOIN roles r ON r.code = upper(u.username);
INSERT INTO role_permissions(role_id,permission_id)
SELECT r.id,p.id FROM roles r CROSS JOIN permissions p WHERE r.code='ADMIN';
INSERT INTO role_permissions(role_id,permission_id)
SELECT r.id,p.id FROM roles r CROSS JOIN permissions p
WHERE r.code='MANAGER' AND p.code IN (
'menu:workorders','page:workorders:view','workorder:list','workorder:create',
'workorder:update','workorder:assign','workorder:export',
'menu:reports','page:reports:view','report:view');
INSERT INTO role_permissions(role_id,permission_id)
SELECT r.id,p.id FROM roles r CROSS JOIN permissions p
WHERE r.code='STAFF' AND p.code IN (
'menu:workorders','page:workorders:view','workorder:list','workorder:create','workorder:update');
INSERT INTO role_permissions(role_id,permission_id)
SELECT r.id,p.id FROM roles r CROSS JOIN permissions p
WHERE r.code='VIEWER' AND p.code IN ('menu:workorders','page:workorders:view','workorder:list');
INSERT INTO work_orders(title,content,creator_id,assignee_id,status,created_at,updated_at)
VALUES ('教学楼投影仪检修','第三教学楼 301 教室投影仪无法启动，请安排检修。',
(SELECT id FROM users WHERE username='staff'),
(SELECT id FROM users WHERE username='manager'),'待处理',
strftime('%Y-%m-%dT%H:%M:%fZ','now'),strftime('%Y-%m-%dT%H:%M:%fZ','now'));
INSERT INTO work_orders(title,content,creator_id,assignee_id,status,created_at,updated_at)
VALUES ('图书馆照明维护','图书馆二层自习区照明更换已安排。',
(SELECT id FROM users WHERE username='manager'),
(SELECT id FROM users WHERE username='staff'),'处理中',
strftime('%Y-%m-%dT%H:%M:%fZ','now'),strftime('%Y-%m-%dT%H:%M:%fZ','now'));
