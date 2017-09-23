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

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.log4j.PropertyConfigurator;

import java.io.BufferedReader;
import java.io.Console;
import java.io.InputStreamReader;
import java.util.Scanner;

import static utils.ImportExportUtils.checkPublisherUrl;

/*
This class is the entry point to the client tool
 */
class ImportExport {

    private static final Log log = LogFactory.getLog(ImportExport.class);
    private static Scanner scanner = new Scanner(System.in, ImportExportConstants.CHARSET);

    public static void main(String[] args) {

        ApiImportExportConfiguration config = ApiImportExportConfiguration.getInstance();

        //config.setZipFile("F:\\SLIIT\\4thYear\\Research\\CLIToolRepo\\ExportedAPIs.zip");
        //  config.setZipFile(System.getProperty("user.dir")+"\\ExportedAPIs");

        //Getting CLI inputs
        System.out.print("Enter user name :");
        config.setUsername(scanner.next());

        System.out.print("Enter Password:");
        Scanner sc = new Scanner(System.in);
        char[] a = sc.next().toCharArray();
        config.setPassword(a);

        System.out.println();

        //Setting up default configurations
        try {
            ImportExportUtils.setDefaultConfigurations(config);
        } catch (UtilException e) {
            log.warn("Error occurred while loading the default configurations", e);
        }
        //Setting user customized configurations sent as cli parameters
        setUserInputs(config);

        //Configuring log4j properties
        PropertyConfigurator.configure(config.getLog4JFilePath());

        //Registering the client
        String consumerCredentials = "";
        try {
            consumerCredentials = ImportExportUtils.registerClient(config.getUsername(),
                    String.valueOf(config.getPassword()));
        } catch (UtilException e) {
            System.out.println("Error occurred while registering the client, cannot proceed");
            System.exit(1);
        }


        System.out.println("\nSelect an option to proceed...");
        System.out.println("1 => API Import");
        System.out.println("2 => Single API Export");
        System.out.println("3 => Bulk API Export");
        System.out.println("4 => API Subscription");
        System.out.println("5 => Deploy with Kubernetes\n");

        System.out.print("Option: ");
        Scanner scannerOption = new Scanner(System.in);
        String option = scannerOption.next();

        if (option.equals("1")) {
            System.out.println("\n------API Importer------");

            System.out.println("Enter the zip file path need to import into API Manager:");
            System.out.println("Ex:- (/home/asbar/Downloads/importExport-master/ExportedAPIs.zip)");
            String zipFile = scanner.next();

            config.setZipFile(zipFile);
            //If the zip file path is given continue API import
            if (StringUtils.isNotBlank(config.getZipFile())) {
                try {
                    ///TODO method seperation
                    //Handling API import
                    APIImporter.importAPIs(config.getZipFile(), consumerCredentials);
                } catch (APIImportException e) {
                    String errorMsg = "Unable to Import the APIs";
                    log.error(errorMsg, e);
                    System.exit(1);
                } catch (UtilException e) {
                    String errorMsg = "Error occurred while importing the API/APIs";
                    log.error(errorMsg, e);
                    System.exit(1);
                }
            }


        } else if (option.equals("2")) {

            System.out.println("\n------Single API Exporter------");

            try {
                //Handling single API export if any of above conditions not satisfied, or both
                // a api list and details about a single API is provided
                if (StringUtils.isNotBlank(config.getApiName()) &&
                        StringUtils.isNotBlank(config.getApiFilePath())) {
                    log.warn("Only API " + config.getApiName() +
                            " will be exported and api list will be ignored");
                }
                APIExporter.singleApiExport(consumerCredentials);
            } catch (APIExportException e) {
                log.error("Error occurred while exporting the API " + config.getApiName() +
                        "_" + config.getApiVersion(), e);
                System.exit(1);
            }

        } else if (option.equals("3")) {

            System.out.println("\n------Bulk API Exporter------");
            String zipFile = System.getProperty(ImportExportConstants.ZIP_FILE_PROP);
            System.out.print("Enter the path of CSV file:");
            String apiList = scanner.next();

            config.setApiFilePath(apiList);

            if (StringUtils.isNotBlank(config.getApiFilePath()) &&
                    StringUtils.isBlank(config.getApiName())) {
                //If a cvs file provided and details about a specific API not provided as cli
                //arguments,bulkExport will be prioritize over single API export
                try {
                    APIExporter.bulkApiExport(consumerCredentials);
                } catch (APIExportException e) {
                    String errorMsg = "Error occurred while attempting to bulk export APIs";
                    log.error(errorMsg, e);
                    System.exit(1);
                }
            }

        } else if(option.equals("4")){

            String str = null;

                try {

                    System.out.println("\n------API Subscription------\n");
                    System.out.println("Select an option to proceed...\n");
                    System.out.println("1 => Add New API Subscription");
                    System.out.println("2 => List API Subscription details by API");
                    System.out.println("3 => List API Subscription details by Application");
                    System.out.println("4 => Remove a Subscription\n");

                    System.out.print("Option: ");
                    Scanner scannerSubscription = new Scanner(System.in);
                    String subscriptionOption = scannerSubscription.next();

                    if (subscriptionOption.equals("1")) {
                        APISubscription.addNewSubscription(consumerCredentials);
                    } else if (subscriptionOption.equals("2")) {
                        APISubscription.listSubscriptionsByAPI(consumerCredentials);
                    } else if (subscriptionOption.equals("3")) {
                        APISubscription.listSubscriptionsByApplication(consumerCredentials);
                    } else if (subscriptionOption.equals("4")) {
                        APISubscription.removeSubscribedAPI(consumerCredentials);
                    }else{

                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }


        }else {

            try {
                System.out.println("Enter the path of the kubernetes file");
                String path = scanner.next();

                try {
                    String target = new String(path);
                    // String target = new String("mkdir stackOver");
                    Runtime rt = Runtime.getRuntime();
                    Process proc = rt.exec(target);
                    proc.waitFor();
                    StringBuffer output = new StringBuffer();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()));
                    String line = "";
                    while ((line = reader.readLine())!= null) {
                        output.append(line + "\n");
                    }
                    System.out.println("### " + output);
                } catch (Throwable t) {
                    t.printStackTrace();
                }

                System.exit(0);
            } catch (Exception e) {
                e.printStackTrace();
            }

        }

      /*  //If the zip file path is given continue API import
        if (StringUtils.isNotBlank(config.getZipFile())) {
            try {
                ///TODO method seperation
                //Handling API import
                APIImporter.importAPIs(config.getZipFile(), consumerCredentials);
            } catch (APIImportException e) {
                String errorMsg = "Unable to Import the APIs";
                log.error(errorMsg, e);
                System.exit(1);
            } catch (UtilException e) {
                String errorMsg = "Error occurred while importing the API/APIs";
                log.error(errorMsg, e);
                System.exit(1);
            }
        } else {
            if (StringUtils.isNotBlank(config.getApiFilePath()) &&
                    StringUtils.isBlank(config.getApiName())) {
                //If a cvs file provided and details about a specific API not provided as cli
                //arguments,bulkExport will be prioritize over single API export
                try {
                    APIExporter.bulkApiExport(consumerCredentials);
                } catch (APIExportException e) {
                    String errorMsg = "Error occurred while attempting to bulk export APIs";
                    log.error(errorMsg, e);
                    System.exit(1);
                }
            } else {
                try {
                    //Handling single API export if any of above conditions not satisfied, or both
                    // a api list and details about a single API is provided
                    if (StringUtils.isNotBlank(config.getApiName()) &&
                            StringUtils.isNotBlank(config.getApiFilePath())) {
                        log.warn("Only API " + config.getApiName() +
                                " will be exported and api list will be ignored");
                    }
                    APIExporter.singleApiExport(consumerCredentials);
                } catch (APIExportException e) {
                    log.error("Error occurred while exporting the API " + config.getApiName() +
                            "_" + config.getApiVersion(), e);
                    System.exit(1);
                }
            }
        }*/

    }

    /**
     * This method will get all the CLI inputs and map the properties in to a
     * ApiImportExportConfiguration object
     *
     * @param config ApiImportExportConfiguration type object
     */
    private static void setUserInputs(ApiImportExportConfiguration config) {
        //Getting configurations sent as command line parameters
        String name = System.getProperty(ImportExportConstants.API_NAME);
        String provider = System.getProperty(ImportExportConstants.API_PROVIDER);
        String version = System.getProperty(ImportExportConstants.API_VERSION);
        String configFile = System.getProperty(ImportExportConstants.CONFIG_FILE);
        String apiList = System.getProperty(ImportExportConstants.API_LIST);
        String log4jFile = System.getProperty(ImportExportConstants.LOG4J_PROP);
        String destinationPath = System.getProperty(ImportExportConstants.DESTINATION);
        String destinationFolderName = System.getProperty(ImportExportConstants.ZIP_NAME);
        String dcrUrlSegment = System.getProperty(ImportExportConstants.DCR_URL_SEG);
        String publisherUrlSegment = System.getProperty(ImportExportConstants.PUBLISHER_URL_SEG);
        String gatewayUrlSegment = System.getProperty(ImportExportConstants.GATEWAY_URL_SEG);
        String zipFile = System.getProperty(ImportExportConstants.ZIP_FILE_PROP);
        String updateApi = System.getProperty(ImportExportConstants.UPDATE_API_PROP);
        String clientName = System.getProperty(ImportExportConstants.CLIENT_NAME_PROP);

        //If a user config file given,overriding default configurations with it.
        if (StringUtils.isNotBlank(configFile)) {
            ImportExportUtils.setUserConfigurations(configFile, config);
        }

        //Setting the configurations taken from CLI
        if (StringUtils.isNotBlank(name)) {
            config.setApiName(name);
        }
        if (StringUtils.isNotBlank(provider)) {
            config.setApiProvider(provider);
        }
        if (StringUtils.isNotBlank(version)) {
            config.setApiVersion(version);
        }
        if (StringUtils.isNotBlank(apiList)) {
            config.setApiFilePath(apiList);
        }
        if (StringUtils.isNotBlank(log4jFile)) {
            config.setLog4JFilePath(log4jFile);
        }
        if (StringUtils.isNotBlank(destinationPath)) {
            config.setDestinationPath(destinationPath);
        }
        if (StringUtils.isNotBlank(destinationFolderName)) {
            config.setDestinationFolderName(destinationFolderName);
        }
        if (StringUtils.isNotBlank(dcrUrlSegment)) {
            config.setDcrUrl(dcrUrlSegment);
        }
        if (StringUtils.isNotBlank(publisherUrlSegment)) {
            config.setPublisherUrl(publisherUrlSegment);
        }
        if (StringUtils.isNotBlank(gatewayUrlSegment)) {
            config.setGatewayUrl(gatewayUrlSegment);
        }
        if (StringUtils.isNotBlank(zipFile)) {
            config.setZipFile(zipFile);
        }
        if (StringUtils.isNotBlank(updateApi)) {
            config.setUpdateApi(Boolean.parseBoolean(updateApi));
        }
        if (StringUtils.isNotBlank(clientName)) {
            config.setClientName(clientName);
        }

        //Validating publisher url
        boolean value = checkPublisherUrl(config.getPublisherUrl());

        while (!value) {
            //If publisher url incorrect, prompting user to re-enter
            System.out.print("Entered publisher url incorrect, check and re-enter : ");
            String url = scanner.next();
            config.setPublisherUrl(url);
            //Re-checking the publisher url
            value = checkPublisherUrl(config.getPublisherUrl());
        }

    }

}
