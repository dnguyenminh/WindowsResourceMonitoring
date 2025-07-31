Unregister-Event -SourceIdentifier "HighCPU" -ErrorAction SilentlyContinue
# Ensure log directory exists
New-Item -ItemType Directory -Path "C:\Logs" -Force | Out-Null

# Set CPU threshold
$cpuThreshold = 50
# Filter out '_Total' and 'Idle' processes
$query = "SELECT * FROM __InstanceModificationEvent WITHIN 1 WHERE TargetInstance ISA 'Win32_PerfFormattedData_PerfProc_Process' AND TargetInstance.PercentProcessorTime > $cpuThreshold AND TargetInstance.Name != '_Total' AND TargetInstance.Name != 'Idle'"

# Clear any existing event subscription
Unregister-Event -SourceIdentifier "HighCPU" -ErrorAction SilentlyContinue
Get-Job -Name "HighCPU" -ErrorAction SilentlyContinue | Remove-Job -Force

# Register WMI event
Register-WmiEvent -Query $query -SourceIdentifier "HighCPU" -Action {
    $process = $Event.SourceEventArgs.NewEvent.TargetInstance
    $time = (Get-Date).ToString('dd-MM-yyyy HH:mm:ss')
    $cpu = $process.PercentProcessorTime
    $name = $process.Name
    $pid = $process.IDProcess
    $message = "[$time] High CPU usage: $name, CPU: $cpu%, PID: $pid"
    Write-Host $message
    $message | Out-File -FilePath "C:\Logs\CPUAlerts.log" -Append
    # Debug: Log event receipt
    "[$time] Event received for $name" | Out-File -FilePath "C:\Logs\CPUAlerts_Debug.log" -Append
}

Write-Host "Monitoring for processes with CPU usage > $cpuThreshold%... Press Ctrl+C to stop."

try {
    while ($true) {
        $event = Wait-Event -SourceIdentifier "HighCPU" -Timeout 10
        if ($event) {
            # Process event and remove it to prevent queue buildup
            Remove-Event -EventIdentifier $event.EventIdentifier
        }
        # Debug: Log heartbeat to ensure script is running
        "[$(Get-Date -Format 'dd-MM-yyy HH:mm:ss')] Script heartbeat" | Out-File -FilePath "C:\Logs\CPUAlerts_Debug.log" -Append
    }
}
finally {
    # Cleanup: Unregister event and remove job
    Write-Host "Cleaning up event subscription..."
    Unregister-Event -SourceIdentifier "HighCPU" -ErrorAction SilentlyContinue
    Get-Job -Name "HighCPU" -ErrorAction SilentlyContinue | Remove-Job -Force
    Write-Host "Event subscription unregistered."
}