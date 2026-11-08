param([switch]$Uninstall)

# Feature switches: mods.ini in the game folder, section [install], 1 = on, 0 = off.
#   windshield      - raindrop shader on the windshield
#   wet_terrain     - wet ground, puddles, shore fade and water shader
#   wet_roads       - wet roads and wheel ruts
#   wet_cars        - wet car paint and water beads
#   grass_sort      - draw grass after trees and props (fixes trees/tents over grass)
#   weather_cubemap - car reflections use current weather sky (make_weather_cubemap.py)
# Change a switch, then run this script again. -Uninstall turns everything off.
# Puddle maps (terrain dips + smooth shore height) come from make_puddle_maps.py -> puddle_maps\.

$ErrorActionPreference = "Stop"
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$data = Join-Path (Split-Path -Parent $here) "Data"

$features = @(
    @{
        Name     = "windshield"
        New      = @{
            "RainDrop.vsh"           = "Shaders\RainDrop.vsh"
            "RainDrop.psh"           = "Shaders\RainDrop.psh"
            "OnFrontGlass_RainN.dds" = "FX\OnFrontGlass_RainN.dds"
        }
        Replaced = @{
            "OnFrontGlass_Rain.mat"  = "FX\FXDEF\glass\OnFrontGlass_Rain.mat"
        }
    },
    @{
        Name     = "wet_terrain"
        New      = @{
            "TerrainWet.vsh"         = "Shaders\TerrainWet.vsh"
            "TerrainWet20.psh"       = "Shaders\TerrainWet20.psh"
            "water\WaterShore.vsh"   = "Shaders\WaterShore.vsh"
            "water\WaterShore.psh"   = "Shaders\WaterShore.psh"
            "water\billboards_refl.mat" = "nature\_blended.mat"
            "water\__BBRefl.mtt" = "Shaders\__BBRefl.mtt"
            "water\RainRipples.dds" = "World\RainRipples.dds"
        }
        Replaced = @{
            "Terrain.mat"            = "World\Terrain.mat"
            "water\WaterSimple.mat"  = "World\WaterSimple.mat"
        }
    },
    @{
        Name     = "wet_roads"
        New      = @{
            "RoadWet.vsh"            = "Shaders\RoadWet.vsh"
            "RoadWet20.psh"          = "Shaders\RoadWet20.psh"
        }
        Replaced = @{
            "_Road.mtt"              = "Shaders\_Road.mtt"
        }
    },
    @{
        Name     = "wet_cars"
        New      = @{
            "CarRainBeads.dds"                  = "cars\CarRainBeads.dds"
        }
        Replaced = @{
            "cars\CarBR_CmCdDm_Bump_ps20.vsh"   = "Shaders\carshaders\CarBR_CmCdDm_Bump_ps20.vsh"
            "cars\CarBR_CmCdDm_Bump_ps20.psh"   = "Shaders\carshaders\CarBR_CmCdDm_Bump_ps20.psh"
            "cars\CarBR_CmCdDm_ps20.vsh"        = "Shaders\carshaders\CarBR_CmCdDm_ps20.vsh"
            "cars\CarBR_CmCdDm_ps20.psh"        = "Shaders\carshaders\CarBR_CmCdDm_ps20.psh"
            "cars\_CAR_BodyR.mtt"               = "Shaders\_CAR_BodyR.mtt"
            "cars\_CAR_BodyR_1.mtt"             = "Shaders\_CAR_BodyR_1.mtt"
            "cars\_CAR_BodyR_2.mtt"             = "Shaders\_CAR_BodyR_2.mtt"
            "cars\_CAR_BodyR_static.mtt"        = "Shaders\_CAR_BodyR_static.mtt"
        }
    },
    @{
        Name     = "grass_sort"
        New      = @{}
        Replaced = @{
            "fixes\_Grass.mtt"                  = "Shaders\_Grass.mtt"
            "fixes\_GrassLit.mtt"               = "Shaders\_GrassLit.mtt"
        }
    }
)

# Car reflections follow the weather: per-state dyncubemap colors in each level climate (.clm).
$clmList = Join-Path $here "weather_cubemap\list.txt"
$clm = @{ Name = "weather_cubemap"; New = @{}; Replaced = @{} }
if (Test-Path $clmList) {
    foreach ($line in Get-Content $clmList) {
        if (-not $line.Trim()) { continue }
        $lvl, $file = $line.Split("|")
        $clm.Replaced["weather_cubemap\$lvl\$file"] = "mapscustom\$lvl\$file"
    }
}
$features += $clm

# Shared by terrain and roads.
$shared = @{
    New      = @{ "puddle_maps\PuddleMap_P.dds" = "World\PuddleMap_P.dds" }
    Replaced = @{}
}

# Material vars (wetness, puddles), needed by terrain, roads and cars.
$vars = @{
    New      = @{}
    Replaced = @{ "varlist.scr" = "varlist.scr" }
}

$ini = @{}
$iniPath = Join-Path (Split-Path -Parent $here) "mods.ini"
if (Test-Path $iniPath) {
    $section = ""
    foreach ($line in Get-Content $iniPath) {
        $t = ($line -split "[;#]", 2)[0].Trim()
        if ($t -match '^\[(.+)\]$') { $section = $Matches[1].Trim().ToLower(); continue }
        if ($t -match '^([^=]+)=(.*)$') { $ini["$section." + $Matches[1].Trim().ToLower()] = $Matches[2].Trim() }
    }
}

function Enabled($name) {
    if ($Uninstall) { return $false }
    $v = $ini["install.$($name.ToLower())"]
    return -not ($v -and $v -match '^(0|false|off|no)$')
}

function Remove-Feature($f) {
    foreach ($dst in $f.Replaced.Values) {
        $p = Join-Path $data $dst
        if (Test-Path "$p.orig") {
            Copy-Item "$p.orig" $p -Force
            Remove-Item "$p.orig"
            Write-Host "restored $p"
        }
    }
    foreach ($dst in $f.New.Values) {
        $p = Join-Path $data $dst
        if (Test-Path $p) { Remove-Item $p; Write-Host "removed $p" }
    }
}

function Add-Feature($f) {
    foreach ($src in $f.Replaced.Keys) {
        $dst = Join-Path $data $f.Replaced[$src]
        if (-not (Test-Path "$dst.orig")) {
            Copy-Item $dst "$dst.orig"
            Write-Host "backup $dst.orig"
        }
        Copy-Item (Join-Path $here $src) $dst -Force
        Write-Host "copied $dst"
    }
    foreach ($src in $f.New.Keys) {
        $dst = Join-Path $data $f.New[$src]
        Copy-Item (Join-Path $here $src) $dst -Force
        Write-Host "copied $dst"
    }
}

foreach ($f in $features) {
    if (Enabled $f.Name) {
        Write-Host "[on]  $($f.Name)"
        Add-Feature $f
    } else {
        Write-Host "[off] $($f.Name)"
        Remove-Feature $f
    }
}

$levels = @()
$manifest = Join-Path $here "puddle_maps\maps.txt"
if (Test-Path $manifest) {
    foreach ($line in Get-Content $manifest) {
        if (-not $line.Trim()) { continue }
        $parts = $line.Split("|")
        $lvl, $scr, $dds = $parts[0..2]
        $dir = Join-Path $data "mapscustom\$lvl"
        $shore = if ($parts.Count -ge 5) { @($parts[3], $parts[4]) } else { $null }
        $levels += @{ Src = Join-Path $here "puddle_maps\$dds"; Dds = Join-Path $dir "World\$dds"; Scr = Join-Path $dir $scr; Name = $dds; Shore = $shore }
    }
}

function Add-LevelMaps {
    foreach ($l in $levels) {
        Copy-Item $l.Src $l.Dds -Force
        Write-Host "copied $($l.Dds)"
        if (-not (Test-Path "$($l.Scr).orig")) {
            Copy-Item $l.Scr "$($l.Scr).orig"
            Write-Host "backup $($l.Scr).orig"
        }
        $text = [IO.File]::ReadAllText("$($l.Scr).orig")
        $nl = if ($text.Contains("`r`n")) { "`r`n" } else { "`n" }
        $text = $text.TrimEnd() + $nl + "VarString( `"puddletexture`", `"$($l.Name)`" )" + $nl
        if ($l.Shore) {
            $text += "VarFloat( `"shorelevel`",  $(([double]$l.Shore[0]).ToString("F6", [Globalization.CultureInfo]::InvariantCulture)) )" + $nl + "VarFloat( `"shorerange`",  $(([double]$l.Shore[1]).ToString("F6", [Globalization.CultureInfo]::InvariantCulture)) )" + $nl
        }
        [IO.File]::WriteAllText($l.Scr, $text)
        Write-Host "patched $($l.Scr)"
    }
}

function Remove-LevelMaps {
    foreach ($l in $levels) {
        if (Test-Path "$($l.Scr).orig") {
            Copy-Item "$($l.Scr).orig" $l.Scr -Force
            Remove-Item "$($l.Scr).orig"
            Write-Host "restored $($l.Scr)"
        }
        if (Test-Path $l.Dds) { Remove-Item $l.Dds; Write-Host "removed $($l.Dds)" }
    }
}

$stale = Join-Path $data "World\PuddleNoise.dds"
if (Test-Path $stale) { Remove-Item $stale; Write-Host "removed $stale" }

if ((Enabled "wet_terrain") -or (Enabled "wet_roads")) {
    if (-not (Test-Path $manifest)) { Write-Host "puddle_maps\maps.txt missing, run make_puddle_maps.py first"; exit 1 }
    Add-Feature $shared
    Add-LevelMaps
} else {
    Remove-Feature $shared
    Remove-LevelMaps
}

if ((Enabled "wet_terrain") -or (Enabled "wet_roads") -or (Enabled "wet_cars")) {
    Add-Feature $vars
} else {
    Remove-Feature $vars
}
