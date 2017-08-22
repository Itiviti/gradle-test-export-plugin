package com.ullink.testtools.elastic

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test

class ElasticPlugin implements Plugin<Project> {
    @Override
    void apply(Project project) {
        ElasticExtension extension = project.extensions.create("elastic", ElasticExtension.class)

        ElasticTask task = project.task "elastic", type: ElasticTask.class, {
            conventionMapping.map 'port', { extension.port }
            conventionMapping.map 'clusterName', { extension.clusterName }
            conventionMapping.map 'ipAddress', { extension.ipAddress }
        }

        project.tasks.withType(Test.class).each { Test test ->
            test.ignoreFailures = true
            task.dependsOn test
        }
    }
}