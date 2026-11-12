plugins {
    alias(libs.plugins.sdk.android.library) apply false
    alias(libs.plugins.sdk.android.library.published) apply false
    alias(libs.plugins.sdk.android.application) apply false
    alias(libs.plugins.sdk.android.lint) apply false
    alias(libs.plugins.sdk.bom) apply false
    alias(libs.plugins.sdk.jvm.library) apply false
}

// 发布配置在 build-logic convention 中。这里只聚合验证任务，不注入 maven-publish。
tasks.register("ciCheck") {
    group = "verification"
    description = "编译、lint、单元测试全部模块，不发布。"
}

gradle.projectsEvaluated {
    val checks = rootProject.subprojects.mapNotNull { project ->
        if ("check" in project.tasks.names) project.tasks.named("check") else null
    }
    tasks.named("ciCheck").configure {
        dependsOn(checks)
    }
}
