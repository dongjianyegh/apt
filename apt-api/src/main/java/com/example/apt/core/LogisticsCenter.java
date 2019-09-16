package com.example.apt.core;

import android.content.Context;

import com.example.apt.exception.HandlerException;
import com.example.apt.exception.NoRouteFoundException;
import com.example.apt.facade.Postcard;
import com.example.apt.facade.model.RouteMeta;
import com.example.apt.facade.template.IInterceptorGroup;
import com.example.apt.facade.template.IProvider;
import com.example.apt.facade.template.IProviderGroup;
import com.example.apt.facade.template.IRouteGroup;
import com.example.apt.facade.template.IRouteRoot;
import com.example.apt.launcher.ARouter;
import com.example.apt.utils.ClassUtils;
import com.example.apt.utils.Consts;
import com.example.apt.utils.PackageUtils;
import com.example.apt.utils.TextUtils;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ThreadPoolExecutor;

import static com.example.apt.launcher.ARouter.logger;
import static com.example.apt.utils.Consts.AROUTER_SP_CACHE_KEY;
import static com.example.apt.utils.Consts.AROUTER_SP_KEY_MAP;
import static com.example.apt.utils.Consts.DOT;
import static com.example.apt.utils.Consts.ROUTE_ROOT_PAKCAGE;
import static com.example.apt.utils.Consts.SDK_NAME;
import static com.example.apt.utils.Consts.SEPARATOR;
import static com.example.apt.utils.Consts.SUFFIX_PROVIDERS;
import static com.example.apt.utils.Consts.SUFFIX_ROOT;
import static com.example.apt.utils.Consts.TAG;

public class LogisticsCenter {

    private static Context mContext;
    static ThreadPoolExecutor executor;
    private static boolean registerByPlugin;
    /**
     * arouter-auto-register plugin will generate code inside this method
     * call this method to register all Routers, Interceptors and Providers
     * @author billy.qi <a href="mailto:qiyilike@163.com">Contact me.</a>
     * @since 2017-12-06
     */
    private static void loadRouterMap() {
        registerByPlugin = false;
        //auto generate register code by gradle plugin: arouter-auto-register
        // looks like below:
    }

    /**
     * register by class name
     * Sacrificing a bit of efficiency to solve
     * the problem that the main dex file size is too large
     * @author billy.qi <a href="mailto:qiyilike@163.com">Contact me.</a>
     * @param className class name
     */
    private static void register(String className) {
        if (!TextUtils.isEmpty(className)) {
            try {
                Class<?> clazz = Class.forName(className);
                Object obj = clazz.getConstructor().newInstance();
                if (obj instanceof IRouteRoot) {
                    registerRouteRoot((IRouteRoot) obj);
                } else if (obj instanceof IProviderGroup) {
                    registerProvider((IProviderGroup) obj);
                } else if (obj instanceof IInterceptorGroup) {
                    registerInterceptor((IInterceptorGroup) obj);
                } else {
                    logger.info(TAG, "register failed, class name: " + className
                            + " should implements one of IRouteRoot/IProviderGroup/IInterceptorGroup.");
                }
            } catch (Exception e) {
                logger.error(TAG,"register class error:" + className);
            }
        }
    }

    /**
     * method for arouter-auto-register plugin to register Routers
     * @param routeRoot IRouteRoot implementation class in the package: com.alibaba.android.arouter.core.routers
     * @author billy.qi <a href="mailto:qiyilike@163.com">Contact me.</a>
     * @since 2017-12-06
     */
    private static void registerRouteRoot(IRouteRoot routeRoot) {
        markRegisteredByPlugin();
        if (routeRoot != null) {
            routeRoot.loadInto(Warehouse.groupsIndex);
        }
    }

    /**
     * method for arouter-auto-register plugin to register Interceptors
     * @param interceptorGroup IInterceptorGroup implementation class in the package: com.alibaba.android.arouter.core.routers
     * @author billy.qi <a href="mailto:qiyilike@163.com">Contact me.</a>
     * @since 2017-12-06
     */
    private static void registerInterceptor(IInterceptorGroup interceptorGroup) {
        markRegisteredByPlugin();
        if (interceptorGroup != null) {
            interceptorGroup.loadInto(Warehouse.interceptorsIndex);
        }
    }

    /**
     * method for arouter-auto-register plugin to register Providers
     * @param providerGroup IProviderGroup implementation class in the package: com.alibaba.android.arouter.core.routers
     * @author billy.qi <a href="mailto:qiyilike@163.com">Contact me.</a>
     * @since 2017-12-06
     */
    private static void registerProvider(IProviderGroup providerGroup) {
        markRegisteredByPlugin();
        if (providerGroup != null) {
            providerGroup.loadInto(Warehouse.providersIndex);
        }
    }

    /**
     * mark already registered by arouter-auto-register plugin
     * @author billy.qi <a href="mailto:qiyilike@163.com">Contact me.</a>
     * @since 2017-12-06
     */
    private static void markRegisteredByPlugin() {
        if (!registerByPlugin) {
            registerByPlugin = true;
        }
    }

    public static void init(final Context context, final ThreadPoolExecutor exec) {
        mContext = context;
        executor = exec;

        try {
            loadRouterMap();
            if (registerByPlugin) {
                logger.info(TAG, "Load router map by arouter-auto-register plugin.");
            } else {
                Set<String> routerMap;
                if (ARouter.debuggable() || PackageUtils.isNewVersion(context)) {
                    ARouter.logger.info(TAG, "Run with debug mode or new install, rebuild router map.");
                    // These class was generated by arouter-compiler.
                    routerMap = ClassUtils.getFileNameByPackageName(mContext, ROUTE_ROOT_PAKCAGE);
                    if (!routerMap.isEmpty()) {
                        context.getSharedPreferences(AROUTER_SP_CACHE_KEY, Context.MODE_PRIVATE).edit().putStringSet(AROUTER_SP_KEY_MAP, routerMap).apply();
                    }

                    PackageUtils.updateVersion(context);    // Save new version name when router map update finishes.
                } else {
                    ARouter.logger.info(TAG, "Load router map from cache.");
                    routerMap = new HashSet<>(context.getSharedPreferences(AROUTER_SP_CACHE_KEY, Context.MODE_PRIVATE).getStringSet(AROUTER_SP_KEY_MAP, new HashSet<String>()));
                }

                for (String name : routerMap) {
                    if (name.startsWith(ROUTE_ROOT_PAKCAGE + DOT + SDK_NAME + SEPARATOR + SUFFIX_ROOT)) {
                        ((IRouteRoot) Class.forName(name).newInstance()).loadInto(Warehouse.groupsIndex);
                    } else if (name.startsWith(ROUTE_ROOT_PAKCAGE + DOT + SDK_NAME + SEPARATOR + SUFFIX_PROVIDERS)) {
                        ((IProviderGroup) Class.forName(name).newInstance()).loadInto(Warehouse.providersIndex);
                    }
                }
            }

            if (Warehouse.groupsIndex.size() == 0) {
                logger.error(TAG, "No mapping files were found, check your configuration please!");
            }

        } catch (Exception e) {
            throw new HandlerException(TAG + "ARouter init logistics center exception! [" + e.getMessage() + "]");
        }
    }

    public synchronized static void completion(Postcard postcard) {
        if (null == postcard) {
            throw new NoRouteFoundException(TAG + "No postcard!");
        }


        RouteMeta routeMeta = Warehouse.routes.get(postcard.getPath());
        if (routeMeta == null) {

            Class<? extends IRouteGroup> clazz = Warehouse.groupsIndex.get(postcard.getGroup());

            if (null == clazz) {
                throw new NoRouteFoundException(TAG + "There is no route match the path [" + postcard.getPath() + "], in group [" + postcard.getGroup() + "]");
            } else {
                try {
                    IRouteGroup group = clazz.getConstructor().newInstance();
                    group.loadInto(Warehouse.routes);
                    Warehouse.groupsIndex.remove(postcard.getGroup());
                } catch (Exception e) {
                    throw new HandlerException(TAG + "Fatal exception when loading group meta. [" + e.getMessage() + "]");
                }

            }

            completion(postcard);
        } else {
            postcard.setDestination(routeMeta.getDestination());
            postcard.setType(routeMeta.getType());
            postcard.setPriority(routeMeta.getPriority());
            postcard.setExtra(routeMeta.getExtra());

            switch (routeMeta.getType()) {
                case PROVIDER:  // if the route is provider, should find its instance
                    // Its provider, so it must implement IProvider
                    Class<? extends IProvider> providerMeta = (Class<? extends IProvider>) routeMeta.getDestination();
                    IProvider instance = Warehouse.providers.get(providerMeta);
                    if (null == instance) { // There's no instance of this provider
                        IProvider provider;
                        try {
                            provider = providerMeta.getConstructor().newInstance();
                            provider.init(mContext);
                            Warehouse.providers.put(providerMeta, provider);
                            instance = provider;
                        } catch (Exception e) {
                            throw new HandlerException("Init provider failed! " + e.getMessage());
                        }
                    }
                    postcard.setProvider(instance);
                    postcard.greenChannel();    // Provider should skip all of interceptors
                    break;
                case FRAGMENT:
                    postcard.greenChannel();    // Fragment needn't interceptors
                default:
                    break;
            }
        }
    }
}
