package com.taoyuanx.littlefile.client.impl;


import com.taoyuanx.littlefile.client.core.ClientConfig;
import com.taoyuanx.littlefile.client.core.FastFileClientFactory;
import com.taoyuanx.littlefile.client.core.FdfsFileClientConstant;
import com.taoyuanx.littlefile.client.impl.security.TokenInterceptor;
import com.taoyuanx.littlefile.fdfshttp.core.client.FileClient;
import lombok.extern.slf4j.Slf4j;
import okhttp3.ConnectionPool;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;

import javax.net.ssl.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 *
 */
@Slf4j
public class DefaultSingletonFastFileClientFactory implements FastFileClientFactory {
    private ClientConfig config;


    public DefaultSingletonFastFileClientFactory(ClientConfig config) {
        this.config = config;
    }

    public DefaultSingletonFastFileClientFactory() {
        this.config = new ClientConfig(ClientConfig.DEFAULT_CONFIG);
    }

    private static FileClient fileClient;

    @Override
    public FileClient getFileClient() {
        if (null == fileClient) {
            synchronized (DefaultSingletonFastFileClientFactory.class) {
                if (null == fileClient) {
                    init();
                }
            }
        }
        return fileClient;
    }

    /**
     * 初始化
     */
    private void init() {
        try {
            //1 初始化http client
            SSLParams sslParams = new SSLParams();
            SSLContext sslContext = SSLContext.getInstance("TLS");
            X509TrustManager trustManager = new UnSafeTrustManager();
            sslContext.init(null, new TrustManager[]{trustManager}, null);
            sslParams.sSLSocketFactory = sslContext.getSocketFactory();
            sslParams.trustManager = trustManager;
            Interceptor tokenInterceptor = new TokenInterceptor(config.getToken());
            OkHttpClient client = new OkHttpClient().newBuilder().hostnameVerifier(new HostnameVerifier() {
                @Override
                public boolean verify(String hostname, SSLSession session) {
                    //强行返回true 即验证成功
                    return true;
                }
            }).sslSocketFactory(sslParams.sSLSocketFactory, sslParams.trustManager)
                    .connectTimeout(config.getConnectTimeout(), TimeUnit.SECONDS)//连接超时时间
                    .connectionPool(new ConnectionPool(config.getMaxIdleConnections(), config.getKeepAliveDuration(), TimeUnit.SECONDS))//连接数量,保持连接时间
                    .retryOnConnectionFailure(false)//重试策略
                    .addInterceptor(tokenInterceptor)
                    .build();


            List<FdfsFileClientConstant.FdfsApi> fdfsApiList = Arrays.asList(FdfsFileClientConstant.FdfsApi.values());
            String fileClientBaseUrl = config.getFdfsHttpBaseUrl();
            Map<FdfsFileClientConstant.FdfsApi, String> apiMap = new HashMap(fdfsApiList.size());
            fdfsApiList.stream().forEach(api -> {
                apiMap.put(api, fileClientBaseUrl + api.path);
            });
            fileClient = new FileClientImpl(client, apiMap);
        } catch (Exception e) {
            log.error("初始化失败", e);
        }
    }


    public static class SSLParams {
        public SSLSocketFactory sSLSocketFactory;
        public X509TrustManager trustManager;
    }

    private static class UnSafeTrustManager implements X509TrustManager {
        @Override
        public void checkClientTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {
        }

        @Override
        public void checkServerTrusted(X509Certificate[] chain, String authType)
                throws CertificateException {
        }

        @Override
        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[]{};
        }
    }

}