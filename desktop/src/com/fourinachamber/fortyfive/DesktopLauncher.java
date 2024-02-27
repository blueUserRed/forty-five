package com.fourinachamber.fortyfive;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.fourinachamber.fortyfive.utils.FortyFiveLogger;

import javax.swing.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class DesktopLauncher {
	public static void main (String[] arg) {
		Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
		config.setForegroundFPS(60);
		config.setTitle(".forty-five");
		config.setWindowedMode(900,(900*9)/16);
		config.setWindowIcon(com.badlogic.gdx.Files.FileType.Internal, "textures/icon.png");
		Exception exception = null;

		try {
			new Lwjgl3Application(FortyFive.INSTANCE, config);
		} catch (Exception e) {
			exception = e;
		}

		if (exception != null) try {
			FortyFiveLogger.INSTANCE.fatal(exception);
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

	private static void showErrorPopup(boolean copiedLog) {
		final JFrame parent = new JFrame();
		parent.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		JLabel label = new JLabel("An error was encountered!");
		JLabel logLabel = new JLabel("The log file was copied to the 'error_log' directory.");

		JButton button = new JButton("Ok");

		panel.add(label);
		if (copiedLog) panel.add(logLabel);
		panel.add(button);
		parent.add(panel);
		parent.pack();
		parent.setVisible(true);

		button.addActionListener(evt -> {
			parent.setVisible(false);
			System.exit(1);
		});

		parent.addWindowListener(new WindowAdapter(){
			public void windowClosing(WindowEvent e){
				System.exit(1);
			}
		});
	}

}
