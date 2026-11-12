package io.github.tehcneko.hyperinstaller;

import androidx.annotation.NonNull;
import android.util.Log;
import io.github.libxposed.api.XposedModule;
import java.lang.reflect.Method;

public class Hooker extends XposedModule {
    private static final String TAG = "HyperInstaller";

    // Tracks nested calls on the current system_server thread.
    private static final ThreadLocal<Integer> FAKE_CTS_DEPTH =
            ThreadLocal.withInitial(() -> 0);

    @Override
    public void onSystemServerStarting(
            @NonNull SystemServerStartingParam param) {
        ClassLoader classLoader = param.getClassLoader();

        try {
            hookPackageManagerServiceImpl(classLoader);
        } catch (Throwable t) {
            log(Log.ERROR, TAG,
                    "Failed to hook PackageManagerServiceImpl", t);
        }

        try {
            hookIsCTS(classLoader);
        } catch (Throwable t) {
            log(Log.ERROR, TAG, "Failed to hook isCTS", t);
        }
    }

    private void hookPackageManagerServiceImpl(
            ClassLoader classLoader) throws ClassNotFoundException {
        Class<?> pms = classLoader.loadClass(
                "com.android.server.pm.PackageManagerServiceImpl"
        );

        for (Method method : pms.getDeclaredMethods()) {
            String name = method.getName();
            if ("hookChooseBestActivity".equals(name)
                    || "updateDefaultPkgInstallerLocked".equals(name)
                    || "assertValidApkAndInstaller".equals(name)) {
                log(Log.DEBUG, TAG, "Hooking method " + name, null);

                hook(method).intercept(chain -> {
                    int previousDepth = FAKE_CTS_DEPTH.get();
                    FAKE_CTS_DEPTH.set(previousDepth + 1);
                    try {
                        return chain.proceed();
                    } finally {
                        if (previousDepth == 0) {
                            FAKE_CTS_DEPTH.remove();
                        } else {
                            FAKE_CTS_DEPTH.set(previousDepth);
                        }
                    }
                });

                deoptimize(method);
            }
        }
    }

    private void hookIsCTS(
            ClassLoader classLoader)
            throws ClassNotFoundException, NoSuchMethodException {
        Class<?> pms = classLoader.loadClass(
                "com.android.server.pm.PackageManagerServiceImpl"
        );

        Method isCTS = pms.getDeclaredMethod("isCTS");
        hook(isCTS).intercept(chain -> {
            if (FAKE_CTS_DEPTH.get() > 0) {
                return true;
            }
            return chain.proceed();
        });
    }
}
