package com.example.apt.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import com.example.apt.thread.DefaultPoolExecutor;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dalvik.system.DexFile;

public class ClassUtils {

    private static final String EXTRACTED_NAME_EXT = ".classes";
    private static final String EXTRACTED_SUFFIX = ".zip";

    private static final String PREFS_FILE = "multidex.version";
    private static final String KEY_DEX_NUMBER = "dex.number";

    private static final String SECONDARY_FOLDER_NAME = "code_cache" + File.separator + "secondary-dexes";

    private static final int VM_WITH_MULTIDEX_VERSION_MAJOR = 2;
    private static final int VM_WITH_MULTIDEX_VERSION_MINOR = 1;

    private static SharedPreferences getMultiDexPreferences(Context context) {
        return context.getSharedPreferences(PREFS_FILE, Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB ? Context.MODE_PRIVATE : Context.MODE_PRIVATE | Context.MODE_MULTI_PROCESS);
    }

    public static Set<String> getFileNameByPackageName(Context context, final String packageName) throws InterruptedException, IOException, PackageManager.NameNotFoundException{
        final Set<String> classNames = Collections.synchronizedSet(new HashSet<String>());

        List<String> paths = getSourcePaths(context);
        final CountDownLatch parserCtl = new CountDownLatch(paths.size());

        for (final String path : paths) {
            DefaultPoolExecutor.getInstance().execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        DexFile dexFile = null;
                        if (path.endsWith(EXTRACTED_SUFFIX)) {
                            dexFile = DexFile.loadDex(path, path + ".tmp", 0);
                        } else {
                            dexFile = new DexFile(path);
                        }

                        Enumeration<String> enumeration = dexFile.entries();
                        while (enumeration.hasMoreElements()) {
                            final String name = enumeration.nextElement();
                            if (name.startsWith(packageName)) {
                                classNames.add(name);
                            }
                        }

                    } catch (Throwable e) {
                        e.printStackTrace();
                    } finally {
                        parserCtl.countDown();
                    }
                }
            });
        }

        parserCtl.await();

        return classNames;

    }

    private static List<String> getSourcePaths(Context context) throws PackageManager.NameNotFoundException, IOException {
        ApplicationInfo applicationInfo = context.getPackageManager().getApplicationInfo(context.getPackageName(), 0);
        File sourceApk = new File(applicationInfo.sourceDir);

        List<String> sourcePaths = new ArrayList<>();
        sourcePaths.add(applicationInfo.sourceDir); //add the default apk path

        //the prefix of extracted file, ie: test.classes
        String extractedFilePrefix = sourceApk.getName() + EXTRACTED_NAME_EXT;

//        如果VM已经支持了MultiDex，就不要去Secondary Folder加载 Classesx.zip了，那里已经么有了
//        通过是否存在sp中的multidex.version是不准确的，因为从低版本升级上来的用户，是包含这个sp配置的
        if (!isVMMultidexCapable()) {
            //the total dex numbers
            int totalDexNumber = getMultiDexPreferences(context).getInt(KEY_DEX_NUMBER, 1);
            File dexDir = new File(applicationInfo.dataDir, SECONDARY_FOLDER_NAME);

            for (int secondaryNumber = 2; secondaryNumber <= totalDexNumber; secondaryNumber++) {
                //for each dex file, ie: test.classes2.zip, test.classes3.zip...
                String fileName = extractedFilePrefix + secondaryNumber + EXTRACTED_SUFFIX;
                File extractedFile = new File(dexDir, fileName);
                if (extractedFile.isFile()) {
                    sourcePaths.add(extractedFile.getAbsolutePath());
                    //we ignore the verify zip part
                } else {
                    throw new IOException("Missing extracted secondary dex file '" + extractedFile.getPath() + "'");
                }
            }
        }

//        if (ARouter.debuggable()) { // Search instant run support only debuggable
//            sourcePaths.addAll(tryLoadInstantRunDexFile(applicationInfo));
//        }
        return sourcePaths;
    }

    private static boolean isVMMultidexCapable() {
        boolean isMultidexCapable = false;
        String vmName = null;

        try {
            if (isYunOS()) {    // YunOS需要特殊判断
                vmName = "'YunOS'";
                isMultidexCapable = Integer.valueOf(System.getProperty("ro.build.version.sdk")) >= 21;
            } else {    // 非YunOS原生Android
                vmName = "'Android'";
                String versionString = System.getProperty("java.vm.version");
                if (versionString != null) {
                    Matcher matcher = Pattern.compile("(\\d+)\\.(\\d+)(\\.\\d+)?").matcher(versionString);
                    if (matcher.matches()) {
                        try {
                            int major = Integer.parseInt(matcher.group(1));
                            int minor = Integer.parseInt(matcher.group(2));
                            isMultidexCapable = (major > VM_WITH_MULTIDEX_VERSION_MAJOR)
                                    || ((major == VM_WITH_MULTIDEX_VERSION_MAJOR)
                                    && (minor >= VM_WITH_MULTIDEX_VERSION_MINOR));
                        } catch (NumberFormatException ignore) {
                            // let isMultidexCapable be false
                        }
                    }
                }
            }
        } catch (Exception ignore) {

        }

        Log.i(Consts.TAG, "VM with name " + vmName + (isMultidexCapable ? " has multidex support" : " does not have multidex support"));
        return isMultidexCapable;
    }

    /**
     * 判断系统是否为YunOS系统
     */
    private static boolean isYunOS() {
        try {
            String version = System.getProperty("ro.yunos.version");
            String vmName = System.getProperty("java.vm.name");
            return (vmName != null && vmName.toLowerCase().contains("lemur"))
                    || (version != null && version.trim().length() > 0);
        } catch (Exception ignore) {
            return false;
        }
    }
}
