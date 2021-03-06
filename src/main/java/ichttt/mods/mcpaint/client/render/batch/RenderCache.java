package ichttt.mods.mcpaint.client.render.batch;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import ichttt.mods.mcpaint.MCPaint;
import ichttt.mods.mcpaint.MCPaintConfig;
import ichttt.mods.mcpaint.client.render.buffer.BufferManager;
import ichttt.mods.mcpaint.common.capability.IPaintable;

import javax.annotation.Nonnull;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class RenderCache {
    private static final Cache<IPaintable, BufferManager> PAINT_CACHE = CacheBuilder.newBuilder()
            .concurrencyLevel(2)
            .expireAfterAccess(30L, TimeUnit.SECONDS)
            .build();

    private static final ThreadPoolExecutor POOL_EXECUTOR = new ThreadPoolExecutor(1, Runtime.getRuntime().availableProcessors() > 8 ? 4 : 2, 30L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(), new ThreadFactory() {
        private final AtomicInteger count = new AtomicInteger(1);

        @Override
        public Thread newThread(@Nonnull Runnable runnable) {
            Thread thread = new Thread(runnable, "MCPaint PictureOptimizer Thread-" + count.getAndIncrement());
            MCPaint.LOGGER.info("Starting " + thread.getName());
            thread.setDaemon(true);
            return thread;
        }
    });

    public static void getOrRequest(IPaintable paintable, IOptimisationCallback callback) {
        if (!MCPaintConfig.CLIENT.optimizePictures.get()) return;
        BufferManager builder = getIfPresent(paintable);
        if (builder != null) {
            callback.provideFinishedBuffer(builder);
            return;
        }
        POOL_EXECUTOR.execute(new PictureOptimizationJob(paintable, callback));
    }

    public static BufferManager getIfPresent(IPaintable paintable) {
        return PAINT_CACHE.getIfPresent(paintable);
    }

    public static void cache(IPaintable paintable, BufferManager obj) {
        if (MCPaintConfig.CLIENT.optimizePictures.get())
            PAINT_CACHE.put(paintable, obj);
    }

    public static void uncache(IPaintable paintable) {
        PAINT_CACHE.invalidate(paintable);
    }

    public static void clear() {
        PAINT_CACHE.invalidateAll();
        PAINT_CACHE.cleanUp();
    }

    public static void scheduleCleanup() {
        if (MCPaintConfig.CLIENT.optimizePictures.get())
            POOL_EXECUTOR.execute(PAINT_CACHE::cleanUp);
    }

    public static void onConfigReload() {
        POOL_EXECUTOR.allowCoreThreadTimeOut(!MCPaintConfig.CLIENT.optimizePictures.get());
        clear();
    }
}
