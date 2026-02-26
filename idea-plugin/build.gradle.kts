import org.jetbrains.intellij.platform.gradle.TestFrameworkType

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.25"
    id("org.jetbrains.intellij.platform") version "2.3.0"
}

group   = "com.imeswitcher"
version = "1.0.1"

kotlin {
    jvmToolchain(17)
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        // 构建目标：IntelliJ IDEA Community 2024.1+
        intellijIdeaCommunity("2024.1")

        // 可选：IdeaVim 集成（用户未安装时插件仍可正常工作）
        // plugin("IdeaVIM:2.14.1")

        // 测试框架
        testFramework(TestFrameworkType.Platform)
    }

    testImplementation("junit:junit:4.13.2")
}

intellijPlatform {
    pluginConfiguration {
        id          = "com.imeswitcher.idea"
        name        = "IME Switcher"
        version     = project.version.toString()
        description = """
            <p>根据光标前后字符自动切换 Windows 输入法（中文 ↔ 英文）。</p>
            <p>依赖项目内置的 <code>ime-switcher.exe</code>（Windows 专属）。</p>
        """.trimIndent()

        ideaVersion {
            sinceBuild = "241"
            untilBuild = provider { null }   // 不限制上限
        }
    }

    signing {
        certificateChain = providers.environmentVariable("CERTIFICATE_CHAIN")
        privateKey        = providers.environmentVariable("PRIVATE_KEY")
        password          = providers.environmentVariable("PRIVATE_KEY_PASSWORD")
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }
}

tasks {
    // 将 ime-switcher.exe / ime-switcher.ps1 打包进插件 bin/ 目录
    prepareSandbox {
        from("${rootProject.projectDir}/../vscode-extension/bin") {
            include("ime-switcher.exe", "ime-switcher.ps1")
            into("${intellijPlatform.projectName.get()}/bin")
        }
    }
}
