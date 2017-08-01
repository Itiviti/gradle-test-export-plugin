/*************************************************************************
 * ULLINK CONFIDENTIAL INFORMATION
 * _______________________________
 *
 * All Rights Reserved.
 *
 * NOTICE: This file and its content are the property of Ullink. The
 * information included has been classified as Confidential and may
 * not be copied, modified, distributed, or otherwise disseminated, in
 * whole or part, without the express written permission of Ullink.
 ************************************************************************/
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