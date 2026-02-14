package com.zain.ksa.alm.acceptance.util;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileTime;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Helper {

	private static final Logger logger = LoggerFactory.getLogger(Helper.class);
	private static final String SUBRACK_LOG_PATH = "/home/app/logs/ALM/Subrack/Subrack.log";
	private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
	private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
	private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

	private Helper() {
		throw new UnsupportedOperationException("Utility class");
	}

	public static void logBatchFile(String log, boolean includeDate, String batchfilename) {
		try (FileWriter f = new FileWriter(new File("/home/app/logs/ALM/BatchFiles/" + batchfilename), true)) {
			if (includeDate)
				f.write(String.format("%s%s", TIME_FORMATTER.format(LocalDateTime.now()), System.lineSeparator()));
			f.write(String.format("%s%s", log, System.lineSeparator()));
		} catch (Exception e) {
			logger.error("Error writing batch file: {}", e.getMessage());
		}
	}

	private static void renameFile() {
		String fname = DATE_FORMATTER.format(LocalDateTime.now().minusDays(1));
		Path source = Paths.get(SUBRACK_LOG_PATH);
		try {
			Files.move(source, source.resolveSibling(String.format("Subrack.log-%s", fname)));
		} catch (IOException e) {
			logger.error("Error renaming file: {}", e.getMessage());
		}
	}

	public static String getKenyaDateTimeString() {
		return DATETIME_FORMATTER.format(LocalDateTime.now());
	}

	public static void logToFile(String log, String status) {
		checkAndRenameLogFileIfNeeded();
		writeToLogFile(log, status);
	}

	private static void checkAndRenameLogFileIfNeeded() {
		try {
			Path source = Paths.get(SUBRACK_LOG_PATH);
			FileTime creationTime = (FileTime) Files.getAttribute(source, "creationTime");
			LocalDateTime convertedFileTime = LocalDateTime.ofInstant(creationTime.toInstant(),
					ZoneId.systemDefault());
			SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
			Date date1 = sdf.parse(DATE_FORMATTER.format(convertedFileTime));
			Date date2 = sdf.parse(DATE_FORMATTER.format(LocalDateTime.now()));

			if (date1.compareTo(date2) != 0) {
				renameFile();
			}
		} catch (Exception ex) {
			logger.warn("Could not check/rename log file: {}", ex.getMessage());
		}
	}

	private static void writeToLogFile(String log, String status) {
		try (FileWriter f = new FileWriter(new File(SUBRACK_LOG_PATH), true)) {
			f.write(String.format("%s : %s : %s%s", 
					DATETIME_FORMATTER.format(LocalDateTime.now()), 
					status, log, System.lineSeparator()));
		} catch (Exception e) {
			logger.error("Error writing to log file: {}", e.getMessage());
		}
	}

}
