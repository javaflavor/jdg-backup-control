package com.redhat.example.jdg;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

import org.apache.log4j.Logger;

public class BackupConfiguration {

	static final String PROPERTY_FILE = "backup.properties";
	static final String CACHE_NAMES = "backup.cache_names";
	static final String PARTITION_SIZE = "backup.partition_size";
	static final String BASE_DIR = "backup.base_dir";
	static final String BACKUP_TIMEOUT_MIN = "backup.backup_timeout_min";
	static final String RESTORE_TIMEOUT_MIN = "backup.restore_timeout_min";

	static Logger log = Logger.getLogger(BackupTask.class);
	static BackupConfiguration config = new BackupConfiguration();

	List<String> cacheNames;
	int partitionSize;
	String baseDir;
	int backupTimeoutMin;
	int restoreTimeoutMin;
	
	public static BackupConfiguration getInstance() {
		return config;
	}
	
	BackupConfiguration() {
		init();
	}

	void init() {
		Properties props = new Properties();
		try (InputStream is = this.getClass().getClassLoader().getResourceAsStream(PROPERTY_FILE)) {
			props.load(is);
			String names = props.getProperty(CACHE_NAMES) == null ? "" : props.getProperty(CACHE_NAMES);
			cacheNames = Arrays.asList(names.split(","))
					.stream().map(e -> e.trim())
					.collect(Collectors.toList());
			log.info("init: cacheNames = "+ cacheNames);
			partitionSize = Integer.parseInt(props.getProperty(PARTITION_SIZE));
			log.info("init: partitionSize = "+ partitionSize);
			backupTimeoutMin = Integer.parseInt(props.getProperty(BACKUP_TIMEOUT_MIN));
			log.info("init: backupTimeoutMin = "+ backupTimeoutMin);
			restoreTimeoutMin = Integer.parseInt(props.getProperty(RESTORE_TIMEOUT_MIN));
			log.info("init: restoreTimeoutMin = "+ restoreTimeoutMin);
			baseDir = props.getProperty(BASE_DIR);
			log.info("init: baseDir = "+ baseDir);
			
			// Check backup directory.
			Path basedir = Paths.get(baseDir);
			Files.createDirectories(basedir);
		} catch (IOException e) {
			throw new IllegalStateException(String.format("Failed to load property file '%s': ", PROPERTY_FILE), e);
		}
	}
	
}
