#!/bin/bash

echo "Generando certificados para desarrollo..."
echo "ADVERTENCIA: Estos certificados son SOLO para desarrollo local"

mkdir -p certificates

echo ""
echo "1. Generando keystore del servidor para HTTPS..."
keytool -genkeypair -alias server -keyalg RSA -keysize 2048 -validity 365 \
    -dname "CN=localhost, OU=EnLaMano, O=EnLaMano, L=Montevideo, ST=Montevideo, C=UY" \
    -keystore certificates/server-keystore.jks -storepass changeit -keypass changeit

echo ""
echo "2. Generando keystore del cliente para mTLS..."
keytool -genkeypair -alias client -keyalg RSA -keysize 2048 -validity 365 \
    -dname "CN=EnLaMano Client, OU=EnLaMano, O=EnLaMano, L=Montevideo, ST=Montevideo, C=UY" \
    -keystore certificates/client-keystore.p12 -storetype PKCS12 -storepass changeit -keypass changeit

echo ""
echo "3. Generando truststore (simulando certificados del BCU)..."
keytool -genkeypair -alias bcu-server -keyalg RSA -keysize 2048 -validity 365 \
    -dname "CN=BCU Server, OU=BCU, O=Banco Central del Uruguay, L=Montevideo, ST=Montevideo, C=UY" \
    -keystore certificates/bcu-temp.jks -storepass changeit -keypass changeit

keytool -exportcert -alias bcu-server -keystore certificates/bcu-temp.jks -storepass changeit \
    -file certificates/bcu-cert.crt

keytool -importcert -alias bcu-server -file certificates/bcu-cert.crt \
    -keystore certificates/bcu-truststore.jks -storepass changeit -noprompt

rm certificates/bcu-temp.jks
rm certificates/bcu-cert.crt

echo ""
echo "Certificados generados exitosamente:"
echo "  - certificates/server-keystore.jks (para HTTPS del servidor)"
echo "  - certificates/client-keystore.p12 (para autenticacion mTLS)"
echo "  - certificates/bcu-truststore.jks (para validar certificados BCU)"
echo ""
echo "IMPORTANTE: En produccion, reemplazar con certificados reales del BCU"
