Unregister-Event -SourceIdentifier ProcessMonitor
# monitor_processes.ps1
$OutputFile = "C:\Temp\process_alerts.txt"
$CPUThreshold = 1
$MemoryThreshold = 10000 # ~10 KB for easier testing
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
# Ensure output file exists at startup
if (-not (Test-Path $OutputFile)) {
    New-Item -Path $OutputFile -ItemType File -Force | Out-Null
}

Write-Output "Registering WMI event..."

# Register WMI event
Register-WmiEvent -Query $Query -SourceIdentifier "ProcessMonitor" -Action {
    Write-Output "Event action triggered!" # Debug log
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

# Diagnostic: Output job status and registered events
$job = Get-Job | Where-Object { $_.Name -eq 'ProcessMonitor' }
if ($job) {
    Write-Output "Job status: $($job.State)"
} else {
    Write-Output "No job found with name 'ProcessMonitor'"
}

$events = Get-EventSubscriber | Where-Object { $_.SourceIdentifier -eq 'ProcessMonitor' }
Write-Output "Registered event subscribers: $($events.Count)"
foreach ($evt in $events) {
    Write-Output "EventSubscriber: $($evt.SourceIdentifier), Action: $($evt.Action)"
}

# Keep the script running to process events
while ($true) {
    $Timestamp = Get-Date -Format "yyyy-MM-dd HH:mm:ss"
    Write-Output "Wait at: $Timestamp"
    Start-Sleep -Seconds 5
}
