# Source text utilities for the port.
#
# Two rules this file exists to enforce:
#
#   1. No byte order mark. PowerShell 5.1's `Set-Content -Encoding UTF8` always writes one,
#      and javac then fails with "illegal character '\ufeff'". Mixin's config parser also
#      rejects a BOM outright.
#   2. Line endings survive. The upstream tree is LF; rewriting a file through a
#      line-oriented cmdlet is how an unrelated 3000-line diff gets created.

function Read-SourceText {
    param([Parameter(Mandatory)][string]$Path)
    $bytes = [System.IO.File]::ReadAllBytes($Path)
    $offset = 0
    if ($bytes.Length -ge 3 -and $bytes[0] -eq 0xEF -and $bytes[1] -eq 0xBB -and $bytes[2] -eq 0xBF) {
        $offset = 3
    }
    return [System.Text.Encoding]::UTF8.GetString($bytes, $offset, $bytes.Length - $offset)
}

function Write-SourceText {
    param(
        [Parameter(Mandatory)][string]$Path,
        [Parameter(Mandatory)][AllowEmptyString()][string]$Text
    )
    # Preserve the dominant newline of the file on disk.
    $crlf = $Text.Contains("`r`n")
    $body = if ($crlf) { $Text -replace "`r`n", "`n" } else { $Text }
    $bytes = [System.Text.Encoding]::UTF8.GetBytes($body)
    [System.IO.File]::WriteAllBytes($Path, $bytes)
}

function Update-SourceText {
    param(
        [Parameter(Mandatory)][string]$Path,
        [Parameter(Mandatory)][scriptblock]$Transform
    )
    $text = Read-SourceText -Path $Path
    $new = & $Transform $text
    if ($null -eq $new) { return $false }
    if ($new -ceq $text) { return $false }
    Write-SourceText -Path $Path -Text $new
    return $true
}

# Rewrites Java sources in bulk with a regex, reporting every file it changed.
function Update-JavaSources {
    param(
        [Parameter(Mandatory)][string[]]$Root,
        [Parameter(Mandatory)][string]$Pattern,
        [Parameter(Mandatory)][string]$Replacement,
        [string]$Include = '*.java',
        [switch]$WhatIfOnly
    )
    $changed = New-Object System.Collections.Generic.List[string]
    foreach ($r in $Root) {
        Get-ChildItem $r -Recurse -File -Filter $Include | ForEach-Object {
            $file = $_
            $text = Read-SourceText -Path $file.FullName
            $new = [regex]::Replace($text, $Pattern, $Replacement)
            if ($new -cne $text) {
                $changed.Add($file.FullName)
                if (-not $WhatIfOnly) { Write-SourceText -Path $file.FullName -Text $new }
            }
        }
    }
    return $changed
}

# Reports the files under $Root that contain at least one BOM.
function Find-BomFiles {
    param([Parameter(Mandatory)][string[]]$Root)
    $found = New-Object System.Collections.Generic.List[string]
    foreach ($r in $Root) {
        Get-ChildItem $r -Recurse -File -ErrorAction SilentlyContinue | ForEach-Object {
            $fs = [System.IO.File]::OpenRead($_.FullName)
            try {
                if ($fs.Length -ge 3) {
                    $b = New-Object byte[] 3
                    $null = $fs.Read($b, 0, 3)
                    if ($b[0] -eq 0xEF -and $b[1] -eq 0xBB -and $b[2] -eq 0xBF) { $found.Add($_.FullName) }
                }
            } finally { $fs.Dispose() }
        }
    }
    return $found
}
