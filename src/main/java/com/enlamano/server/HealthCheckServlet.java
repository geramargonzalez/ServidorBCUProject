package com.enlamano.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.RuntimeMXBean;

/**
 * Servlet para verificar el estado del servidor
 */
public class HealthCheckServlet extends HttpServlet {
    
    private static final Logger logger = LoggerFactory.getLogger(HealthCheckServlet.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        // Configurar headers CORS
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type");
        
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        
        try {
            ObjectNode healthStatus = createHealthStatus();
            
            response.setStatus(HttpServletResponse.SC_OK);
            PrintWriter writer = response.getWriter();
            writer.write(healthStatus.toString());
            writer.flush();
            
        } catch (Exception e) {
            logger.error("Error generando health check", e);
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            
            ObjectNode errorResponse = objectMapper.createObjectNode();
            errorResponse.put("status", "ERROR");
            errorResponse.put("mensaje", "Error interno del servidor");
            
            PrintWriter writer = response.getWriter();
            writer.write(errorResponse.toString());
            writer.flush();
        }
    }
    
    private ObjectNode createHealthStatus() {
        ObjectNode health = objectMapper.createObjectNode();
        
        // Estado general
        health.put("status", "OK");
        health.put("servicio", "EnLaMano Axis Server");
        health.put("version", "1.0.0");
        health.put("timestamp", System.currentTimeMillis());
        
        // Información del sistema
        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
        ObjectNode sistema = health.putObject("sistema");
        sistema.put("uptime", runtime.getUptime());
        sistema.put("javaVersion", System.getProperty("java.version"));
        sistema.put("javaVendor", System.getProperty("java.vendor"));
        sistema.put("osName", System.getProperty("os.name"));
        sistema.put("osVersion", System.getProperty("os.version"));
        
        // Información de memoria
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        ObjectNode memoria = health.putObject("memoria");
        long heapUsed = memory.getHeapMemoryUsage().getUsed();
        long heapMax = memory.getHeapMemoryUsage().getMax();
        memoria.put("heapUsada", heapUsed);
        memoria.put("heapMaxima", heapMax);
        memoria.put("porcentajeUso", (heapUsed * 100.0) / heapMax);
        
        // Estado de servicios
        ObjectNode servicios = health.putObject("servicios");
        servicios.put("bcuSoapClient", verificarBcuClient());
        servicios.put("httpsEndpoint", "OK");
        servicios.put("jsonProcessor", "OK");
        
        // Información de conexiones
        ObjectNode conexiones = health.putObject("conexiones");
        conexiones.put("mtlsHabilitado", isMtlsEnabled());
        conexiones.put("certificadosConfigurados", verificarCertificados());
        
        return health;
    }
    
    private String verificarBcuClient() {
        try {
            // Verificar que el cliente SOAP puede inicializarse
            BcuSoapClient client = new BcuSoapClient();
            client.close();
            return "OK";
        } catch (Exception e) {
            logger.warn("Error verificando cliente BCU: {}", e.getMessage());
            return "ERROR: " + e.getMessage();
        }
    }
    
    private boolean isMtlsEnabled() {
        // Verificar si mTLS está configurado
        String keystorePath = "certificates/client-keystore.p12";
        return new java.io.File(keystorePath).exists();
    }
    
    private String verificarCertificados() {
        java.io.File clientKeystore = new java.io.File("certificates/client-keystore.p12");
        java.io.File serverKeystore = new java.io.File("certificates/server-keystore.jks");
        java.io.File truststore = new java.io.File("certificates/bcu-truststore.jks");
        
        int certificadosPresentes = 0;
        if (clientKeystore.exists()) certificadosPresentes++;
        if (serverKeystore.exists()) certificadosPresentes++;
        if (truststore.exists()) certificadosPresentes++;
        
        return certificadosPresentes + "/3 certificados presentes";
    }
    
    @Override
    protected void doOptions(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        // Configurar headers CORS para preflight requests
        response.setHeader("Access-Control-Allow-Origin", "*");
        response.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        response.setHeader("Access-Control-Allow-Headers", "Content-Type");
        response.setStatus(HttpServletResponse.SC_OK);
    }
}
