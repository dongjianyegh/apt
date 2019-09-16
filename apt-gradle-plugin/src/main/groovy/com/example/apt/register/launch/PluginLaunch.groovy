package com.example.apt.register.launch

import com.android.build.gradle.AppExtension
import com.android.build.gradle.AppPlugin
import com.example.apt.register.core.RegisterTransform
import com.example.apt.register.utils.Logger
import com.example.apt.register.utils.ScanSetting;
import org.gradle.api.Plugin;
import org.gradle.api.Project;

public class PluginLaunch implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        def isApp = project.plugins.hasPlugin(AppPlugin)

        if (isApp) {
            Logger.make(project)

            Logger.i('Project enable arouter-register plugin')

            def android = project.extensions.getByType(AppExtension)
            def transformImpl = new RegisterTransform(project)

            //init arouter-auto-register settings
            ArrayList<ScanSetting> list = new ArrayList<>(3)
            list.add(new ScanSetting('IRouteRoot'))
            list.add(new ScanSetting('IInterceptorGroup'))
            list.add(new ScanSetting('IProviderGroup'))
            RegisterTransform.registerList = list
            //register this plugin
            android.registerTransform(transformImpl)

        }
    }
}
