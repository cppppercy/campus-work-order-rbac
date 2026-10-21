package edu.campus.rbac;

import edu.campus.rbac.model.Models.*;
import edu.campus.rbac.service.Services;
import edu.campus.rbac.ui.CampusApplication;
import org.junit.jupiter.api.*;
import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

/** Native Swing integration test; run with -Pgui-tests on a graphical desktop. */
@Tag("gui")
class GuiTest {
    private final AtomicReference<JFrame> frame = new AtomicReference<>();
    private final AtomicBoolean serviceOnEdt = new AtomicBoolean();
    private final AtomicInteger pageChecks = new AtomicInteger();
    private final AtomicBoolean revoked = new AtomicBoolean();
    private final Set<String> viewerPermissions = Set.of("menu:workorders", "page:workorders:view", "workorder:list");

    @AfterEach void closeWindows() throws Exception {
        SwingUtilities.invokeAndWait(() -> { for (Window window : Window.getWindows()) window.dispose(); });
    }

    @Test void viewerMenusButtonsPageCheckAndRefreshFollowPermissionSnapshot() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());
        Services.Authentication auth = new Services.Authentication() {
            @Override public Session login(String username, char[] password) {
                checkThread(); return snapshot();
            }
            @Override public void logout(String token) { checkThread(); }
        };
        Services.Permissions permissions = new Services.Permissions() {
            @Override public Session refresh(String token) { checkThread(); return snapshot(); }
            @Override public void require(String token, String permission) { checkThread(); pageChecks.incrementAndGet(); }
        };
        CampusApplication.launch(new Services.Bundle(auth, permissions, stub(Services.Users.class), stub(Services.Roles.class),
                stub(Services.WorkOrders.class), stub(Services.Reports.class), stub(Services.Audit.class)));
        await(() -> {
            for (Frame value : Frame.getFrames()) if (value instanceof JFrame found && found.isShowing()) frame.set(found);
            return frame.get() != null;
        });
        SwingUtilities.invokeAndWait(() -> {
            ((JTextField) named(frame.get(), "login.username")).setText("viewer");
            ((JPasswordField) named(frame.get(), "login.password")).setText("Demo@12345");
            ((JButton) named(frame.get(), "login.submit")).doClick();
        });
        await(() -> button("工单管理") != null);
        onEdt(() -> { assertNull(button("用户管理")); assertNull(button("角色与权限")); assertNull(button("审计日志")); return null; });
        SwingUtilities.invokeAndWait(() -> button("工单管理").doClick());
        await(() -> named(frame.get(), "action.新增") != null);
        onEdt(() -> {
            JButton create = (JButton) named(frame.get(), "action.新增");
            assertFalse(create.isEnabled()); assertTrue(create.getToolTipText().contains("workorder:create"));
            assertTrue(((JButton) named(frame.get(), "action.查询")).isEnabled());
            assertFalse(((JButton) named(frame.get(), "action.导出 CSV")).isEnabled()); return null;
        });
        assertEquals(1, pageChecks.get());
        revoked.set(true); SwingUtilities.invokeAndWait(() -> button("刷新权限").doClick());
        await(() -> button("工单管理") == null && button("退出登录") != null);
        SwingUtilities.invokeAndWait(() -> button("退出登录").doClick());
        await(() -> named(frame.get(), "login.submit") != null);
        assertFalse(serviceOnEdt.get(), "Services must never execute on EDT");
    }

    private Session snapshot() {
        return new Session("test-token", new User(4, "viewer", "只读用户", true, List.of("VIEWER")),
                revoked.get() ? Set.of() : viewerPermissions);
    }
    private void checkThread() { if (SwingUtilities.isEventDispatchThread()) serviceOnEdt.set(true); }
    private <T> T stub(Class<T> type) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
            checkThread();
            if (method.getReturnType() == List.class) return List.of();
            if (method.getReturnType() == Set.class) return Set.of();
            return null;
        }));
    }
    private JButton button(String text) { return find(frame.get(), component -> component instanceof JButton b && text.equals(b.getText())) instanceof JButton b ? b : null; }
    private static Component named(Container parent, String name) { return find(parent, component -> name.equals(component.getName())); }
    private static Component find(Container parent, java.util.function.Predicate<Component> predicate) {
        if (parent == null) return null;
        for (Component component : parent.getComponents()) {
            if (predicate.test(component)) return component;
            if (component instanceof Container child) { Component found = find(child, predicate); if (found != null) return found; }
        } return null;
    }
    private static <T> T onEdt(Callable<T> callable) throws Exception {
        AtomicReference<T> value = new AtomicReference<>(); AtomicReference<Throwable> failure = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> { try { value.set(callable.call()); } catch (Throwable error) { failure.set(error); } });
        if (failure.get() != null) throw new AssertionError(failure.get()); return value.get();
    }
    private static void await(Callable<Boolean> condition) throws Exception {
        long deadline = System.nanoTime() + 10_000_000_000L;
        while (System.nanoTime() < deadline) { if (onEdt(condition)) return; Thread.sleep(40); }
        fail("Timed out waiting for Swing state");
    }
}
