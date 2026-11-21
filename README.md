# Asistente personal (Android)

App de Android que funciona como un asistente personal:

| Pestaña | Qué hace |
|---|---|
| 📧 **Correos** | Se conecta a tu Gmail y muestra solo los correos **importantes sin leer** de los últimos 14 días (usa la etiqueta "Importante" que Gmail ya calcula). Al tocar uno se abre en Gmail. |
| 💬 **Mensajes** | Lee las notificaciones de WhatsApp y marca como ⭐ importantes las que vienen de chats VIP o contienen tus palabras clave. Opcionalmente **quita de la barra de notificaciones** los mensajes de grupos que no son importantes. |
| 🏆 **T1** | Calendario de partidos de **T1** (LoL Esports) con la hora en **hora del centro de México** (y la hora de Corea como referencia), resultados recientes y botón para **agregarlo a tu calendario** con recordatorio. |
| 🔊 **Resumen** | Botón arriba a la derecha: el asistente te lee en voz alta tus correos importantes, los mensajes importantes del día y el próximo partido de T1. |

Todo se queda en tu teléfono: los mensajes de WhatsApp se guardan solo localmente y la app no tiene servidor propio.

## Descargar en el celular (sin cable ni modo desarrollador)

Página de descarga: **https://batiares.github.io/asistente-personal/**

Cada vez que se sube un cambio a `main`, GitHub Actions corre los tests, compila el APK firmado y lo publica en
[Releases](https://github.com/batiares/asistente-personal/releases). La página siempre descarga la versión más reciente.

La firma usa una llave fija (secretos `KEYSTORE_BASE64` y `KEYSTORE_PASSWORD` del repo, alias `asistente`) para que
las actualizaciones se instalen encima y Gmail siga reconociendo la app. **Guarda una copia de esa llave**: si se pierde,
hay que desinstalar la app y registrar una SHA-1 nueva en Google Cloud.

**Si Play Protect dice "App blocked to protect your device":** Google bloquea los APK descargados que piden acceso a
notificaciones. Desactiva temporalmente *Play Store → tu foto → Play Protect → ⚙️ → Analizar apps con Play Protect*,
instala y vuelve a activarlo. Instalar por `adb` no tiene este bloqueo.

## Compilar en tu computadora

Requisitos: Android Studio (o JDK 17 + Android SDK 35).

1. Abre esta carpeta en Android Studio y deja que sincronice Gradle.
2. Conecta tu celular con *Depuración USB* activada y dale **Run ▶**.

Desde terminal:

```bash
./gradlew assembleDebug
```

El APK queda en `app/build/outputs/apk/debug/app-debug.apk`.

## Configurar Gmail (una sola vez)

Google exige registrar la app para poder leer correos:

1. Entra a <https://console.cloud.google.com/> y crea un proyecto.
2. **APIs y servicios → Biblioteca** → habilita **Gmail API**.
3. **Pantalla de consentimiento de OAuth** → tipo *Externo*, deja la app en modo **Prueba** y agrega tu propio correo en **Usuarios de prueba**.
4. **Credenciales → Crear credenciales → ID de cliente de OAuth → Android**:
   - Nombre del paquete: `com.asistente.personal`
   - Huella SHA-1 de la llave con la que se firma el APK que instalas:
     ```bash
     keytool -list -v -keystore release.jks -alias asistente
     ```
     (si compilas tú en modo debug, usa la SHA-1 que muestra `./gradlew signingReport`).
5. Listo. En la app toca **Conectar Gmail** y acepta el permiso de solo lectura.

> Mientras la app esté en modo "Prueba", Google puede pedir que vuelvas a autorizar cada cierto tiempo. Es normal para apps personales.

## Configurar WhatsApp

1. En la pestaña **Mensajes** toca **Dar acceso** y activa *Asistente* en "Acceso a notificaciones".
2. Toca **⚙️ Filtro** para editar:
   - **Palabras clave**: p. ej. `urgente, examen, pago, @TuNombre` (sin importar mayúsculas ni acentos).
   - **Chats o grupos siempre importantes**: p. ej. `Familia, Trabajo`.
   - **Ocultar notificaciones de grupos que no sean importantes**.

**Sobre los grupos archivados:** WhatsApp no avisa de los grupos que están *silenciados*, así que la app tampoco puede verlos. Para que el filtro funcione con esos grupos:
1. Quita el silencio a los grupos archivados que te interesan.
2. Activa en la app "Ocultar notificaciones de grupos que no sean importantes".

Así la app deja pasar solo los mensajes importantes de esos grupos y quita el resto.

## Calendario de T1

Los datos vienen de la API pública que usa la web de lolesports.com (no es oficial y Riot podría cambiarla). Los horarios se convierten a `America/Mexico_City`. Si quieres seguir a otro equipo, cambia `TEAM` en `T1Schedule.kt`.

## Estructura

```
app/src/main/java/com/asistente/personal/
├── MainActivity.kt         estado de la app, Gmail, T1 y resumen por voz
├── Screens.kt              interfaz (Jetpack Compose)
├── Http.kt                 GET HTTP sencillo
├── gmail/GmailRepository.kt
├── whatsapp/               lector de notificaciones, filtro y almacenamiento local
└── t1/T1Schedule.kt        calendario de LoL Esports y conversión de horario
```

Pruebas: `./gradlew test`
