# BlueBl (Android)

**Habla sin internet.** BlueBl es una aplicación nativa de Android (Kotlin + Jetpack Compose) para conversar por Bluetooth Low Energy (BLE) en malla con las personas que tienes cerca: sin WiFi, sin datos móviles y sin servidores.

## Características

- **Cero internet.** Transporte BLE dual (servidor GATT + anunciante periférico y escáner/cliente central) con los mismos UUIDs de servicio y característica que el protocolo BlueBl/bitchat.
- **Malla real (TTL + deduplicación).** Cada dispositivo reenvía los paquetes públicos de difusión que recibe mientras les quede TTL, deduplicando ecos por `(emisor, timestamp, payload)`.
- **Identidad y firmas Ed25519 + X25519.** Identidad persistente por dispositivo; el peer ID de 16 caracteres hex se deriva de los primeros 8 bytes del SHA-256 de la clave pública X25519 (Noise).
- **Compatibilidad de protocolo por el aire.** Cabeceras v1/v2, compresión raw-deflate con protección contra bombas deflate, padding PKCS#7 determinista (256/512/1024/2048 B), anuncios TLV y fragmentación/reensamblado de paquetes grandes.
- **Un solo canal (`#general`).** Chat público en tiempo real con soporte de comandos estilo IRC (`/nick`, `/me`, `/who`, `/peers`, `/theme`, `/density`, `/clear`, `/deps`, `/pair`, `/fp`, `/accept`, `/nodo`, `/help`).
- **Emparejamiento QR (`bitchat://verify` y `BB1|`) + huellas digitales.** Genera códigos QR firmados con Ed25519 (vigencia de 5 minutos), escanea QRs con CameraX + ZXing, o compara huellas SHA-256 4x4 para verificar contactos (`★`).
- **42 temas y 3 densidades.** Todos los temas organizados en cuatro categorías (*Base y accesibilidad*, *Terminales clásicos*, *Videojuegos*, *Cine*) y tres densidades (`compacto`, `cómodo`, `grande`) con persistencia local.
- **Modo nodo (repetidor).** Vista mínima de repetidor respaldada por un servicio en primer plano (`connectedDevice`) para mantener el reenvío de paquetes públicos en segundo plano.
- **Diseño adaptable.** Pantalla dividida (Pares + `#general`) en tablets y pantallas anchas (`≥ 720dp`) y pestañas compactas en teléfonos.

## Compilación y pruebas

```sh
gradle assembleDebug
gradle :app:testDebugUnitTest
```

## Agradecimientos

- **[bitchat](https://github.com/permissionlesstech/bitchat)** (Swift) y **[bitchat-android](https://github.com/permissionlesstech/bitchat-android)** (Kotlin) — **inspiración** de este proyecto: el concepto de chat en malla por Bluetooth Low Energy sin internet, la estética de canal IRC (`#general`, `/nick`, `/me`, `/who`…), las cabeceras de paquete y los UUIDs de servicio/característica con los que BlueBl busca ser compatible por el aire. BlueBl es una reimplementación independiente, **no** un fork: no comparte código con ellos.
- **[apkExtractor](https://github.com/axxapy/apkExtractor)** (axxapy, MIT) — herramienta usada para **extraer el APK desde el dispositivo** y publicarlo con su nombre en los [releases](https://github.com/L3IB0R/Bluebl-Android/releases).

## Licencia

MIT
