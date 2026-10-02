package com.xtoxray.api.plugin.event;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class EventBus {
    private final Map<Class<?>, List<Object>> listeners = new ConcurrentHashMap<>();

    public void register(Object listener) {
        if (listener == null) return;
        Class<?> clazz = listener.getClass();
        for (java.lang.reflect.Method method : clazz.getDeclaredMethods()) {
            if (method.isAnnotationPresent(com.xtoxray.api.plugin.event.Subscribe.class)) {
                Class<?>[] params = method.getParameterTypes();
                if (params.length == 1) {
                    Class<?> eventType = params[0];
                    listeners.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>()).add(listener);
                }
            }
        }
    }

    public void post(Object event) {
        if (event == null) return;
        List<Object> handlers = listeners.get(event.getClass());
        if (handlers != null) {
            for (Object handler : handlers) {
                try {
                    java.lang.reflect.Method method = handler.getClass().getDeclaredMethod("onEvent", event.getClass());
                    method.setAccessible(true);
                    method.invoke(handler, event);
                } catch (Exception e) {
                }
            }
        }
    }
}