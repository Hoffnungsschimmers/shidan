<#
.SYNOPSIS
    检查已构建的 androidTest APK 是否完整打包当前历史 Room schema。
.DESCRIPTION
    只检查 ZIP 资产及内容一致性，不运行设备测试。先执行 assembleDebugAndroidTest。
#>
[CmdletBinding()]
param(
    [string]$TestApk
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repoRoot = Split-Path -Parent $PSScriptRoot
if (-not $TestApk) {
    $TestApk = Join-Path $repoRoot 'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'
}
$schemaRoot = Join-Path $repoRoot 'app/schemas'
$schemas = @(Get-ChildItem -LiteralPath $schemaRoot -Recurse -File -Filter '*.json')
if ($schemas.Count -eq 0) { throw '没有找到历史 Room schema。' }
if (-not (Test-Path -LiteralPath $TestApk -PathType Leaf)) {
    throw '找不到测试 APK，请先运行 assembleDebugAndroidTest。'
}

Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive = [System.IO.Compression.ZipFile]::OpenRead((Get-Item -LiteralPath $TestApk).FullName)
try {
    foreach ($schema in $schemas) {
        $relative = $schema.FullName.Substring($schemaRoot.Length + 1) -replace '\\', '/'
        $assetPath = 'assets/' + $relative
        $entries = @($archive.Entries | Where-Object { $_.FullName -ceq $assetPath })
        if ($entries.Count -ne 1) {
            throw "测试 APK 必须且只能包含一份 $assetPath，实际为 $($entries.Count) 份。"
        }
        $stream = $entries[0].Open()
        $sha256 = [System.Security.Cryptography.SHA256]::Create()
        try {
            $actual = [Convert]::ToHexString($sha256.ComputeHash($stream))
        } finally {
            $sha256.Dispose()
            $stream.Dispose()
        }
        $expected = (Get-FileHash -LiteralPath $schema.FullName -Algorithm SHA256).Hash
        if ($actual -cne $expected) { throw "测试 APK 中的 $assetPath 与源码 schema 不一致。" }
        Write-Host ("PASS {0}" -f $assetPath)
    }
    Write-Host ("已验证 {0} 份历史 schema 资产及 SHA-256 内容一致性；未执行仪器测试。" -f $schemas.Count)
} finally {
    $archive.Dispose()
}
