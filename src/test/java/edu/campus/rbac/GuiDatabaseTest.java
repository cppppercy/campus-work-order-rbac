package edu.campus.rbac;

import edu.campus.rbac.ui.CampusApplication;
import edu.campus.rbac.model.Models.*;
import edu.campus.rbac.service.Services;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import java.util.HashSet;
import java.util.Set;
import javax.imageio.ImageIO;
import javax.swing.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** 使用真实 SQLite 和真实 Service 的可见 Swing 验收，并生成组件截图。 */
@Tag("gui")
class GuiDatabaseTest {
    @TempDir Path directory;
    private JFrame frame;
    private volatile String latestUiToken;
    private final Set<String> completedLogouts = java.util.concurrent.ConcurrentHashMap.newKeySet();

    @AfterEach void close() throws Exception {
        String token = latestUiToken;
        SwingUtilities.invokeAndWait(() -> {
            for (Window window : Window.getWindows()) window.dispose();
        });
        // WindowClosed 会异步记录退出审计；等待真实服务关闭连接后再让 @TempDir 清理。
        if (token != null) awaitData(() -> completedLogouts.contains(token));
    }

    @Test void realDatabaseShowsRoleSpecificMenusAndButtons() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());
        CampusApplication.launch(trackLogout(ApplicationFactory.open(directory.resolve("gui.db"))));
        await(() -> {
            for (Frame candidate : Frame.getFrames())
                if (candidate instanceof JFrame found && found.isShowing()) frame = found;
            return frame != null && named("login.submit") != null;
        });

        login("admin");
        await(() -> button("角色与权限") != null);
        capture("admin-dashboard.png");
        click("工单管理");
        await(() -> named("action.新增") != null);
        await(() -> firstTable() != null && firstTable().getRowCount() >= 2);
        assertTrue(onEdt(() -> named("action.新增").isEnabled()));
        capture("admin-workorders.png");
        click("角色与权限");
        await(() -> named("action.配置权限") != null);
        await(() -> firstTable() != null && firstTable().getRowCount() == 4);
        await(() -> named("roles.permission.groups") != null);
        capture("admin-roles.png");
        clickModal("配置继承"); await(() -> modal("dialog.form") != null);
        assertNotNull(onEdt(() -> named(modal("dialog.form"), "roles.hierarchy.editor")));
        capture(modalOnEdt("dialog.form"), "admin-role-hierarchy.png");
        answerModal(false);

        click("退出登录");
        await(() -> named("login.submit") != null);
        login("viewer");
        await(() -> button("工单管理") != null);
        assertNull(onEdt(() -> button("用户管理")));
        assertNull(onEdt(() -> button("角色与权限")));
        click("工单管理");
        await(() -> named("action.新增") != null);
        await(() -> firstTable() != null && firstTable().getRowCount() >= 2);
        assertFalse(onEdt(() -> named("action.新增").isEnabled()));
        assertTrue(onEdt(() -> named("action.查询").isEnabled()));
        capture("viewer-workorders.png");
    }

    @Test void realModalCreateDeleteAndRoleAuthorizationPersistWithCancellation() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());
        Services.Bundle services = ApplicationFactory.open(directory.resolve("modal.db"));
        Session inspector = services.authentication().login("admin", "Demo@12345".toCharArray());
        String token = inspector.token();
        Role staff = services.roles().list(token).stream().filter(role -> "STAFF".equals(role.code())).findFirst().orElseThrow();
        long deleteId = services.roles().permissions(token).stream().filter(permission -> "workorder:delete".equals(permission.code()))
                .findFirst().orElseThrow().id();
        Set<Long> original = services.roles().permissionIds(token, staff.id());
        CampusApplication.launch(trackLogout(services));
        await(() -> {
            for (Frame candidate : Frame.getFrames()) if (candidate instanceof JFrame found && found.isShowing()) frame = found;
            return frame != null && named("login.submit") != null;
        });
        login("admin"); await(() -> button("角色与权限") != null);
        click("工单管理"); await(() -> firstTable() != null && firstTable().getRowCount() >= 2);

        // 模态对话框必须异步点击，避免测试线程 invokeAndWait 等待整个模态会话。
        clickModal("新增"); await(() -> modal("dialog.form") != null);
        onEdt(() -> {
            JDialog editor = modal("dialog.form");
            ((JTextField) named(editor, "workorder.editor.title")).setText("GUI验收临时工单");
            ((JTextArea) named(editor, "workorder.editor.content")).setText("由真实Swing表单创建，用于删除确认与取消验证。");
            return null;
        });
        answerModal(true);
        await(() -> rowWith(firstTable(), 1, "GUI验收临时工单") >= 0);
        assertEquals(1, services.workOrders().list(token, "GUI验收临时工单", null).size());
        onEdt(() -> { int row = rowWith(firstTable(), 1, "GUI验收临时工单"); firstTable().setRowSelectionInterval(row, row); return null; });
        clickModal("删除"); await(() -> modal("dialog.confirmation") != null);
        assertTrue(onEdt(() -> ((JTextArea) named(modal("dialog.confirmation"), "confirmation.message")).getText().contains("GUI验收临时工单")));
        capture(modalOnEdt("dialog.confirmation"), "workorder-delete-confirm.png");
        answerModal(false);
        assertEquals(1, services.workOrders().list(token, "GUI验收临时工单", null).size(), "取消删除必须保留工单");
        clickModal("删除"); await(() -> modal("dialog.confirmation") != null); answerModal(true);
        await(() -> rowWith(firstTable(), 1, "GUI验收临时工单") < 0);
        assertTrue(services.workOrders().list(token, "GUI验收临时工单", null).isEmpty());

        click("角色与权限"); await(() -> firstTable() != null && firstTable().getRowCount() == 4);
        onEdt(() -> { int row = rowWith(firstTable(), 1, "STAFF"); firstTable().setRowSelectionInterval(row, row); return null; });
        await(() -> named("roles.permission.groups") != null && ((JLabel) named("roles.permission.summary")).getText().startsWith(staff.name()));
        clickModal("配置权限"); await(() -> modal("dialog.form") != null);
        onEdt(() -> {
            JDialog editor = modal("dialog.form");
            assertNotNull(named(editor, "permissions.group.MENU")); assertNotNull(named(editor, "permissions.group.PAGE"));
            assertNotNull(named(editor, "permissions.group.ACTION"));
            JCheckBox delete = (JCheckBox) named(editor, "permission.workorder:delete");
            assertFalse(delete.isSelected()); delete.doClick(); return null;
        });
        answerModal(false);
        assertEquals(original, services.roles().permissionIds(token, staff.id()), "取消配置不得修改数据库授权");
        clickModal("配置权限"); await(() -> modal("dialog.form") != null);
        onEdt(() -> { ((JCheckBox) named(modal("dialog.form"), "permission.workorder:delete")).doClick(); return null; });
        capture(modalOnEdt("dialog.form"), "admin-permissions.png");
        answerModal(true);
        awaitData(() -> services.roles().permissionIds(token, staff.id()).contains(deleteId));

        click("退出登录"); await(() -> named("login.submit") != null);
        login("staff"); await(() -> button("工单管理") != null);
        click("工单管理"); await(() -> named("action.删除") != null && firstTable().getRowCount() >= 2);
        assertTrue(onEdt(() -> named("action.删除").isEnabled())); capture("staff-workorders-granted.png");
        // 另一管理员会话撤销权限，同一个员工会话刷新后立即体现变化。
        services.roles().setPermissions(token, staff.id(), new HashSet<>(original));
        click("刷新权限"); await(() -> named("dashboard.identity") != null);
        click("工单管理"); await(() -> named("action.删除") != null && firstTable().getRowCount() >= 2);
        assertFalse(onEdt(() -> named("action.删除").isEnabled())); capture("staff-workorders.png");
        services.authentication().logout(token);
    }

    private void login(String username) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            ((JTextField) named("login.username")).setText(username);
            ((JPasswordField) named("login.password")).setText("Demo@12345");
            ((JButton) named("login.submit")).doClick();
        });
    }

    private void click(String text) throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            JButton value = button(text);
            assertNotNull(value, "找不到按钮：" + text);
            value.doClick();
        });
    }

    private void capture(String name) throws Exception {
        capture(frame, name);
    }

    private void capture(Window window, String name) throws Exception {
        Path output = Path.of("docs", "images", name).toAbsolutePath();
        Files.createDirectories(output.getParent());
        AtomicReference<BufferedImage> image = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            assertTrue(window.isShowing(), "只能截取实际已显示的组件");
            Container content = window instanceof JFrame f ? f.getContentPane() : ((JDialog) window).getContentPane();
            BufferedImage value = new BufferedImage(content.getWidth(), content.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = value.createGraphics();
            content.printAll(graphics);
            graphics.dispose();
            image.set(value);
        });
        ImageIO.write(image.get(), "png", output.toFile());
        assertTrue(Files.size(output) > (window instanceof JDialog ? 1_500 : 10_000), "截图内容异常：" + output);
    }

    private Services.Bundle trackLogout(Services.Bundle actual) {
        Services.Authentication authentication = new Services.Authentication() {
            @Override public Session login(String username, char[] password) {
                Session result = actual.authentication().login(username, password); latestUiToken = result.token(); return result;
            }
            @Override public void logout(String token) {
                try { actual.authentication().logout(token); } finally { completedLogouts.add(token); }
            }
        };
        return new Services.Bundle(authentication, actual.permissions(), actual.users(), actual.roles(), actual.workOrders(), actual.reports(), actual.audit());
    }

    private Component named(String name) { return find(frame, value -> name.equals(value.getName())); }
    private static Component named(Container parent, String name) { return find(parent, value -> name.equals(value.getName())); }
    private void clickModal(String text) throws Exception {
        JButton target = onEdt(() -> button(text)); assertNotNull(target); assertTrue(onEdt(target::isEnabled));
        SwingUtilities.invokeLater(target::doClick);
    }
    private static JDialog modal(String name) {
        for (Window window : Window.getWindows())
            if (window instanceof JDialog dialog && dialog.isShowing() && name.equals(dialog.getName())) return dialog;
        return null;
    }
    private static JDialog modalOnEdt(String name) throws Exception { return onEdt(() -> modal(name)); }
    private static void answerModal(boolean accept) throws Exception {
        JDialog dialog = onEdt(() -> {
            for (Window window : Window.getWindows()) if (window instanceof JDialog d && d.isShowing()) return d;
            return null;
        });
        assertNotNull(dialog);
        JButton target = onEdt(() -> (JButton) named(dialog, accept ? "dialog.confirm" : "dialog.cancel"));
        assertNotNull(target); SwingUtilities.invokeLater(target::doClick); await(() -> !dialog.isShowing());
    }
    private static int rowWith(JTable table, int column, String text) {
        if (table == null) return -1;
        for (int row = 0; row < table.getRowCount(); row++) if (text.equals(table.getValueAt(row, column))) return row;
        return -1;
    }
    private static void awaitData(Callable<Boolean> condition) throws Exception {
        long deadline = System.nanoTime() + 15_000_000_000L;
        while (System.nanoTime() < deadline) { if (condition.call()) return; Thread.sleep(40); }
        fail("等待数据库更新超时");
    }
    private JTable firstTable() {
        Component result = find(frame, value -> value instanceof JTable);
        return result instanceof JTable table ? table : null;
    }
    private JButton button(String text) {
        Component result = find(frame, value -> value instanceof JButton b && text.equals(b.getText()));
        return result instanceof JButton button ? button : null;
    }
    private static Component find(Container parent, java.util.function.Predicate<Component> predicate) {
        if (parent == null) return null;
        for (Component component : parent.getComponents()) {
            if (predicate.test(component)) return component;
            if (component instanceof Container child) {
                Component found = find(child, predicate);
                if (found != null) return found;
            }
        }
        return null;
    }
    private static <T> T onEdt(Callable<T> callable) throws Exception {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            try { result.set(callable.call()); } catch (Throwable error) { failure.set(error); }
        });
        if (failure.get() != null) throw new AssertionError(failure.get());
        return result.get();
    }
    private static void await(Callable<Boolean> condition) throws Exception {
        long deadline = System.nanoTime() + 15_000_000_000L;
        while (System.nanoTime() < deadline) {
            if (onEdt(condition)) return;
            Thread.sleep(40);
        }
        fail("等待 Swing 状态超时");
    }
}
