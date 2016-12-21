package com.redhat.example.jdg;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;
import org.infinispan.Cache;
import org.infinispan.distexec.DefaultExecutorService;
import org.infinispan.distexec.DistributedExecutorService;
import org.infinispan.distexec.DistributedTask;
import org.infinispan.manager.EmbeddedCacheManager;

import com.redhat.example.jdg.util.Trace;

public class RestoreTask {
	
	static Logger log = Logger.getLogger(RestoreTask.class);
	static BackupConfiguration config = BackupConfiguration.getInstance();
	
	List<Future<String>> futures = Collections.synchronizedList(new ArrayList<Future<String>>());

	public RestoreTask() { init(); }

	void init() {
		Trace.init();
	}
	
	public void restore(EmbeddedCacheManager manager) {
		log.info("### Restore start!");
		try {
			Cache<Object, Object> cache = manager.getCache("cacheController");
			DistributedExecutorService des = new DefaultExecutorService(cache);
			Callable<String> call = new RestoreCommand();
			DistributedTask<String> task  = des.createDistributedTaskBuilder(call)
					.timeout(config.restoreTimeoutMin, TimeUnit.MINUTES)
					.build();
			futures = des.submitEverywhere(task);
		} catch (Exception e) {
			log.error("Failed backup task.", e);
			throw new IllegalStateException(String.format("Failed to read backup directory '%s': %s", config.baseDir, e));
		}
	}
	
	void waitForDone(Future<String> future) {
		while (!future.isDone()) {
			try { Thread.sleep(100); } catch (InterruptedException e) {}
		}
	}

	public List<Future<String>> getFutures() {
		return futures;
	}

}
