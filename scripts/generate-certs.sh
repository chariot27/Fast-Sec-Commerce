#!/bin/bash
set -e

mkdir -p certs
cd certs

echo "=== Gerando CA (Certificate Authority) ==="
openssl req -new -x509 -days 3650 -keyout ca.key -out ca.crt -passout pass:fsc123 -subj "/C=BR/ST=SP/L=SaoPaulo/O=FSC/OU=Security/CN=FSC Root CA"

echo "=== Gerando Chaves e Certificados para Gateway Node ==="
openssl genrsa -out gateway.key 2048
openssl req -new -key gateway.key -out gateway.csr -subj "/C=BR/ST=SP/L=SaoPaulo/O=FSC/OU=Gateway/CN=gateway.fsc.local"
openssl x509 -req -in gateway.csr -CA ca.crt -CAkey ca.key -CAcreateserial -out gateway.crt -days 3650 -passin pass:fsc123

echo "=== Gerando Chaves e Certificados para Sec Engine Node ==="
openssl genrsa -out sec-engine.key 2048
openssl req -new -key sec-engine.key -out sec-engine.csr -subj "/C=BR/ST=SP/L=SaoPaulo/O=FSC/OU=SecEngine/CN=secengine.fsc.local"
openssl x509 -req -in sec-engine.csr -CA ca.crt -CAkey ca.key -CAcreateserial -out sec-engine.crt -days 3650 -passin pass:fsc123

# Convert to PKCS12 for Java / Spring Boot
openssl pkcs12 -export -out gateway.p12 -inkey gateway.key -in gateway.crt -certfile ca.crt -passout pass:fsc123 -name gateway
openssl pkcs12 -export -out sec-engine.p12 -inkey sec-engine.key -in sec-engine.crt -certfile ca.crt -passout pass:fsc123 -name sec-engine

# Criar truststores
keytool -import -trustcacerts -alias root -file ca.crt -keystore truststore.p12 -storepass fsc123 -noprompt

echo "=== Certificados mTLS gerados com sucesso ==="
