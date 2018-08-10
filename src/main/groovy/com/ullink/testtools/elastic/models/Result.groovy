package com.ullink.testtools.elastic.models

import groovy.json.JsonSlurper
import org.gradle.api.tasks.testing.TestResult
import org.gradle.internal.impldep.com.fasterxml.jackson.databind.util.JSONPObject
import org.gradle.internal.impldep.com.google.gson.JsonObject
import java.nio.file.Path
import java.nio.file.Paths

import java.lang.reflect.Array

class Result {
    String name
    String classname
    float executionTime
    String failureMessage
    String failureType
    TestResult.ResultType resultType
    String timestamp
    String feature
    String filePath
    String projectName
    Map<String, ?> properties
    ArrayList steps
}