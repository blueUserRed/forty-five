package com.fourinachamber.fortyfive;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class DesktopLauncher {
    public static void main(String[] arg) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setForegroundFPS(60);
        config.setTitle(".Forty-Five");
        config.setWindowIcon(com.badlogic.gdx.Files.FileType.Internal, "blobs/icon.png");
        Exception exception = null;

        if (arg.length > 0 && arg[0].equals("createDropShadows")) {
            config.setWindowedMode(10000, 10000);
        }else{
            config.setWindowedMode(900, (900 * 9) / 16);
        }
        try {
            new Lwjgl3Application(FortyFive.INSTANCE, config);
        } catch (Exception e) {
            exception = e;
        }

		if (exception != null) try {
			FortyFive.INSTANCE.getLogger().fatal(exception);
		} catch (Exception ignored) {
			// "more robust logging" failed in this case
			//noinspection CallToPrintStackTrace
			exception.printStackTrace();
		}
        if (exception != null) try {
            FortyFive.INSTANCE.getLogger().fatal(exception);
        } catch (Exception ignored) {
            // "more robust logging" failed in this case
            //noinspection CallToPrintStackTrace
            exception.printStackTrace();
        }

        if (FortyFive.INSTANCE.getCleanExit() && exception == null) return;
        boolean copiedLog = copyLogFile();
        showErrorPopup(copiedLog);
    }

    private static boolean copyLogFile() {
        File log = new File("logging/forty-five.log");
        if (!log.exists() || !log.isFile()) return false;
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy_HH-mm-ss");
        String time = formatter.format(LocalDateTime.now());
        try {
            Files.copy(log.toPath(), Paths.get("./error_logs/" + time + ".log"));
        } catch (IOException e) {
            return false;
        }
        return true;
    }

    public static void showErrorPopup(boolean copiedLog) {
        final JFrame parent = new JFrame("The Program Got Shot");
        parent.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        parent.setUndecorated(true);

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 30, 20, 30));
        Color forty_white = new Color(240, 234, 221);
        panel.setBackground(forty_white);

        JLabel errorLabel = new JLabel("⚠ An error has occurred!");
        errorLabel.setFont(new Font("SansSerif", Font.BOLD, 16));
        errorLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel logLabel = new JLabel("The log file has been saved to the 'error_log' directory.");
        logLabel.setFont(new Font("SansSerif", Font.PLAIN, 13));
        logLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JButton okButton = new JButton("OK");
        okButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        okButton.setFocusPainted(false);
        okButton.setBackground(new Color(42,36,36));
        okButton.setForeground(forty_white);
        okButton.setFont(new Font("SansSerif", Font.BOLD, 13));
        okButton.setPreferredSize(new Dimension(80, 30));

        panel.add(errorLabel);
        panel.add(Box.createRigidArea(new Dimension(0, 10)));
        if (copiedLog) {
            panel.add(logLabel);
            panel.add(Box.createRigidArea(new Dimension(0, 10)));
        }
        panel.add(okButton);

        parent.getContentPane().add(panel);
        parent.pack();
        parent.setLocationRelativeTo(null); // Center on screen
        parent.setVisible(true);

        okButton.addActionListener(evt -> {
            parent.dispose();
            System.exit(1);
        });

        parent.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                System.exit(1);
            }
        });
    }

}
