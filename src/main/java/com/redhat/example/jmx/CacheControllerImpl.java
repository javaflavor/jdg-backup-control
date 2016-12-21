package com.redhat.example.jmx;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import org.infinispan.manager.EmbeddedCacheManager;
import org.infinispan.util.logging.Log;
import org.infinispan.util.logging.LogFactory;

import com.redhat.example.jdg.BackupTask;
import com.redhat.example.jdg.RestoreTask;

public class CacheControllerImpl implements CacheControllerMXBean {
	static Log log = LogFactory.getLog(CacheControllerImpl.class);
	
	EmbeddedCacheManager manager;
	BackupTask backupTask = new BackupTask();
	RestoreTask restoreTask = new RestoreTask();
	
	public CacheControllerImpl(EmbeddedCacheManager manager) {
		this.manager = manager;
	}
	
	@Override
	public void backup() {
		log.info("### backup(): called.");
		if (isBackupRunning()) {
			log.error("Backup() request rejected. Another backup process is running.");
			throw new CacheControllerException("Another backup process is running.");
		}
		backupTask = new BackupTask();
		backupTask.backup(manager);
	}
    
	@Override
	public void restore() {
		log.info("### restore(): called.");
		if (isRestoreRuning()) {
			log.error("Restore() request rejected. Another backup process is running.");
			throw new CacheControllerException("Another restore process is running.");
		}
		restoreTask = new RestoreTask();
		restoreTask.restore(manager);
	}

	@Override
	public List<String> getBackupList() {
		return backupTask.getFutures()
				.stream()
				.filter(f -> f.isDone())
				.map(f -> {
					try {
						return f.get();
					} catch (InterruptedException | ExecutionException e) {
						return e.toString();
					}
				})
				.collect(Collectors.toList());
	}

	@Override
	public List<String> getRestoreList() {
		return restoreTask.getFutures()
				.stream()
				.filter(f -> f.isDone())
				.map(f -> {
					try {
						return f.get();
					} catch (InterruptedException | ExecutionException e) {
						return e.toString();
					}
				})
				.collect(Collectors.toList());
	}

	@Override
	public boolean isBackupRunning() {
		return backupTask.getFutures().stream().anyMatch(f -> !f.isDone());
	}

	@Override
	public boolean isRestoreRuning() {
		return restoreTask.getFutures().stream().anyMatch(f -> !f.isDone());
	}
	
}
