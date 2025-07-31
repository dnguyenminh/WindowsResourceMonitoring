# monitor_processes.ps1
$OutputFile = "C:\Temp\process_alerts.txt"
$CPUThreshold = 10
$MemoryThreshold = 1048576 # 1 MB in bytes
$Query = @"
SELECT * FROM __InstanceModificationEvent WITHIN 1
WHERE TargetInstance ISA 'Win32_PerfFormattedData_PerfProc_Process'
AND (TargetInstance.PercentProcessorTime > $CPUThreshold OR TargetInstance.WorkingSet > $MemoryThreshold)
"@

Write-Output "Starting process monitor script..."
Write-Output "Query: $Query"

# Ensure output directory exists
$OutputDir = Split-Path $OutputFile -Parent
if (-not (Test-Path $OutputDir)) {
    New-Item -ItemType Directory -Path $OutputDir | Out-Null
}

Write-Output "Registering WMI event..."

# Register WMI event
Register-WmiEvent -Query $Query -SourceIdentifier "ProcessMonitor" -Action {
    try {
        $Event = $EventArgs.NewEvent.TargetInstance
        $ProcessName = $Event.Name
        $PID = $Event.IDProcess
        $CPUUsage = $Event.PercentProcessorTime
        $MemoryUsage = $Event.WorkingSet
        $Timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"

        $Message = "[$Timestamp] Process: $ProcessName (PID: $PID), CPU: $CPUUsage%, Memory: $($MemoryUsage / 1048576) MB"
        Write-Output "Alert: $Message"
        Add-Content -Path $OutputFile -Value $Message
    } catch {
        Write-Output "Error in event action: $_"
        Add-Content -Path $OutputFile -Value "[$(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')] Error: $_"
    }
}
Write-Output "WMI event registered. Waiting for events..."
