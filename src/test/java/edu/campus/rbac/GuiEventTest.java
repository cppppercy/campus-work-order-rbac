package edu.campus.rbac;

import edu.campus.rbac.event.BackgroundAction;
import org.junit.jupiter.api.Test;
import javax.swing.SwingUtilities;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class GuiEventTest {
    @Test void workRunsOffEdtAndResultReturnsOnEdt() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicBoolean background = new AtomicBoolean(), edt = new AtomicBoolean();
        AtomicReference<Integer> result = new AtomicReference<>();
        BackgroundAction<Integer> action = new BackgroundAction<>(() -> {
            background.set(!SwingUtilities.isEventDispatchThread()); return 42;
        }, () -> true, value -> { result.set(value); edt.set(SwingUtilities.isEventDispatchThread()); done.countDown(); },
                failure -> done.countDown());
        SwingUtilities.invokeAndWait(() -> action.actionPerformed(null));
        assertTrue(done.await(5, TimeUnit.SECONDS)); assertTrue(background.get()); assertTrue(edt.get()); assertEquals(42, result.get());
    }

    @Test void departedSessionDropsResultAndDuplicateClickDoesNotRepeatWork() throws Exception {
        CountDownLatch started = new CountDownLatch(1), release = new CountDownLatch(1), checked = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger(), callbacks = new AtomicInteger();
        AtomicBoolean current = new AtomicBoolean(true);
        BackgroundAction<Integer> action = new BackgroundAction<>(() -> {
            calls.incrementAndGet(); started.countDown(); release.await(5, TimeUnit.SECONDS); return 1;
        }, () -> { checked.countDown(); return current.get(); }, value -> callbacks.incrementAndGet(), failure -> callbacks.incrementAndGet());
        SwingUtilities.invokeAndWait(() -> { action.actionPerformed(null); action.actionPerformed(null); });
        assertTrue(started.await(5, TimeUnit.SECONDS)); current.set(false); release.countDown();
        assertTrue(checked.await(5, TimeUnit.SECONDS)); SwingUtilities.invokeAndWait(() -> {});
        assertEquals(1, calls.get()); assertEquals(0, callbacks.get());
    }

    @Test void serviceFailureIsUnwrappedAndDeliveredOnEdt() throws Exception {
        CountDownLatch done = new CountDownLatch(1); AtomicReference<Throwable> captured = new AtomicReference<>();
        IllegalStateException expected = new IllegalStateException("当前账号没有所需权限");
        AtomicBoolean edt = new AtomicBoolean();
        SwingUtilities.invokeAndWait(() -> new BackgroundAction<>(() -> { throw expected; }, () -> true,
                value -> done.countDown(), failure -> { captured.set(failure); edt.set(SwingUtilities.isEventDispatchThread()); done.countDown(); })
                .actionPerformed(null));
        assertTrue(done.await(5, TimeUnit.SECONDS)); assertSame(expected, captured.get()); assertTrue(edt.get());
    }
}
