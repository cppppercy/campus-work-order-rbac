package edu.campus.rbac.event;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** 事件监听器：用户事件 → 后台 Service（授权与数据访问）→ EDT 更新界面。 */
public final class BackgroundAction<T> implements ActionListener {
    private final Callable<T> work;
    private final BooleanSupplier current;
    private final Consumer<T> success;
    private final Consumer<Throwable> failure;
    private boolean running;

    public BackgroundAction(Callable<T> work, BooleanSupplier current,
                            Consumer<T> success, Consumer<Throwable> failure) {
        this.work = work;
        this.current = current;
        this.success = success;
        this.failure = failure;
    }

    @Override public void actionPerformed(ActionEvent event) {
        if (running) return;
        running = true;
        new SwingWorker<T, Void>() {
            @Override protected T doInBackground() throws Exception { return work.call(); }
            @Override protected void done() {
                running = false;
                // 注销、切换账号或离开页面后，丢弃旧任务的界面回调。
                if (!current.getAsBoolean()) return;
                try { success.accept(get()); }
                catch (InterruptedException ex) { Thread.currentThread().interrupt(); failure.accept(ex); }
                catch (ExecutionException ex) { failure.accept(ex.getCause()); }
                catch (RuntimeException ex) { failure.accept(ex); }
            }
        }.execute();
    }
}
