# Configuración de Despliegue para NetSuite
# =======================================

## Opción 1: Servidor con IP Pública
# ------------------------------------
# Tu servidor Java se ejecuta directamente en internet
# NetSuite accede directamente a tu IP pública

### Configuración:
```bash
# Variables de entorno
SERVER_PORT=8443
SERVER_HOST=0.0.0.0  # Escucha en todas las interfaces

# Comando de ejecución
java -Dserver.port=8443 -Dserver.host=0.0.0.0 \
     -jar target/axis-server-1.0.0-jar-with-dependencies.jar
```

### URL de NetSuite:
```
https://tu-ip-publica:8443/api/bcu/consulta
```

### Ventajas:
✅ Configuración simple
✅ Acceso directo desde NetSuite
✅ Menos componentes

### Desventajas:
❌ Servidor expuesto directamente
❌ Gestión de certificados SSL más compleja
❌ Menos control de seguridad

---

## Opción 2: Detrás de Proxy Reverso (RECOMENDADA)
# ------------------------------------------------
# Nginx/Apache maneja SSL y redirige a tu aplicación Java

### Configuración Nginx:
```nginx
server {
    listen 443 ssl;
    server_name tu-dominio.com;
    
    ssl_certificate /path/to/ssl/certificate.crt;
    ssl_certificate_key /path/to/ssl/private.key;
    
    location /api/bcu/ {
        proxy_pass https://localhost:8443/api/bcu/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        
        # Para HTTPS interno
        proxy_ssl_verify off;
    }
}
```

### Configuración Java:
```bash
# Solo escucha localhost (más seguro)
java -Dserver.port=8443 -Dserver.host=127.0.0.1 \
     -jar target/axis-server-1.0.0-jar-with-dependencies.jar
```

### URL de NetSuite:
```
https://tu-dominio.com/api/bcu/consulta
```

### Ventajas:
✅ Aplicación Java no expuesta directamente
✅ Nginx maneja SSL/TLS optimizado
✅ Balanceador de carga futuro
✅ Logs centralizados
✅ Certificados Let's Encrypt fáciles

---

## Opción 3: Red Privada + VPN
# -----------------------------
# Si tu empresa tiene VPN o conexión privada con NetSuite

### Configuración:
```bash
# Solo red interna
SERVER_PORT=8080  # HTTP interno está bien
SERVER_HOST=192.168.1.100  # IP interna

java -Dserver.port=8080 -Dserver.host=192.168.1.100 \
     -jar target/axis-server-1.0.0-jar-with-dependencies.jar
```

### URL de NetSuite:
```
http://192.168.1.100:8080/api/bcu/consulta
# o
https://servidor-interno.empresa.com:8080/api/bcu/consulta
```

---

## Opción 4: Servicio en la Nube
# -------------------------------
# AWS, Azure, Google Cloud, etc.

### AWS Example:
```bash
# EC2 + Application Load Balancer
ALB (443) → Target Group (8443) → EC2 Instance

# Variables de entorno
SERVER_PORT=8443
SERVER_HOST=0.0.0.0
```

### Azure Example:
```bash
# App Service + Application Gateway
Application Gateway (443) → App Service (8443)
```

---

## Configuración de NetSuite RESTlet
# ==================================

```javascript
/**
 * RESTlet que consume tu servidor Axis
 */
define(['N/https', 'N/log'], function(https, log) {
    
    // Configurar según tu opción elegida
    const AXIS_SERVER_URL = 'https://tu-servidor:8443/api/bcu/consulta';
    // const AXIS_SERVER_URL = 'https://tu-dominio.com/api/bcu/consulta';
    // const AXIS_SERVER_URL = 'http://servidor-interno:8080/api/bcu/consulta';
    
    function post(context) {
        try {
            var response = https.post({
                url: AXIS_SERVER_URL,
                body: JSON.stringify({
                    tipoConsulta: context.tipoConsulta || 'cotizacion',
                    parametros: context.parametros
                }),
                headers: {
                    'Content-Type': 'application/json',
                    'User-Agent': 'NetSuite-RESTlet/1.0'
                }
            });
            
            if (response.code === 200) {
                return JSON.parse(response.body);
            } else {
                log.error('Error HTTP', response.code + ': ' + response.body);
                return { error: 'Error de conexión: ' + response.code };
            }
            
        } catch (e) {
            log.error('Error RESTlet', e.message);
            return { error: 'Error interno: ' + e.message };
        }
    }
    
    return { post: post };
});
```

---

## Recomendación Final
# ====================

**Para Desarrollo/Pruebas:**
→ Opción 1 (IP Pública directa)

**Para Producción:**
→ Opción 2 (Proxy Reverso con Nginx)

**Para Empresa Grande:**
→ Opción 3 (Red Privada + VPN)

**Para Escalabilidad:**
→ Opción 4 (Servicios en la Nube)

---

## Configuración de Firewall
# ===========================

### Si usas IP pública:
```bash
# Ubuntu/Debian
sudo ufw allow 8443/tcp

# CentOS/RHEL
sudo firewall-cmd --permanent --add-port=8443/tcp
sudo firewall-cmd --reload

# Windows
netsh advfirewall firewall add rule name="Axis Server" dir=in action=allow protocol=TCP localport=8443
```

### Si usas proxy:
```bash
# Solo puerto 443 para Nginx, 8443 solo localhost
sudo ufw allow 443/tcp
# 8443 no necesita estar abierto externamente
```
