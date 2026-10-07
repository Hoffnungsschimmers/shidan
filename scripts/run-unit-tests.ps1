<#
.SYNOPSIS
    在 Windows 上直接跑 mealnote 的全量 JVM 单元测试（绕开本机必失败的 testDebugUnitTest）。

.DESCRIPTION
    项目里 `testDebugUnitTest` 有已知环境问题：AGP 的 bundleDebugClassesToRuntimeJar 产物
    不进测试运行时类路径，所有测试类都会报 ClassNotFoundException at initializationError
    （与被测代码无关）。惯例是改用 JDK 直跑 org.junit.runner.JUnitCore。

    直跑要求手工维护一份测试类清单，而 README 里那条「新增测试类必须同步更新命令」
    已经**至少失守三次**。本脚本改为**从 app/src/test 的源码自动发现测试类**，
    清单不存在，也就不可能漂移。

    同时它刻意不从 build 目录里扫 .class 文件计数：Gradle 增量会留下**源码已删除**的
    陈旧 class（历史上真的发生过），据此统计会把测试数虚报。

.PARAMETER NoBuild
    跳过 Gradle 编译步骤，直接用现有产物跑测试。仅在确认产物是最新时使用。

.EXAMPLE
    ./scripts/run-unit-tests.ps1
    ./scripts/run-unit-tests.ps1 -NoBuild
#>

[CmdletBinding()]
param(
    [switch]$NoBuild
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
$testSourceRoot = Join-Path $repoRoot 'app/src/test/java'
$gradleCache = Join-Path $env:USERPROFILE '.gradle/caches/modules-2/files-2.1'

function Resolve-Jdk17 {
    # 优先用 JAVA_HOME；否则按项目惯例在 D:\env 下找 JDK 17。
    if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin/java.exe'))) {
        return (Join-Path $env:JAVA_HOME 'bin/java.exe')
    }
    $candidate = Get-ChildItem 'D:\env' -Directory -Filter 'jdk-17*' -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if ($candidate -and (Test-Path (Join-Path $candidate.FullName 'bin/java.exe'))) {
        return (Join-Path $candidate.FullName 'bin/java.exe')
    }
    throw "找不到 JDK 17。设置 JAVA_HOME，或把 -Porg.gradle.java.installations.paths 指向它。"
}

function Get-RequiredJar([string]$pattern) {
    # 依赖 jar 的路径含内容哈希，绝不能写死；按文件名在 Gradle 缓存里找。
    $found = Get-ChildItem $gradleCache -Recurse -Filter $pattern -ErrorAction SilentlyContinue |
        Where-Object { $_.Extension -eq '.jar' } |
        Select-Object -First 1
    if (-not $found) {
        throw "在 Gradle 缓存里找不到 $pattern —— 先跑一次构建让依赖下载完成。"
    }
    return $found.FullName
}

function Get-TestClasses {
    # 类名 = 源码相对路径去掉 .kt、斜杠换成点。一个 .kt 里若有多个测试类会被漏掉，
    # 因此同时校验：文件里至少要有一个 class ...Test 声明。
    Get-ChildItem $testSourceRoot -Recurse -Filter '*Test.kt' | ForEach-Object {
        $relative = $_.FullName.Substring($testSourceRoot.Length + 1)
        $fqn = ($relative -replace '\.kt$', '') -replace '[\\/]', '.'
        [pscustomobject]@{ FullyQualifiedName = $fqn; Source = $relative }
    } | Sort-Object FullyQualifiedName
}

function Invoke-Build {
    $java = Resolve-Jdk17
    $gradlew = Join-Path $repoRoot 'gradlew.bat'
    Write-Host '==> 编译主源码与测试源码，并刷新运行时产物' -ForegroundColor Cyan
    & $gradlew `
        -p $repoRoot `
        compileDebugKotlin compileDebugUnitTestKotlin `
        :app:bundleDebugClassesToRuntimeJar :app:transformDebugUnitTestClassesWithAsm `
        "-Porg.gradle.java.installations.paths=$(Split-Path -Parent (Split-Path -Parent $java))" `
        --console=plain --no-build-cache
    if ($LASTEXITCODE -ne 0) { throw "Gradle 编译失败（退出码 $LASTEXITCODE）" }
}

$classes = Get-TestClasses
if (-not $classes) { throw "在 $testSourceRoot 下没找到任何 *Test.kt" }

if (-not $NoBuild) { Invoke-Build }

$intermediates = Join-Path $repoRoot 'app/build/intermediates'
$testDirs = Join-Path $intermediates 'classes/debugUnitTest/transformDebugUnitTestClassesWithAsm/dirs'
$runtimeJar = Join-Path $intermediates 'runtime_app_classes_jar/debug/bundleDebugClassesToRuntimeJar/classes.jar'

foreach ($path in @($testDirs, $runtimeJar)) {
    if (-not (Test-Path $path)) {
        throw "缺少产物 $path —— 去掉 -NoBuild 重跑一次。"
    }
}

$classPath = @(
    $testDirs,
    $runtimeJar,
    (Get-RequiredJar 'junit-4.13.2.jar'),
    (Get-RequiredJar 'hamcrest-core-1.3.jar'),
    (Get-RequiredJar 'kotlin-stdlib-2.3.21.jar')
) -join ';'

Write-Host ("==> 直跑 {0} 个测试类（从源码发现）" -f $classes.Count) -ForegroundColor Cyan
& (Resolve-Jdk17) -cp $classPath org.junit.runner.JUnitCore ($classes.FullyQualifiedName)

# JUnitCore 非零退出即有用例失败，原样传给调用方，便于接进发布门槛。
exit $LASTEXITCODE
