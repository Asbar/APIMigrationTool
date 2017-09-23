/*
 *
 *  Copyright (c) 2016, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * /
 */
package utils;

import java.util.Arrays;

/**
 * This class is hold all the configurations related to the tool and can access each through the
 * instance of the class
 */
class ApiImportExportConfiguration {

    private String apiName;
    private String apiVersion;
    private String apiProvider;
    private String username;
    private char[] password;
    private String apiFilePath;
    private String destinationPath;
    private String destinationFolderName;
    private String log4JFilePath;
    private String dcrUrl;
    private String gatewayUrl;
    private String publisherUrl;
    private String clientName;
    private String zipFile;
    private boolean updateApi;

    //subscription
    private String storeUrl;
   // private String subscriptionUrl;
    private String apiTier;
   // private String applicationUrl;
    private String applicationID;
    private String applicationName;
    private String throttlingTier;

    private static ApiImportExportConfiguration instance;

    static ApiImportExportConfiguration getInstance() {
        if (instance == null) {
            instance = new ApiImportExportConfiguration();
        }
        return instance;
    }

    void setApiName(String apiName) {
        this.apiName = apiName;
    }

    String getApiName() {
        return apiName;
    }

    void setApiVersion(String version) {
        this.apiVersion = version;
    }

    String getApiVersion() {
        return apiVersion;
    }

    void setApiProvider(String provider) {
        this.apiProvider = provider;
    }

    String getApiProvider() {
        return apiProvider;
    }

    void setApiFilePath(String path) {
        this.apiFilePath = path;
    }

    String getApiFilePath() {
        return apiFilePath;
    }

    void setDestinationPath(String path) {
        this.destinationPath = path;
    }

    String getDestinationPath() {
        return destinationPath;
    }

    void setDestinationFolderName(String name) {
        this.destinationFolderName = name;
    }

    String getDestinationFolderName() {
        return destinationFolderName;
    }

    void setLog4JFilePath(String path) {
        this.log4JFilePath = path;
    }

    String getLog4JFilePath() {
        return log4JFilePath;
    }

    void setDcrUrl(String url) {
        this.dcrUrl = url;
    }

    String getDcrUrl() {
        return dcrUrl;
    }

    void setGatewayUrl(String url) {
        this.gatewayUrl = url;
    }

    String getGatewayUrl() {
        return gatewayUrl;
    }

    void setPublisherUrl(String url) {
        this.publisherUrl = url;
    }

    String getPublisherUrl() {
        return publisherUrl;
    }

    void setClientName(String name) {
        this.clientName = name;
    }

    String getClientName() {
        return clientName;
    }

    void setUsername(String username) {
        this.username = username;
    }

    String getUsername() {
        return username;
    }

    void setPassword(char[] arr) {
        int size = arr.length;
        this.password = Arrays.copyOf(arr, size);
    }

    char[] getPassword() {
        return password;
    }

    void setZipFile(String path) {
        this.zipFile = path;
    }

    String getZipFile() {
        return zipFile;
    }

    void setUpdateApi(boolean value) {
        updateApi = value;
    }

    Boolean getUpdateApi() {
        return updateApi;
    }

    public String getAPITier() {
        return apiTier;
    }

    public void setAPITier(String tier) {
        this.apiTier = tier;
    }

    public String getApplicationID() {
        return applicationID;
    }

    public void setApplicationID(String applicationID) {
        this.applicationID = applicationID;
    }

    public String getApplicationName() {
        return applicationName;
    }

    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }

    public String getThrottlingTier() {
        return throttlingTier;
    }

    public void setThrottlingTier(String throttlingTier) {
        this.throttlingTier = throttlingTier;
    }


    public String getStoreUrl() {
        return storeUrl;
    }

    public void setStoreUrl(String storeUrl) {
        this.storeUrl = storeUrl;
    }


}
