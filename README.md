# EnLaMano Axis Server

Servidor Java basado en Apache Axis2 que actúa como gateway entre NetSuite (JSON/HTTPS) y el Banco Central del Uruguay (SOAP/mTLS).

## Funcionalidades

1. **Endpoint HTTPS/JSON**: Expone un servicio REST para que lo consuma NetSuite
2. **Cliente SOAP**: Se conecta al web service del BCU usando Apache Axis2
3. **Soporte mTLS**: Autenticación mutua con certificados (configurable)
4. **Consolidación JSON**: Transforma respuestas SOAP en JSON optimizado para NetSuite

## Arquitectura

```
NetSuite (RESTlet) 
    ↓ HTTPS/JSON
┌─────────────────────┐
│   Axis Server       │
│   (Puerto 8443)     │
│                     │
│  ┌──────────────┐   │
│  │ JSON Gateway │   │ ← BcuGatewayServlet
│  └──────────────┘   │
│          ↓          │
│  ┌──────────────┐   │
│  │ SOAP Client  │   │ ← BcuSoapClient
│  └──────────────┘   │
└─────────────────────┘
    ↓ SOAP/mTLS
BCU Web Services
```

## Requisitos

- Java 11 o superior
- Maven 3.6+
- Certificados SSL (se incluye script para generar certificados de desarrollo)

## Instalación y Configuración

### 1. Instalar Maven

**Windows:**
1. Descargar Maven desde https://maven.apache.org/download.cgi
2. Extraer el archivo ZIP
3. Agregar `MAVEN_HOME` y `%MAVEN_HOME%\bin` al PATH
4. Verificar instalación: `mvn --version`

**Linux/Mac:**
```bash
# Ubuntu/Debian
sudo apt-get install maven

# macOS con Homebrew
brew install maven
```

### 2. Compilar el proyecto

```bash
mvn clean compile
```

### 2. Generar certificados de desarrollo

```bash
# En Windows


# En Linux/Mac
chmod +x generate-certificates.sh
./generate-certificates.sh
```

### 3. Configurar conexión con BCU

Editar `src/main/resources/bcu-config.properties`:

```properties
# Para producción, habilitar mTLS
mtls.enabled=true

# URLs reales del BCU
bcu.endpoint=https://webservices.bcu.gub.uy/ArbitrajeServicio/AWArbitrajes.svc

# Rutas a certificados reales
client.keystore.path=certificates/client-keystore.p12
client.keystore.password=password_real
```

### 4. Ejecutar el servidor

**Opción A: Puerto por defecto (8443)**
```bash
mvn exec:java -Dexec.mainClass="com.enlamano.server.AxisServerMain"
```

**Opción B: Puerto personalizado**
```bash
java -Dserver.port=9443 -jar target/axis-server-1.0.0-jar-with-dependencies.jar
```

**Opción C: Solo red local (más seguro)**
```bash
java -Dserver.host=127.0.0.1 -Dserver.port=8443 -jar target/axis-server-1.0.0-jar-with-dependencies.jar
```

Ver `CONFIGURACION-DESPLIEGUE.md` para opciones detalladas de arquitectura.

## Endpoints

### 1. Consulta al BCU
```
POST https://localhost:8443/api/bcu/consulta
Content-Type: application/json

{
  "tipoConsulta": "cotizacion",
  "parametros": {
    "moneda": "USD",
    "fecha": "2024-03-15"
  }
}
```

**Respuesta:**
```json
{
  "status": "success",
  "tipoConsulta": "cotizacion",
  "datos": {
    "moneda": "USD",
    "fecha": "2024-03-15",
    "compra": 42.50,
    "venta": 44.20,
    "fechaConsulta": "2024-03-15 10:30:00"
  },
  "metadatos": {
    "fuente": "BCU",
    "procesadoEn": 1710505800000,
    "version": "1.0"
  }
}
```

### 2. Consulta de Arbitraje
```json
{
  "tipoConsulta": "arbitraje",
  "parametros": {
    "monedaOrigen": "USD",
    "monedaDestino": "EUR",
    "fecha": "2024-03-15"
  }
}
```

### 3. Consulta Histórica
```json
{
  "tipoConsulta": "historico",
  "parametros": {
    "moneda": "USD",
    "fechaInicio": "2024-03-01",
    "fechaFin": "2024-03-15"
  }
}
```

### 4. Health Check
```
GET https://localhost:8443/api/health
```

## Integración con NetSuite

### RESTlet de NetSuite (ejemplo)

```javascript
/**
 * @NApiVersion 2.1
 * @NScriptType Restlet
 */
define(['N/https', 'N/log'], function(https, log) {
    
    function post(context) {
        try {
            var response = https.post({
                url: 'https://your-axis-server:8443/api/bcu/consulta',
                body: JSON.stringify(context),
                headers: {
                    'Content-Type': 'application/json'
                }
            });
            
            var data = JSON.parse(response.body);
            
            if (data.status === 'success') {
                // Procesar datos en NetSuite
                return {
                    success: true,
                    cotizacion: data.datos
                };
            } else {
                log.error('Error BCU', data.mensaje);
                return {
                    success: false,
                    error: data.mensaje
                };
            }
            
        } catch (e) {
            log.error('Error conexión', e.message);
            return {
                success: false,
                error: 'Error de conexión con BCU'
            };
        }
    }
    
    return {
        post: post
    };
});
```

## Configuración de Producción

### Certificados SSL

1. **Obtener certificados del BCU**: Contactar al BCU para obtener certificados para mTLS
2. **Certificado del servidor**: Obtener certificado SSL válido para el dominio
3. **Configurar keystores**:

```bash
# Importar certificado del BCU
keytool -importcert -alias bcu-prod -file bcu-certificate.crt \
    -keystore certificates/bcu-truststore.jks -storepass password

# Importar certificado cliente autorizado por BCU
openssl pkcs12 -export -in client-cert.crt -inkey client-key.key \
    -out certificates/client-keystore.p12 -name client -passout pass:password
```

### Variables de Entorno

```bash
export AXIS_SERVER_PORT=8443
export KEYSTORE_PASSWORD=production_password
export BCU_ENDPOINT=https://webservices.bcu.gub.uy/...
export MTLS_ENABLED=true
```

### Logging en Producción

Editar `src/main/resources/simplelogger.properties`:

```properties
org.slf4j.simpleLogger.defaultLogLevel=INFO
org.slf4j.simpleLogger.log.com.enlamano=DEBUG
org.slf4j.simpleLogger.logFile=logs/axis-server.log
```

## Monitoreo

### Health Check
```bash
curl -k https://localhost:8443/api/health
```

### Logs
- Logs del servidor: `logs/axis-server.log`
- Logs de solicitudes SOAP: Configurar en `bcu-config.properties`

### Métricas
- Uptime del servidor
- Uso de memoria
- Estado de conexión con BCU
- Estadísticas de respuesta

## Troubleshooting

### Error SSL/TLS
```
WARN: SSL handshake failed
```
- Verificar certificados en `certificates/`
- Validar configuración de truststore
- Comprobar conectividad con BCU

### Error de timeout
```
ERROR: SocketTimeoutException
```
- Aumentar timeouts en `bcu-config.properties`
- Verificar conectividad de red
- Revisar firewall/proxy

### Error de autenticación mTLS
```
ERROR: Client certificate required
```
- Verificar certificado cliente
- Confirmar que el certificado está autorizado por BCU
- Revisar configuración de keystore

## Desarrollo

### Estructura del Proyecto
```
src/
├── main/
│   ├── java/com/enlamano/server/
│   │   ├── AxisServerMain.java      # Servidor principal
│   │   ├── BcuGatewayServlet.java   # Gateway JSON/SOAP
│   │   ├── BcuSoapClient.java       # Cliente SOAP
│   │   ├── BcuSoapResponse.java     # DTO respuesta BCU
│   │   └── HealthCheckServlet.java  # Health check
│   └── resources/
│       └── bcu-config.properties    # Configuración
├── test/
└── certificates/                    # Certificados SSL
```

### Agregar Nuevos Tipos de Consulta

1. Agregar caso en `BcuGatewayServlet.doPost()`
2. Implementar método `procesarConsultaXXX()`
3. Agregar método correspondiente en `BcuSoapClient`
4. Actualizar documentación

## Licencia

Propietario - EnLaMano
#   S e r v i d o r B C U P r o j e c t  
 