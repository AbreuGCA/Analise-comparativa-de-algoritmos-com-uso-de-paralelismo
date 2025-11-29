<#
benchmark.ps1

PowerShell helper to:
- compile Java sources (with JOCL if available)
- run benchmarks (serial,cpu,gpu via mode 'all') for all files in `samples/`
- consolidate CSV outputs into `results/results.csv`
- run the Python plotting script to generate PNGs

Usage: run this script from anywhere. It detects its location and runs relative commands.
#>

Set-StrictMode -Version Latest

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$src = Join-Path $root 'src'
$libsJar = Join-Path $root 'libs\jocl-2.0.4.jar'
$samplesDir = Join-Path $root 'samples'
$resultsDir = Join-Path $root 'results'
$pythonPlot = Join-Path $root 'python-frontend\plot_results.py'

$runs = 3
$target = 'the'

Write-Host "Project root: $root"
Write-Host "Source dir: $src"

# --- Requirements check (simple) ---
# This script now only checks for required tools and warns the user if something is missing.
# It will NOT attempt to install or download dependencies automatically.

$skipGPU = $false

# Check Java (required)
if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    Write-Error "Java (java/javac) not found in PATH. Please install JDK 8+ and ensure 'java' and 'javac' are available. Aborting."
    exit 1
}

# Check Python (optional, used for plotting)
$pythonCmd = $null
if (Get-Command python -ErrorAction SilentlyContinue) { $pythonCmd = 'python' }
elseif (Get-Command python3 -ErrorAction SilentlyContinue) { $pythonCmd = 'python3' }

if (-not $pythonCmd) {
    Write-Warning "Python not found in PATH. Plotting step will be skipped unless Python is installed."
}
else {
    # Check pandas and matplotlib availability (do not install automatically)
    & $pythonCmd -c "import sys
try:
    import pandas, matplotlib
except Exception:
    sys.exit(1)
else:
    sys.exit(0)"
    if ($LASTEXITCODE -ne 0) {
        Write-Warning "Python packages 'pandas' and/or 'matplotlib' are missing. Install them to enable plotting:"
        Write-Host "    $pythonCmd -m pip install pandas matplotlib"
    }
}

# Check JOCL JAR presence (do not download automatically)
if (-not (Test-Path $libsJar)) {
    Write-Warning "JOCL JAR not found at: $libsJar. GPU compilation/execution will be skipped unless you place 'jocl-2.0.4.jar' there."
    Write-Host "You can download it from: https://repo1.maven.org/maven2/org/jocl/jocl/2.0.4/jocl-2.0.4.jar"
    $skipGPU = $true
}

if (-not (Test-Path $resultsDir)) {
    New-Item -ItemType Directory -Path $resultsDir | Out-Null
}

$masterCsv = Join-Path $resultsDir 'results.csv'
if (Test-Path $masterCsv) { Remove-Item $masterCsv }

Push-Location $src
try {
    # === Environment checks and requirements ===
    $skipGPU = $false

    # Check Java
    $javaOk = $false
    try { java -version *> $null; $javaOk = $true } catch { $javaOk = $false }
    if (-not $javaOk) {
        Write-Warning "Java not found in PATH. Please install Java (JDK) and ensure 'java' and 'javac' are available. Aborting."
        Pop-Location; exit 1
    }

    # Check Python
    $pythonCmd = $null
    try { python --version *> $null; $pythonCmd = 'python' } catch { }
    if (-not $pythonCmd) {
        try { py --version *> $null; $pythonCmd = 'py' } catch { }
    }
    if (-not $pythonCmd) {
        Write-Warning "Python not found in PATH. Plotting step will be skipped unless you install Python."
    }

    # Check for virtualenv in repo (.venv)
    $venvPath = Join-Path $root '.venv'
    $pipExe = $null
    if (Test-Path $venvPath) {
        $pipExe = Join-Path $venvPath 'Scripts\pip.exe'
        if (-not (Test-Path $pipExe)) { $pipExe = $null }
        Write-Host "Found virtualenv at $venvPath"
    }

    if (-not $pipExe -and $pythonCmd) {
        $installReq = Read-Host "No virtualenv found. Create .venv and install Python requirements (pandas, matplotlib)? [Y/n]"
        if ($installReq -eq '' -or $installReq.ToLower().StartsWith('y')) {
            & $pythonCmd -m venv $venvPath
            $pipExe = Join-Path $venvPath 'Scripts\pip.exe'
            if (Test-Path $pipExe) {
                Write-Host "Upgrading pip and installing packages in virtualenv..."
                & $pipExe install --upgrade pip
                & $pipExe install pandas matplotlib
            }
            else {
                Write-Warning "Failed to create or locate pip in virtualenv. Will attempt global pip installs."
                $pipExe = $null
            }
        }
        else {
            $globalInstall = Read-Host "Install Python requirements globally? (requires pip) [y/N]"
            if ($globalInstall.ToLower().StartsWith('y')) {
                if ($pythonCmd) { & $pythonCmd -m pip install pandas matplotlib } else { pip install pandas matplotlib }
            }
            else {
                Write-Warning "Python plotting dependencies will be skipped."
            }
        }
    }

    # Check JOCL jar
    if (-not (Test-Path $libsJar)) {
        $downloadChoice = Read-Host "JOCL JAR not found at $libsJar. Download jocl-2.0.4.jar into libs/? [Y=download/N=skip GPU]"
        if ($downloadChoice -eq '' -or $downloadChoice.ToLower().StartsWith('y')) {
            if (-not (Test-Path (Split-Path $libsJar -Parent))) { New-Item -ItemType Directory -Path (Split-Path $libsJar -Parent) | Out-Null }
            $url = 'https://repo1.maven.org/maven2/org/jocl/jocl/2.0.4/jocl-2.0.4.jar'
            try {
                Write-Host "Downloading JOCL from $url ..."
                Invoke-WebRequest -Uri $url -OutFile $libsJar -UseBasicParsing
                Write-Host "Downloaded JOCL to $libsJar"
            }
            catch {
                Write-Warning "Failed to download JOCL jar: $_. Exception. Will skip GPU runs."
                $skipGPU = $true
            }
        }
        else {
            Write-Host "User chose to skip GPU; continuing without JOCL"
            $skipGPU = $true
        }
    }

    # Compile
    if (-not $skipGPU -and (Test-Path $libsJar)) {
        Write-Host "Found JOCL JAR: compiling with JOCL in classpath..."
        javac -cp ".;$libsJar" WordCountMain.java GPUWordCounter.java
    }
    else {
        Write-Host "Compiling without GPU support (only WordCountMain)."
        javac WordCountMain.java
        $skipGPU = $true
    }

    # Iterate samples and run on 25%/50%/75%/100% slices
    $sampleFiles = Get-ChildItem -Path $samplesDir -Filter *.txt -File
    $percentages = @(0.25, 0.5, 0.75, 1.0)
    foreach ($f in $sampleFiles) {
        Write-Host "Processing sample: $($f.Name)"
        $content = Get-Content $f.FullName -Raw -Encoding UTF8
        $len = $content.Length

        foreach ($p in $percentages) {
            $pct = [int]([math]::Round($p * 100))
            $partCount = [int]([math]::Ceiling($len * $p))
            if ($partCount -lt 1) { $partCount = 1 }
            $subText = $content.Substring(0, [math]::Min($partCount, $len))

            $partName = "{0}_{1}pct.txt" -f $f.BaseName, $pct
            $tempInput = Join-Path $resultsDir $partName
            Set-Content -Path $tempInput -Value $subText -Encoding UTF8

            $tempCsv = Join-Path $resultsDir ("tmp_{0}_{1}.csv" -f $f.BaseName, $pct)
            Write-Host " Running on $pct% -> temp input: $tempInput, csv: $tempCsv"

            if (-not $skipGPU -and (Test-Path $libsJar)) { $cp = ".;$libsJar" } else { $cp = "." }

            & java -cp $cp WordCountMain all $tempInput $target $runs $tempCsv

            if (-not (Test-Path $tempCsv)) {
                Write-Warning "Temporary CSV not created for $($f.Name) $pct% . Skipping."
                Remove-Item $tempInput -ErrorAction SilentlyContinue
                continue
            }

            # Import CSV and replace 'file' column with basename + pct (e.g. MobyDick_25pct)
            $rows = Import-Csv $tempCsv
            foreach ($row in $rows) { $row.file = "{0}_{1}pct" -f $f.BaseName, $pct }

            if (-not (Test-Path $masterCsv)) {
                $rows | Export-Csv -Path $masterCsv -NoTypeInformation -Encoding UTF8
            }
            else {
                $rows | Export-Csv -Path $masterCsv -NoTypeInformation -Append -Encoding UTF8
            }

            Remove-Item $tempCsv -ErrorAction SilentlyContinue
            Remove-Item $tempInput -ErrorAction SilentlyContinue
        }
    }

    Write-Host "All benchmarks finished. Master CSV: $masterCsv"

    # Run Python plotting (if available) and save plots into results directory
    if (Test-Path $pythonPlot -PathType Leaf -ErrorAction SilentlyContinue) {
        # choose python executable: prefer virtualenv python if present
        $plotter = $null
        if ($pipExe) {
            $plotter = Join-Path (Split-Path $pipExe -Parent) 'python.exe'
            if (-not (Test-Path $plotter)) { $plotter = $null }
        }
        if (-not $plotter) {
            if (Get-Command python -ErrorAction SilentlyContinue) { $plotter = 'python' }
            elseif (Get-Command py -ErrorAction SilentlyContinue) { $plotter = 'py' }
        }

        if ($plotter) {
            Write-Host "Generating plots using Python script (output will be saved into results)..."
            Push-Location $resultsDir
            try {
                & $plotter $pythonPlot $masterCsv
            }
            finally { Pop-Location }
            Write-Host "Plots generated in: $resultsDir (results_time.png, results_counts.png)."
        }
        else {
            Write-Warning "Python not available; skipping plotting step."
        }
    }
    else {
        Write-Warning "Plot script not found at $pythonPlot. Skipping plotting step."
    }

}
finally {
    Pop-Location
}

Write-Host "Benchmarking completed. Master CSV: $masterCsv"
