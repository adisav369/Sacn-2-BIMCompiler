#!/bin/bash
# Generate self-signed TLS certificate for development/testing
# For production, use Let's Encrypt: certbot certonly --standalone -d your-domain.com

CERT_DIR="$(dirname "$0")/certs"
mkdir -p "$CERT_DIR"

openssl req -x509 -newkey rsa:2048 -nodes \
    -keyout "$CERT_DIR/server.key" \
    -out "$CERT_DIR/server.crt" \
    -days 365 \
    -subj "/CN=bim-backoffice/O=BIM5D/C=MY"

echo "Self-signed certificate generated in $CERT_DIR/"
echo "  server.crt — certificate"
echo "  server.key — private key"
echo ""
echo "For production, replace with Let's Encrypt:"
echo "  certbot certonly --standalone -d your-domain.com"
