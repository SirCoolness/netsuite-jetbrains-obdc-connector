package com.netsuite.jetbrains;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

/**
 * Helpers shared by the Connection, Statement and DatabaseMetaData proxies.
 */
final class Proxies {

    private Proxies() {}

    /**
     * Create a proxy that implements every interface of the real object's class hierarchy
     * (not just {@code required}), so instanceof checks against vendor interfaces still pass.
     */
    static <T> T create(Object real, Class<T> required, InvocationHandler handler) {
        List<Class<?>> interfaces = new ArrayList<Class<?>>();
        for (Class<?> c = real.getClass(); c != null; c = c.getSuperclass()) {
            for (Class<?> iface : c.getInterfaces()) {
                if (!interfaces.contains(iface)) {
                    interfaces.add(iface);
                }
            }
        }
        if (!interfaces.contains(required)) {
            interfaces.add(required);
        }
        return required.cast(Proxy.newProxyInstance(
            real.getClass().getClassLoader(), interfaces.toArray(new Class<?>[0]), handler));
    }

    /**
     * Invoke the method on the target, rethrowing the target's own exception.
     */
    static Object invoke(Object target, Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    /** Returned by {@link #common} when the method is not one it answers. */
    static final Object UNHANDLED = new Object();

    interface Target {
        Object get() throws Throwable;
    }

    /**
     * Methods every wrapper answers the same way: identity equals/hashCode on the proxy, and
     * unwrap/isWrapperFor that hand out the proxy for any interface it implements, so callers
     * never reach the raw OpenAccess object through java.sql interfaces. Only a request for a
     * vendor class the proxy does not implement is delegated.
     */
    static Object common(Object proxy, Method method, Object[] args, Target real) throws Throwable {
        String name = method.getName();
        int argc = args == null ? 0 : args.length;
        if ("equals".equals(name) && argc == 1 && method.getParameterTypes()[0] == Object.class) {
            return proxy == args[0];
        }
        if ("hashCode".equals(name) && argc == 0) {
            return System.identityHashCode(proxy);
        }
        if (("unwrap".equals(name) || "isWrapperFor".equals(name)) && argc == 1 && args[0] instanceof Class) {
            boolean own = ((Class<?>) args[0]).isInstance(proxy);
            if ("unwrap".equals(name)) {
                return own ? proxy : invoke(real.get(), method, args);
            }
            return own || (Boolean) invoke(real.get(), method, args);
        }
        return UNHANDLED;
    }

    static boolean isNoArg(Object[] args) {
        return args == null || args.length == 0;
    }
}
