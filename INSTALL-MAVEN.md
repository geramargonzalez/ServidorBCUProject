# Instrucciones de Instalación de Maven

## Windows

### Opción 1: Descarga Manual
1. Ir a https://maven.apache.org/download.cgi
2. Descargar "Binary zip archive" (apache-maven-3.9.x-bin.zip)
3. Extraer en `C:\Program Files\Apache\maven`
4. Agregar variables de entorno:
   - `MAVEN_HOME` = `C:\Program Files\Apache\maven`
   - Agregar `%MAVEN_HOME%\bin` al PATH
5. Reiniciar PowerShell
6. Verificar: `mvn --version`

### Opción 2: Con Chocolatey (Recomendado)
```powershell
# Instalar Chocolatey primero (si no está instalado)
Set-ExecutionPolicy Bypass -Scope Process -Force; [System.Net.ServicePointManager]::SecurityProtocol = [System.Net.ServicePointManager]::SecurityProtocol -bor 3072; iex ((New-Object System.Net.WebClient).DownloadString('https://community.chocolatey.org/install.ps1'))

# Instalar Maven
choco install maven

# Verificar
mvn --version
```

### Opción 3: Con Scoop
```powershell
# Instalar Scoop primero (si no está instalado)
Set-ExecutionPolicy RemoteSigned -Scope CurrentUser
irm get.scoop.sh | iex

# Instalar Maven
scoop install maven

# Verificar
mvn --version
```

## Después de Instalar Maven

1. **Compilar el proyecto:**
   ```bash
   mvn clean compile
   ```

2. **Crear JAR ejecutable:**
   ```bash
   mvn clean package
   ```

3. **Ejecutar el servidor:**
   ```bash
   java -jar target/axis-server-1.0.0-jar-with-dependencies.jar
   ```

4. **Generar certificados de desarrollo:**
   ```bash
   # Windows
   generate-certificates.bat
   ```

## Linux/Mac

```bash
# Ubuntu/Debian
sudo apt-get update
sudo apt-get install maven

# CentOS/RHEL/Fedora
sudo yum install maven  # o dnf install maven

# macOS con Homebrew
brew install maven

# Verificar instalación
mvn --version
```
