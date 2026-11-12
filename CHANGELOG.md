# Changelog

## Unreleased

- 建立 FastPrism 最小闭环：纯 JVM 的 core / engine、MockWebServer 夹具，以及只写入应用内部目录的 sample。
- FastPrism 作为单个 SDK 放在 `fast-prism/`，core、engine、testing、sample 是它的子模块。
- 不提供暂停或停止，不申请存储、通知和前台服务权限。

## 0.1.0 - 2026-11-09

- 初始化 Monorepo：`build-logic` convention、可发布模块 `:example`、不发布的 `:sample`。
- 发布走 `com.vanniktech.maven.publish` 0.28.0，模块使用独立版本。
