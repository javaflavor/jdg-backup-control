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

public class BackupTask {
	static Logger log = Logger.getLogger(BackupTask.class);

	static String nodeName = System.getProperty("jboss.node.name");
	static BackupConfiguration config = BackupConfiguration.getInstance();
	
	List<Future<String>> futures = Collections.synchronizedList(new ArrayList<Future<String>>());

	public BackupTask() { init(); }
	
	void init() {
		Trace.init();
	}
	
	public void backup(EmbeddedCacheManager manager) {
		log.info("### Backup start!");
		log.info("backup(): cacheNames="+manager.getCacheNames());
		try {
			manager.getCacheNames()
			.stream()
			.parallel()
			.filter(name -> config.cacheNames.contains(name))
			.map(name -> {
				Cache<Object, Object> cache = manager.getCache(name);	// thread-safe
				DistributedExecutorService des = new DefaultExecutorService(cache);
				Callable<String> call = new BackupCommand();
				DistributedTask<String> task  = des.createDistributedTaskBuilder(call)
						.timeout(config.backupTimeoutMin, TimeUnit.MINUTES)
						.build();
				return des.submitEverywhere(task);
			})
			.forEach(future -> futures.addAll(future));
		} catch (Exception e) {
			log.error("Failed backup task.", e);
			throw e;
		}
	}

	public List<Future<String>> getFutures() {
		return futures;
	}

}
