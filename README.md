# JDG Backup/Restore Tool

## Preface

There is no standard backup and resotre tools in JDG product. This is a sample implementation of backup/restore tool for JDG cluster in client-server mode.

The tool is implementated as follows:

1. In the client server mode, developers can customize the server-side behavior by deploying custom listeners, filters, converters, stores etc. The custom module can access the library mode API of CacheManager and Cache, read the Cache entries iteratively and backup these data.
2. Unlike RemoteCache, Cache.keySet() and entrySet() in library mode handle the local entries in the target instance. Therefore, distributed executor is useful to backup all instances simultaneously.
3. The above-mentioned custom processing (backup) module deployed in the JDG server can not be called via Hot Rod. Instead, the module is exporsed the custom MBean interface for triggering the backup process from the outside via JMX API.
4. Work fine for JDG 6.6 and 7.0.

## How to use

### Preparation

Apache Maven is used to build the tool of the project. Check if JDK 8 and Apache Maven are available in your environment.

~~~
$  mvn --version
Apache Maven 3.3.1 (cab6659f9874fa96462afef40fcf6bc033d58c1c; 2015-03-14T05:10:27+09:00)
	:
$ javac -version
javac 1.8.0_65
~~~

### Review the configuration

The tool reads the following property file as the configuration.

* backup.properties

The contents of each setting are as follows. Please change the setting according to your environment.

src/main/resources/backup.properties:

~~~
# Cache name list to backup.
backup.cache_names = namedCache, \
	default, \
	LuceneIndexesMetadata, \
	LuceneIndexesData, \
	LuceneIndexesLocking, \
	___protobuf_metadata

# Base directory to store back files.
backup.base_dir = /tmp/backup

# Partition size: max number of entries to save in each backup file.
backup.partition_size = 50000

# Timeout(min) for backup process.
backup.backup_timeout_min = 60

# Timeout(min) for restore process.
backup.restore_timeout_min = 60
~~~

Please note that if you are using Remote Query and infinispan directory provider, you should also backup the hidden cache called **"___protobuf_metadata"**.

The backup data is chunked for each backup.partition\_size entries and stored in the directory specified by backup.base_dir as follows.

~~~
/tmp/backup/
├── backup-namedCache-localhost:11222-0.bin
├── backup-namedCache-localhost:11222-1.bin
├── backup-namedCache-localhost:11222-2.bin
├── backup-namedCache-localhost:11222-3.bin
├── backup-namedCache-localhost:11222-4.bin
			:
~~~

### Build the deployment module

The tool can be built by the following command, in case that target JDG version is 7.0 or later.

~~~
$ cd jdg-backup-control
$ mvn clean package

or,

$ mvn clean package -Pjdg7
~~~

For JDG 6.6.x, you must specify the profile name **jdg6** in the mvn command line.

~~~
$ cd jdg-backup-control
$ mvn clean package -Pjdg6
~~~

Check "BUILD SUCCESS" message.

~~~
	:
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] ------------------------------------------------------------------------
[INFO] Total time: 1.989 s
[INFO] Finished at: 2015-11-27T15:57:18+09:00
[INFO] Final Memory: 17M/220M
[INFO] ------------------------------------------------------------------------
~~~

The deployable module can be found in the target directory.

~~~
$ ls target/
./                      generated-sources/      maven-status/
../                     jdg-backup-control.jar
classes/                maven-archiver/
~~~
    
### Deply the module in JDG server

In order to deploy the module in JDG server, copy it to the deployments directory of the target JDG server.

~~~
$ /opt/jboss/jboss-datagrid-7.0.0-server/bin/cli.sh --controller=server1:9990 'deploy target/jdg-backup-control.jar --force'
$ /opt/jboss/jboss-datagrid-7.0.0-server/bin/cli.sh --controller=server2:9990 'deploy target/jdg-backup-control.jar --force'
    :
~~~

Create a dummy cache, called cacheController using the deployed module and restart the server.

~~~
$ /opt/jboss/jboss-datagrid-7.0.0-server/bin/cli.sh --controller=server1:9990 --file=cli/add-controller-cache-jdg7.cli
$ /opt/jboss/jboss-datagrid-7.0.0-server/bin/cli.sh --controller=server2:9990 --file=cli/add-controller-cache-jdg7.cli
	:
~~~

If your target JDG is 6.6, use cli script `cli/add-controller-cache-jdg6.cli`

Please check the configuration file clustered.xml is modified as follows:

~~~
                <distributed-cache-configuration name="cacheControl" mode="SYNC" start="EAGER">
                    <store class="com.redhat.example.jdg.store.CacheControlStore"/>
                </distributed-cache-configuration>
                <distributed-cache name="cacheController" configuration="cacheControl"/>
~~~

### Test Fright

#### Backup

Backup request is using JMX interface. The deployed module will expose the following custom MBean.

~~~
com.redhat.example:name=CacheController
~~~

You can access the CacheController MBean using JConsole. In order to backup, execute the backup() operation.

Please note that you can access any instance in the JDG cluster. The backup request calls the distributed executor and spans the backup request to all of the instances in the cluster.

#### Restore


The restore is started by calling the restore() operation of the CacheController MBean like a backup.

### Command line tools

In addition, sh script for calling back up and restore JMX API is also included, so please check these files.

* bin/jdg-backup.sh
* bin/jdg-restore.sh
* bin/jdg-status.sh

The connection information of the sh script above is defined in bin/cachecontrol.config. Please correct the settings according to your environment.

~~~
// Any server to connect using JMX protocol.
// 
// JDG6: "remoting-jmx://<host>:<port>"
// JDG7: "remote+http://<host>:<port>" (or "http-remoting-jmx://<host>:<port>")

//var server = "remoting-jmx://localhost:9999"
var server = "remote+http://localhost:9990"

// Authentication info: username and password.

var username = "admin"
var password = "welcome1!"
~~~

In order to backup, execute the script `jdg-backup.sh`.

~~~
$ bin/jdg-backup.sh
~~~

And for restore, use the script `jdg-restore.sh`.

~~~
$ bin/jdg-restore.sh
~~~

You can check the current backup/restore status using `jdg-status.sh`

~~~
$ bin/jdg-status.sh

Backup:  Stopped, finished nodes: []
Restore: Stopped, finished nodes: [node2, node1, node3, node4]
~~~
