package com.redhat.example.jmx;

import java.util.List;

public interface CacheControllerMXBean {
    public void backup();
    
    public void restore();
    
    public List<String> getBackupList();

    public List<String> getRestoreList();
    
    public boolean isBackupRunning();
    
    public boolean isRestoreRuning();
}
