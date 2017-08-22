package com.ullink.testtools.elastic.models

import org.gradle.api.tasks.testing.TestResult

class Result {
    String name
    String classname
    String errorMessage
    long executionTime
    String failureMessage
    String failureType
    TestResult.ResultType resultType
}