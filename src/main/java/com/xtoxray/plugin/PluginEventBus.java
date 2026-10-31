package com.xtoxray.plugin;

import com.xtoxray.api.plugin.event.EventBus;
import com.xtoxray.api.plugin.event.Listener;
import com.xtoxray.api.plugin.event.PluginEvent;
import com.xtoxray.api.plugin.event.Subscribe;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

/** Host side of the bus. Plugins only get the scoped view, so they cannot post fake events. */
public final class PluginEventBus {
    private record Handler(Object owner, Listener listener, Method method, Class<?> eventType) {
    }

    private final List<Handler> handlers = new CopyOnWriteArrayList<>();
    private final BiConsumer<Object, Throwable> errorHandler;

    public PluginEventBus(BiConsumer<Object, Throwable> errorHandler) {
        this.errorHandler = errorHandler;
    }

    public EventBus scopedTo(Object owner) {
        return new EventBus() {
            @Override
            public void register(Listener listener) {
                add(owner, listener);
            }

            @Override
            public void unregister(Listener listener) {
                handlers.removeIf(h -> h.owner() == owner && h.listener() == listener);
            }
        };
    }

    private void add(Object owner, Listener listener) {
        if (listener == null) return;
        for (Method method : listener.getClass().getMethods()) {
            if (!method.isAnnotationPresent(Subscribe.class)) continue;
            Class<?>[] params = method.getParameterTypes();
            if (params.length != 1 || !PluginEvent.class.isAssignableFrom(params[0])) {
                throw new IllegalArgumentException("@Subscribe method needs one PluginEvent parameter: " + method);
            }
            method.setAccessible(true);
            handlers.add(new Handler(owner, listener, method, params[0]));
        }
    }

    public void removeOwner(Object owner) {
        handlers.removeIf(h -> h.owner() == owner);
    }

    public void post(PluginEvent event) {
        for (Handler handler : handlers) {
            if (!handler.eventType().isInstance(event)) continue;
            try {
                handler.method().invoke(handler.listener(), event);
            } catch (InvocationTargetException e) {
                errorHandler.accept(handler.owner(), e.getCause());
            } catch (ReflectiveOperationException | RuntimeException e) {
                errorHandler.accept(handler.owner(), e);
            }
        }
    }
}
