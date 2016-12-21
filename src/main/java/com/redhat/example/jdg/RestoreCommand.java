package com.redhat.example.jdg;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.log4j.Logger;
import org.infinispan.Cache;
import org.infinispan.distexec.DistributedCallable;
import org.infinispan.manager.EmbeddedCacheManager;

import com.redhat.example.jdg.util.Trace;

public class RestoreCommand implements DistributedCallable<Object, Object, String>, Serializable {
	private static final long serialVersionUID = -8307084250504172126L;
	
	static Logger log = Logger.getLogger(RestoreCommand.class);
	static String nodeName = System.getProperty("jboss.node.name");
	static BackupConfiguration config = BackupConfiguration.getInstance();
	static Object lock = new Object();
	
	EmbeddedCacheManager manager;

	@Override
	public void setEnvironment(Cache<Object, Object> cache, Set<Object> inputKeys) {
		this.manager = cache.getCacheManager();
	}

	@Override
	public String call() throws Exception {
		log.info("### Restore start!");
		try {
			long t1 = System.currentTimeMillis();

			Path dir = Paths.get(config.baseDir);
			List<Path> files = Files.list(dir)
					.filter(f -> !Files.isDirectory(f))
					.filter(f -> f.toString().endsWith(".bin") && f.toString().contains(nodeName))
					.collect(Collectors.toList());
			files.stream().parallel().forEach(file -> {
				readFileAndRestore(file, manager);
			});
			
			long t2 = System.currentTimeMillis();
			log.info(String.format("### Restore ends! elapsed = %d (sec), max concurrency = %d",
					(t2-t1)/1000, Trace.maxConcurrency.get()));

			return nodeName;
		} catch (Exception e) {
			RuntimeException ex = new RuntimeException("Backup failed: "+nodeName, e);
			log.error(ex.getMessage(), e);
			throw ex;
		}
	}

	void readFileAndRestore(Path file, EmbeddedCacheManager manager) {
		Trace.begin();
		
		try (ObjectInputStream in = new ObjectInputStream(new BufferedInputStream(Files.newInputStream(file)))) {
			log.info("Begin restore file: "+file);
			String cacheName = in.readUTF();	// read cache name.
			Cache<Object,Object> cache = manager.getCache(cacheName);
			int size = in.readInt();			// read size.
			for (int i = 0; i < size; i++) {
				Object key = in.readObject();	// read key.
				Object val = in.readObject();	// read value.
				// Restore entry, if not exists.
				cache.putIfAbsent(key, val);
			}
			assert(in.available() == 0);
			log.info(String.format("End restore file: %s, %d entries.", file, size));
		} catch (IOException | ClassNotFoundException e) {
			throw new IllegalStateException(String.format("Failed to read backup file '%s': ", file), e);
		} finally {
			Trace.end();
		}
	}
	
}
