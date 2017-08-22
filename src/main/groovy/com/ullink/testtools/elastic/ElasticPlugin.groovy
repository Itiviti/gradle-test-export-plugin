package com.ullink.testtools.elastic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test

class ElasticPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {

        ElasticTask task = project.task "testExport", type: ElasticTask, {
            group = 'verification'
            description = 'Generates test reports and pushes them to elasticsearch cluster'
        }

        project.tasks.withType(Test.class).each { Test test ->
            test.ignoreFailures = true
            task.dependsOn test
        }
    }
}