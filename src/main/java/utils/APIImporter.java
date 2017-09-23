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


import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * This class handles all the activities related to API import
 */
class APIImporter {
    private static final Log log = LogFactory.getLog(APIImporter.class);
    private static ApiImportExportConfiguration config = ApiImportExportConfiguration.getInstance();

    /**
     * This method handle importing of APIs
     *
     * @param zipFileLocation     path to the imported zip file
     * @param consumerCredentials encoded consumerKey and consumerSecret
     * @throws APIImportException if unable to export successfully
     */
    static void importAPIs(String zipFileLocation, String consumerCredentials) throws
            APIImportException, UtilException {
        //Obtaining access tokens
        String token = ImportExportUtils.getAccessToken
                (ImportExportConstants.IMPORT_SCOPE, consumerCredentials);

        System.out.println("Token " + token);
        //Create temporary directory withing user's current directory to extract the content from
        // imported folder
        String currentDirectory = System.getProperty(ImportExportConstants.USER_DIR);
        //Name the temporary directory with random name
        String tempDirectoryName = RandomStringUtils.randomAlphanumeric
                (ImportExportConstants.TEMP_FILENAME_LENGTH);
        String temporaryDirectory = currentDirectory + File.separator + tempDirectoryName;
        ImportExportUtils.createDirectory(temporaryDirectory);
        //unzipping the imported zip folder
        unzipFolder(zipFileLocation, temporaryDirectory);
        try {
            //Get the list of API folders inside the created temporary folder
            File[] files = new File(temporaryDirectory).listFiles();
            if (files != null) {
                //Publishing each api in imported folder
                for (File file : files) {
                    if (!file.getName().equalsIgnoreCase(ImportExportConstants.MEDIATION_FOLDER)) {
                        //Get all API folder except global mediation policy folder
                        String pathToApiDirectory = temporaryDirectory + File.separator + file.getName();
                        createAPI(pathToApiDirectory, token);
                    }
                }
            }
            //Delete the temporary directory after importing all the contained APIs
            FileUtils.deleteDirectory(new File(temporaryDirectory));
        } catch (IOException e) {
            log.warn("Error occurred while deleting temporary directory", e);
        }
    }

    /**
     * This method unzip the imported folder in to folder specified as outputFolder
     *
     * @param zipFile      zip file path
     * @param outputFolder folder to copy the zip content
     * @throws APIImportException if failed to unzip the folder content
     */
    private static void unzipFolder(String zipFile, String outputFolder) throws APIImportException {

        byte[] buffer = new byte[ImportExportConstants.defaultBufferSize];

        //Create output directory if not exists
        File folder = new File(outputFolder);
        if (!folder.exists()) {
            folder.mkdir();
        }
        //Get the zip file content
        ////TODO close input streams
        ZipInputStream zis;
        try {
            zis = new ZipInputStream(new FileInputStream(zipFile));
        } catch (FileNotFoundException e) {
            String errorMsg = "cannot find the zip file to unzip";
            log.error(errorMsg, e);
            throw new APIImportException(errorMsg, e);
        }
        //Get the zipped file list entry
        ZipEntry ze;
        FileOutputStream fos = null;
        try {
            ze = zis.getNextEntry();
        } catch (IOException e) {
            String errorMsg = "error occurred while getting the zip file entries";
            log.error(errorMsg, e);
            throw new APIImportException(errorMsg, e);
        }
        while (ze != null) {
            //Get the files inside zip entry, if it is a directory
            if (ze.isDirectory()) {
                try {
                    ze = zis.getNextEntry();
                } catch (IOException e) {
                    String errorMsg = "error occurred while getting the zip entries";
                    log.error(errorMsg, e);
                    throw new APIImportException(errorMsg, e);
                }
                continue;
            }
            //If not a directory
            String fileName = ze.getName();
            File newFile = new File(outputFolder + File.separator + fileName);

            //create all non exists folders
            //else you will hit FileNotFoundException for compressed folder
            ////todo TEMP directory
            new File(newFile.getParent()).mkdirs();
            try {
                fos = new FileOutputStream(newFile);
            } catch (FileNotFoundException e) {
                log.error("File " + fileName + " not created succussfully ", e);
            }
            int len;
            try {
                while ((len = zis.read(buffer)) > 0) {
                    if (fos != null) {
                        fos.write(buffer, 0, len);
                    }
                }
            } catch (IOException e) {
                log.error("Error occurred while writing file content to the temporary " +
                        "directory", e);
            }

            try {
                ze = zis.getNextEntry();
            } catch (IOException e) {
                String errorMsg = "Error occurred while continuing the while getting all the " +
                        "zip entries";
                log.error(errorMsg, e);
                throw new APIImportException(errorMsg, e);
            }
        }
        try {
            zis.closeEntry();
        } catch (IOException e) {
            log.error("Error occurred while closing the zip entry ", e);
        } finally {
            IOUtils.closeQuietly(fos);
            IOUtils.closeQuietly(zis);
        }
    }

    /**
     * Creating each API in the imported zip file
     *
     * @param apiFolder path to the API folder withing imported folder
     * @param token     access token
     */
    private static void createAPI(String apiFolder, String token) {
        try {
            //Getting API definition (api.json) of the imported API
            String pathToApiJson = apiFolder + ImportExportConstants.JSON_FILE_LOCATION;
            String apiDefinition = FileUtils.readFileToString(new File(pathToApiJson));
            String apiName = ImportExportUtils.readJsonValues(apiDefinition,
                    ImportExportConstants.API_NAME);

            //Creating the API
            String url = config.getPublisherUrl() + ImportExportConstants.APIS_URL;
            CloseableHttpClient client;
            try {
                client = HttpClientGenerator.getHttpClient();
            } catch (UtilException e) {
                String errorMsg = "Error occurred while getting a closableHttpClient for creating" +
                        " the API";
                log.error(errorMsg, e);
                return;
            }
            HttpPost request = new HttpPost(url);
            request.setEntity(new StringEntity(apiDefinition, ImportExportConstants.CHARSET));
            request.setHeader(HttpHeaders.AUTHORIZATION, ImportExportConstants.CONSUMER_KEY_SEGMENT
                    + token);
            request.setHeader(HttpHeaders.CONTENT_TYPE, ImportExportConstants.CONTENT_JSON);
            CloseableHttpResponse response = client.execute(request);
            if (response.getStatusLine().getStatusCode() == Response.Status.CONFLICT.getStatusCode()) {
                //If API already exists perform update, if enabled
                if (config.getUpdateApi()) {
                    updateApi(apiDefinition, token, apiFolder);
                } else {
                    //If update disabled
                    log.info("API " + apiName + " already exists. ");
                }
            } else if (response.getStatusLine().getStatusCode() ==
                    Response.Status.CREATED.getStatusCode()) {
                System.out.println("creating API " + apiName);
                //Getting uuid of created API
                HttpEntity entity = response.getEntity();
                String responseString = EntityUtils.toString(entity, ImportExportConstants.CHARSET);
                String uuid = ImportExportUtils.readJsonValues(responseString,
                        ImportExportConstants.UUID);
                if (StringUtils.isNotBlank(uuid)) {
                    //Importing API thumbnail
                    if (StringUtils.isNotBlank(ImportExportUtils.readJsonValues(apiDefinition,
                            ImportExportConstants.THUMBNAIL))) {
                        addAPIImage(apiFolder, token, uuid);
                    }

                    File folder = new File(apiFolder);
                    File[] fileList = folder.listFiles();
                    for (File file : fileList) {
                        //Check for the document directory
                        if (file.getName().equalsIgnoreCase(ImportExportConstants.DOCUMENT_DIRECTORY)) {
                            //Adding API documentations
                            addAPIDocuments(apiFolder, token, uuid);
                        } else if (file.getName().equalsIgnoreCase(ImportExportConstants.WSDL_FILE_NAME)) {
                            //Add the exported wsdl to the API
                            addWsdl(apiFolder, uuid, token);
                        }
                    }
                    JSONParser parser = new JSONParser();
                    JSONObject jsonObj = (JSONObject) parser.parse(apiDefinition);
                    if (jsonObj.containsKey(ImportExportConstants.SEQUENCES_ELEM)) {
                        org.json.simple.JSONArray arr = (org.json.simple.JSONArray) jsonObj.
                                get(ImportExportConstants.SEQUENCES_ELEM);
                        if (arr.size() > 0) {
                            //Adding mediation policies
                            addMediationPolicies(arr, apiFolder, token, uuid);
                        }
                    }
                    System.out.println("API " + apiName + " imported successfully");
                }

            } else if (response.getStatusLine().getStatusCode() ==
                    Response.Status.FORBIDDEN.getStatusCode()) {
                log.error("cannot create different APIs with duplicate context exists");
            } else if (response.getStatusLine().getStatusCode() ==
                    Response.Status.BAD_REQUEST.getStatusCode()) {
                log.error(EntityUtils.toString(response.getEntity()));
            } else if (response.getStatusLine().getStatusCode() ==
                    Response.Status.UNAUTHORIZED.getStatusCode()) {
                log.error("Unauthorized request, You cannot create the API since scope validation " +
                        "failed");
            } else {
                log.error("Error occurred while creating the API " + apiName);
            }
        } catch (IOException e) {
            log.error("Error occurred while creating the API ", e);
        } catch (ParseException e) {
            log.error("Error occurred on parsing api definition in to json object ", e);
        }
    }

    /**
     * Upload the wsdl of created API, if there any
     *
     * @param apiFolder path to the imported API folder location
     * @param uuid      API uuid
     * @param token     access token with exporting scopes
     */
    private static void addWsdl(String apiFolder, String uuid, String token) {

        // todo wsdl put
        CloseableHttpClient client = null;
        try {
            String url = config.getPublisherUrl() + ImportExportConstants.APIS + uuid +
                    ImportExportConstants.WSDL_SEG;
            client = HttpClientGenerator.getHttpClient();
            HttpPost request = new HttpPost(url);
            String wsdlFilePath = apiFolder.concat(File.separator + ImportExportConstants.WSDL_FILE_NAME);
            //Getting the content of wsdl file
            byte[] encoded = Files.readAllBytes(Paths.get(wsdlFilePath));
            String content = new String(encoded, ImportExportConstants.CHARSET);
            MultipartEntityBuilder multipartEntityBuilder = MultipartEntityBuilder.create();
            multipartEntityBuilder.addTextBody(ImportExportConstants.MULTIPART_WSDL_CONTENT,
                    content, ContentType.TEXT_XML);
            HttpEntity entity = multipartEntityBuilder.build();
            request.setEntity(entity);
            request.setHeader(HttpHeaders.AUTHORIZATION, ImportExportConstants.CONSUMER_KEY_SEGMENT + token);
            client.execute(request);
        } catch (UtilException e) {
            log.error("Error occurred while getting a http client for wsdl upload in api " + uuid, e);
        } catch (IOException e) {
            log.error("Error while uploading the wsdl of the API " + uuid, e);
        } finally {
            IOUtils.closeQuietly(client);
        }
    }

    /**
     * Upload mediation policy to the created API
     *
     * @param array     array of mediation policies
     * @param apiFolder path to the imported API folder loaction
     * @param token     access token with importing scopes
     * @param apiUuid   API uuid
     */
    static void addMediationPolicies(JSONArray array, String apiFolder, String token, String apiUuid) {
        for (Object item : array) {
            JSONObject mediationPolicy = (JSONObject) item;
            if (!(boolean) mediationPolicy.get(ImportExportConstants.SHARED_STATUS)) {
                //Shared status= false : API specific mediation policy
                addApiSpecificMediation(config, mediationPolicy, apiFolder, token, apiUuid);
            } else {
                File api = new File(apiFolder);
                String mediationPolicyDirectory = api.getParent() + File.separator +
                        ImportExportConstants.MEDIATION_FOLDER;
                addGlobalMediationPolicies(config, mediationPolicy, mediationPolicyDirectory, token);
            }
        }
    }

    /**
     * Upload API specific mediation policy to the created API, if there is any
     *
     * @param config          ApiImportExportConfiguration instance
     * @param mediationPolicy mediation policy object
     * @param apiFolder       path to the imported API folder
     * @param token           access token with import scopes
     * @param uuid            API uuid
     */
    private static void addApiSpecificMediation(ApiImportExportConfiguration config,
                                                JSONObject mediationPolicy, String apiFolder,
                                                String token, String uuid) {
        //todo method for core
        Path mediationFolder = Paths.get(apiFolder + File.separator + ImportExportConstants.MEDIATION_FOLDER);
        if (Files.exists(mediationFolder)) {
            String mediationDirection = (String) mediationPolicy.get(ImportExportConstants.TYPE_ELEM);
            Path directionFolderPath = Paths.get(mediationFolder + File.separator + mediationDirection);
            if (Files.exists(directionFolderPath)) {
                Path mediationFilePath = Paths.get(directionFolderPath + File.separator + mediationPolicy.get
                        (ImportExportConstants.NAME_ELEM));
                if (Files.exists(mediationFilePath)) {
                    CloseableHttpClient httpClient = null;
                    try {
                        //Getting content of the mediation policy
                        byte[] encoded = Files.readAllBytes(Paths.get(String.valueOf(mediationFilePath)));
                        String content = new String(encoded, ImportExportConstants.CHARSET);
                        //Generating payload for mediation upload
                        JSONObject obj = new JSONObject();
                        obj.put("name", mediationPolicy.get(ImportExportConstants.NAME_ELEM));
                        obj.put("type", mediationPolicy.get(ImportExportConstants.TYPE_ELEM));
                        obj.put("config", content);

                        String url = config.getPublisherUrl() + ImportExportConstants.APIS + uuid + ImportExportConstants.MEDIATION_SEG;
                        httpClient = HttpClientGenerator.getHttpClient();
                        HttpPost request = new HttpPost(url);
                        request.setHeader(HttpHeaders.AUTHORIZATION, ImportExportConstants.CONSUMER_KEY_SEGMENT + token);
                        request.setHeader(HttpHeaders.CONTENT_TYPE, ImportExportConstants.CONTENT_JSON);
                        request.setEntity(new StringEntity(obj.toJSONString(), ImportExportConstants.CHARSET));
                        CloseableHttpResponse response = httpClient.execute(request);
                        if (response.getStatusLine().getStatusCode() == Response.Status.CONFLICT.getStatusCode()) {
                            if (config.getUpdateApi()) {
                                //If mediation policy already exists and update enable, update the
                                //existing mediation policy
                                updateApiSpecificMediation(config, obj, token, uuid);
                            }
                        }
                    } catch (IOException e) {
                        //todo error report
                        log.error("Error occurred while reading the content of mediation policy" +
                                mediationPolicy.get("name"));
                    } catch (UtilException e) {
                        log.error("Error occurred while getting http client for import global " +
                                "mediation sequence " + mediationPolicy.get(ImportExportConstants.NAME_ELEM));
                    } finally {
                        IOUtils.closeQuietly(httpClient);
                    }
                }
            }
        } else {
            log.error("Global mediation policy folder not found, cannot add global mediation");
        }
    }

    /**
     * Upload global mediation policy
     *
     * @param config                   ApiImportExportConfiguration instance
     * @param mediationPolicy          Mediation object
     * @param mediationPolicyDirectory path to the global mediation policy directory inside imported
     *                                 folder
     * @param token                    access token with importing scopes
     */
    private static void addGlobalMediationPolicies(ApiImportExportConfiguration config, JSONObject mediationPolicy,
                                                   String mediationPolicyDirectory, String token) {
        Path mediationFolderPath = Paths.get(mediationPolicyDirectory);
        if (Files.exists(mediationFolderPath)) {
            //Mediation direction : in/out/fault
            String mediationDirection = (String) mediationPolicy.get(ImportExportConstants.TYPE_ELEM);
            Path directionFolderPath = Paths.get(mediationPolicyDirectory +
                    File.separator + mediationDirection);
            if (Files.exists(directionFolderPath)) {
                //Path to the mediation policy file
                Path mediationFilePath = Paths.get(directionFolderPath + File.separator +
                        mediationPolicy.get(ImportExportConstants.NAME_ELEM));
                if (Files.exists(mediationFilePath)) {
                    CloseableHttpClient httpClient = null;
                    try {
                        //Extracting mediation policy content
                        byte[] encoded = Files.readAllBytes(Paths.get(String.valueOf(mediationFilePath)));
                        String content = new String(encoded, ImportExportConstants.CHARSET);
                        //Generating payload for update
                        JSONObject obj = new JSONObject();
                        obj.put("name", mediationPolicy.get(ImportExportConstants.NAME_ELEM));
                        obj.put("type", mediationPolicy.get(ImportExportConstants.TYPE_ELEM));
                        obj.put("config", content);

                        String url = config.getPublisherUrl() + ImportExportConstants.MEDIATION_SEG;
                        httpClient = HttpClientGenerator.getHttpClient();
                        HttpPost request = new HttpPost(url);
                        request.setHeader(HttpHeaders.AUTHORIZATION,
                                ImportExportConstants.CONSUMER_KEY_SEGMENT + token);
                        request.setHeader(HttpHeaders.CONTENT_TYPE, ImportExportConstants.CONTENT_JSON);
                        request.setEntity(new StringEntity(obj.toJSONString(),
                                ImportExportConstants.CHARSET));
                        CloseableHttpResponse response = httpClient.execute(request);
                        if (response.getStatusLine().getStatusCode() ==
                                Response.Status.CONFLICT.getStatusCode()) {
                            //If the mediation policy already exists and update enabled, update the
                            // existing mediation policy
                            if (config.getUpdateApi()) {
                                updateGlobalMediationSequence(config, obj, token);
                            }
                        }
                    } catch (IOException e) {
                        log.error("Error occurred while reading the content of mediation policy" +
                                mediationPolicy.get(ImportExportConstants.NAME_ELEM));
                    } catch (UtilException e) {
                        log.error("Error occurred while getting http client for import global " +
                                "mediation sequence " + mediationPolicy.get(ImportExportConstants.NAME_ELEM));
                    } finally {
                        IOUtils.closeQuietly(httpClient);
                    }
                }
            }
        } else {
            log.error("Global mediation policy folder not found, cannot add global mediation");

        }
    }

    /**
     * Update a already existing global mediation policy
     *
     * @param config    ApiImportExportConfiguration instance
     * @param mediation mediation json object
     * @param token     access token with importing scopes
     */
    private static void updateGlobalMediationSequence(ApiImportExportConfiguration config,
                                                      JSONObject mediation, String token) {
        //Get all mediation policies to find the corresponding uuid
        String url = config.getPublisherUrl() + ImportExportConstants.MEDIATION_SEG;
        //Getting uuid of the existing mediation policy
        String uuid = getMediationPolicyUuid(url, token, mediation);
        //Updating the mediation policy specify by the uuid
        CloseableHttpClient client = null;
        try {
            url = url.concat(ImportExportConstants.URL_SEPARATOR + uuid);
            client = HttpClientGenerator.getHttpClient();
            HttpPut request = new HttpPut(url);
            request.setHeader(HttpHeaders.AUTHORIZATION, ImportExportConstants.CONSUMER_KEY_SEGMENT +
                    token);
            request.setHeader(HttpHeaders.CONTENT_TYPE, ImportExportConstants.CONTENT_JSON);
            request.setEntity(new StringEntity(mediation.toJSONString(), ImportExportConstants.CHARSET));
            client.execute(request);
        } catch (UtilException e) {
            log.error("error occurred while getting a ClosableHttpClient,on updating" +
                    " mediation policy " + mediation.get(ImportExportConstants.NAME_ELEM), e);
        } catch (IOException e) {
            log.error("Error while updating the existing mediation policy", e);
        } finally {
            IOUtils.closeQuietly(client);
        }
    }

    /**
     * Update a already existing API specific mediation policy
     *
     * @param config  ApiImportExportConfiguration instance
     * @param payload mediation object
     * @param token   access token with import scopes
     * @param apiUuid API uuid
     */
    private static void updateApiSpecificMediation(ApiImportExportConfiguration config,
                                                   JSONObject payload, String token, String apiUuid) {

        String url = config.getPublisherUrl() + ImportExportConstants.APIS + apiUuid +
                ImportExportConstants.MEDIATION_SEG;
        //Get the uuid of existing mediation policy
        String uuid = getMediationPolicyUuid(url, token, payload);

        //Updating the mediation policy specify by the uuid
        CloseableHttpClient client = null;
        try {
            url = url.concat(ImportExportConstants.URL_SEPARATOR + uuid);
            client = HttpClientGenerator.getHttpClient();
            HttpPut request = new HttpPut(url);
            request.setHeader(HttpHeaders.AUTHORIZATION, ImportExportConstants.CONSUMER_KEY_SEGMENT + token);
            request.setHeader(HttpHeaders.CONTENT_TYPE, ImportExportConstants.CONTENT_JSON);
            request.setEntity(new StringEntity(payload.toJSONString(), ImportExportConstants.CHARSET));
            client.execute(request);
        } catch (UtilException e) {
            log.error("error occurred while getting a ClosableHttpClient,on updating" +
                    " mediation policy " + payload.get(ImportExportConstants.NAME_ELEM), e);
        } catch (IOException e) {
            log.error("Error occurred on updating existing mediation policy ", e);
        } finally {
            IOUtils.closeQuietly(client);
        }
    }

    static String getMediationPolicyUuid(String url, String token, JSONObject obj) {
        String uuid = null;
        try {
            CloseableHttpClient client = HttpClientGenerator.getHttpClient();
            HttpGet request = new HttpGet(url);
            request.setHeader(HttpHeaders.AUTHORIZATION, ImportExportConstants.CONSUMER_KEY_SEGMENT + token);
            CloseableHttpResponse response = client.execute(request);
            String responseString = EntityUtils.toString(response.getEntity(), ImportExportConstants.CHARSET);
            JSONParser parser = new JSONParser();
            JSONObject jsonObj = (JSONObject) parser.parse(responseString);
            JSONArray array = (JSONArray) jsonObj.get(ImportExportConstants.MEDIATION_LIST);
            for (Object api : array) {
                JSONObject mediationObj = (JSONObject) api;
                if (mediationObj.get(ImportExportConstants.NAME_ELEM).equals
                        (obj.get(ImportExportConstants.NAME_ELEM))) {
                    uuid = (String) mediationObj.get(ImportExportConstants.UUID);
                    break;
                }
            }
        } catch (UtilException e) {
            log.error("Error occurred while getting a ClosableHttpClient, on get all mediation " +
                    "policies", e);
        } catch (IOException e) {
            log.error("Error occurred while getting all global mediation policies", e);
        } catch (ParseException e) {
            log.error("Error occurred while parsing get mediation response in to json", e);
        }
        return uuid;
    }

    /**
     * Updated an existing API
     *
     * @param payload    payload to update the API
     * @param token      access token
     * @param folderPath folder path to the imported API folder
     */
    private static void updateApi(String payload, String token,
                                  String folderPath) {
        String apiName = ImportExportUtils.readJsonValues(payload, ImportExportConstants.API_NAME);
        String version = ImportExportUtils.readJsonValues(payload, ImportExportConstants.API_VERSION);
        String identifier = apiName + "-" + version;

        //getting uuid of the existing API
        String uuid = null;
        String httpUrl = config.getPublisherUrl() + ImportExportConstants.APIS_URL + "?query=name:" +
                apiName;
        CloseableHttpClient httpClient;
        try {
            httpClient = HttpClientGenerator.getHttpClient();
        } catch (UtilException e) {
            String errorMsg = "Error occurred while getting a closableHttpClient on getting uuid " +
                    "of existing API";
            log.error(errorMsg, e);
            return;
        }
        HttpGet httpRequest = new HttpGet(httpUrl);
        httpRequest.setHeader(HttpHeaders.AUTHORIZATION, ImportExportConstants.CONSUMER_KEY_SEGMENT
                + token);
        try {
            CloseableHttpResponse httpResponse = httpClient.execute(httpRequest);
            if (httpResponse.getStatusLine().getStatusCode() == Response.Status.OK.getStatusCode()) {
                String responseString = EntityUtils.toString(httpResponse.getEntity());
                JSONParser parser = new JSONParser();
                JSONObject jsonObj = (JSONObject) parser.parse(responseString);
                JSONArray array = (JSONArray) jsonObj.get(ImportExportConstants.DOC_LIST);
                for (Object api : array) {
                    JSONObject obj = (JSONObject) api;
                    if (obj.get(ImportExportConstants.API_VERSION).equals(version)) {
                        uuid = (String) obj.get(ImportExportConstants.UUID);
                        break;
                    }
                }
            }
        } catch (IOException e) {
            System.out.println("error in getting all the apis");
        } catch (ParseException e) {
            System.out.println("error in simplifying response string");
        }

        JSONParser parser = new JSONParser();
        try {
            JSONObject jsonObj = (JSONObject) parser.parse(payload);
            if (jsonObj.containsKey(ImportExportConstants.SEQUENCES_ELEM)) {
                org.json.simple.JSONArray arr = (org.json.simple.JSONArray) jsonObj.
                        get(ImportExportConstants.SEQUENCES_ELEM);
                if (arr.size() > 0) {
                    //Updating mediation policies
                    addMediationPolicies(arr, folderPath, token, uuid);
                }
            }
        } catch (ParseException e) {
            log.error("Error while parsing payload to json object during API update", e);
        }
        //updating API
        String url = config.getPublisherUrl() + ImportExportConstants.APIS + uuid;
        CloseableHttpClient client;
        try {
            client = HttpClientGenerator.getHttpClient();
        } catch (UtilException e) {
            String errorMsg = "Error occurred while getting a closableHttpClient for updating " +
                    "the existing API ";
            log.error(errorMsg, e);
            return;
        }
        HttpPut request = new HttpPut(url);
        request.setEntity(new StringEntity(payload, ImportExportConstants.CHARSET));
        request.setHeader(HttpHeaders.AUTHORIZATION, ImportExportConstants.CONSUMER_KEY_SEGMENT +
                token);
        request.setHeader(HttpHeaders.CONTENT_TYPE, ImportExportConstants.CONTENT_JSON);
        try {
            CloseableHttpResponse response = client.execute(request);
            if (response.getStatusLine().getStatusCode() == Response.Status.OK.getStatusCode()) {

                //updating API documents
                updateAPIDocumentation(uuid, identifier, token, folderPath);

                //adding API thumbnail
                addAPIImage(folderPath, token, uuid);

                //adding wsdl
                updateWsdl(folderPath, uuid, token);

                System.out.println("API " + identifier + " updated successfully");
            } else if (response.getStatusLine().getStatusCode() ==
                    Response.Status.NOT_FOUND.getStatusCode()) {
                String status = "API " + identifier + " not found/ does not exists";
                log.error(status);
                return;
            } else {
                String status = "Updating API " + identifier + " unsuccessful";
                log.error(status);
                return;
            }
        } catch (IOException e) {
            String errorMsg = "Error occurred while updating, API " + apiName;
            log.error(errorMsg, e);
        }
    }

    private static void updateWsdl(String folderPath, String uuid, String token) {

        CloseableHttpClient client = null;
        try {
            String url = config.getPublisherUrl() + ImportExportConstants.APIS + uuid +
                    ImportExportConstants.WSDL_SEG;
            client = HttpClientGenerator.getHttpClient();
            HttpPut request = new HttpPut(url);
            String wsdlFilePath = folderPath.concat(File.separator + ImportExportConstants.WSDL_FILE_NAME);
            //Getting the content of wsdl file
            byte[] encoded = Files.readAllBytes(Paths.get(wsdlFilePath));
            String content = new String(encoded, ImportExportConstants.CHARSET);
            MultipartEntityBuilder multipartEntityBuilder = MultipartEntityBuilder.create();
            multipartEntityBuilder.addTextBody(ImportExportConstants.MULTIPART_WSDL_CONTENT,
                    content, ContentType.TEXT_XML);
            HttpEntity entity = multipartEntityBuilder.build();
            request.setEntity(entity);
            request.setHeader(HttpHeaders.AUTHORIZATION, ImportExportConstants.CONSUMER_KEY_SEGMENT + token);
            client.execute(request);
        } catch (UtilException e) {
            log.error("Error occurred while getting a http client for wsdl upload in api " + uuid, e);
        } catch (IOException e) {
            log.error("Error while uploading the wsdl of the API " + uuid, e);
        } finally {
            IOUtils.closeQuietly(client);
        }
    }

    /**
     * Posting API thumbnail
     *
     * @param folderPath  imported folder location
     * @param accessToken access token with importing scopes
     * @param uuid        API uuid
     */
    private static void addAPIImage(String folderPath, String accessToken, String uuid) {
        File apiFolder = new File(folderPath);
        File[] fileArray = apiFolder.listFiles();
        if (fileArray != null) {
            for (File file : fileArray) {
                //Finding the file with name 'icon'
                String fileName = file.getName();
                if (fileName.contains(ImportExportConstants.IMG_NAME)) {
                    File imageFile = new File(folderPath + ImportExportConstants.ZIP_FILE_SEPARATOR +
                            fileName);
                    //Converting image in to multipart entity
                    MultipartEntityBuilder multipartEntityBuilder = MultipartEntityBuilder.create();
                    multipartEntityBuilder.addBinaryBody(ImportExportConstants.MULTIPART_FILE,
                            imageFile);
                    HttpEntity entity = multipartEntityBuilder.build();
                    String url = config.getPublisherUrl() + ImportExportConstants.APIS + uuid +
                            ImportExportConstants.THUMBNAIL_SEG;
                    try {
                        CloseableHttpClient client = HttpClientGenerator.getHttpClient();
                        HttpPost request = new HttpPost(url);
                        request.setHeader(HttpHeaders.AUTHORIZATION,
                                ImportExportConstants.CONSUMER_KEY_SEGMENT + accessToken);
                        request.setEntity(entity);
                        client.execute(request);
                    } catch (UtilException e) {
                        log.warn("Error occurred while getting ClosableHttpClient for importing " +
                                "API thumbnail", e);
                    } catch (IOException e) {
                        log.error("Error occurred while uploading the API thumbnail", e);
                    }
                    break;
                }
            }
        }
    }

    /**
     * Adding API documents to the created API
     *
     * @param folderPath  folder path for imported API folder
     * @param accessToken access token
     * @param uuid        uuid of the created API
     */
    private static void addAPIDocuments(String folderPath, String accessToken, String uuid) {
        String docSummaryLocation = folderPath + ImportExportConstants.DOCUMENT_FILE_LOCATION;
        CloseableHttpClient client = null;
        try {
            //Getting the document list from imported folder
            String jsonContent = FileUtils.readFileToString(new File(docSummaryLocation));
            JSONParser parser = new JSONParser();
            JSONObject jsonObject = (JSONObject) parser.parse(jsonContent);
            JSONArray array = (JSONArray) jsonObject.get(ImportExportConstants.DOC_LIST);
            if (array.size() == 0) {
                log.warn("Imported API doesn't have any documents to be publish");
            } else {
                for (Object documentArray : array) {
                    JSONObject document = (JSONObject) documentArray;
                    //Publishing each document
                    String url = config.getPublisherUrl() + ImportExportConstants.APIS + uuid +
                            ImportExportConstants.DOCUMENT_SEG;
                    client = HttpClientGenerator.getHttpClient();
                    HttpPost request = new HttpPost(url);
                    request.setEntity(new StringEntity(document.toString(), ImportExportConstants.CHARSET));
                    request.setHeader(HttpHeaders.AUTHORIZATION,
                            ImportExportConstants.CONSUMER_KEY_SEGMENT + accessToken);
                    request.setHeader(HttpHeaders.CONTENT_TYPE, ImportExportConstants.CONTENT_JSON);
                    CloseableHttpResponse response = client.execute(request);
                    if (response.getStatusLine().getStatusCode() ==
                            Response.Status.CREATED.getStatusCode()) {
                        //If document created successfully withing created API
                        String responseString = EntityUtils.toString(response.getEntity(),
                                ImportExportConstants.CHARSET);
                        //Getting the source type of the created document (inline/file/url)
                        String sourceType = document.get(ImportExportConstants.SOURCE_TYPE).toString();
                        if (sourceType.equalsIgnoreCase(ImportExportConstants.FILE_DOC_TYPE) ||
                                sourceType.equalsIgnoreCase(ImportExportConstants.INLINE_DOC_TYPE)) {
                            try {
                                //Adding content of the inline and file type documents
                                addDocumentContent(folderPath, uuid, responseString, accessToken);
                            } catch (UtilException e) {
                                log.error("Error occurred while updating the content of document " +
                                        document.get(ImportExportConstants.DOC_NAME), e);
                            }
                        }
                    } else {
                        log.warn("Error occurred while importing the API document " +
                                document.get(ImportExportConstants.DOC_NAME));
                    }
                }
            }
        } catch (IOException e) {
            log.error("error occurred while importing the API documents", e);
        } catch (ParseException e) {
            log.error("error occurred getting the document list from the imported folder", e);
        } catch (UtilException e) {
            log.warn("Error occurred while getting ClosableHttpClient for " +
                    "import API Documents", e);
        } finally {
            IOUtils.closeQuietly(client);
        }
    }

    /**
     * Update the content of a document
     *
     * @param folderPath  folder path to the imported API folder
     * @param uuid        uuid of the API
     * @param response    payload for the publishing document
     * @param accessToken access token
     */
    private static void addDocumentContent(String folderPath, String uuid, String response,
                                           String accessToken) throws UtilException {
        //Get uuid of the created document
        String documentId = ImportExportUtils.readJsonValues(response, ImportExportConstants.DOC_ID);
        //Get source type of the created document
        String sourceType = ImportExportUtils.readJsonValues(response,
                ImportExportConstants.SOURCE_TYPE);
        //Get the name of the created document
        String documentName = ImportExportUtils.readJsonValues(response,
                ImportExportConstants.DOC_NAME);
        String directoryName;
        //Setting directory name depending on the document source type
        //TODO chnge order
        if (sourceType.equals(ImportExportConstants.INLINE_DOC_TYPE)) {
            directoryName = ImportExportConstants.INLINE_DOCUMENT_DIRECTORY;
        } else {
            directoryName = ImportExportConstants.FILE_DOCUMENT_DIRECTORY;
        }
        //Getting document content from the imported folder
        String documentContentPath = folderPath + ImportExportConstants.DIRECTORY_SEPARATOR +
                ImportExportConstants.DOCUMENT_DIRECTORY + ImportExportConstants.DIRECTORY_SEPARATOR
                + directoryName + ImportExportConstants.DIRECTORY_SEPARATOR + documentName;
        File content = new File(documentContentPath);
        HttpEntity entity;
        if (sourceType.equals(ImportExportConstants.FILE_DOC_TYPE)) {
            //Setting the  file type content to http entity
            MultipartEntityBuilder multipartEntityBuilder = MultipartEntityBuilder.create();
            multipartEntityBuilder.addBinaryBody(ImportExportConstants.MULTIPART_FILE, content);
            entity = multipartEntityBuilder.build();
        } else {
            //Setting inline content to http entity
            try {
                String inlineContent = IOUtils.toString(new InputStreamReader(new FileInputStream(content),
                        ImportExportConstants.CHARSET));
                MultipartEntityBuilder multipartEntityBuilder = MultipartEntityBuilder.create();
                multipartEntityBuilder.addTextBody(ImportExportConstants.MULTIPART_Inline,
                        inlineContent, ContentType.APPLICATION_OCTET_STREAM);
                entity = multipartEntityBuilder.build();
            } catch (IOException e) {
                String errorMsg = "error occurred while converting inline content to multipart " +
                        "entity of document " + documentName;
                log.error(errorMsg, e);
                throw new UtilException(errorMsg, e);
            }
        }
        //Updating the document content
        String url = config.getPublisherUrl() + ImportExportConstants.APIS + uuid +
                ImportExportConstants.DOCUMENT_SEG + ImportExportConstants.ZIP_FILE_SEPARATOR +
                documentId + ImportExportConstants.CONTENT_SEG;
        CloseableHttpClient client = null;
        try {
            client = HttpClientGenerator.getHttpClient();
            HttpPost request = new HttpPost(url);
            request.setHeader(HttpHeaders.AUTHORIZATION, ImportExportConstants.CONSUMER_KEY_SEGMENT
                    + accessToken);
            request.setEntity(entity);
            client.execute(request);
        } catch (UtilException e) {
            log.warn("Error occurred while getting ClosableHttpClient on " +
                    "importing document content", e);
        } catch (IOException e) {
            log.error("error occurred while uploading the content of document " +
                    ImportExportUtils.readJsonValues(response, ImportExportConstants.DOC_NAME), e);
        } finally {
            IOUtils.closeQuietly(client);
        }
    }

    /**
     * Update the documents of a existing API
     *
     * @param uuid       uuid of the API
     * @param apiId      api id of the API(provider-name-version)
     * @param token      access token
     * @param folderPath folder path to the imported folder
     */
    private static void updateAPIDocumentation(String uuid, String apiId, String token,
                                               String folderPath) {
        //getting the document list of existing API
        String url = config.getPublisherUrl() + ImportExportConstants.APIS + uuid +
                ImportExportConstants.DOCUMENT_SEG;
        CloseableHttpClient client = null;
        try {
            client = HttpClientGenerator.getHttpClient();
        } catch (UtilException e) {
            String errorMsg = "Error while getting ClosableHttpClient for updating API documents";
            log.warn(errorMsg, e);
        }
        HttpGet request = new HttpGet(url);
        request.setHeader(HttpHeaders.AUTHORIZATION, ImportExportConstants.CONSUMER_KEY_SEGMENT +
                token);
        try {
            HttpResponse response = client.execute(request);
            if (response.getStatusLine().getStatusCode() == Response.Status.OK.getStatusCode()) {
                String responseString = EntityUtils.toString(response.getEntity());
                //parsing string array of documents in to a jsonArray
                JSONParser parser = new JSONParser();
                JSONObject jsonObj = (JSONObject) parser.parse(responseString);
                JSONArray array = (JSONArray) jsonObj.get(ImportExportConstants.DOC_LIST);
                //accessing each document in the document array
                for (Object anArray : array) {
                    JSONObject obj = (JSONObject) anArray;
                    //getting document id and delete each existing document
                    String documentId = (String) obj.get(ImportExportConstants.DOC_ID);
                    String deleteUrl = config.getPublisherUrl() + ImportExportConstants.APIS + uuid +
                            ImportExportConstants.DOCUMENT_SEG +
                            ImportExportConstants.ZIP_FILE_SEPARATOR + documentId;
                    CloseableHttpClient httpclient =
                            HttpClientGenerator.getHttpClient();
                    HttpDelete deleteRequest = new HttpDelete(deleteUrl);
                    deleteRequest.setHeader(HttpHeaders.AUTHORIZATION,
                            ImportExportConstants.CONSUMER_KEY_SEGMENT + token);
                    httpclient.execute(deleteRequest);
                }
                //adding new documentation
                addAPIDocuments(folderPath, token, uuid);
            } else {
                String errorMsg = "Error occurred while getting the document list of API " + apiId;
                log.warn(errorMsg);
            }
        } catch (IOException | ParseException e) {
            String errorMsg = "Error occurred while updating the documents of API " + apiId;
            log.warn(errorMsg, e);
        } catch (UtilException e) {
            String errorMsg = "Error occurred while getting a ClosableHttpClient on updating" +
                    " the API documents";
            log.warn(errorMsg, e);
        }
    }
}