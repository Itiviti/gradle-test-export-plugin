# Gradle Test Export Plugin
This plugin allows an application to post the JUnit test results to an ElasticSearch
cluster.

The main prupose of this plugin is to read the `xml` test results files and post
the results for visualization on a Kibana Dashboard.

## Maven/Gradle
You can add this plugin as a dependency to your project through [JitPack](https://jitpack.io/#Ullink/gradle-test-export-plugin).

```
apply: plugin 'elastic'
```
- `Group id: com.ullink.gradle`
- `Artifact id: gradle-test-export-plugin`

## How to use it?
The plugin depends on the Gradle `Test` tasks. Therefore, it ensures that the `test`
task is up to date before executing. Once it executes, it reads the xml reports generated
by the JUnit runners and posts them to an elastic search cluster.

Settings for the cluster can be modified in the `build.gradle` file

```
elastic {
    ipAddress: 127.0.0.1
    port: 9300
    clusterName: elasticsearch
}
```

