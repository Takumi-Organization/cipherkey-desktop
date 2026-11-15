# 0001 — Sincronización directa en la red local (proximidad)

- **Estado:** aceptado; el apartado de pertenencia por grupo y la integración exigiendo servidor quedan sustituidos por [0002](0002-proximidad-sin-servidor.md)
- **Fecha:** 2026-11-14
- **Ámbito:** `core` (protocolo), escritorio (`dev.sync.ProximityService`) y Android (`PeerManager`)

## Contexto

Hasta ahora las bóvedas se encontraban a través de un servidor propio: los registros viajan cifrados de extremo a extremo,
pero dos dispositivos que están en la misma mesa dependen de internet y de que el servidor esté disponible. Hacía falta
una vía directa, sin terceros, que funcione con el móvil y el ordenador en la misma red y que no obligue a subir nada a
ningún sitio.

Restricciones que condicionan la solución:

- La red local no es de confianza: cualquiera puede escuchar, suplantar o hacer de intermediario.
- La bóveda ya tiene una clave de sincronización compartida por sus miembros; los dispositivos tienen además una identidad
  Ed25519 propia.
- El mismo protocolo debe servir al escritorio (Java) y a Android (Kotlin), sin duplicar criptografía.
- El servidor sigue siendo la vía de respaldo y el punto de encuentro entre redes distintas.

## Decisión

Se añade un canal directo por la red local, independiente del servidor, con estas piezas:

### 1. Descubrimiento

- Balizas UDP a la dirección de multidifusión `239.255.67.75:47891`, cada **4 s**, con caducidad de **15 s**.
- Cada baliza (`CKB1`, 38 bytes) lleva solo el puerto TCP, un nonce y una **etiqueta de dispositivo de 8 bytes**:
  `HKDF(clave de sincronización, "ck-p2p-vtag")` + `HMAC("id", deviceId)`. Para quien no es miembro es ruido; nunca
  contiene el nombre ni datos de la bóveda. Un anuncio falso solo consigue que se intente una conexión que fallará en el
  apretón de manos.

### 2. Conexiones y cifrado

- TCP en el puerto **47890** (configurable). Tramas con longitud de 4 bytes, máximo **16 MiB**.
- `SecureChannel` es agnóstico del transporte (TCP en LAN, RFCOMM en Bluetooth en el futuro).
- Apretón de manos: **X25519 efímero** en ambos lados (secreto hacia delante) y un transcripto con todo lo enviado.
  Antes de revelar nada, cada lado exige una etiqueta HMAC con la clave de sincronización de la bóveda: un tercero que no
  la tenga no puede completar el apretón ni hacer de intermediario.
- Cada dispositivo prueba su identidad con una **firma Ed25519** sobre el transcripto y un MAC de pertenencia.
- Datos: `AES-256-GCM` con nonce aleatorio y AAD = dirección + contador. El **contador estricto** impide repetir,
  reordenar o eliminar tramas.

### 3. Confianza (TOFU + código de seguridad)

- La primera vez, ambos lados derivan un **código de seguridad (SAS) de 6 dígitos** del transcripto y de las dos claves
  públicas. El usuario compara el código en las dos pantallas y confirma; solo entonces cada uno **fija (pin)** la clave
  Ed25519 del otro.
- Si un dispositivo ya fijado presenta otra clave, se aborta: posible suplantación. La recuperación es desvincular y
  volver a vincular.
- Un dispositivo nuevo solo se acepta si el usuario ha abierto la ventana de vinculación (**120 s** en la interfaz) o si
  él mismo inició la vinculación.
- Límite de intentos fallidos: **5 por origen en 10 minutos**; superado, no se responde hasta que caduque la ventana.

### 4. Datos

- Se intercambian resúmenes `uuid + hash`; cada lado pide lo que le falta o difiere.
- La fusión es **a tres bandas** con `Merger`, usando como base lo último que tuvo ese par (o, en el primer contacto, la
  última versión conocida del servidor). Nada se descarta: un conflicto se conserva y lo resuelve el usuario.
- Lo cambiado se marca como pendiente para subirlo también al servidor, de modo que la proximidad acelera la
  convergencia pero no crea un estado paralelo.

### 5. Integración

- **Escritorio:** `SyncService` crea `ProximityService` en cuanto la sincronización está configurada. Al sincronizar,
  primero intenta los dispositivos vinculados que ve en la red y después el servidor; el resultado indica la ruta usada
  (`proximidad`, `proximidad + servidor`, `servidor` o `proximidad (servidor no accesible)`).
- **Android:** `PeerManager` escucha solo con la bóveda **desbloqueada**, la sincronización configurada y la app abierta;
  usa un `MulticastLock` para recibir balizas. Al terminar una sincronización refresca la lista y avisa al servidor.

## Consecuencias

**A favor**

- Dos dispositivos de la misma bóveda convergen sin internet, sin servidor y sin subir nada a terceros.
- El canal y la confianza se comparten entre plataformas; Android y escritorio hablan el mismo protocolo.
- El servidor sigue funcionando como respaldo: si la proximidad falla, la sincronización normal no cambia.

**En contra / límites conocidos**

- Requiere la misma red local. El aislamiento de clientes (Wi-Fi de invitados, puntos de acceso con `AP isolation`) y los
  cortafuegos que bloquean multidifusión impiden el descubrimiento; queda la conexión manual por `IP:puerto`.
- Hay que permitir `47890/tcp` y `47891/udp` en el cortafuegos del escritorio.
- En Android la proximidad solo vive mientras la app está abierta y desbloqueada; no hay servicio en segundo plano.
- La clave de sincronización es de toda la bóveda: el pin y el SAS protegen el primer contacto y detectan cambios de
  clave, pero la revocación de un miembro sigue dependiendo del servidor.
- Android 17 (target 37) exigirá `ACCESS_LOCAL_NETWORK`. La app compila con target 36 y aún no la declara; al subir el
  target habrá que añadirla y comprobar el flujo de permiso.

## Alternativas descartadas

- **Solo servidor:** es lo que había; no resuelve el caso de dos dispositivos juntos sin internet.
- **Bluetooth/RFCOMM:** `SecureChannel` ya está preparado, pero añade emparejamiento del sistema, permisos y otro conjunto
  de pruebas; se deja como transporte futuro.
- **Wi-Fi Direct:** más complejo en escritorio y con soporte irregular; la LAN normal cubre el caso de uso.
- **Confiar en la red local (sin SAS ni pin):** inaceptable: cualquiera en la red podría hacerse pasar por un miembro.

## Verificación

- `core`: `PeerSyncTest` (14 pruebas), `PeerServerE2ETest` (1).
- Escritorio: `SyncServiceE2ETest` (3), con servidor y dos pares simulados; suite completa `mvn test` en verde
  (61 pruebas en la raíz + 43 en `core`).
- Arnés `PeerHarness` (núcleo real de ambos lados, sockets reales): par vinculado con SAS y sincronización convergente.
- Android: pruebas JVM, `lintDebug` sin errores y `assembleDebug` correctos (2026-11-15). El APK se instaló en un móvil
  físico, pero la sincronización real móvil ↔ escritorio **no está probada** de extremo a extremo (la bóveda del móvil es real
  y no se desbloqueó para la prueba).
- Endurecimiento tras revisión: tiempo de espera de 20 s hasta autenticar (los 120 s solo después), máximo de 8 conexiones
  entrantes y error limpio si el par se desvincula durante la sincronización.
