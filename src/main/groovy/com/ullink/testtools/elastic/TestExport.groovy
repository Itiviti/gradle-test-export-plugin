package com.ullink.testtools.elastic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test

class TestExport implements Plugin<Project> {
    @Override
    void apply(Project project) {

        TestExportTask task = project.task "testExport", type: TestExportTask, {
            group = 'verification'
            description = 'Generates test reports and pushes them to elasticsearch cluster'
        }

        project.tasks.withType(Test.class).each { Test test ->
            test.ignoreFailures = true
            task.dependsOn test
        }
    }
}