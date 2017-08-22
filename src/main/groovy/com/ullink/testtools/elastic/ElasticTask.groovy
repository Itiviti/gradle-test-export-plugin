package com.ullink.testtools.elastic

import org.gradle.api.DefaultTask

class ElasticTask extends DefaultTask {
    String port
    String clusterName
    String ipAddress
}