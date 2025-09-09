package com.enlamano.server;

import org.apache.axis2.AxisFault;
import org.apache.axis2.client.ServiceClient;
import org.apache.axis2.client.Options;
import org.apache.axis2.context.ConfigurationContext;
import org.apache.axis2.context.ConfigurationContextFactory;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.OMFactory;
import org.apache.axiom.om.OMAbstractFactory;
import org.apache.axiom.om.OMNamespace;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.namespace.QName;
import javax.net.ssl.*;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;

/**
 * Cliente SOAP para comunicación con el Banco Central del Uruguay (BCU)
 * Soporta mTLS para autenticación mutua
 */
public class BcuSoapClient {
    
    private static final Logger logger = LoggerFactory.getLogger(BcuSoapClient.class);
    
    // Constantes de configuración
    private static final String BCU_WSDL_URL = "https://webservices.bcu.gub.uy/ArbitrajeServicio/AWArbitrajes.svc?wsdl";
    private static final String BCU_ENDPOINT_URL = "https://webservices.bcu.gub.uy/ArbitrajeServicio/AWArbitrajes.svc";
    private static final String BCU_NAMESPACE = "http://tempuri.org/";
    private static final String SOAP_ACTION_COTIZACION = "http://tempuri.org/IArbitrajeServicio/ConsultarCotizacion";
    private static final String SOAP_ACTION_HISTORICO = "http://tempuri.org/IArbitrajeServicio/ConsultarHistorico";
    
    // Configuración mTLS
    private static final String CLIENT_KEYSTORE_PATH = "certificates/client-keystore.p12";
    private static final String CLIENT_KEYSTORE_PASSWORD = "changeit";
    private static final String TRUSTSTORE_PATH = "certificates/bcu-truststore.jks";
    private static final String TRUSTSTORE_PASSWORD = "changeit";
    
    private final Properties config;
    private ServiceClient serviceClient;
    private boolean mtlsEnabled;
    
    public BcuSoapClient() {
        this.config = loadConfiguration();
        this.mtlsEnabled = Boolean.parseBoolean(config.getProperty("mtls.enabled", "false"));
        initializeServiceClient();
    }
    
    /**
     * Consulta cotización de una moneda en una fecha específica
     */
    public BcuSoapResponse consultarCotizacion(String moneda, String fecha) throws Exception {
        logger.info("Consultando cotización: moneda={}, fecha={}", moneda, fecha);
        
        try {
            OMElement request = buildCotizacionRequest(moneda, fecha);
            OMElement response = serviceClient.sendReceive(request);
            
            return parseCotizacionResponse(response);
            
        } catch (Exception e) {
            logger.error("Error conectando con el BCU", e);
            String errorMessage = buildErrorMessage(e);
            throw new Exception(errorMessage, e);
        }
    }
    
    /**
     * Consulta datos históricos de una moneda en un rango de fechas
     */
    public BcuSoapResponse[] consultarHistorico(String moneda, String fechaInicio, String fechaFin) throws Exception {
        logger.info("Consultando histórico: moneda={}, desde={}, hasta={}", moneda, fechaInicio, fechaFin);
        
        try {
            OMElement request = buildHistoricoRequest(moneda, fechaInicio, fechaFin);
            OMElement response = serviceClient.sendReceive(request);
            
            return parseHistoricoResponse(response);
            
        } catch (Exception e) {
            logger.error("Error conectando con el BCU para consulta histórica", e);
            String errorMessage = buildErrorMessage(e);
            throw new Exception(errorMessage, e);
        }
    }
    
    private void initializeServiceClient() {
        try {
            ConfigurationContext context = ConfigurationContextFactory.createDefaultConfigurationContext();
            serviceClient = new ServiceClient(context, null);
            
            Options options = new Options();
            options.setTo(new org.apache.axis2.addressing.EndpointReference(BCU_ENDPOINT_URL));
            options.setTransportInProtocol("https");
            
            // Configurar timeouts
            options.setTimeOutInMilliSeconds(30000); // 30 segundos
            options.setProperty("SO_TIMEOUT", 30000);
            options.setProperty("CONNECTION_TIMEOUT", 10000);
            
            // Configurar mTLS si está habilitado
            if (mtlsEnabled) {
                configureMutualTLS(options);
            } else {
                // Configurar SSL básico (solo para desarrollo)
                configureBasicSSL(options);
            }
            
            serviceClient.setOptions(options);
            
            logger.info("Cliente SOAP inicializado correctamente. mTLS: {}", mtlsEnabled);
            
        } catch (Exception e) {
            logger.error("Error inicializando cliente SOAP", e);
            throw new RuntimeException("No se pudo inicializar el cliente SOAP", e);
        }
    }
    
    private void configureMutualTLS(Options options) throws Exception {
        logger.info("Configurando mTLS para comunicación con BCU");
        
        // Cargar keystore del cliente (certificado para autenticación)
        KeyStore clientKeyStore = KeyStore.getInstance("PKCS12");
        try (FileInputStream keyStoreFile = new FileInputStream(CLIENT_KEYSTORE_PATH)) {
            clientKeyStore.load(keyStoreFile, CLIENT_KEYSTORE_PASSWORD.toCharArray());
        }
        
        // Cargar truststore (certificados confiables del BCU)
        KeyStore trustStore = KeyStore.getInstance("JKS");
        try (FileInputStream trustStoreFile = new FileInputStream(TRUSTSTORE_PATH)) {
            trustStore.load(trustStoreFile, TRUSTSTORE_PASSWORD.toCharArray());
        }
        
        // Configurar KeyManager para autenticación del cliente
        KeyManagerFactory keyManagerFactory = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        keyManagerFactory.init(clientKeyStore, CLIENT_KEYSTORE_PASSWORD.toCharArray());
        
        // Configurar TrustManager para validar certificados del servidor
        TrustManagerFactory trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        trustManagerFactory.init(trustStore);
        
        // Crear contexto SSL
        SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(keyManagerFactory.getKeyManagers(), trustManagerFactory.getTrustManagers(), null);
        
        // Configurar factory SSL personalizada
        System.setProperty("axis2.ssl.protocol", "TLS");
        System.setProperty("javax.net.ssl.keyStore", CLIENT_KEYSTORE_PATH);
        System.setProperty("javax.net.ssl.keyStorePassword", CLIENT_KEYSTORE_PASSWORD);
        System.setProperty("javax.net.ssl.trustStore", TRUSTSTORE_PATH);
        System.setProperty("javax.net.ssl.trustStorePassword", TRUSTSTORE_PASSWORD);
        
        logger.info("mTLS configurado correctamente");
    }
    
    private void configureBasicSSL(Options options) {
        logger.warn("Configurando SSL básico - SOLO PARA DESARROLLO");
        
        // Para desarrollo: aceptar todos los certificados (NO USAR EN PRODUCCIÓN)
        TrustManager[] trustAllCerts = new TrustManager[] {
            new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() { return null; }
                public void checkClientTrusted(X509Certificate[] certs, String authType) { }
                public void checkServerTrusted(X509Certificate[] certs, String authType) { }
            }
        };
        
        try {
            SSLContext sslContext = SSLContext.getInstance("SSL");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier(new HostnameVerifier() {
                @Override
                public boolean verify(String hostname, SSLSession session) {
                    return true;
                }
            });
        } catch (Exception e) {
            logger.error("Error configurando SSL básico", e);
        }
    }
    
    private OMElement buildCotizacionRequest(String moneda, String fecha) {
        OMFactory factory = OMAbstractFactory.getOMFactory();
        OMNamespace namespace = factory.createOMNamespace(BCU_NAMESPACE, "tns");
        
        OMElement request = factory.createOMElement("ConsultarCotizacion", namespace);
        
        OMElement monedaElement = factory.createOMElement("moneda", namespace);
        monedaElement.setText(moneda);
        request.addChild(monedaElement);
        
        OMElement fechaElement = factory.createOMElement("fecha", namespace);
        fechaElement.setText(fecha);
        request.addChild(fechaElement);
        
        return request;
    }
    
    private OMElement buildHistoricoRequest(String moneda, String fechaInicio, String fechaFin) {
        OMFactory factory = OMAbstractFactory.getOMFactory();
        OMNamespace namespace = factory.createOMNamespace(BCU_NAMESPACE, "tns");
        
        OMElement request = factory.createOMElement("ConsultarHistorico", namespace);
        
        OMElement monedaElement = factory.createOMElement("moneda", namespace);
        monedaElement.setText(moneda);
        request.addChild(monedaElement);
        
        OMElement fechaInicioElement = factory.createOMElement("fechaInicio", namespace);
        fechaInicioElement.setText(fechaInicio);
        request.addChild(fechaInicioElement);
        
        OMElement fechaFinElement = factory.createOMElement("fechaFin", namespace);
        fechaFinElement.setText(fechaFin);
        request.addChild(fechaFinElement);
        
        return request;
    }
    
    private BcuSoapResponse parseCotizacionResponse(OMElement response) {
        logger.debug("Parseando respuesta de cotización: {}", response.toString());
        
        try {
            BcuSoapResponse bcuResponse = new BcuSoapResponse();
            
            // Buscar los elementos en la respuesta SOAP del BCU
            OMElement resultElement = response.getFirstChildWithName(
                new QName(BCU_NAMESPACE, "ConsultarCotizacionResult"));
            
            if (resultElement != null) {
                // Parsear respuesta real del BCU
                OMElement monedaElement = resultElement.getFirstChildWithName(new QName("Moneda"));
                OMElement fechaElement = resultElement.getFirstChildWithName(new QName("Fecha"));
                OMElement compraElement = resultElement.getFirstChildWithName(new QName("TipoCambioCompra"));
                OMElement ventaElement = resultElement.getFirstChildWithName(new QName("TipoCambioVenta"));
                
                if (monedaElement != null) bcuResponse.setMoneda(monedaElement.getText());
                if (fechaElement != null) bcuResponse.setFecha(fechaElement.getText());
                if (compraElement != null) bcuResponse.setCompra(Double.parseDouble(compraElement.getText()));
                if (ventaElement != null) bcuResponse.setVenta(Double.parseDouble(ventaElement.getText()));
                
                bcuResponse.setFechaConsulta(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
                
                logger.info("Cotización parseada exitosamente: {} = {} / {}", 
                    bcuResponse.getMoneda(), bcuResponse.getCompra(), bcuResponse.getVenta());
                
            } else {
                String errorMsg = "No se encontró resultado válido en la respuesta del BCU";
                logger.error(errorMsg);
                throw new RuntimeException(errorMsg);
            }
            
            return bcuResponse;
            
        } catch (Exception e) {
            logger.warn("No se encontró resultado en respuesta del BCU, error de parseo", e);
            throw new RuntimeException("Error procesando respuesta del BCU: " + e.getMessage(), e);
        }
    }
    
    private String buildErrorMessage(Exception e) {
        if (e.getCause() instanceof java.net.UnknownHostException) {
            return "No se puede conectar al Banco Central del Uruguay. " +
                   "Verifique la conexión a internet y que el servicio del BCU esté disponible. " +
                   "Host: webservices.bcu.gub.uy";
        } else if (e.getCause() instanceof java.net.ConnectException) {
            return "El servicio web del BCU no está disponible temporalmente. " +
                   "Intente nuevamente en unos minutos.";
        } else if (e.getCause() instanceof java.net.SocketTimeoutException) {
            return "Tiempo de espera agotado conectando al BCU. " +
                   "El servicio puede estar sobrecargado, intente nuevamente.";
        } else if (e instanceof org.apache.axis2.AxisFault) {
            return "Error en la comunicación SOAP con el BCU: " + e.getMessage() + 
                   ". Verifique que el servicio esté disponible.";
        } else {
            return "Error inesperado consultando el BCU: " + e.getMessage() + 
                   ". Contacte al administrador del sistema.";
        }
    }
    
    private BcuSoapResponse[] parseHistoricoResponse(OMElement response) {
        logger.debug("Parseando respuesta histórica: {}", response.toString());
        
        try {
            // Buscar elementos en la respuesta SOAP del BCU para datos históricos
            OMElement resultElement = response.getFirstChildWithName(
                new QName(BCU_NAMESPACE, "ConsultarHistoricoResult"));
                
            if (resultElement != null) {
                // Aquí se parseará la respuesta real del BCU cuando esté disponible
                // Por ahora lanzamos excepción para implementar posteriormente
                throw new Exception("Parseo de respuesta histórica real del BCU pendiente de implementar");
            } else {
                throw new Exception("No se encontraron datos históricos en la respuesta del BCU");
            }
            
        } catch (Exception e) {
            logger.error("Error parseando respuesta histórica del BCU", e);
            throw new RuntimeException("Error procesando datos históricos del BCU: " + e.getMessage(), e);
        }
    }
    
    private Properties loadConfiguration() {
        Properties props = new Properties();
        try (FileInputStream configFile = new FileInputStream("src/main/resources/bcu-config.properties")) {
            props.load(configFile);
        } catch (IOException e) {
            logger.warn("No se pudo cargar configuración desde archivo, usando valores por defecto");
            // Valores por defecto
            props.setProperty("mtls.enabled", "false");
            props.setProperty("bcu.endpoint", BCU_ENDPOINT_URL);
            props.setProperty("connection.timeout", "10000");
            props.setProperty("socket.timeout", "30000");
        }
        return props;
    }
    
    public void close() {
        try {
            if (serviceClient != null) {
                serviceClient.cleanup();
            }
        } catch (AxisFault e) {
            logger.error("Error cerrando cliente SOAP", e);
        }
    }
}
