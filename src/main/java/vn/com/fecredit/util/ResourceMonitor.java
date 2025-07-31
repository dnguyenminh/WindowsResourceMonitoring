package vn.com.fecredit.util;

import com.sun.jna.platform.win32.COM.WbemcliUtil;
import javax.mail.*;
import javax.mail.internet.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

public class ResourceMonitor {
    // Email configuration
    private static final String SMTP_HOST = "smtp.gmail.com";
    private static final String SMTP_PORT = "587";
    private static final String SENDER_EMAIL = "dnguyenminh@gmail.com";
    private static final String SENDER_PASSWORD = "DucMai210703"; // Use Gmail App Password
    private static final String RECIPIENT_EMAIL = "jidenna.azarius@freedrops.org";

    // Resource thresholds
    private static final double CPU_THRESHOLD = 80.0; // CPU usage percentage
    private static final long MEMORY_THRESHOLD = 1024 * 1024 * 1024; // 1 GB in bytes
    private static final long DISK_IO_THRESHOLD = 10 * 1024 * 1024; // 10 MB/s

    // Paths
    private static final String POWERSHELL_SCRIPT = "scripts\\monitor_processes.ps1";
    private static final String ALERT_FILE = "C:\\Temp\\process_alerts.txt";

    public static void main(String[] args) {
        // Start PowerShell script
        startPowerShellScript();

        // Monitor alert file
        ScheduledExecutorService executor = Executors.newScheduledThreadPool(1);
        executor.scheduleAtFixedRate(ResourceMonitor::checkAlertFile, 0, 10, TimeUnit.SECONDS);

        // Keep program running
        try {
            Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            executor.shutdown();
            stopPowerShellScript();
        }
    }

    private static void startPowerShellScript() {
        try {
            // Start PowerShell script in the background
            ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-File", POWERSHELL_SCRIPT);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // Log PowerShell output (optional)
            new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println("PowerShell: " + line);
                    }
                } catch (IOException e) {
                    System.err.println("Error reading PowerShell output: " + e.getMessage());
                }
            }).start();
        } catch (IOException e) {
            System.err.println("Error starting PowerShell script: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void stopPowerShellScript() {
        try {
            // Stop WMI event subscription
            ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-Command", "Unregister-Event -SourceIdentifier ProcessMonitor");
            pb.start();
        } catch (IOException e) {
            System.err.println("Error stopping PowerShell script: " + e.getMessage());
        }
    }

    private static void checkAlertFile() {
        try {
            File alertFile = new File(ALERT_FILE);
            if (!alertFile.exists()) {
                return;
            }

            List<String> lines = Files.readAllLines(alertFile.toPath());
            if (lines.isEmpty()) {
                return;
            }

            StringBuilder alertMessage = new StringBuilder();
            for (String line : lines) {
                // Parse line: "Process: name (PID: pid), CPU: cpu%, Memory: memory MB"
                String[] parts = line.split(", ");
                if (parts.length < 3) continue;

                String processInfo = parts[0].replace("Process: ", "");
                String[] processParts = processInfo.split(" \\(PID: ");
                if (processParts.length < 2) continue;

                String processName = processParts[0];
                String pidStr = processParts[1].replace(")", "");
                long pid = Long.parseLong(pidStr);
                double cpuUsage = Double.parseDouble(parts[1].replace("CPU: ", "").replace("%", ""));
                double memoryUsageMb = Double.parseDouble(parts[2].replace("Memory: ", "").replace(" MB", ""));
                long memoryUsage = (long) (memoryUsageMb * 1024 * 1024); // Convert MB to bytes
                long diskIoUsage = getDiskIoUsage(pid);

                // Check thresholds
                if (cpuUsage > CPU_THRESHOLD || memoryUsage > MEMORY_THRESHOLD || diskIoUsage > DISK_IO_THRESHOLD) {
                    alertMessage.append(String.format(
                            "Process: %s (PID: %d)\nCPU Usage: %.2f%%\nMemory Usage: %.2f MB\nDisk I/O: %.2f MB/s\n\n",
                            processName, pid, cpuUsage, memoryUsage / (1024.0 * 1024.0), diskIoUsage / (1024.0 * 1024.0)
                    ));
                }
            }

            // Send email if there are alerts
            if (!alertMessage.isEmpty()) {
                String subject = "Resource Usage Alert for Windows Server Processes";
                sendEmail(subject, alertMessage.toString());
                System.out.println("Alert sent for processes:\n" + alertMessage);
                // Clear the file after processing
                Files.write(alertFile.toPath(), new byte[0], StandardOpenOption.TRUNCATE_EXISTING);
            }
        } catch (IOException e) {
            System.err.println("Error reading alert file: " + e.getMessage());
        }
    }

    // Get disk I/O usage using WbemcliUtil
    private static long getDiskIoUsage(long pid) {
        try {
            enum IoProperty { IOReadBytesPersec, IOWriteBytesPersec }
            WbemcliUtil.WmiQuery<IoProperty> ioQuery = new WbemcliUtil.WmiQuery<>(
                    "Win32_PerfRawData_PerfProc_Process WHERE IDProcess = " + pid, IoProperty.class);
            WbemcliUtil.WmiResult<IoProperty> result = ioQuery.execute();

            if (result.getResultCount() > 0) {
                long readBytes = Long.parseLong(result.getValue(IoProperty.IOReadBytesPersec, 0).toString());
                long writeBytes = Long.parseLong(result.getValue(IoProperty.IOWriteBytesPersec, 0).toString());
                return readBytes + writeBytes; // Total bytes per second
            }
        } catch (Exception e) {
            System.err.println("Error querying disk I/O for PID " + pid + ": " + e.getMessage());
        }
        return 0;
    }

    // Send email alert
    private static void sendEmail(String subject, String body) {
        Properties props = new Properties();
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.host", SMTP_HOST);
        props.put("mail.smtp.port", SMTP_PORT);

        Session session = Session.getInstance(props, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(SENDER_EMAIL, SENDER_PASSWORD);
            }
        });

        try {
            Message message = new MimeMessage(session);
            message.setFrom(new InternetAddress(SENDER_EMAIL));
            message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(RECIPIENT_EMAIL));
            message.setSubject(subject);
            message.setText(body);
            Transport.send(message);
        } catch (MessagingException e) {
            System.err.println("Failed to send email: " + e.getMessage());
        }
    }
}