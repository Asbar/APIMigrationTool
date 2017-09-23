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

import java.io.File;

public class ImportExportConstants {

    //system dependent default path separator character, represented as a string
    public static final String DIRECTORY_SEPARATOR = File.separator;
    //character encoding type
    public static final String CHARSET = "UTF-8";

    //archive constants
    public static final String DOCUMENT_DIRECTORY = "docs";
    public static final String INLINE_DOCUMENT_DIRECTORY = "InlineContents";
    public static final String JSON_FILE_LOCATION = DIRECTORY_SEPARATOR + "meta-information" +
            DIRECTORY_SEPARATOR + "api.json";
    public static final String DOCUMENT_FILE_LOCATION = DIRECTORY_SEPARATOR + "docs" +
            DIRECTORY_SEPARATOR + "docs.json";
    public static final char ZIP_FILE_SEPARATOR = '/';
    //length of the name of the temporary directory
    public static final int TEMP_FILENAME_LENGTH = 5;
    //image File name
    public static final String IMG_NAME = "icon";
    //default buffer size
    public static final int defaultBufferSize = 1024;


    //OAuth constants
    public static final String EXPORT_SCOPE = "apim:api_view apim:mediation_policy_view";
    public static final String IMPORT_SCOPE = "apim:api_create apim:api_view apim:mediation_policy_view apim:mediation_policy_create";
    public static final String AUTHORIZATION_KEY_SEGMENT = "Basic ";
    public static final String CONSUMER_KEY_SEGMENT = "Bearer ";
    public static final String DEFAULT_GRANT_TYPE = "password";
    public static final String CLIENT_ID = "clientId";
    public static final String CLIENT_SECRET = "clientSecret";
    public static final String ACCESS_TOKEN = "access_token";
    public static final String USERNAME = "username";

    //default configuration location
    public static final String DEFAULT_CONFIG_FILE = "/configs/config.properties";

    //configuration constants
    public static final String TRUST_STORE_URL_PROPERTY = "trust.store.url";
    public static final String TRUST_STORE_PASSWORD_PROPERTY = "trust.store.password";
    public static final String PUBLISHER_URL = "publisher.url.segment";
    public static final String GATEWAY_URL = "gateway.url.segment";
    public static final String REGISTRATION_URL = "client.registration.url";
    public static final String CLIENT_NAME_PROPERTY = "client.name";
    public static final String SSL_VALIDATION = "ssl.certificate.validation";
    public static final String IS_SAAS = "is.saas.app";
    public static final String LOG4J_FILE = "log4j.file";
    public static final String CONFIG_API = "api.name";
    public static final String CONFIG_VERSION = "api.version";
    public static final String CONFIG_PROVIDER = "api.provider";
    public static final String API_LIST_FILE = "api.list";
    public static final String DESTINATION_FOLDER = "destination.file.name";
    public static final String ZIP_FILE = "zip.file";
    public static final String UPDATE_API = "update.existing.API";

    //api subscription constants
    public static final String APIM_STORE_URL ="store.url.segment";
 //   public static final String API_SUBSCRIPTION_URL ="api.subscription.url";
    public static final String SUBSCRIPTION_SCOPE ="apim:subscribe";
   // public static final String API_APPLICATIONS_URL= "api.application.url";

    //system property constants
    public static final String USER_DIR = "user.dir";
    public static final String API_NAME = "name";
    public static final String API_VERSION = "version";
    public static final String API_PROVIDER = "provider";
    public static final String API_LIST = "list";
    public static final String CONFIG_FILE = "config";
    public static final String ZIP_DESTINATION = "destination.file.path";
    public static final String SSL_CERT = "certCheck";
    public static final String LOG4J_PROP = "log4j";
    public static final String DESTINATION = "filepath";
    public static final String ZIP_NAME = "filename";
    public static final String TRUST_STORE_URL = "trustStore";
    public static final String DCR_URL_SEG = "dcr";
    public static final String GATEWAY_URL_SEG = "gateway";
    public static final String PUBLISHER_URL_SEG = "publisher";
    public static final String ZIP_FILE_PROP = "zip";
    public static final String UPDATE_API_PROP = "updateIfExists";
    public static final String CLIENT_NAME_PROP = "clientName";

    //REST API constants
    public static final String CREATED = "CREATED";
    public static final String CONTENT_JSON = "application/json";
    public static final String PROTOTYPED = "PROTOTYPED";

    //url constants
    public static final String APIS = "/apis/";
    public static final String THUMBNAIL_SEG = "/thumbnail";
    public static final String DOCUMENT_SEG = "/documents";
    public static final String URL_SEPARATOR = "/";
    public static final String CONTENT_SEG = "/content";
    public static final String SWAGGER_SEG = "/swagger";
    public static final String APIS_URL = "/apis";
    public static final String MEDIATION_SEG = "/policies/mediation";
    public static final String WSDL_SEG = "/wsdl";

    //json string constants
    public static final String STATUS_CONSTANT = "status";
    public static final String SCOPE_CONSTANT = "scope";
    public static final String SWAGGER = "apiDefinition";
    public static final String UUID = "id";
    public static final String DOC_LIST = "list";
    public static final String SOURCE_TYPE = "sourceType";
    public static final String DOC_ID = "documentId";
    public static final String DOC_NAME = "name";
    public static final String THUMBNAIL = "thumbnailUri";
    public static final String WSDL_URI = "wsdlUri";
    public static final String FILE_DOCUMENT_DIRECTORY = "FileContents";
    public static final String INLINE_DOC_TYPE = "INLINE";
    public static final String FILE_DOC_TYPE = "FILE";
    public static final String BASEPATH = "basePath";
    public static final String PUBLISHER = "publisher";
    public static final String SEQUENCES_ELEM = "sequences";

    //mediation policy constants
    public static final String SHARED_STATUS = "shared";
    public static final String CONFIG_ELEM = "config";
    public static final String TYPE_ELEM = "type";
    public static final String NAME_ELEM = "name";
    public static final String MEDIATION_LIST = "list";

    //util constants
    public static final String DEFAULT_FOLDER_NAME = "ExportedAPIs";

    //foldername constants
    public static final String META_INFO = "meta-information";
    public static final String API_JSON = "api.json";
    public static final String DOC_JSON = "docs.json";
    public static final String SWAGGER_JSON = "swagger.json";
    public static final String ZIP_EXTENSION = ".zip";
    public static final String MEDIATION_FOLDER = "mediation_policies";
    public static final String WSDL_FILE_NAME = "wsdl";

    //payload constants
    public static final String CLIENT_NAME = "clientName";
    public static final String OWNER = "owner";
    public static final String GRANT_TYPE = "grantType";
    public static final String SAAS_APP = "saasApp";
    public static final String TOKEN_GRANT_TYPE = "grant_type";
    public static final String MULTIPART_Inline = "inlineContent";
    public static final String MULTIPART_WSDL_CONTENT = "wsdlDefinision";
    public static final String MULTIPART_FILE = "file";
    public static final String PNG_TYPE = "png";
    public static final String PNG_IMG = "image/png";
    public static final String JPG_TYPE = "jpg";
    public static final String JPG_IMG = "image/jpg";
    public static final String JPEG_TYPE = "jpeg";
    public static final String JPEG_IMG = "image/jpeg";
    public static final String BMP_TYPE = "bmp";
    public static final String BMP_IMG = "image/bmp";
    public static final String GIF_TYPE = "gif";
    public static final String GIF_IMG = "image/gif";
    public static final boolean DEFAULT_SAAS_APP = true;


}
