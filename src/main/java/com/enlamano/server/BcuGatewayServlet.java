package com.enlamano.server;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * Servlet que actúa como gateway entre NetSuite (JSON/HTTPS) y BCU (SOAP)
 */
public class BcuGatewayServlet extends HttpServlet {
    
    private static final Logger logger = LoggerFactory.getLogger(BcuGatewayServlet.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final BcuSoapClient bcuSoapClient;
    
    public BcuGatewayServlet() {
        this.bcuSoapClient = new BcuSoapClient();
    }
    
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        logger.info("Recibiendo petición de NetSuite: {}", request.getRequestURI());
        
        try {
            // Configurar headers CORS
            response.setHeader("Access-Control-Allow-Origin", "*");
            response.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            response.setHeader("Access-Control-Allow-Headers", "Content-Type");
            
            // Configurar respuesta JSON
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            
            // Leer petición JSON de NetSuite
            JsonNode requestJson = readJsonRequest(request);
            logger.debug("JSON recibido: {}", requestJson.toString());
            
            // Validar petición
            validateRequest(requestJson);
            
            // Determinar tipo de consulta
            String tipoConsulta = requestJson.path("tipoConsulta").asText();
            
            ObjectNode responseJson;
            switch (tipoConsulta) {
                case "cotizacion":
                    responseJson = procesarConsultaCotizacion(requestJson);
                    break;
                case "arbitraje":
                    responseJson = procesarConsultaArbitraje(requestJson);
                    break;
                case "historico":
                    responseJson = procesarConsultaHistorico(requestJson);
                    break;
                default:
                    throw new IllegalArgumentException("Tipo de consulta no soportado: " + tipoConsulta);
            }
            
            // Enviar respuesta
            response.setStatus(HttpServletResponse.SC_OK);
            PrintWriter writer = response.getWriter();
            writer.write(responseJson.toString());
            writer.flush();
            
            logger.info("Respuesta enviada exitosamente");
            
        } catch (Exception e) {
            logger.error("Error procesando petición", e);
            handleError(response, e);
        }
    }
    
    private JsonNode readJsonRequest(HttpServletRequest request) throws IOException {
        StringBuilder jsonBuilder = new StringBuilder();
        try (BufferedReader reader = request.getReader()) {
            String line;
            while ((line = reader.readLine()) != null) {
                jsonBuilder.append(line);
            }
        }
        
        String jsonString = jsonBuilder.toString();
        if (jsonString.trim().isEmpty()) {
            throw new IllegalArgumentException("Petición JSON vacía");
        }
        
        return objectMapper.readTree(jsonString);
    }
    
    private void validateRequest(JsonNode requestJson) {
        if (!requestJson.has("tipoConsulta")) {
            throw new IllegalArgumentException("Campo 'tipoConsulta' requerido");
        }
        
        if (!requestJson.has("parametros")) {
            throw new IllegalArgumentException("Campo 'parametros' requerido");
        }
    }
    
    private ObjectNode procesarConsultaCotizacion(JsonNode requestJson) throws Exception {
        logger.info("Procesando consulta de cotización");
        
        JsonNode parametros = requestJson.path("parametros");
        String moneda = parametros.path("moneda").asText();
        String fecha = parametros.path("fecha").asText();
        
        // Llamar al web service SOAP del BCU
        BcuSoapResponse soapResponse = bcuSoapClient.consultarCotizacion(moneda, fecha);
        
        // Consolidar respuesta JSON
        ObjectNode response = objectMapper.createObjectNode();
        response.put("status", "success");
        response.put("tipoConsulta", "cotizacion");
        
        ObjectNode datos = response.putObject("datos");
        datos.put("moneda", soapResponse.getMoneda());
        datos.put("fecha", soapResponse.getFecha());
        datos.put("compra", soapResponse.getCompra());
        datos.put("venta", soapResponse.getVenta());
        datos.put("fechaConsulta", soapResponse.getFechaConsulta());
        
        // Información adicional para NetSuite
        ObjectNode metadatos = response.putObject("metadatos");
        metadatos.put("fuente", "BCU");
        metadatos.put("procesadoEn", System.currentTimeMillis());
        metadatos.put("version", "1.0");
        
        return response;
    }
    
    private ObjectNode procesarConsultaArbitraje(JsonNode requestJson) throws Exception {
        logger.info("Procesando consulta de arbitraje");
        
        JsonNode parametros = requestJson.path("parametros");
        String monedaOrigen = parametros.path("monedaOrigen").asText();
        String monedaDestino = parametros.path("monedaDestino").asText();
        String fecha = parametros.path("fecha").asText();
        
        // Llamar al web service SOAP del BCU para ambas monedas
        BcuSoapResponse cotizacionOrigen = bcuSoapClient.consultarCotizacion(monedaOrigen, fecha);
        BcuSoapResponse cotizacionDestino = bcuSoapClient.consultarCotizacion(monedaDestino, fecha);
        
        // Calcular arbitraje
        ObjectNode response = objectMapper.createObjectNode();
        response.put("status", "success");
        response.put("tipoConsulta", "arbitraje");
        
        ObjectNode datos = response.putObject("datos");
        datos.put("monedaOrigen", monedaOrigen);
        datos.put("monedaDestino", monedaDestino);
        datos.put("fecha", fecha);
        
        // Calcular tasas de arbitraje
        double tasaArbitraje = cotizacionDestino.getVenta() / cotizacionOrigen.getCompra();
        datos.put("tasaArbitraje", tasaArbitraje);
        
        ObjectNode origen = datos.putObject("cotizacionOrigen");
        origen.put("compra", cotizacionOrigen.getCompra());
        origen.put("venta", cotizacionOrigen.getVenta());
        
        ObjectNode destino = datos.putObject("cotizacionDestino");
        destino.put("compra", cotizacionDestino.getCompra());
        destino.put("venta", cotizacionDestino.getVenta());
        
        return response;
    }
    
    private ObjectNode procesarConsultaHistorico(JsonNode requestJson) throws Exception {
        logger.info("Procesando consulta histórica");
        
        JsonNode parametros = requestJson.path("parametros");
        String moneda = parametros.path("moneda").asText();
        String fechaInicio = parametros.path("fechaInicio").asText();
        String fechaFin = parametros.path("fechaFin").asText();
        
        // Llamar al web service SOAP del BCU para datos históricos
        BcuSoapResponse[] historicos = bcuSoapClient.consultarHistorico(moneda, fechaInicio, fechaFin);
        
        ObjectNode response = objectMapper.createObjectNode();
        response.put("status", "success");
        response.put("tipoConsulta", "historico");
        
        ObjectNode datos = response.putObject("datos");
        datos.put("moneda", moneda);
        datos.put("fechaInicio", fechaInicio);
        datos.put("fechaFin", fechaFin);
        datos.put("totalRegistros", historicos.length);
        
        // Agregar serie histórica
        ArrayNode serieHistorica = datos.putArray("serie");
        for (BcuSoapResponse historico : historicos) {
            ObjectNode registro = serieHistorica.addObject();
            registro.put("fecha", historico.getFecha());
            registro.put("compra", historico.getCompra());
            registro.put("venta", historico.getVenta());
        }
        
        return response;
    }
    
    private void handleError(HttpServletResponse response, Exception e) throws IOException {
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        response.setContentType("application/json");
        
        ObjectNode errorResponse = objectMapper.createObjectNode();
        errorResponse.put("status", "error");
        errorResponse.put("mensaje", e.getMessage());
        errorResponse.put("codigo", "ERR_INTERNAL");
        errorResponse.put("timestamp", System.currentTimeMillis());
        
        PrintWriter writer = response.getWriter();
        writer.write(errorResponse.toString());
        writer.flush();
    }
    
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) 
            throws ServletException, IOException {
        
        response.setContentType("application/json");
        response.setStatus(HttpServletResponse.SC_OK);
        
        ObjectNode info = objectMapper.createObjectNode();
        info.put("servicio", "Gateway BCU");
        info.put("version", "1.0");
        info.put("descripcion", "Endpoint para consultas al BCU via SOAP");
        
        ArrayNode endpoints = info.putArray("endpoints");
        endpoints.add("POST /api/bcu/consulta - Realizar consulta al BCU");
        
        PrintWriter writer = response.getWriter();
        writer.write(info.toString());
        writer.flush();
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
