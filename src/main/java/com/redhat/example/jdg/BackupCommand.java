package com.redhat.example.jdg;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import org.apache.log4j.Logger;
import org.infinispan.Cache;
import org.infinispan.distexec.DistributedCallable;

import com.redhat.example.jdg.util.Trace;

public class BackupCommand implements DistributedCallable<Object, Object, String>, Serializable {
	private static final long serialVersionUID = 5991478010533196655L;
	
	static Logger log = Logger.getLogger(BackupCommand.class);
	static String nodeName = System.getProperty("jboss.node.name");
	static BackupConfiguration config = BackupConfiguration.getInstance();
	
	Cache<Object, Object> cache;

	@Override
	public void setEnvironment(Cache<Object, Object> cache, Set<Object> inputKeys) {
		this.cache = cache;
	}

	@Override
	public String call() throws Exception {
		log.info("### Backup start! cache name = "+cache.getName());
		try {
			long t1 = System.currentTimeMillis();

			int partitionNum = cache.size()/config.partitionSize + 1;
			log.info(String.format("### %s: partitioned: %d / %s = %d",
					cache.getName(), cache.size(), config.partitionSize, partitionNum));

			AtomicLong index = new AtomicLong();

			// Entry map devided into some partioned and save these in parallel.
			cache.entrySet().parallelStream()
			.filter(entry -> cache.getAdvancedCache().getDistributionManager().getPrimaryLocation(entry.getKey()).equals(cache.getCacheManager().getAddress()))
			.collect(Collectors.groupingBy(t -> index.getAndIncrement()%partitionNum))
			.entrySet()
			.forEach(partition -> {
				saveToFile(nodeName, cache.getName(), partition.getKey(), partition.getValue());
			});
			
			long t2 = System.currentTimeMillis();

			log.info(String.format("### Backup ends! cache name = %s, elapsed = %d (sec), max concurrency = %d",
					cache.getName(), (t2-t1)/1000, Trace.maxConcurrency.get()));

			return cache.getName()+"@"+nodeName;
		} catch (Exception e) {
			RuntimeException ex = new RuntimeException("Backup failed: "+cache.getName()+"@"+nodeName, e);
			log.error(ex.getMessage(), e);
			throw ex;
		}
	}

	void saveToFile(String server, String cacheName, Object partition, List<Map.Entry<Object, Object>> entryList) {
		Trace.begin();
		Path file = Paths.get(config.baseDir, "backup-"+cacheName+"-"+server+"-"+partition+".bin");
		
		try (ObjectOutputStream out = new ObjectOutputStream(new BufferedOutputStream(Files.newOutputStream(file)))) {
			log.info("Begin backup file: "+file);
			out.writeUTF(cacheName);					// write cache name.
			out.writeInt(entryList.size());				// write partition size.
			entryList.forEach(entry -> {
				try {
					out.writeObject(entry.getKey());	// write key.
					out.writeObject(entry.getValue());	// write value.
				} catch (IOException e) {
					throw new IllegalStateException(String.format("Failed to write backup file '%s': ", file), e);
				}
			});
			log.info("End backup file: "+file);
		} catch (IOException e) {
			throw new IllegalStateException(String.format("Failed to write backup file '%s': ", file), e);
		} finally {
			Trace.end();
		}
	}
}
