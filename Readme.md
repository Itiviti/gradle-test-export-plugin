[![Build Status](https://travis-ci.org/Itiviti/gradle-test-export-plugin.svg?branch=master)](https://travis-ci.org/Itiviti/gradle-test-export-plugin) [![](https://jitpack.io/v/Itiviti/gradle-test-export-plugin.svg)](https://jitpack.io/#Itiviti/gradle-test-export-plugin)


# Gradle Test Export Plugin
This plugin allows an application to post the JUnit test results to an ElasticSearch
cluster.

The main prupose of this plugin is to read the `xml` test results files and post
the results for visualization on a Kibana Dashboard.

## Maven/Gradle
You can add this plugin as a dependency to your project through [JitPack](https://jitpack.io/#Itiviti/gradle-test-export-plugin).

```
apply: plugin 'test-export'
```
- `Group id: com.ullink.gradle`
- `Artifact id: gradle-test-export-plugin`

## How to use it?
The plugin depends on the Gradle `Test` tasks. Therefore, it ensures that the `test`
task is up to date before executing. Once it executes, it reads the xml reports generated
by the JUnit runners and posts them to an elastic search cluster.

Settings for the cluster can be modified in the `build.gradle` file

```
testExport {
    host = '127.0.0.1'
    port = 9300
    clusterName = 'elasticsearch'
    properties = [
            any: "property"
        ]
    // properites = {
    //        return [
    //            any: "property"
    //        ]
    //    }
    type = 'testcase'
    targetDirectory = 'build/test-results'
}
```

### Parameters
| name | optional | default value | type|
|------|----------|----------------|-----|
|host| true | 127.0.0.1| string |
|port | true | 9300| int |
|clusterName | true | elasticsearch | string |
| properties | true |`null`| map / closure |
| type | true |`testcase`| string / closure |
| indexPrefix | true |`testresults-`| string |
| indexTimestampPattern | true |`yyyy-MM`| string |
| targetDirectory | true |build/test-results| string |

### Properties
Properties can take a closure or a map as input. The closure must return a map of _extra_ properties
that one might want to push to the elastic search.

### Type
Type is a string or a closure that returns string, it is the __type_ field in the ElasticSearch record.

## Final TestExport JSON
```
{
    "_index": "testresults-LOWER-CASED-SUITE-NAME-DATESTAMP",
    "_type": Type_Property_Field,
    "_id": TESTCASE_NAME_TIMESTAMP,
    "_score": 1,
    "_source": {
      "classname":  CLASS_NAME,
      "failureType": "",
      "executionTime": INT_MILLISECONDS,
      "failureMessage": "",
      "timestamp": TIMESTAMP,
      "name": TESTCASE_NAME,
      "properties": {
        "key": "value"
      },
      "resultType": FAILURE/SUCCESS/SKIPPED
    }
}
```




