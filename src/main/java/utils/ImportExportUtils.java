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

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHeaders;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;
import org.codehaus.jackson.map.ObjectMapper;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import javax.ws.rs.core.Response;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.apache.log4j.Logger.getLogger;

/**
 * This class perform the util functions required for both import and export
 */

class ImportExportUtils {
    private static final Logger log = getLogger(ImportExportUtils.class);

    /**
     * Registering the clientApplication
     *
     * @param username user name of the logged in user
     * @param password password of the logged in user
     * @return encoded consumer credentials
     */
    public static String registerClient(String username, String password) throws UtilException {

        ApiImportExportConfiguration config = ApiImportExportConfiguration.getInstance();
        String userPwd = username + ":" + password;

        //Encoding user name and password to base 64
        byte[] encodedBytes = Base64.encodeBase64(userPwd.getBytes
                (Charset.forName(ImportExportConstants.CHARSET)));

        String encodedCredentials;
        try {
            encodedCredentials = new String(encodedBytes, ImportExportConstants.CHARSET);
            System.out.println("encodedCredintials "+encodedCredentials);
        } catch (UnsupportedEncodingException e) {
            String error = "Error occurred while encoding the user credentials";
            log.error(error, e);
            throw new UtilException(error, e);
        }
        //Payload for registering
        JSONObject jsonObject = new JSONObject();
        jsonObject.put(ImportExportConstants.CLIENT_NAME, config.getClientName());
        jsonObject.put(ImportExportConstants.OWNER, username);
        jsonObject.put(ImportExportConstants.GRANT_TYPE, ImportExportConstants.DEFAULT_GRANT_TYPE);
        jsonObject.put(ImportExportConstants.SAAS_APP, ImportExportConstants.DEFAULT_SAAS_APP);

        //REST API call for registering client
        CloseableHttpResponse response;
        CloseableHttpClient client = null;
        try {
            //token API URL
            String url = config.getDcrUrl();
            client = HttpClientGenerator.getHttpClient();
            HttpPost request = new HttpPost(url);
            request.setEntity(new StringEntity(jsonObject.toJSONString(), ImportExportConstants.CHARSET));
            request.setHeader(HttpHeaders.AUTHORIZATION,
                    ImportExportConstants.AUTHORIZATION_KEY_SEGMENT + encodedCredentials);
            request.setHeader(HttpHeaders.CONTENT_TYPE, ImportExportConstants.CONTENT_JSON);
            response = client.execute(request);

            if (response.getStatusLine().getStatusCode() == Response.Status.OK.getStatusCode()) {
                String jsonString = EntityUtils.toString(response.getEntity());
                JSONObject jsonObj = (JSONObject) new JSONParser().parse(jsonString);
                //Getting encoded consumer key and consumer secret
                String consumerCredentials = jsonObj.get(ImportExportConstants.CLIENT_ID) + ":" +
                        jsonObj.get(ImportExportConstants.CLIENT_SECRET);
                byte[] bytes = Base64.encodeBase64(consumerCredentials.getBytes
                        (Charset.defaultCharset()));
                return new String(bytes, ImportExportConstants.CHARSET);
            } else if (response.getStatusLine().getStatusCode() == Response.Status.UNAUTHORIZED.getStatusCode()) {
                String errorMsg = "invalid username or password entered ,cannot register the client";
                log.error(errorMsg);
                throw new UtilException(errorMsg);
            } else if (response.getStatusLine().getStatusCode() ==
                    Response.Status.FORBIDDEN.getStatusCode()) {
                String errorMsg = "Invalid client registration url, correct in configurations " +
                        "and retry again";
                log.error(errorMsg);
                throw new UtilException(errorMsg);
            } else {
                String errorMsg = "Error occurred while registering the client";
                log.error(errorMsg);
                throw new UtilException(errorMsg);
            }
        } catch (ParseException e) {
            String msg = "Error occurred while parsing registration response's entity to string, " +
                    "cannot extract consumer keys";
            log.error(msg, e);
            throw new UtilException(msg, e);
        } catch (IOException e) {
            String msg = "Error occurred while getting consumer key and consumer secret upon" +
                    " registration";
            log.error(msg, e);
            throw new UtilException(msg, e);
        } finally {
            IOUtils.closeQuietly(client);
        }
    }

    /**
     * Retrieve a access token with requested scope
     *
     * @param scope               required token scope
     * @param consumerCredentials encoded consumerKey and consumerSecret
     */

    static String getAccessToken(String scope, String consumerCredentials) throws UtilException {
        System.out.println("\nRetrieving Access Token...");
        ApiImportExportConfiguration config = ApiImportExportConfiguration.getInstance();
        //Mapping payload to a List
        List<NameValuePair> params = new ArrayList<>(4);
        params.add(new BasicNameValuePair(ImportExportConstants.TOKEN_GRANT_TYPE,
                ImportExportConstants.DEFAULT_GRANT_TYPE));
        params.add(new BasicNameValuePair(ImportExportConstants.USERNAME, config.getUsername()));
        params.add(new BasicNameValuePair(ImportExportConstants.DEFAULT_GRANT_TYPE,
                String.valueOf(config.getPassword())));
        params.add(new BasicNameValuePair(ImportExportConstants.SCOPE_CONSTANT, scope));

        //REST API call for get tokens
        CloseableHttpClient client = HttpClientGenerator.getHttpClient();
        String url = config.getGatewayUrl();
        try {
            HttpPost request = new HttpPost(url);
            request.setEntity(new UrlEncodedFormEntity(params, ImportExportConstants.CHARSET));
            request.setHeader(HttpHeaders.AUTHORIZATION,
                    ImportExportConstants.AUTHORIZATION_KEY_SEGMENT + consumerCredentials);
            CloseableHttpResponse response;
            response = client.execute(request);
            String responseString = EntityUtils.toString(response.getEntity());
            JSONObject jsonObj = (JSONObject) new JSONParser().parse(responseString);

            //Extract access token form the response string and return
            return ((String) jsonObj.get(ImportExportConstants.ACCESS_TOKEN));
        } catch (ParseException e) {
            String errorMsg = "Error occurred while extracting access token from the response";
            log.error(errorMsg, e);
            throw new UtilException(errorMsg, e);
        } catch (IOException e) {
            String errorMsg = "Error occurred while generating access tokens";
            log.error(errorMsg, e);
            throw new UtilException(errorMsg, e);
        } finally {
            IOUtils.closeQuietly(client);
        }
    }

    /**
     * Reading the configuration file sent as parameter and load in to the properties
     *
     * @param config file path to the configuration file
     */
    public static Properties readProperties(String config) throws UtilException {
        Properties prop = new Properties();
        InputStream input = null;
        try {
            if (config.equals(ImportExportConstants.DEFAULT_CONFIG_FILE)) {
                input = ImportExportUtils.class.getResourceAsStream(config);
            } else {
                File configurations = new File(config);
                input = new FileInputStream(configurations);
            }
            // Load the properties file
            prop.load(input);
        } catch (FileNotFoundException e) {
            String errorMsg = "Cannot find the configuration file,or does not exists " + config;
            log.error(errorMsg, e);
            throw new UtilException(errorMsg, e);
        } catch (IOException e) {
            String errorMsg = "error occurred while loading the properties from file " + config;
            log.error(errorMsg, e);
            throw new UtilException(errorMsg, e);
        } finally {
            IOUtils.closeQuietly(input);
        }
        return prop;
    }

    /**
     * Load the properties from default configuration file and map in to a ApiImportExportConfiguration
     * type object
     *
     * @param config instance of ApiImportExportConfiguration
     * @throws UtilException if failed to load default configurations
     */
    static void setDefaultConfigurations(ApiImportExportConfiguration config) throws UtilException {

        Properties prop = readProperties(ImportExportConstants.DEFAULT_CONFIG_FILE);

        //Save the values from configuration object to ApiImportExportConfiguration object
        config.setDestinationPath(System.getProperty(ImportExportConstants.USER_DIR));
        config.setDestinationFolderName(prop.getProperty(ImportExportConstants.DESTINATION_FOLDER));
        config.setDcrUrl(prop.getProperty(ImportExportConstants.REGISTRATION_URL));
        config.setPublisherUrl(prop.getProperty(ImportExportConstants.PUBLISHER_URL));
        config.setGatewayUrl(prop.getProperty(ImportExportConstants.GATEWAY_URL));
        config.setClientName(prop.getProperty(ImportExportConstants.CLIENT_NAME_PROPERTY));
        config.setLog4JFilePath(prop.getProperty(ImportExportConstants.LOG4J_FILE));
        config.setApiFilePath(prop.getProperty(ImportExportConstants.API_LIST_FILE));
        config.setUpdateApi(Boolean.parseBoolean(prop.getProperty(ImportExportConstants.UPDATE_API)));
        config.setStoreUrl(prop.getProperty(ImportExportConstants.APIM_STORE_URL));

    }

    /**
     * Loading the properties from user given configuration file and set into
     * ApiImportExportConfiguration object
     *
     * @param configFile user given config file
     * @param config     ApiImportExportConfiguration object
     */
    static void setUserConfigurations(String configFile, ApiImportExportConfiguration config) {
        Properties prop = null;
        try {
            prop = readProperties(configFile);
        } catch (UtilException e) {
            log.warn("Unable to find the user configuration file, may continue with default " +
                    "configurations");
        }
        if (prop != null) {
            if (StringUtils.isNotBlank(prop.getProperty(ImportExportConstants.CONFIG_API))) {
                config.setApiName(prop.getProperty(ImportExportConstants.CONFIG_API));
            }
            if (StringUtils.isNotBlank(prop.getProperty(ImportExportConstants.CONFIG_VERSION))) {
                config.setApiVersion(prop.getProperty(ImportExportConstants.CONFIG_VERSION));
            }
            if (StringUtils.isNotBlank(prop.getProperty(ImportExportConstants.CONFIG_PROVIDER))) {
                config.setApiProvider(prop.getProperty(ImportExportConstants.CONFIG_PROVIDER));
            }
            if (StringUtils.isNotBlank(prop.getProperty(ImportExportConstants.ZIP_DESTINATION))) {
                config.setDestinationPath(prop.getProperty(ImportExportConstants.ZIP_DESTINATION));
            }
            if (StringUtils.isNotBlank(prop.getProperty(ImportExportConstants.DESTINATION_FOLDER))) {
                config.setDestinationFolderName(prop.getProperty
                        (ImportExportConstants.DESTINATION_FOLDER));
            }
            if (StringUtils.isNotBlank(prop.getProperty(ImportExportConstants.REGISTRATION_URL))) {
                config.setDcrUrl(prop.getProperty(ImportExportConstants.REGISTRATION_URL));
            }
            if (StringUtils.isNotBlank(prop.getProperty(ImportExportConstants.PUBLISHER_URL))) {
                config.setPublisherUrl(prop.getProperty(ImportExportConstants.PUBLISHER_URL));
            }
            if (StringUtils.isNotBlank(prop.getProperty(ImportExportConstants.GATEWAY_URL))) {
                config.setGatewayUrl(prop.getProperty(ImportExportConstants.GATEWAY_URL));
            }
            if (StringUtils.isNotBlank(prop.getProperty(ImportExportConstants.CLIENT_NAME_PROPERTY))) {
                config.setClientName(prop.getProperty(ImportExportConstants.CLIENT_NAME_PROPERTY));
            }
            if (StringUtils.isNotBlank(prop.getProperty(ImportExportConstants.LOG4J_FILE))) {
                config.setLog4JFilePath(prop.getProperty(ImportExportConstants.LOG4J_FILE));
            }
            if (StringUtils.isNotBlank(prop.getProperty(ImportExportConstants.API_LIST_FILE))) {
                config.setApiFilePath(prop.getProperty(ImportExportConstants.API_LIST_FILE));
            }
            if (StringUtils.isNotBlank(prop.getProperty(ImportExportConstants.ZIP_FILE))) {
                config.setZipFile(prop.getProperty(ImportExportConstants.ZIP_FILE));
            }
            if (StringUtils.isNotBlank(prop.getProperty(ImportExportConstants.UPDATE_API))) {
                config.setUpdateApi(Boolean.parseBoolean(prop.getProperty
                        (ImportExportConstants.UPDATE_API)));
            }
            if (StringUtils.isNotBlank(prop.getProperty(ImportExportConstants.APIM_STORE_URL))) {
                config.setStoreUrl(prop.getProperty(ImportExportConstants.APIM_STORE_URL));
            }
        }
    }

    /**
     * Create directory at the given path
     *
     * @param path Path to create the directory
     */
    static void createDirectory(String path) throws UtilException {
        if (path != null) {
            //Creating the directory
            File file = new File(path);
            if (!file.exists() && !file.mkdirs()) {
                //Throws a exception if the directory is not created successfully
                String errorMessage = "Error while creating directory : " + path;
                log.error(errorMessage);
                throw new UtilException(errorMessage);
            }
        }
    }

    /**
     * Retrieve the value correspond to the requesting element from a json string
     *
     * @param responseString json string
     * @param key            key of the element
     * @return Value corresponds to the key or null
     */
    public static String readJsonValues(String responseString, String key) {
        String value = null;
        JSONParser parser = new JSONParser();
        try {
            JSONObject json = (JSONObject) parser.parse(responseString);
            //Check if json string contains required key
            if (json.containsKey(key)) {
                value = (String) json.get(key);
            }
        } catch (ParseException e) {
            log.error("Error occurred while parsing the json string to Json object", e);
        }
        return value;
    }

    /**
     * This method will pretty print a json string and set the formatted string back
     *
     * @param input unformatted json string
     * @return pretty printed json string
     */
    public static String formatJsonString(String input) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            Object json = mapper.readValue(input, Object.class);
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(json);
        } catch (IOException e) {
            log.warn("Error occurred while formatting json string", e);
            return input;
        }
    }

    /**
     * Validating the publisher url
     *
     * @param existingUrl current publisher url
     * @return true on valid, false on invalid url
     */
    public static boolean checkPublisherUrl(String existingUrl) {
        boolean value = false;
        //Getting swagger.json correspond to given url
        String url = existingUrl + ImportExportConstants.URL_SEPARATOR +
                ImportExportConstants.SWAGGER_JSON;
        CloseableHttpClient client = null;
        try {
            client = HttpClientGenerator.getHttpClient();
        } catch (UtilException e) {
            ////TODO client
            log.warn("Error occurred while getting closable http client for validating the" +
                    " publisher url", e);
        }
        HttpGet request = new HttpGet(url);
        CloseableHttpResponse response;
        String basePath;
        try {
            response = client.execute(request);
            HttpEntity entity = response.getEntity();
            if (isOfJson(entity)) {
                String responseString = EntityUtils.toString(entity);
                //Check if the base path is directed to publisher
                basePath = readJsonValues(responseString, ImportExportConstants.BASEPATH);
                if (basePath.indexOf(ImportExportConstants.PUBLISHER) > -1) {
                    value = true;
                }
            }
        } catch (IOException e) {
            log.error("Error occurred while getting url base path ", e);
        } finally {
            IOUtils.closeQuietly(client);
        }
        return value;
    }

    /**
     * Check if the content type of the resulted response is json/ not
     *
     * @param entity httpEntity of the response
     * @return true if json, false for any other content type
     */
    public static boolean isOfJson(HttpEntity entity) {
        ContentType contentType = ContentType.getOrDefault(entity);
        //Getting the mime type of content type extracted from the response entity
        String mimeType = contentType.getMimeType();
        if (mimeType.equalsIgnoreCase(ImportExportConstants.CONTENT_JSON)) {
            return true;
        }
        return false;
    }
}
