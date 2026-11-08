import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.1.0"
    id("org.jetbrains.intellij.platform") version "2.2.1"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    testImplementation(kotlin("test"))
    testImplementation("junit:junit:4.13.2")

    intellijPlatform {
        create(
            providers.gradleProperty("platformType").get(),
            providers.gradleProperty("platformVersion").get(),
        )

        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
    }
}

kotlin {
    jvmToolchain(21)
}

tasks {
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
        compilerOptions.jvmTarget.set(JvmTarget.JVM_21)
        compilerOptions.freeCompilerArgs.add("-Xjvm-default=all")
    }

    test {
        useJUnitPlatform()
    }

    wrapper {
        gradleVersion = "8.10.2"
    }
}

intellijPlatform {
    pluginConfiguration {
        name = "FlowIME"
        version = providers.gradleProperty("pluginVersion").get()

        ideaVersion {
            sinceBuild = providers.gradleProperty("platformSinceBuild").get()
            untilBuild = providers.gradleProperty("platformUntilBuild").get()
        }

        vendor {
            name = "JustCodeIt"
        }

        description = """
            FlowIME 是一款面向 JetBrains IDE 的智能输入法切换插件。
            它会根据代码、注释、字符串等编辑场景自动切换中英文输入法，
            支持所有常见编程语言和 Git commit 场景，提供诊断窗口、结构化报告、
            im-select 与自定义命令适配器。
        """.trimIndent()

        changeNotes = """
            <p>0.2.0</p>
            <ul>
                <li>扩展语言支持：适配所有 JetBrains IDE 和常见编程语言</li>
                <li>Git commit 消息自动切换到中文</li>
                <li>新增通用规则配置项：支持非 Java/Kotlin 语言的注释与字符串场景</li>
            </ul>
            <p>0.1.0</p>
            <ul>
                <li>完成首版上下文识别、策略状态机、设置页与诊断窗口</li>
                <li>支持 im-select 与自定义命令适配器</li>
                <li>原生 macOS / Windows 适配器调整为实验性开关，降低闪退风险</li>
            </ul>
        """.trimIndent()
    }
}
