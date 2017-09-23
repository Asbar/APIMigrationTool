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

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.config.Registry;
import org.apache.http.config.RegistryBuilder;
import org.apache.http.conn.socket.ConnectionSocketFactory;
import org.apache.http.conn.socket.PlainConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.SSLContextBuilder;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.conn.ssl.X509HostnameVerifier;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;


public class HttpClientGenerator {

    private static final Log log = LogFactory.getLog(HttpClientGenerator.class);

    /**
     * Returns a closableHttpClient from a connection manager pool
     *
     * @return a closable http client
     */

    public static CloseableHttpClient getHttpClient() throws UtilException {


            HttpClientBuilder b = HttpClientBuilder.create();

            // Setup a Trust Strategy that allows all certificates.
            SSLContext sslContext;
            try {
                sslContext = new SSLContextBuilder().loadTrustMaterial(null, new TrustStrategy() {
                    public boolean isTrusted(X509Certificate[] arg0, String arg1)
                            throws CertificateException {
                        return true;
                    }
                }).build();
            } catch (NoSuchAlgorithmException | KeyManagementException | KeyStoreException e) {
                String errorMsg = "error occurred while disabling SSL certificate validation";
                log.error(errorMsg, e);
                throw new UtilException(errorMsg, e);
            }
            b.setSslcontext(sslContext);

            // Allow all Hostnames
            HostnameVerifier hostnameVerifier =
                    SSLConnectionSocketFactory.ALLOW_ALL_HOSTNAME_VERIFIER;

            //       Create an SSL Socket Factory, to use weakened "trust strategy";
            //       and create a Registry, to register it.
            SSLConnectionSocketFactory sslSocketFactory = new SSLConnectionSocketFactory(sslContext,
                    (X509HostnameVerifier) hostnameVerifier);
            Registry<ConnectionSocketFactory> socketFactoryRegistry =
                    RegistryBuilder.<ConnectionSocketFactory>create()
                            .register("http", PlainConnectionSocketFactory.getSocketFactory())
                            .register("https", sslSocketFactory)
                            .build();

            // Creating connection-manager using Registry.
            //      -- allows multi-threaded use
            PoolingHttpClientConnectionManager connMgr =
                    new PoolingHttpClientConnectionManager(socketFactoryRegistry);
            b.setConnectionManager(connMgr);

            // Finally, build the HttpClient;
            CloseableHttpClient client = b.build();
            return client;
    }
}
