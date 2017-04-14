// Check Java version >= 1.8
if (java.lang.System.getProperty("java.specification.version") >= "1.8") {
  // Load java 7 compatible global objects.
  load("nashorn:mozilla_compat.js")
}

importPackage(java.lang)
importPackage(java.nio.file)
importPackage(java.text)
importPackage(java.util)
importPackage(java.util.concurrent)
importPackage(java.util.stream)
importPackage(javax.management)
importPackage(javax.management.remote)

// Check arguments.
if (arguments.length != 2) {
    println("Usage: jrunscript cachecontrol.js cachecontrol.config [backup|restore|status]")
    exit(1)
}
var config = arguments[0]
var command = arguments[1]
// Load config (server, username, password).
load(config)

var connector = getJMXConnector(server, username, password)
var con = connector.getMBeanServerConnection()

var objname = new ObjectName("com.redhat.example:name=CacheController")

if (command == "status") {
    var attrs = getStatus(con, objname)
    System.out.printf("Backup:  %s, finished nodes: %s%n", attrs.get(0).getValue() ? "Running" : "Stopped", asList(attrs.get(1).getValue()))
    System.out.printf("Restore: %s, finished nodes: %s%n", attrs.get(2).getValue() ? "Running" : "Stopped", asList(attrs.get(3).getValue()))
} else {
    System.out.printf("Sending '%s' command to server '%s'.%n", command, server)
    con.invoke(objname, command, null, null)
}

function getJMXConnector(server, username, password) {
    var url = new JMXServiceURL("service:jmx:" + server)
    var cls = new java.lang.String().getClass()
    var cred = java.lang.reflect.Array.newInstance(cls, 2)
    cred[0] = username
    cred[1] = password
    var env = {"jmx.remote.credentials" : cred}
    return JMXConnectorFactory.connect(url, env)
}

function getStatus(con, objname) {
    var cls = new java.lang.String().getClass()
    var names = java.lang.reflect.Array.newInstance(cls, 4)
    names[0] = "BackupRunning"
    names[1] = "BackupList"
    names[2] = "RestoreRuning"
    names[3] = "RestoreList"
    return con.getAttributes(objname, names)
}

function asList(arr) {
    return java.util.Arrays.asList(arr)
}

function sleep(sec) {
    try { java.lang.Thread.sleep(sec*1000) } catch (e) {}
}

