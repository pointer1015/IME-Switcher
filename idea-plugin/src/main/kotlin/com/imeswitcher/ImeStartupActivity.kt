package com.imeswitcher

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/**
 * ImeStartupActivity.kt
 *
 * 项目打开时执行一次：
 *  1. 解析插件目录，初始化 ImeController 路径
 *  2. 将插件目录注入 ImeService（用于定位内置 ime-switcher.exe）
 */
class ImeStartupActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        val pluginPath = PluginManagerCore
            .getPlugin(PluginId.getId("com.imeswitcher.idea"))
            ?.pluginPath
            ?.toAbsolutePath()
            ?.toString()
            ?: return

        ImeService.getInstance().setPluginBasePath(pluginPath)
    }
}
