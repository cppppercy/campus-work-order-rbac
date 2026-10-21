package edu.campus.rbac.ui;

import edu.campus.rbac.event.BackgroundAction;
import edu.campus.rbac.model.Models.*;
import edu.campus.rbac.service.Services;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

/** Chinese Swing client. All database access is delegated to background service calls. */
public final class CampusApplication {
    private static final Color INK = new Color(26, 44, 66);
    private static final Color BACKGROUND = new Color(244, 247, 251);
    private static final String[] STATUSES = {"待处理", "处理中", "已完成", "已关闭"};
    private final Services.Bundle services;
    private final JFrame frame = new JFrame("校园工单 · RBAC 权限管理系统");
    private final JPanel main = new JPanel(new BorderLayout(16, 16));
    private Session session;
    private long revision;
    private JLabel statusLine;

    private CampusApplication(Services.Bundle services) {
        this.services = Objects.requireNonNull(services);
        frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        frame.setSize(1180, 760);
        frame.setMinimumSize(new Dimension(960, 640));
        frame.setLocationRelativeTo(null);
        frame.addWindowListener(new WindowAdapter() {
            @Override public void windowClosed(WindowEvent event) { endSession(); }
        });
        loginPage();
        frame.setVisible(true);
    }

    public static void launch(Services.Bundle services) {
        SwingUtilities.invokeLater(() -> {
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); }
            catch (Exception ignored) { /* Platform default remains usable. */ }
            Font font = new Font("Microsoft YaHei", Font.PLAIN, 14);
            for (Object key : Collections.list(UIManager.getDefaults().keys())) {
                if (UIManager.get(key) instanceof javax.swing.plaf.FontUIResource)
                    UIManager.put(key, new javax.swing.plaf.FontUIResource(font));
            }
            new CampusApplication(services);
        });
    }

    private void loginPage() {
        revision++;
        JPanel outer = new JPanel(new GridBagLayout());
        outer.setBackground(BACKGROUND);
        JPanel card = new JPanel(new GridBagLayout());
        card.setBackground(Color.WHITE);
        card.setBorder(new EmptyBorder(35, 45, 35, 45));
        GridBagConstraints gc = new GridBagConstraints();
        gc.gridx = 0; gc.gridy = 0; gc.fill = GridBagConstraints.HORIZONTAL;
        gc.insets = new Insets(8, 0, 8, 0);
        JLabel title = new JLabel("校园工单管理");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 28)); title.setForeground(INK);
        card.add(title, gc); gc.gridy++;
        card.add(new JLabel("基于事件监听机制的 RBAC 权限管理系统"), gc); gc.gridy++;
        card.add(new JLabel("用户名"), gc); gc.gridy++;
        JTextField username = new JTextField("admin", 28);
        username.setName("login.username"); card.add(username, gc); gc.gridy++;
        card.add(new JLabel("密码"), gc); gc.gridy++;
        JPasswordField password = new JPasswordField(28);
        password.setName("login.password"); card.add(password, gc); gc.gridy++;
        JLabel error = new JLabel(" "); error.setForeground(new Color(176, 40, 40));
        JButton login = new JButton("登 录"); login.setName("login.submit");
        login.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent event) {
                String name = username.getText().trim();
                char[] secret = password.getPassword();
                if (name.isEmpty() || secret.length == 0) {
                    Arrays.fill(secret, '\0'); error.setText("请输入用户名和密码。"); return;
                }
                login.setEnabled(false); error.setText("正在验证身份…");
                long expected = revision;
                new BackgroundAction<>(() -> {
                    try { return services.authentication().login(name, secret); }
                    finally { Arrays.fill(secret, '\0'); }
                }, () -> revision == expected && frame.isDisplayable(), result -> {
                    session = result; password.setText(""); shell();
                }, problem -> { login.setEnabled(true); error.setText(message(problem)); })
                        .actionPerformed(event);
            }
        });
        card.add(login, gc); gc.gridy++;
        card.add(error, gc); gc.gridy++;
        card.add(new JLabel("演示账号：admin / manager / staff / viewer"), gc); gc.gridy++;
        card.add(new JLabel("统一初始密码：Demo@12345"), gc);
        outer.add(card); frame.setContentPane(outer); frame.getRootPane().setDefaultButton(login);
        frame.revalidate(); frame.repaint();
    }

    private void shell() {
        revision++;
        JPanel root = new JPanel(new BorderLayout(0, 0));
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(INK); header.setBorder(new EmptyBorder(14, 20, 14, 20));
        JLabel brand = new JLabel("校园工单  /  权限工作台"); brand.setForeground(Color.WHITE);
        brand.setFont(brand.getFont().deriveFont(Font.BOLD, 20)); header.add(brand, BorderLayout.WEST);
        JLabel who = new JLabel(session.user().displayName() + "  ·  " + session.user().username()
                + "  [" + String.join("、", session.user().roles()) + "]");
        who.setForeground(Color.WHITE); header.add(who, BorderLayout.EAST); root.add(header, BorderLayout.NORTH);
        JPanel sidebar = new JPanel(); sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setBorder(new EmptyBorder(16, 12, 12, 12)); sidebar.setBackground(Color.WHITE);
        sidebar.setPreferredSize(new Dimension(180, 0));
        nav(sidebar, "工作台", null, null, this::dashboard);
        nav(sidebar, "工单管理", "menu:workorders", "page:workorders:view", this::workOrders);
        nav(sidebar, "用户管理", "menu:users", "page:users:view", this::users);
        nav(sidebar, "角色与权限", "menu:roles", "page:roles:view", this::roles);
        nav(sidebar, "工单统计", "menu:reports", "page:reports:view", this::reports);
        nav(sidebar, "审计日志", "menu:audit", "page:audit:view", this::audit);
        sidebar.add(Box.createVerticalGlue());
        JButton refresh = new JButton("刷新权限"); refresh.setMaximumSize(new Dimension(156, 40));
        refresh.addActionListener(e -> {
            String token = session.token();
            run(() -> services.permissions().refresh(token), result -> { session = result; shell(); });
        });
        sidebar.add(refresh); sidebar.add(Box.createVerticalStrut(8));
        JButton logout = new JButton("退出登录"); logout.setMaximumSize(new Dimension(156, 40));
        logout.addActionListener(e -> { endSession(); loginPage(); }); sidebar.add(logout);
        root.add(sidebar, BorderLayout.WEST);
        main.setBackground(BACKGROUND); main.setBorder(new EmptyBorder(22, 22, 12, 22));
        root.add(main, BorderLayout.CENTER);
        statusLine = new JLabel("就绪 · 服务层会在每次操作时校验当前权限");
        statusLine.setBorder(new EmptyBorder(8, 20, 8, 20)); root.add(statusLine, BorderLayout.SOUTH);
        frame.setContentPane(root); frame.getRootPane().setDefaultButton(null); dashboard();
        frame.revalidate(); frame.repaint();
    }

    private void nav(JPanel sidebar, String label, String menu, String page, Runnable display) {
        if (menu != null && !allowed(menu)) return;
        JButton button = new JButton(label); button.setAlignmentX(Component.LEFT_ALIGNMENT);
        button.setMaximumSize(new Dimension(156, 42));
        button.addActionListener(e -> {
            if (page == null) { display.run(); return; }
            String token = session.token();
            run(() -> { services.permissions().require(token, page); return true; }, ok -> display.run());
        });
        sidebar.add(button); sidebar.add(Box.createVerticalStrut(10));
    }

    private JPanel page(String title, String description) {
        revision++;
        main.removeAll();
        JPanel heading = new JPanel(new BorderLayout(0, 8)); heading.setOpaque(false);
        JLabel label = new JLabel(title); label.setFont(label.getFont().deriveFont(Font.BOLD, 24));
        label.setForeground(INK); heading.add(label, BorderLayout.NORTH);
        heading.add(new JLabel(description), BorderLayout.SOUTH); main.add(heading, BorderLayout.NORTH);
        JPanel body = new JPanel(new BorderLayout(0, 12)); body.setOpaque(false);
        main.add(body, BorderLayout.CENTER); main.revalidate(); main.repaint(); return body;
    }

    private void dashboard() {
        JPanel body = page("欢迎，" + session.user().displayName(), "选择左侧菜单开始工作。当前权限来自角色的权限并集。");
        JTextArea text = new JTextArea(); text.setEditable(false); text.setLineWrap(true); text.setWrapStyleWord(true);
        text.setBorder(new EmptyBorder(20, 20, 20, 20));
        text.setText("校园工单协作\n\n提交设施故障、跟踪处理进度，并由负责人协同完成处理。\n\n"
                + "权限说明\n菜单按权限显示；不可用按钮会说明缺少的权限。权限变更后，可点击“刷新权限”更新界面。"
                + "每次操作仍由服务端重新校验权限。\n\n当前权限（" + session.permissions().size() + " 项）\n"
                + String.join("\n", new TreeSet<>(session.permissions())));
        body.add(new JScrollPane(text));
    }

    private void workOrders() {
        JPanel body = page("工单管理", "搜索、分派与跟踪校园事务。选择表格中的一条工单后操作。");
        JPanel toolbar = toolbar(); JTextField query = new JTextField(16);
        JComboBox<String> filter = new JComboBox<>(new String[]{"全部状态", "待处理", "处理中", "已完成", "已关闭"});
        toolbar.add(new JLabel("搜索")); toolbar.add(query); toolbar.add(filter);
        DefaultTableModel model = model("编号", "标题", "创建人", "负责人", "状态", "更新时间");
        JTable table = table(model); List<WorkOrder> rows = new ArrayList<>();
        String token = session.token();
        Runnable reload = () -> {
            String q = query.getText().trim(); String st = filter.getSelectedIndex() == 0 ? null : (String) filter.getSelectedItem();
            run(() -> services.workOrders().list(token, q, st), data -> {
                rows.clear(); rows.addAll(data); model.setRowCount(0);
                for (WorkOrder w : data) model.addRow(new Object[]{w.id(), w.title(), w.creatorName(),
                        Objects.toString(w.assigneeName(), "未分派"), w.status(), w.updatedAt()});
                statusLine.setText("工单共 " + data.size() + " 条");
            });
        };
        toolbar.add(action("查询", "workorder:list", reload));
        toolbar.add(action("新增", "workorder:create", () -> editWorkOrder(null, token, reload)));
        toolbar.add(action("编辑", "workorder:update", () -> selected(table, rows, w -> editWorkOrder(w, token, reload))));
        toolbar.add(action("删除", "workorder:delete", () -> selected(table, rows, w -> {
            if (confirm("确定删除工单 #" + w.id() + "“" + w.title() + "”？"))
                change(() -> { services.workOrders().delete(token, w.id()); return true; }, reload);
        })));
        toolbar.add(action("分派", "workorder:assign", () -> selected(table, rows, w ->
            run(() -> services.workOrders().assignees(token), candidates -> {
                JComboBox<Choice> choices = new JComboBox<>(); choices.addItem(new Choice(null, "未分派"));
                for (User user : candidates) choices.addItem(new Choice(user.id(), user.displayName() + " (" + user.username() + ")"));
                for (int i = 0; i < choices.getItemCount(); i++)
                    if (Objects.equals(choices.getItemAt(i).id(), w.assigneeId())) choices.setSelectedIndex(i);
                if (dialog("分派工单 #" + w.id(), form("负责人", choices))) {
                    Long assignee = ((Choice) choices.getSelectedItem()).id();
                    change(() -> { services.workOrders().assign(token, w.id(), assignee); return true; }, reload);
                }
            }))));
        toolbar.add(action("修改状态", "workorder:update", () -> selected(table, rows, w -> {
            JComboBox<String> choices = new JComboBox<>(STATUSES); choices.setSelectedItem(w.status());
            if (dialog("修改工单状态", form("状态", choices))) {
                String value = (String) choices.getSelectedItem();
                change(() -> { services.workOrders().changeStatus(token, w.id(), value); return true; }, reload);
            }
        })));
        toolbar.add(action("导出 CSV", "workorder:export", () -> {
            JFileChooser chooser = new JFileChooser(); chooser.setSelectedFile(new java.io.File("校园工单.csv"));
            if (chooser.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION) return;
            Path destination = chooser.getSelectedFile().toPath();
            if (Files.exists(destination) && !confirm("文件已存在，确定覆盖？")) return;
            String q = query.getText().trim(); String st = filter.getSelectedIndex() == 0 ? null : (String) filter.getSelectedItem();
            run(() -> {
                String csv = services.workOrders().exportCsv(token, q, st);
                Files.writeString(destination, csv.startsWith("\uFEFF") ? csv : "\uFEFF" + csv, StandardCharsets.UTF_8);
                return destination;
            }, saved -> JOptionPane.showMessageDialog(frame, "已导出：" + saved));
        }));
        body.add(toolbar, BorderLayout.NORTH); body.add(new JScrollPane(table));
        JTextArea detail = new JTextArea(3, 30); detail.setEditable(false); detail.setLineWrap(true); detail.setWrapStyleWord(true);
        detail.setBorder(new EmptyBorder(8, 10, 8, 10));
        table.getSelectionModel().addListSelectionListener(e -> {
            int index = table.getSelectedRow();
            if (index >= 0 && table.convertRowIndexToModel(index) < rows.size())
                detail.setText(rows.get(table.convertRowIndexToModel(index)).content());
            else detail.setText("");
        });
        body.add(new JScrollPane(detail), BorderLayout.SOUTH); if (allowed("workorder:list")) reload.run();
    }

    private void editWorkOrder(WorkOrder order, String token, Runnable reload) {
        JTextField title = new JTextField(order == null ? "" : order.title(), 35);
        JTextArea content = new JTextArea(order == null ? "" : order.content(), 7, 35);
        content.setLineWrap(true); content.setWrapStyleWord(true);
        if (!dialog(order == null ? "新增工单" : "编辑工单 #" + order.id(), form("标题", title, "描述", new JScrollPane(content)))) return;
        WorkOrderInput input = new WorkOrderInput(title.getText().trim(), content.getText().trim());
        change(() -> {
            if (order == null) services.workOrders().create(token, input);
            else services.workOrders().update(token, order.id(), input);
            return true;
        }, reload);
    }

    private void users() {
        JPanel body = page("用户管理", "管理账号、启停状态及用户角色。系统会保护最后一个完整管理员。");
        JPanel toolbar = toolbar(); DefaultTableModel model = model("编号", "用户名", "显示名称", "状态", "角色");
        JTable table = table(model); List<User> rows = new ArrayList<>(); String token = session.token();
        Runnable reload = () -> run(() -> services.users().list(token), data -> {
            rows.clear(); rows.addAll(data); model.setRowCount(0);
            for (User u : data) model.addRow(new Object[]{u.id(), u.username(), u.displayName(), u.active() ? "启用" : "停用", String.join("、", u.roles())});
        });
        toolbar.add(action("刷新", "user:list", reload));
        toolbar.add(action("新增用户", "user:manage", () -> {
            JTextField name = new JTextField(25), display = new JTextField(25); JPasswordField password = new JPasswordField(25);
            if (!dialog("新增用户", form("用户名", name, "显示名称", display, "初始密码", password))) return;
            String username = name.getText().trim(), displayName = display.getText().trim(); char[] secret = password.getPassword();
            change(() -> { try { return services.users().create(token, username, displayName, secret); }
                finally { Arrays.fill(secret, '\0'); } }, reload);
        }));
        toolbar.add(action("编辑 / 启停", "user:manage", () -> selected(table, rows, u -> {
            JTextField display = new JTextField(u.displayName(), 25); JCheckBox active = new JCheckBox("账号启用", u.active());
            if (!dialog("编辑用户 " + u.username(), form("显示名称", display, "账号状态", active))) return;
            String displayName = display.getText().trim(); boolean enabled = active.isSelected();
            change(() -> { services.users().update(token, u.id(), displayName, enabled); return true; }, reload);
        })));
        toolbar.add(action("删除用户", "user:manage", () -> selected(table, rows, u -> {
            if (confirm("确定删除用户“" + u.username() + "”？"))
                change(() -> { services.users().delete(token, u.id()); return true; }, reload);
        })));
        toolbar.add(action("重置密码", new String[]{"user:manage", "permission:grant"}, () -> selected(table, rows, u -> {
            JPasswordField password = new JPasswordField(25);
            if (!dialog("重置密码 · " + u.username(), form("新密码", password))) return;
            char[] secret = password.getPassword();
            change(() -> { try { services.users().resetPassword(token, u.id(), secret); return true; }
                finally { Arrays.fill(secret, '\0'); } }, reload);
        })));
        toolbar.add(action("分配角色", new String[]{"user:manage", "permission:grant", "role:list"}, () -> selected(table, rows, u ->
            run(() -> new RoleChoices(services.roles().list(token), services.users().roleIds(token, u.id())), data -> {
                Map<Long, JCheckBox> boxes = new LinkedHashMap<>(); JPanel list = choicesPanel();
                for (Role role : data.roles()) {
                    JCheckBox box = new JCheckBox(role.name() + "  [" + role.code() + "]", data.selected().contains(role.id()));
                    boxes.put(role.id(), box); list.add(box);
                }
                if (dialog("分配角色 · " + u.username(), scrollChoices(list))) {
                    Set<Long> ids = checked(boxes);
                    change(() -> { services.users().setRoles(token, u.id(), ids); return true; }, reload);
                }
            }))));
        body.add(toolbar, BorderLayout.NORTH); body.add(new JScrollPane(table)); if (allowed("user:list")) reload.run();
    }

    private void roles() {
        JPanel body = page("角色与权限", "通过角色关联权限。菜单、页面和操作权限分别授权。");
        JPanel toolbar = toolbar(); DefaultTableModel model = model("编号", "角色编码", "角色名称");
        JTable table = table(model); List<Role> rows = new ArrayList<>(); String token = session.token();
        Runnable reload = () -> run(() -> services.roles().list(token), data -> {
            rows.clear(); rows.addAll(data); model.setRowCount(0);
            for (Role role : data) model.addRow(new Object[]{role.id(), role.code(), role.name()});
        });
        toolbar.add(action("刷新", "role:list", reload));
        toolbar.add(action("新增角色", "role:manage", () -> {
            JTextField code = new JTextField(25), name = new JTextField(25);
            if (!dialog("新增角色", form("角色编码", code, "角色名称", name))) return;
            String c = code.getText().trim(), n = name.getText().trim();
            change(() -> services.roles().create(token, c, n), reload);
        }));
        toolbar.add(action("编辑角色", "role:manage", () -> selected(table, rows, role -> {
            JTextField name = new JTextField(role.name(), 25);
            if (!dialog("编辑角色 " + role.code(), form("角色名称", name))) return;
            String n = name.getText().trim();
            change(() -> { services.roles().update(token, role.id(), n); return true; }, reload);
        })));
        toolbar.add(action("删除角色", "role:manage", () -> selected(table, rows, role -> {
            if (confirm("确定删除角色“" + role.name() + "”及其授权关联？"))
                change(() -> { services.roles().delete(token, role.id()); return true; }, reload);
        })));
        toolbar.add(action("配置权限", "permission:grant", () -> selected(table, rows, role ->
            run(() -> new PermissionChoices(services.roles().permissions(token), services.roles().permissionIds(token, role.id())), data -> {
                JPanel list = choicesPanel(); Map<Long, JCheckBox> boxes = new LinkedHashMap<>();
                for (Permission permission : data.permissions()) {
                    JCheckBox box = new JCheckBox("[" + permission.type() + "] " + permission.name() + "  (" + permission.code() + ")",
                            data.selected().contains(permission.id()));
                    boxes.put(permission.id(), box); list.add(box);
                }
                if (dialog("配置权限 · " + role.name(), scrollChoices(list))) {
                    Set<Long> ids = checked(boxes);
                    change(() -> { services.roles().setPermissions(token, role.id(), ids); return true; }, reload);
                }
            }))));
        body.add(toolbar, BorderLayout.NORTH); body.add(new JScrollPane(table)); if (allowed("role:list")) reload.run();
    }

    private void reports() {
        JPanel body = page("工单统计", "实时查看各处理状态下的工单数量。");
        JPanel toolbar = toolbar(); DefaultTableModel model = model("状态", "数量");
        String token = session.token();
        Runnable reload = () -> run(() -> services.reports().statistics(token), data -> {
            model.setRowCount(0); model.addRow(new Object[]{"全部工单", data.total()});
            for (String state : STATUSES) model.addRow(new Object[]{state, data.byStatus().getOrDefault(state, 0L)});
        });
        toolbar.add(action("刷新统计", "report:view", reload)); body.add(toolbar, BorderLayout.NORTH);
        body.add(new JScrollPane(table(model))); if (allowed("report:view")) reload.run();
    }

    private void audit() {
        JPanel body = page("审计日志", "追踪登录、授权变更、业务操作和被拒绝的请求。");
        JPanel toolbar = toolbar(); JTextField query = new JTextField(25);
        toolbar.add(new JLabel("关键字")); toolbar.add(query);
        DefaultTableModel model = model("编号", "时间", "操作人", "操作", "对象", "结果", "说明");
        String token = session.token();
        Runnable reload = () -> {
            String q = query.getText().trim();
            run(() -> services.audit().list(token, q), data -> {
                model.setRowCount(0);
                for (AuditEntry entry : data) model.addRow(new Object[]{entry.id(), entry.createdAt(), entry.actorName(),
                        entry.action(), entry.target(), entry.result(), entry.detail()});
            });
        };
        toolbar.add(action("查询日志", "audit:view", reload)); body.add(toolbar, BorderLayout.NORTH);
        JTable table = table(model); table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        int[] widths = {65, 200, 110, 180, 150, 95, 350};
        for (int i = 0; i < widths.length; i++) table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        body.add(new JScrollPane(table)); if (allowed("audit:view")) reload.run();
    }

    private boolean allowed(String permission) { return session != null && session.permissions().contains(permission); }
    private JButton action(String text, String permission, Runnable handler) { return action(text, new String[]{permission}, handler); }
    private JButton action(String text, String[] permissions, Runnable handler) {
        JButton button = new JButton(text); button.setName("action." + text);
        List<String> missing = Arrays.stream(permissions).filter(p -> !allowed(p)).toList();
        button.setEnabled(missing.isEmpty());
        if (!missing.isEmpty()) button.setToolTipText("缺少权限：" + String.join("、", missing));
        button.addActionListener(new ActionListener() {
            @Override public void actionPerformed(ActionEvent event) { handler.run(); }
        }); return button;
    }

    private <T> void run(Callable<T> work, Consumer<T> success) {
        String token = session == null ? null : session.token(); long expected = revision;
        if (statusLine != null) statusLine.setText("正在处理…");
        new BackgroundAction<>(work,
                () -> frame.isDisplayable() && revision == expected && session != null && Objects.equals(token, session.token()),
                result -> { statusLine.setText("操作完成"); success.accept(result); }, this::error).actionPerformed(null);
    }

    private <T> void change(Callable<T> work, Runnable reload) { run(work, result -> reload.run()); }
    private void endSession() {
        Session old = session; session = null; revision++;
        if (old != null) new BackgroundAction<>(() -> { services.authentication().logout(old.token()); return true; },
                () -> false, ignored -> {}, ignored -> {}).actionPerformed(null);
    }
    private void error(Throwable problem) {
        statusLine.setText("操作未完成 · " + message(problem));
        JOptionPane.showMessageDialog(frame, message(problem), "操作未完成", JOptionPane.WARNING_MESSAGE);
    }
    private static String message(Throwable problem) {
        String text = problem.getMessage();
        return text == null || text.isBlank() ? "操作失败，请稍后重试。" : text;
    }
    private boolean dialog(String title, JComponent content) {
        return JOptionPane.showConfirmDialog(frame, content, title, JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE) == JOptionPane.OK_OPTION;
    }
    private boolean confirm(String prompt) {
        return JOptionPane.showConfirmDialog(frame, prompt, "确认操作", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE) == JOptionPane.OK_OPTION;
    }
    private <T> void selected(JTable table, List<T> data, Consumer<T> consumer) {
        int row = table.getSelectedRow();
        if (row < 0 || table.convertRowIndexToModel(row) >= data.size()) {
            JOptionPane.showMessageDialog(frame, "请先选择一行记录。"); return;
        }
        consumer.accept(data.get(table.convertRowIndexToModel(row)));
    }
    private static JPanel toolbar() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 6));
        // Two rows at a fixed useful height prevent controls being clipped on smaller screens.
        panel.setPreferredSize(new Dimension(800, 84)); panel.setBackground(Color.WHITE); return panel;
    }
    private static JTable table(DefaultTableModel model) {
        JTable table = new JTable(model); table.setRowHeight(31); table.setAutoCreateRowSorter(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION); table.setFillsViewportHeight(true);
        table.setShowVerticalLines(false); table.setGridColor(new Color(230, 235, 241));
        table.getTableHeader().setReorderingAllowed(false); return table;
    }
    private static DefaultTableModel model(String... columns) {
        return new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
    }
    private static JPanel form(Object... pairs) {
        JPanel panel = new JPanel(new GridBagLayout()); GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(7, 8, 7, 8); gc.anchor = GridBagConstraints.WEST;
        for (int i = 0; i < pairs.length; i += 2) {
            gc.gridy = i / 2; gc.gridx = 0; gc.weightx = 0; gc.fill = GridBagConstraints.NONE;
            panel.add(new JLabel((String) pairs[i]), gc); gc.gridx = 1; gc.weightx = 1; gc.fill = GridBagConstraints.HORIZONTAL;
            panel.add((Component) pairs[i + 1], gc);
        } return panel;
    }
    private static JPanel choicesPanel() { JPanel panel = new JPanel(); panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS)); return panel; }
    private static JScrollPane scrollChoices(JPanel panel) {
        JScrollPane scroll = new JScrollPane(panel); scroll.setPreferredSize(new Dimension(600, 400)); return scroll;
    }
    private static Set<Long> checked(Map<Long, JCheckBox> boxes) {
        Set<Long> ids = new HashSet<>(); boxes.forEach((id, box) -> { if (box.isSelected()) ids.add(id); }); return ids;
    }
    private record Choice(Long id, String label) { @Override public String toString() { return label; } }
    private record RoleChoices(List<Role> roles, Set<Long> selected) {}
    private record PermissionChoices(List<Permission> permissions, Set<Long> selected) {}
}
