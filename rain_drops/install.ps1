param([switch]$Uninstall)

# Feature switches: create rain_drops\no_<feature>.txt to turn a feature off, then run this script again.
#   no_windshield.txt  - raindrop shader on the windshield
#   no_wet_terrain.txt - wet ground and puddles on terrain
#   no_wet_roads.txt   - wet roads and wheel ruts

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
        }
        Replaced = @{
            "Terrain.mat"            = "World\Terrain.mat"
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
    }
)

# Shared by terrain and roads.
$shared = @{
    New      = @{ "PuddleNoise.dds" = "World\PuddleNoise.dds" }
    Replaced = @{ "varlist.scr"     = "varlist.scr" }
}

function Enabled($name) {
    return -not $Uninstall -and -not (Test-Path (Join-Path $here "no_$name.txt"))
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

if ((Enabled "wet_terrain") -or (Enabled "wet_roads")) {
    Add-Feature $shared
} else {
    Remove-Feature $shared
}
