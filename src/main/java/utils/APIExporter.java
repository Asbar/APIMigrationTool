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

import com.opencsv.CSVReader;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.entity.BufferedHttpEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.util.EntityUtils;
import org.codehaus.jackson.map.ObjectMapper;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Scanner;

/**
 * This class handle all the functionality of API export
 */
class APIExporter {

    private static final Log log = LogFactory.getLog(APIExporter.class);
    private static JSONParser parser = new JSONParser();
    private static ObjectMapper mapper = new ObjectMapper();


    /**
     * Handles the export of single API
     *
     * @param consumerCredentials encoded consumer key and consumer secret
     * @throws APIExportException If unable to export the API
     */
    static void singleApiExport(String consumerCredentials) throws APIExportException {
        ApiImportExportConfiguration config = ApiImportExportConfiguration.getInstance();
        //If no any parameters provided at jar execution, default export will continue
        Scanner scanner = new Scanner(System.in, ImportExportConstants.CHARSET);
        if (StringUtils.isBlank(config.getApiName())) {
            System.out.print("Enter the name of the API been export : ");
            String name = scanner.next();
            if (StringUtils.isNotBlank(name)) {
                config.setApiName(name);
            }
        }
        if (StringUtils.isBlank(config.getApiProvider())) {
            System.out.print("Enter the provider of the API been export : ");
            String provider = scanner.next();
            if (StringUtils.isNotBlank(provider)) {
                config.setApiProvider(provider);
            }
        }
        if (StringUtils.isBlank(config.getApiVersion())) {
            System.out.print("Enter the version of the API been export : ");
            String version = scanner.next();
            if (StringUtils.isNotBlank(version)) {
                config.setApiVersion(version);
            }
        }
        //Generating access token
        String token;
        try {
            token = ImportExportUtils.getAccessToken
                    (ImportExportConstants.EXPORT_SCOPE, consumerCredentials);
        } catch (UtilException e) {
            String errorMsg = "Error occurred while generating access token for " + config.getApiName();
            log.error(errorMsg, e);
            throw new APIExportException(errorMsg, e);
        }

        //Get the destination, and folder name for the archive from configurations
        String archivePath = getDestinationFolder(config);

        //Exporting the API
        exportAPI(archivePath, config.getApiName(), config.getApiProvider(),
                config.getApiVersion(), token);

        //Archiving created directory
        createArchive(archivePath);
    }

    /**
     * This method will Perform bulk export of APIs
     *
     * @param credentials encoded consumer key and consumer secret
     */
    static void bulkApiExport(String credentials) throws APIExportException {
        ApiImportExportConfiguration config = ApiImportExportConfiguration.getInstance();
        String token;
        try {
            token = ImportExportUtils.getAccessToken(ImportExportConstants.EXPORT_SCOPE, credentials);
        } catch (UtilException e) {
            String errorMsg = "Error occurred while generating access token for bulk export of APIs";
            log.error(errorMsg, e);
            throw new APIExportException(errorMsg, e);
        }
        //Retrieve csv file location from the configurations
        String csvFile = config.getApiFilePath();

        //Retrieve the folder information for the resulting zip file
        String archivePath = getDestinationFolder(config);

        try {
            //Read the content in the CSV file
            CSVReader reader = new CSVReader(new InputStreamReader(new FileInputStream(csvFile),
                    ImportExportConstants.CHARSET));
            String[] line;
            while ((line = reader.readNext()) != null) {
                //Extract API name, version , provider from the csv file
                String apiProvider = line[0];
                String apiName = line[1];
                String apiVersion = line[2];
                try {
                    //Exporting each API
                    exportAPI(archivePath, apiName, apiProvider, apiVersion, token);
                } catch (APIExportException e) {
                    //If throws APIExportException, gives a warning and continue with the next API in the list
                    log.warn("Error occurred while exporting API " + apiName + "-" +
                            apiVersion, e);
                }
            }
            //Archive created directory
            createArchive(archivePath);
        } catch (FileNotFoundException e) {
            String errorMsg = "Cannot find the source file file for bulk export";
            log.error(errorMsg, e);
            throw new APIExportException(errorMsg, e);
        } catch (IOException e) {
            String errorMsg = "Error occurred while reading the source file for bulk export";
            log.error(errorMsg, e);
            throw new APIExportException(errorMsg, e);
        }
    }

    /**
     * Retrieve the information of API and create a exportable archive
     *
     * @param apiName     name of the API
     * @param provider    provider of the API
     * @param version     version of the API
     * @param accessToken valid access token
     */
    private static void exportAPI(String destinationLocation, String apiName, String provider,
                                  String version, String accessToken) throws APIExportException {
        String responseString;
        ApiImportExportConfiguration config = ApiImportExportConfiguration.getInstance();
        //Building the API id
        String apiId = provider + "-" + apiName + "-" + version;
        CloseableHttpResponse response;
        CloseableHttpClient client = null;

        try {
            //Retrieve API meta- information
            client = HttpClientGenerator.getHttpClient();
            String url = config.getPublisherUrl() + ImportExportConstants.APIS + apiId;
            HttpGet request = new HttpGet(url);
            request.setHeader(HttpHeaders.AUTHORIZATION, ImportExportConstants.CONSUMER_KEY_SEGMENT +
                    accessToken);
            request.setHeader(HttpHeaders.CONTENT_TYPE, ImportExportConstants.CONTENT_JSON);
            response = client.execute(request);
        } catch (IOException e) {
            String errorMsg = "Error occurred while retrieving details of the API " + apiId;
            log.error(errorMsg, e);
            throw new APIExportException(errorMsg, e);
        } catch (UtilException e) {
            String errorMsg = "Error occurred while getting a closable http client for execution";
            log.error(errorMsg, e);
            throw new APIExportException(errorMsg, e);
        }
        if (response.getStatusLine().getStatusCode() == Response.Status.OK.getStatusCode()) {
            //If API meta-information retrieved successfully
            try {
                HttpEntity entity = response.getEntity();
                responseString = EntityUtils.toString(entity);
            } catch (IOException e) {
                String errorMsg = "Error occurred while converting API response to string";
                log.error(errorMsg, e);
                throw new APIExportException(errorMsg, e);
            }
            //Creating directory to store API information
            String APIFolderPath = destinationLocation.concat(File.separator + apiName + "-" + version);

            //Creating directory to store API meta-information
            String metaInfoFolderPath = APIFolderPath.concat(File.separator +
                    ImportExportConstants.META_INFO);
            try {
                ImportExportUtils.createDirectory(APIFolderPath);
                ImportExportUtils.createDirectory(metaInfoFolderPath);

            } catch (UtilException e) {
                String errorMsg = "Error occurred while creating directory to hold API details " +
                        "of API " + apiId;
                log.error(errorMsg, e);
                throw new APIExportException(errorMsg, e);
            }
            //Set API status and scope before exporting
            if (!ImportExportUtils.readJsonValues(responseString, ImportExportConstants.STATUS_CONSTANT).
                    equalsIgnoreCase(ImportExportConstants.PROTOTYPED)) {
                responseString = setJsonValues(responseString, ImportExportConstants.STATUS_CONSTANT,
                        ImportExportConstants.CREATED);
            }
            responseString = setJsonValues(responseString, ImportExportConstants.SCOPE_CONSTANT, null);

            //Get API uuid from the retrieved api definition
            String uuid = ImportExportUtils.readJsonValues(responseString, ImportExportConstants.UUID);

            //Writing API definition in to exporting folder
            try {
                writeFile(metaInfoFolderPath + File.separator + ImportExportConstants.API_JSON,
                        ImportExportUtils.formatJsonString(responseString));
            } catch (UtilException e) {
                String errorMsg = "Error occurred while writing API defifnition to the exporting file";
                log.error(errorMsg, e);
                throw new APIExportException(errorMsg, e);
            }

            //Add API swagger definition to exporting folder
            addSwagger(uuid, accessToken, metaInfoFolderPath);

            //Check if API consists of a thumbnail
            String thumbnailUri = ImportExportUtils.readJsonValues(responseString,
                    ImportExportConstants.THUMBNAIL);
            if (StringUtils.isNotBlank(thumbnailUri)) {
                //Export api thumbnail
                exportAPIThumbnail(uuid, accessToken, APIFolderPath);
            }
            //Get API documents list
            String documentList = getAPIDocumentList(accessToken, uuid);
            try {
                JSONObject jsonObj = (JSONObject) parser.parse(documentList);
                org.json.simple.JSONArray arr = (org.json.simple.JSONArray)
                        jsonObj.get(ImportExportConstants.DOC_LIST);
                if (arr.size() > 0) {
                    try {
                        exportAPIDocumentation(uuid, documentList, accessToken, APIFolderPath);
                    } catch (UtilException e) {
                        log.error("Error occurred while exporting documents of API " + apiId, e);
                    }
                }
            } catch (ParseException e) {
                log.error("Error occurred while converting document list to json", e);
            }

            //Exporting mediation policies
            JSONObject jsonObj = null;
            try {
                jsonObj = (JSONObject) parser.parse(responseString);
                //Convert API definition in to json object and extract the sequences element.
                org.json.simple.JSONArray arr = (org.json.simple.JSONArray) jsonObj.
                        get(ImportExportConstants.SEQUENCES_ELEM);
                if (arr.size() > 0) {
                    //Traverse through each sequence mediation to add them in to the exporting folder
                    for (Object item : arr) {
                        JSONObject mediationPolicy = (JSONObject) item;
                        //If value of attribute shared == false, its a API specific mediation sequence
                        if (!(boolean) mediationPolicy.get(ImportExportConstants.SHARED_STATUS)) {
                            //Exporting API specific mediation policy
                            exportApiSpecificMediationPolicies(config, uuid, accessToken, mediationPolicy,
                                    APIFolderPath);
                        } else {
                            //Exporting global mediation policy
                            exportGlobalMediationPolicies(config, accessToken, mediationPolicy,
                                    destinationLocation);
                        }
                    }
                }
            } catch (ParseException e) {
                log.error("Error occurred while getting mediation sequences of the API " + apiName);
            } catch (UtilException e) {
                log.error("Error occurred while writing mediation policies in to the exporting " +
                        "folder", e);
            } finally {
                IOUtils.closeQuietly(client);
            }
            //Exporting API wsdl, if exists
            if (jsonObj != null && StringUtils.isNotBlank((String) jsonObj.get
                    (ImportExportConstants.WSDL_URI))) {
                exportApiWsdl(config, uuid, accessToken, APIFolderPath);
            }

        } else if (response.getStatusLine().getStatusCode() ==
                Response.Status.NOT_FOUND.getStatusCode()) {
            String message = "API " + apiId + " does not exist/ not found ";
            log.warn(message);
            throw new APIExportException(message);
        } else {
            String errorMsg = "Error occurred while retrieving the meta-information of API " + apiId;
            log.error(errorMsg);
            throw new APIExportException(errorMsg);
        }
    }

    /**
     * Add API wsdl to the exporting folder
     *
     * @param config        ApiImportExportConfiguration object
     * @param uuid          API identifier
     * @param token         access token with apim:api_view scope
     * @param apiFolderPath path to the API folder
     */
    private static void exportApiWsdl(ApiImportExportConfiguration config, String uuid,
                                      String token, String apiFolderPath) throws APIExportException {
        InputStream inputStream = null;
        OutputStream outputStream = null;
        CloseableHttpClient client = null;
        try {
            String url = config.getPublisherUrl() + ImportExportConstants.APIS + uuid +
                    ImportExportConstants.WSDL_SEG;
            client = HttpClientGenerator.getHttpClient();
            HttpGet request = new HttpGet(url);
            request.setHeader(HttpHeaders.AUTHORIZATION, ImportExportConstants.CONSUMER_KEY_SEGMENT + token);
            CloseableHttpResponse response = client.execute(request);
            //Extracting the wsdl implementation
            inputStream = response.getEntity().getContent();
            outputStream = new FileOutputStream(apiFolderPath + File.separator +
                    ImportExportConstants.WSDL_FILE_NAME);
            //Writing wsdl content to the exporting directory
            IOUtils.copy(inputStream, outputStream);
        } catch (UtilException e) {
            String errorMsg = "Error occurred while getting a http client for exporting wsdl" +
                    " of API " + uuid;
            log.error(errorMsg, e);
            throw new APIExportException(errorMsg, e);
        } catch (IOException e) {
            String errorMsg = "Error occurred while retrieving wsdl of the API " + uuid;
            log.error(errorMsg, e);
            throw new APIExportException(errorMsg, e);
        } finally {
            IOUtils.closeQuietly(inputStream);
            IOUtils.closeQuietly(outputStream);
            IOUtils.closeQuietly(client);
        }
    }

    /**
     * This method get the API thumbnail and write in to the zip file
     *
     * @param uuid          id of the API
     * @param accessToken   valid access token with exporting scopes
     * @param apiFolderPath path to the folder with API information
     */
    private static void exportAPIThumbnail(String uuid, String accessToken, String apiFolderPath) {
        ApiImportExportConfiguration config = ApiImportExportConfiguration.getInstance();
        CloseableHttpClient client = null;
        try {
            //REST API call to get API thumbnail
            String url = config.getPublisherUrl() + ImportExportConstants.APIS + uuid +
                    ImportExportConstants.THUMBNAIL_SEG;
            client = HttpClientGenerator.getHttpClient();
            HttpGet request = new HttpGet(url);
            request.setHeader(HttpHeaders.AUTHORIZATION, ImportExportConstants.CONSUMER_KEY_SEGMENT
                    + accessToken);
            CloseableHttpResponse response = client.execute(request);
            HttpEntity entity = response.getEntity();
            //Converting the response in to inputStream
            BufferedHttpEntity httpEntity = new BufferedHttpEntity(entity);
            InputStream imageStream = httpEntity.getContent();
            byte[] byteArray = IOUtils.toByteArray(imageStream);
            InputStream inputStream = new BufferedInputStream(new ByteArrayInputStream(byteArray));
            //Getting the mime type of the inputStream
            String mimeType = URLConnection.guessContentTypeFromStream(inputStream);
            //Getting file extension
            String extension = getThumbnailFileType(mimeType);
            OutputStream outputStream = null;
            if (extension != null) {
                //Writing image in to the  exporting folder
                try {
                    outputStream = new FileOutputStream(apiFolderPath + File.separator +
                            ImportExportConstants.IMG_NAME + "." + extension);
                    IOUtils.copy(httpEntity.getContent(), outputStream);
                } finally {
                    IOUtils.closeQuietly(outputStream);
                    IOUtils.closeQuietly(imageStream);
                    IOUtils.closeQuietly(inputStream);
                }
            }
        } catch (IOException e) {
            log.error("Error occurred while exporting the API thumbnail", e);
        } catch (UtilException e) {
            log.error("Error occurred while getting a closableHttpClient while retrieving " +
                    "thumbnail image", e);
        } finally {
            IOUtils.closeQuietly(client);
        }
    }

    /**
     * Retrieve file type of the thumbnail
     *
     * @param mediaType Media type of the thumbnail
     * @return File extension of thumbnail image or null
     */
    private static String getThumbnailFileType(String mediaType) {
        if ((ImportExportConstants.PNG_IMG).equals(mediaType)) {
            return ImportExportConstants.PNG_TYPE;
        } else if (ImportExportConstants.JPG_IMG.equals(mediaType)) {
            return ImportExportConstants.JPG_TYPE;
        } else if (ImportExportConstants.JPEG_IMG.equals(mediaType)) {
            return ImportExportConstants.JPEG_TYPE;
        } else if ((ImportExportConstants.BMP_IMG).equals(mediaType)) {
            return ImportExportConstants.BMP_TYPE;
        } else if ((ImportExportConstants.GIF_IMG).equals(mediaType)) {
            return ImportExportConstants.GIF_TYPE;
        } else {
            log.error("can't predict the thumbnail file type.");
        }
        return null;
    }

    /**
     * Retrieve list of documents of an API
     *
     * @param accessToken access token with importing scope
     * @param uuid        uuid of the API
     * @return String output of documentation summary or null
     */
    private static String getAPIDocumentList(String accessToken, String uuid) {
        ApiImportExportConfiguration config = ApiImportExportConfiguration.getInstance();
        try {
            //REST API call to Get API Document list
            String url = config.getPublisherUrl() + ImportExportConstants.APIS + uuid +
                    ImportExportConstants.DOCUMENT_SEG;
            CloseableHttpClient client = HttpClientGenerator.getHttpClient();
            HttpGet request = new HttpGet(url);
            request.setHeader(HttpHeaders.AUTHORIZATION, ImportExportConstants.CONSUMER_KEY_SEGMENT
                    + accessToken);
            CloseableHttpResponse response = client.execute(request);
            HttpEntity entity = response.getEntity();
            return EntityUtils.toString(entity, ImportExportConstants.CHARSET);
        } catch (IOException e) {
            log.error("Error occurred while getting API document list", e);
        } catch (UtilException e) {
            log.error("Error occurred while getting closableHttpClient for getting API document" +
                    " list", e);
        }
        return null;
    }

    /**
     * Write API documents in to the zip file
     *
     * @param uuid         APIId
     * @param documentList resultant string from the getAPIDocuments
     * @param accessToken  token with scope apim:api_view
     */
    private static void exportAPIDocumentation(String uuid, String documentList, String accessToken,
                                               String archivePath) throws APIExportException, UtilException {
        OutputStream outputStream = null;
        ApiImportExportConfiguration config = ApiImportExportConfiguration.getInstance();
        //Create directory to hold API documents
        String documentFolderPath = archivePath.concat(File.separator +
                ImportExportConstants.DOCUMENT_DIRECTORY);
        ImportExportUtils.createDirectory(documentFolderPath);

        //Writing API document list to exporting folder
        try {
            Object json = mapper.readValue(documentList, Object.class);
            //Format the document list string and write in to the directory
            String formattedJson = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(json);
            writeFile(documentFolderPath + File.separator + ImportExportConstants.DOC_JSON,
                    formattedJson);
        } catch (IOException e) {
            String errorMsg = "Error occurred while formatting the document list response string";
            //If formatting failed, writing unformatted string on to the zip
            writeFile(documentFolderPath + File.separator + ImportExportConstants.DOC_JSON,
                    documentList);
            log.warn(errorMsg, e);
        }
        //Convert document list to json object
        JSONObject jsonObj;
        try {
            jsonObj = (JSONObject) parser.parse(documentList);
        } catch (ParseException e) {
            String errorMsg = "Error occurred while parsing document list json string to " +
                    "json object,cannot export the API documents";
            log.error(errorMsg, e);
            throw new UtilException(errorMsg, e);
        }
        //Get all the available documents in to a array
        org.json.simple.JSONArray arr = (org.json.simple.JSONArray)
                jsonObj.get(ImportExportConstants.DOC_LIST);
        // Traverse through each document
        for (Object anArr : arr) {
            JSONObject document = (JSONObject) anArr;
            //Getting document source type (inline/url/file)
            String sourceType = (String) document.get(ImportExportConstants.SOURCE_TYPE);
            if (ImportExportConstants.FILE_DOC_TYPE.equalsIgnoreCase(sourceType) ||
                    ImportExportConstants.INLINE_DOC_TYPE.equalsIgnoreCase(sourceType)) {
                String documentId = (String) document.get(ImportExportConstants.DOC_ID);
                String documentName = (String) document.get(ImportExportConstants.DOC_NAME);
                //If source type equals file or inline getting the content of the document
                String url = config.getPublisherUrl() + ImportExportConstants.APIS + uuid +
                        ImportExportConstants.DOCUMENT_SEG + ImportExportConstants.URL_SEPARATOR +
                        documentId + ImportExportConstants.CONTENT_SEG;
                CloseableHttpClient client = HttpClientGenerator.getHttpClient();
                HttpGet request = new HttpGet(url);
                request.setHeader(HttpHeaders.AUTHORIZATION,
                        ImportExportConstants.CONSUMER_KEY_SEGMENT + accessToken);
                HttpResponse response;
                try {
                    response = client.execute(request);
                } catch (IOException e) {
                    log.error("Error occurred while exporting document " + documentName, e);
                    //If throws exception,continue with the next document
                    continue;
                }
                HttpEntity entity = response.getEntity();
                //Adding FILE type content to zip
                if (ImportExportConstants.FILE_DOC_TYPE.equalsIgnoreCase(sourceType)) {
                    //Creating directory to hold FILE type content
                    String fileTypeFolderPath = documentFolderPath.concat
                            (File.separator + ImportExportConstants.FILE_DOCUMENT_DIRECTORY);
                    try {
                        ImportExportUtils.createDirectory(fileTypeFolderPath);
                    } catch (UtilException e) {
                        log.error("Error occurred while creating a directory to hold FILE " +
                                "type document content", e);
                        //If throws exception,continue with the next document
                        continue;
                    }
                    //Writing file type content in to the exporting folder
                    String localFilePath = fileTypeFolderPath + File.separator + document.get
                            (ImportExportConstants.DOC_NAME);
                    try {
                        outputStream = new FileOutputStream(localFilePath);
                        entity.writeTo(outputStream);
                    } catch (FileNotFoundException e) {
                        log.error("File to write the content of the document " + documentName +
                                " not found", e);
                    } catch (IOException e) {
                        log.error("Error occurred while writing the content of file type document "
                                + documentName + " to the folder", e);
                    } finally {
                        IOUtils.closeQuietly(outputStream);
                    }
                } else {
                    //Create directory to hold inline contents
                    String inlineFolderPath = documentFolderPath.concat(File.separator +
                            ImportExportConstants.INLINE_DOCUMENT_DIRECTORY);
                    try {
                        ImportExportUtils.createDirectory(inlineFolderPath);
                    } catch (UtilException e) {
                        log.error("Error occurred while creating a directory to hold inline " +
                                "document content of the API ", e);
                    }
                    //Writing inline content in to the zip folder
                    try {
                        String localFilePath = inlineFolderPath + File.separator +
                                document.get(ImportExportConstants.DOC_NAME);
                        outputStream = new FileOutputStream(localFilePath);
                        entity.writeTo(outputStream);
                    } catch (IOException e) {
                        log.error("Error occurred while writing content of inline document" +
                                documentName + " to the zip", e);
                    } finally {
                        IOUtils.closeQuietly(outputStream);
                    }
                }

            }
        }
    }

    /**
     * Method will retrieve the swagger definition of API specified by the uuid using
     * corresponding REST API
     *
     * @param uuid       api identifier
     * @param token      access token with exporting scopes
     * @param folderPath path to the exporting folder
     * @throws APIExportException If failed to get the swagger definition of the API
     */
    private static void addSwagger(String uuid, String token, String folderPath)
            throws APIExportException {
        ApiImportExportConfiguration config = ApiImportExportConfiguration.getInstance();
        String url = config.getPublisherUrl() + ImportExportConstants.APIS + uuid
                + ImportExportConstants.SWAGGER_SEG;
        HttpGet request = new HttpGet(url);
        CloseableHttpClient client = null;
        try {
            client = HttpClientGenerator.getHttpClient();
            request.setHeader(HttpHeaders.AUTHORIZATION, ImportExportConstants.CONSUMER_KEY_SEGMENT +
                    token);
            CloseableHttpResponse response = client.execute(request);
            HttpEntity entity = response.getEntity();
            String unformattedResponse = EntityUtils.toString(entity);
            //Format the returned swagger definition and write in to the folder to be exported
            writeFile(folderPath + File.separator + ImportExportConstants.SWAGGER_JSON,
                    ImportExportUtils.formatJsonString(unformattedResponse));
        } catch (IOException e) {
            String errorMsg = "Error occurred while retrieving swagger definition of API " + uuid;
            log.error(errorMsg, e);
            throw new APIExportException(errorMsg, e);
        } catch (UtilException e) {
            String errorMsg = "Error occurred while getting a closableHttpClient for exporting " +
                    "swagger";
            log.error(errorMsg, e);
            throw new APIExportException(errorMsg, e);
        } finally {
            IOUtils.closeQuietly(client);
        }
    }

    /**
     * Save API specific mediation policies inside specific API Folder inside exporting folder
     *
     * @param config       ApiImportExportConfiguration object
     * @param uuid         API identifier
     * @param accessToken  access token with exporting scopes
     * @param mediationObj json object correspond to the mediation policy
     * @param apiFolder    API Folder location inside exporting folder
     */
    private static void exportApiSpecificMediationPolicies(ApiImportExportConfiguration config,
                                                           String uuid, String accessToken,
                                                           JSONObject mediationObj,
                                                           String apiFolder) throws UtilException {
        String url = config.getPublisherUrl() + ImportExportConstants.APIS + uuid +
                ImportExportConstants.MEDIATION_SEG + ImportExportConstants.URL_SEPARATOR +
                mediationObj.get(ImportExportConstants.UUID);
        CloseableHttpClient client = null;
        try {
            client = HttpClientGenerator.getHttpClient();
            HttpGet request = new HttpGet(url);
            request.setHeader(HttpHeaders.AUTHORIZATION, ImportExportConstants.CONSUMER_KEY_SEGMENT
                    + accessToken);
            CloseableHttpResponse response = client.execute(request);
            HttpEntity entity = response.getEntity();
            String mediationPolicy = EntityUtils.toString(entity);
            //Extracting config from the mediation policy
            String content = ImportExportUtils.readJsonValues(mediationPolicy,
                    ImportExportConstants.CONFIG_ELEM);
            String mediationPoliciesFolder = apiFolder.concat(File.separator +
                    ImportExportConstants.MEDIATION_FOLDER);
            Path mediationFolderPath = Paths.get(mediationPoliciesFolder);
            if (!Files.exists(mediationFolderPath)) {
                ImportExportUtils.createDirectory(mediationPoliciesFolder);
            }
            String mediationDirection = (String) mediationObj.get(ImportExportConstants.TYPE_ELEM);
            String directionFolder = mediationPoliciesFolder.concat(File.separator + mediationDirection);
            Path directionFolderPath = Paths.get(directionFolder);
            if (!Files.exists(directionFolderPath)) {
                ImportExportUtils.createDirectory(directionFolder);
            }
            //Writing the mediation policy config in to the exporting folder
            writeFile(directionFolder + File.separator +
                    mediationObj.get(ImportExportConstants.NAME_ELEM), content);
        } catch (IOException e) {
            log.error("Error occurred while getting API specific mediation policies of API " + uuid, e);
        } finally {
            IOUtils.closeQuietly(client);
        }
    }

    /**
     * Add Global mediation policies to the exporting folder
     *
     * @param config              ApiImportExportConfiguration object
     * @param accessToken         access token with scope api_view
     * @param mediationObj        json object correspond to the mediation policy
     * @param destinationLocation path to the exporting folder
     */
    private static void exportGlobalMediationPolicies(ApiImportExportConfiguration config,
                                                      String accessToken, JSONObject mediationObj,
                                                      String destinationLocation) throws UtilException {
        String url = config.getPublisherUrl() + ImportExportConstants.MEDIATION_SEG +
                ImportExportConstants.URL_SEPARATOR + mediationObj.get(ImportExportConstants.UUID);
        CloseableHttpClient client = null;
        try {
            client = HttpClientGenerator.getHttpClient();
            HttpGet request = new HttpGet(url);
            request.setHeader(HttpHeaders.AUTHORIZATION, ImportExportConstants.CONSUMER_KEY_SEGMENT
                    + accessToken);
            CloseableHttpResponse response = client.execute(request);
            HttpEntity entity = response.getEntity();
            String mediationPolicy = EntityUtils.toString(entity);
            //Getting config details of the mediation policy
            String content = ImportExportUtils.readJsonValues(mediationPolicy,
                    ImportExportConstants.CONFIG_ELEM);
            String mediationPoliciesFolder = destinationLocation.concat(File.separator +
                    ImportExportConstants.MEDIATION_FOLDER);
            Path folderPath = Paths.get(mediationPoliciesFolder);
            //Create folder for global mediation policies if not exists.
            if (!Files.exists(folderPath)) {
                ImportExportUtils.createDirectory(mediationPoliciesFolder);
            }
            String mediationDirection = (String) mediationObj.get(ImportExportConstants.TYPE_ELEM);
            String directionFolder = mediationPoliciesFolder.concat(File.separator + mediationDirection);
            Path directionFolderPath = Paths.get(directionFolder);
            //Create folder for specific mediation types (in/out/fault) if not exists
            if (!Files.exists(directionFolderPath)) {
                ImportExportUtils.createDirectory(directionFolder);
            }
            //Writing the content of mediation policy config in to the exporting folder
            writeFile(directionFolder + File.separator +
                    mediationObj.get(ImportExportConstants.NAME_ELEM), content);
        } catch (IOException e) {
            String errorMsg = "Error occurred while retrieving global mediation policies from " +
                    "the registry";
            log.error(errorMsg, e);
            throw new UtilException(errorMsg, e);
        } finally {
            IOUtils.closeQuietly(client);
        }
    }


    /**
     * Write the given content to file specified in path
     *
     * @param path    Location of the file
     * @param content Content to be written
     */
    private static void writeFile(String path, String content) throws UtilException {

        FileOutputStream fileStream = null;
        OutputStreamWriter writer = null;
        try {
            fileStream = new FileOutputStream(new File(path));
            writer = new OutputStreamWriter(fileStream, ImportExportConstants.CHARSET);
            IOUtils.copy(new StringReader(content), writer);
        } catch (FileNotFoundException e) {
            String errorMsg = "File to be write not found";
            log.error(errorMsg, e);
            throw new UtilException(errorMsg, e);
        } catch (UnsupportedEncodingException e) {
            String errorMsg = "Error occurred while writing details to the file";
            log.error(errorMsg, e);
            throw new UtilException(errorMsg, e);
        } catch (IOException e) {
            String errorMsg = "Error occurred while coping details to the file";
            log.error(errorMsg, e);
            throw new UtilException(errorMsg, e);
        } finally {
            IOUtils.closeQuietly(writer);
            IOUtils.closeQuietly(fileStream);
        }
    }

    /**
     * Get destination location for the zip file
     *
     * @param config ApiImportExportConfiguration type object
     * @return destination location and name for the zip file
     */
    private static String getDestinationFolder(ApiImportExportConfiguration config) {
        String destinationFolderPath;
        //If destination location not explicitly given as a configuration,user's current directory will be chosen
        if (StringUtils.isNotBlank(config.getDestinationPath())) {
            destinationFolderPath = config.getDestinationPath();
        } else {
            log.warn("zip folder of exporting API/APIs will be created at user's current directory ");
            destinationFolderPath = System.getProperty(ImportExportConstants.USER_DIR);
        }
        String folderName;
        //If folder name for zip not explicitly given, tool's default name will be use
        if (StringUtils.isBlank(config.getDestinationFolderName())) {
            folderName = ImportExportConstants.DEFAULT_FOLDER_NAME;
            log.warn("zip folder will be created with default name ' " +
                    ImportExportConstants.DEFAULT_FOLDER_NAME + " ' ");
        } else {
            folderName = config.getDestinationFolderName();
        }
        return destinationFolderPath.concat(File.separator + folderName);
    }

    /**
     * Creating archive of the directory specified by the folder path
     *
     * @param folderPath file to archive
     */
    private static void createArchive(String folderPath) {
        File file = new File(folderPath);
        //Check if the directory is empty
        if (file.list().length > 0) {
            ArchiveGeneratorUtil.archiveDirectory(folderPath);
            //Check if the zip file created successfully
            if (new File(folderPath.concat(ImportExportConstants.ZIP_EXTENSION)).exists()) {
                System.out.println("API exported successfully");
            } else {
                System.out.println("API exporting unsuccessful");
            }
        }
    }

    /**
     * Set the value been sent to the corresponding json element specified by key
     *
     * @param responseString string to be replace
     * @param key            key
     * @param value          new value to be set
     */
    private static String setJsonValues(String responseString, String key, String value) {
        try {
            JSONObject json = (JSONObject) parser.parse(responseString);
            //Check the availability of the key
            if (json.containsKey(key)) {
                json.put(key, value);
                return json.toString();
            }
        } catch (ParseException e) {
            log.error("error occurred modifying json string ", e);
        }
        return responseString;
    }
}
