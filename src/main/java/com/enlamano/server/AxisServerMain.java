package com.enlamano.server;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.SecureRequestCustomizer;
import org.eclipse.jetty.server.SslConnectionFactory;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.http.HttpServlet;
import java.io.File;

/**
 * Clase principal del servidor Axis que expone endpoints HTTPS/JSON
 * para comunicación con NetSuite y conexión SOAP con BCU
 */
public class AxisServerMain {
    
    private static final Logger logger = LoggerFactory.getLogger(AxisServerMain.class);
    
    private static final int HTTPS_PORT = Integer.parseInt(System.getProperty("server.port", "8443"));
    private static final int HTTP_PORT = Integer.parseInt(System.getProperty("server.http.port", "8080"));
    private static final String KEYSTORE_PATH = System.getProperty("server.keystore.path", "certificates/server-keystore.jks");
    private static final String KEYSTORE_PASSWORD = System.getProperty("server.keystore.password", "changeit");
    private static final String KEY_PASSWORD = System.getProperty("server.key.password", "changeit");
    
    public static void main(String[] args) {
        try {
            AxisServerMain serverMain = new AxisServerMain();
            serverMain.startServer();
        } catch (Exception e) {
            logger.error("Error iniciando el servidor", e);
            System.exit(1);
        }
    }
    
    public void startServer() throws Exception {
        logger.info("Iniciando servidor Axis EnLaMano...");
        
        // Crear servidor Jetty
        Server server = new Server();
        
        // Configurar HTTP (para desarrollo)
        configureHttp(server);
        
        // Configurar HTTPS
        configureHttps(server);
        
        // Configurar contexto de servlets
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");
        server.setHandler(context);
        
        // Registrar servlets
        registerServlets(context);
        
        // Iniciar servidor
        server.start();
        logger.info("Servidor iniciado en:");
        logger.info("  - HTTP:  http://localhost:{}", HTTP_PORT);
        logger.info("  - HTTPS: https://localhost:{}", HTTPS_PORT);
        logger.info("Endpoints disponibles:");
        logger.info("  - POST http://localhost:{}/api/bcu/consulta", HTTP_PORT);
        logger.info("  - POST https://localhost:{}/api/bcu/consulta", HTTPS_PORT);
        logger.info("  - GET  http://localhost:{}/api/health", HTTP_PORT);
        logger.info("  - GET  https://localhost:{}/api/health", HTTPS_PORT);
        
        server.join();
    }
    
    private void configureHttp(Server server) {
        // Configuración HTTP básica para desarrollo
        HttpConfiguration httpConfig = new HttpConfiguration();
        httpConfig.setSecureScheme("https");
        httpConfig.setSecurePort(HTTPS_PORT);
        httpConfig.setOutputBufferSize(32768);
        
        // Conector HTTP
        ServerConnector httpConnector = new ServerConnector(server, new HttpConnectionFactory(httpConfig));
        httpConnector.setPort(HTTP_PORT);
        httpConnector.setIdleTimeout(30000);
        
        server.addConnector(httpConnector);
    }
    
    private void configureHttps(Server server) throws Exception {
        // Configuración HTTP
        HttpConfiguration httpConfig = new HttpConfiguration();
        httpConfig.setSecureScheme("https");
        httpConfig.setSecurePort(HTTPS_PORT);
        httpConfig.addCustomizer(new SecureRequestCustomizer());
        
        // Configuración SSL
        SslContextFactory.Server sslContextFactory = new SslContextFactory.Server();
        
        // Verificar si existe el keystore
        File keystoreFile = new File(KEYSTORE_PATH);
        if (!keystoreFile.exists()) {
            logger.warn("Keystore no encontrado en: {}. Se creará uno temporal.", KEYSTORE_PATH);
            createSelfSignedKeystore();
        }
        
        sslContextFactory.setKeyStorePath(KEYSTORE_PATH);
        sslContextFactory.setKeyStorePassword(KEYSTORE_PASSWORD);
        sslContextFactory.setKeyManagerPassword(KEY_PASSWORD);
        
        // Configurar cipher suites seguros
        sslContextFactory.setIncludeCipherSuites(
            "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384",
            "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256",
            "TLS_RSA_WITH_AES_256_GCM_SHA384",
            "TLS_RSA_WITH_AES_128_GCM_SHA256"
        );
        
        // Crear conector HTTPS
        ServerConnector httpsConnector = new ServerConnector(server,
            new SslConnectionFactory(sslContextFactory, "http/1.1"),
            new HttpConnectionFactory(httpConfig));
        httpsConnector.setPort(HTTPS_PORT);
        
        server.addConnector(httpsConnector);
    }
    
    private void registerServlets(ServletContextHandler context) {
        // Servlet principal para comunicación con BCU
        HttpServlet bcuServlet = new BcuGatewayServlet();
        context.addServlet(new ServletHolder(bcuServlet), "/api/bcu/*");
        
        // Servlet de health check
        HttpServlet healthServlet = new HealthCheckServlet();
        context.addServlet(new ServletHolder(healthServlet), "/api/health");
        
        logger.info("Servlets registrados correctamente");
    }
    
    private void createSelfSignedKeystore() {
        logger.info("Creando keystore auto-firmado temporal...");
        // En producción, usar certificados reales
        // Por ahora, documentar la necesidad de configurar certificados apropiados
        logger.warn("ADVERTENCIA: Es necesario configurar certificados SSL apropiados para producción");
    }
}
