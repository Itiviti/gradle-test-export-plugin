package com.ullink.testtools.elastic

class PropertiesLoader {

    private final Properties parameters = new Properties()

    PropertiesLoader(InputStream loadingClass) throws IOException {
        parameters.load(loadingClass)
    }

    Properties getParameters() {
        return parameters
    }
}
