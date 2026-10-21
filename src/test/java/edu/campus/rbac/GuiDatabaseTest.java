package edu.campus.rbac;

import edu.campus.rbac.ui.CampusApplication;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
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

    @AfterEach void close() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            for (Window window : Window.getWindows()) window.dispose();
        });
    }

    @Test void realDatabaseShowsRoleSpecificMenusAndButtons() throws Exception {
        Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());
        CampusApplication.launch(ApplicationFactory.open(directory.resolve("gui.db")));
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
        capture("admin-roles.png");

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
        Path output = Path.of("docs", "images", name).toAbsolutePath();
        Files.createDirectories(output.getParent());
        AtomicReference<BufferedImage> image = new AtomicReference<>();
        SwingUtilities.invokeAndWait(() -> {
            BufferedImage value = new BufferedImage(frame.getWidth(), frame.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = value.createGraphics();
            frame.paintAll(graphics);
            graphics.dispose();
            image.set(value);
        });
        ImageIO.write(image.get(), "png", output.toFile());
        assertTrue(Files.size(output) > 10_000, "截图内容异常：" + output);
    }

    private Component named(String name) { return find(frame, value -> name.equals(value.getName())); }
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
