package com.moulberry.flashback.utils;

import java.lang.reflect.Field;
import java.util.Objects;
import java.util.concurrent.*;

/** Runs production dialog completion from a worker without opening any native UI. */
public final class ClientDialogSmoke {
    static void require(boolean ok, String message) { if (!ok) throw new AssertionError(message); }
    static void check(String selection) throws Exception {
        BlockingQueue<Runnable> clientQueue = new LinkedBlockingQueue<>();
        CompletableFuture<String> result = new CompletableFuture<>();
        Field active = AsyncFileDialogs.class.getDeclaredField("currentSaveOrOpenFileDialog");
        active.setAccessible(true);
        active.set(null, result);
        CompletableFuture<String> callback = result.thenApply(path -> {
            require(Thread.currentThread().getName().equals("Test Client Thread"), "Callback must run on client thread");
            require(!AsyncFileDialogs.hasDialog(), "Dialog flag cleared before callback");
            require(Objects.equals(selection, path), "Selection/cancellation preserved");
            return path;
        });
        Thread worker = new Thread(() -> AsyncFileDialogs.completeOnClient(clientQueue::add, result, selection), "Test Native Dialog Worker");
        worker.start(); worker.join(5000);
        require(!worker.isAlive(), "Worker returned without waiting for client");
        require(!result.isDone(), "Worker must not publish directly");
        require(AsyncFileDialogs.hasDialog(), "Dialog stays active until client receives result");
        Runnable completion = clientQueue.poll(5, TimeUnit.SECONDS);
        require(completion != null, "Completion queued to client");
        Thread client = new Thread(completion, "Test Client Thread");
        client.start(); client.join(5000);
        require(Objects.equals(selection, callback.get(5, TimeUnit.SECONDS)), "Callback completed");
    }
    public static void main(String[] args) throws Exception {
        check("D:/exports/replay.mp4");
        check(null);
        System.out.println("PASS: dialog selection and cancellation publish on client thread; callbacks see cleared dialog state; worker never runs UI callbacks");
    }
}
