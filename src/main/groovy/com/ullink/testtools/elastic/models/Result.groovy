package com.ullink.testtools.elastic.models

import org.gradle.api.tasks.testing.TestResult

class Result {
    String name
    String classname
    float executionTime
    String failureMessage
    String failureType
    String failureText
    TestResult.ResultType resultType
    String timestamp
    Map<String, ?> properties
}
