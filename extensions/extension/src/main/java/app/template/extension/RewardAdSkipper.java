package app.template.extension;

import android.util.Log;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashSet;
import java.util.Set;

/**
 * Fakes a completed rewarded ad.
 *
 * The app passes its listener (a Java proxy backed by C# in Unity) into the SDK show() call.
 * We skip the SDK and invoke that listener ourselves, so the app sees the normal
 * "reward granted" + "ad closed" sequence and continues its retry flow.
 */
public final class RewardAdSkipper {
    private static final String TAG = "MeowdokuSkipAds";

    private static final String[] REWARD = {
            "onUserEarnedReward",
            "onAdRewarded",
            "onRewardedVideoAdRewarded",
            "onRewarded",
            "onReward"
    };

    private static final String[] DISMISS = {
            "onAdDismissedFullScreenContent",
            "onAdHidden",
            "onRewardedVideoAdClosed",
            "onAdClosed"
    };

    /** ironSource has no listener on show(), it is registered once up-front. */
    private static Object ironSourceListener;

    private RewardAdSkipper() {}

    public static void captureIronSourceListener(Object listener) {
        ironSourceListener = listener;
        Log.d(TAG, "captured ironSource listener");
    }

    public static void unityAds(String placementId, Object showListener) {
        try {
            // Unity wants onUnityAdsShowComplete(placement, COMPLETED) - one call covers reward + close.
            invokeOne(showListener, "onUnityAdsShowComplete", placementId);
        } catch (Throwable t) {
            Log.w(TAG, "unityAds", t);
        }
    }

    public static void maxAd(Object ad, String placementId, Object listener) {
        try {
            complete(listener, ad, placementId);
        } catch (Throwable t) {
            Log.w(TAG, "maxAd", t);
        }
    }

    public static void ironSource() {
        ironSource(null);
    }

    public static void ironSource(String placementId) {
        try {
            complete(ironSourceListener, null, placementId);
        } catch (Throwable t) {
            Log.w(TAG, "ironSource", t);
        }
    }

    public static void admob(Object ad, Object earnedListener) {
        try {
            complete(earnedListener, ad, null);
            fireFromField(ad, "onAdDismissedFullScreenContent");
        } catch (Throwable t) {
            Log.w(TAG, "admob", t);
        }
    }

    private static void complete(Object listener, Object ad, String placement) {
        if (listener == null) {
            Log.d(TAG, "no listener captured, app keeps its own flow");
            return;
        }
        Set<String> called = new HashSet<>();
        for (Method method : listenerMethods(listener)) {
            String name = method.getName();
            if (!contains(REWARD, name) && !contains(DISMISS, name)) continue;
            if (!called.add(name)) continue;
            invoke(listener, method, ad, placement);
        }
    }

    private static void invokeOne(Object listener, String name, String placement) {
        if (listener == null) return;
        for (Method method : listenerMethods(listener)) {
            if (!name.equals(method.getName())) continue;
            invoke(listener, method, null, placement);
            return;
        }
    }

    private static void invoke(Object listener, Method method, Object ad, String placement) {
        try {
            method.setAccessible(true);
            method.invoke(listener, buildArgs(method, ad, placement));
            Log.d(TAG, "invoked " + method.getName());
        } catch (Throwable t) {
            Log.w(TAG, "invoke " + method.getName() + " failed", t);
        }
    }

    private static Object[] buildArgs(Method method, Object ad, String placement) {
        Class<?>[] types = method.getParameterTypes();
        Object[] args = new Object[types.length];
        for (int i = 0; i < types.length; i++) {
            Class<?> type = types[i];
            if (ad != null && type.isInstance(ad)) {
                args[i] = ad;
            } else if (type == String.class) {
                args[i] = placement == null ? "" : placement;
            } else if (type.isEnum()) {
                args[i] = preferredConstant(type);
            } else if (type.isInterface()) {
                args[i] = stub(type);
            } else {
                args[i] = instance(type);
            }
        }
        return args;
    }

    private static Method[] listenerMethods(Object listener) {
        Set<Method> methods = new HashSet<>();
        for (Class<?> c = listener.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                methods.addAll(java.util.Arrays.asList(c.getMethods()));
            } catch (Throwable ignored) {
            }
        }
        return methods.toArray(new Method[0]);
    }

    private static Object preferredConstant(Class<?> enumType) {
        Object[] constants = enumType.getEnumConstants();
        if (constants == null || constants.length == 0) return null;
        for (String wanted : new String[]{"COMPLETED", "REWARDED", "REWARD"}) {
            for (Object constant : constants) {
                if (constant.toString().contains(wanted)) return constant;
            }
        }
        return constants[0];
    }

    private static Object stub(Class<?> iface) {
        try {
            return Proxy.newProxyInstance(iface.getClassLoader(), new Class<?>[]{iface},
                    (proxy, method, args) -> defaultValue(method.getReturnType()));
        } catch (Throwable t) {
            return null;
        }
    }

    private static Object instance(Class<?> type) {
        try {
            Constructor<?>[] constructors = type.getDeclaredConstructors();
            java.util.Arrays.sort(constructors, (a, b) ->
                    Integer.compare(a.getParameterCount(), b.getParameterCount()));
            for (Constructor<?> constructor : constructors) {
                try {
                    constructor.setAccessible(true);
                    Class<?>[] types = constructor.getParameterTypes();
                    Object[] args = new Object[types.length];
                    for (int i = 0; i < types.length; i++) args[i] = defaultValue(types[i]);
                    return constructor.newInstance(args);
                } catch (Throwable ignored) {
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class) return true;
        if (type == byte.class) return (byte) 1;
        if (type == short.class) return (short) 1;
        if (type == int.class) return 1;
        if (type == long.class) return 1L;
        if (type == float.class) return 1F;
        if (type == double.class) return 1D;
        if (type == char.class) return ' ';
        if (type == String.class) return "";
        return null;
    }

    private static void fireFromField(Object target, String methodName) {
        if (target == null) return;
        for (Class<?> c = target.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field field : c.getDeclaredFields()) {
                try {
                    field.setAccessible(true);
                    Object callback = field.get(target);
                    if (callback == null) continue;
                    for (Method method : listenerMethods(callback)) {
                        if (methodName.equals(method.getName())) {
                            invoke(callback, method, null, null);
                            return;
                        }
                    }
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private static boolean contains(String[] values, String value) {
        for (String candidate : values) if (candidate.equals(value)) return true;
        return false;
    }
}
