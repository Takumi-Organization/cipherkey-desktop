# 0002 — Proximidad sin servidor: vínculo por código de seguridad, sin grupo de sincronización

- **Estado:** aceptado (sustituye las partes de [0001](0001-sincronizacion-en-proximidad.md) que dependían del grupo)
- **Fecha:** 2026-11-15
- **Ámbito:** `core` (`p2p`, `model.LinkState`), escritorio (`dev.sync`, `dev.view`) y Android (`PeerManager`, Ajustes)

## Contexto

El ADR 0001 dejó la sincronización directa por red local funcionando, pero **solo si la bóveda ya estaba sincronizada con un
servidor**: `SyncService.ensureProximity()` y la pantalla de Ajustes del móvil exigían `sync != null`. Sin servidor no había
identidad de dispositivo, ni puertos abiertos, ni código de seguridad. El autor quiere sincronizar su escritorio y su móvil en
la misma red **sin montar ningún servidor**.

Además, cada dispositivo tiene su propia bóveda (su clave maestra, su `vaultId`, su `syncKey`); lo único que viaja por el canal
es el documento descifrado en memoria dentro del canal cifrado. Dos bóvedas que nunca se han visto no comparten base de fusión.

### Qué dependía del servidor / del grupo (auditoría)

| Pieza (0001) | Dependencia | Consecuencia sin servidor |
|---|---|---|
| Identidad (`deviceId`, clave Ed25519) | `SyncState` (solo existe tras emparejar con el servidor) | no hay identidad |
| Prueba de pertenencia HMAC con `HKDF(syncKey)` en el apretón de manos | `syncKey` **compartida** por el grupo | dos bóvedas independientes tienen claves distintas: el apretón falla siempre |
| Baliza con etiqueta `HMAC(syncKey, id)` y MAC | `syncKey` del grupo | no se pueden descubrir ni reconocer |
| Lista de pares (`SyncState.peers`) | `SyncState` | no hay dónde guardar los vínculos |
| Base de fusión en primer contacto = `baselines` del servidor | historial de servidor | sin base, o base que no es común entre dos bóvedas distintas |
| Arranque (`isSyncConfigured`), UI y Ajustes | servidor configurado | sin puertos ni pantalla |
| Revocación de un miembro | servidor | sin servidor, la revocación es local (desvincular) |

Lo que **no** depende del servidor y se conserva: el canal X25519 + HKDF + AES-256-GCM con contador, las tramas, la firma
Ed25519 de identidad, el SAS, el pin de clave, el límite de intentos, la ventana de vinculación y la fusión a tres bandas.

## Decisión

### 1. Identidad y vínculos independientes del servidor: `LinkState`

La bóveda (`VaultDocument.link`, cifrada con la clave maestra) guarda `deviceId`, `deviceName`, la clave privada Ed25519 y el
mapa de pares vinculados (clave pública fijada + base de fusión por par). Se crea al desbloquear la bóveda
(`LinkState.ensure`), sin red. Si ya hay servidor configurado se **reutiliza** su identidad (mismo id y clave) para que las
filas de dispositivos coincidan. Se elimina al exportar (`withoutSync`). `SyncState.peers` desaparece.

### 2. Sin grupo: la confianza es la clave pinneada tras confirmar el SAS

Se elimina la prueba de pertenencia HMAC. Un dispositivo es de confianza solo si el usuario comparó el código de 6 dígitos en
**ambas** pantallas y cada lado fijó la clave Ed25519 del otro. Las siguientes veces basta la firma con la clave fijada.
Si una clave fijada cambia: se aborta (posible suplantación) y la recuperación es desvincular y volver a vincular.

### 3. Apretón de manos con compromiso (para que 6 dígitos sigan siendo seguros)

Sin la clave de grupo, un intermediario podría **buscar** (fuerza bruta de ~10^6 claves efímeras o de identidad) una mitad que
produzca el mismo SAS en los dos lados. Por eso el SAS ahora va con **compromiso**, como ZRTP:

1. El iniciador envía solo `SHA-256(efímera ‖ nonce ‖ clave pública ‖ id)`.
2. El respondedor contesta con su mitad en claro.
3. Solo entonces el iniciador revela la suya y el respondedor comprueba el compromiso.

Ningún lado, ni un intermediario, puede escoger su mitad después de ver la del otro; con un SAS de 6 dígitos acierta con
probabilidad ≈ 1/10^6 por intento, y los intentos están limitados (5 por origen cada 10 min; la persona ve códigos distintos
y rechaza). El transcripto `T` cubre todo; las claves de sesión salen de X25519 y `T` (secreto hacia delante); cada lado firma
`T` con su clave de dispositivo (posesión de la clave). El resto del canal (AES-GCM, contador estricto, máx. 16 MiB) no cambia.
La versión del protocolo pasa a 2 (`CKP2P\x02`): no habla con compilaciones del 0001.

### 4. Garantías

- **Sin vinculación no se revela nada:** antes de vincular solo se intercambian claves públicas y firmas. El `hello` de un
  dispositivo **no incluye su nombre** salvo que el usuario haya abierto la vinculación o el par ya esté fijado; un dispositivo
  desconocido sin ventana abierta recibe un `deny` y no se le envía ningún registro ni resumen. Sin ventana abierta ni siquiera
  se muestra un código.
- **Límite de intentos:** 5 fallos de autenticación por origen en 10 min; pasado el límite no se responde. 8 conexiones
  entrantes máximo; 20 s de espera antes de autenticar (los 120 s solo después de pasar el `hello`).
- **Ventana de vinculación:** 120 s, un solo dispositivo nuevo.
- **Desvinculación:** cada lado borra el vínculo (pin + base). Un par desvinculado por uno de los lados deja de sincronizar
  (el otro exige de nuevo el SAS en ambos). Volver a vincular **reinicia la base** y vuelve a hacer la unión segura con resumen.
- **Descubrimiento:** baliza `CKB2` de 22 bytes (puerto, nonce y etiqueta de 8 bytes = `SHA-256("ck-p2p-vtag" ‖ clave
  pública)`). Ya no hay MAC ni clave de grupo, así que **cualquiera en la LAN puede emitir o leer balizas**; la etiqueta es un
  seudónimo estable visible en la red local (no contiene nombre ni datos). Un anuncio falso solo provoca un intento de
  conexión que se detiene en el SAS o en el `deny`.
- El nombre del otro dispositivo es texto no fiable: se limpia, se limita a 60 caracteres y se escapa antes de mostrarlo.

### 5. Primera fusión entre bóvedas sin base común: unión segura

Cuando un par se vincula (o se revincula), `lastSync == 0`: **no hay base común**. En ese caso:

1. Tras intercambiar resúmenes (uuid + hash de contenido + lista de lápidas) y **antes de aplicar nada**, cada lado calcula
   su resumen: `X` entradas vivas que el otro no tiene («nuevas de este dispositivo»), `Y` entradas del otro que aquí no
   existen («del otro dispositivo») y `Z` con el mismo uuid y contenido distinto.
2. Cada usuario ve el resumen y debe **confirmar** (Fusionar / Cancelar). Después se hace una **copia de seguridad local
   automática** (`<bóveda>.bak-proximidad-<fecha>`, el mismo fichero cifrado con la misma clave maestra, permisos `600`). Si la
   copia falla o cualquiera cancela, **no se cambia nada**. Ambos intercambian `go`.
3. Se aplica con **base nula** (nunca `baselines` del servidor: ese historial no es común con otra bóveda):
   - uuid que solo tiene el otro → se añade; uuid que solo tengo yo → se envía. **Nada se sustituye ni se borra.**
   - mismo uuid y mismo contenido → nada.
   - mismo uuid y contenido **distinto** → `Merger` con base vacía: los campos que solo uno de los lados rellenó se unen; si
     ambos tienen valores distintos en un campo, **conflicto**: se conserva la versión local y se adjunta la del otro
     (`Record.conflict`) para que el usuario elija («la mía», «la otra» o «conservar ambas»). El historial se une.
   - lápida de un lado frente a entrada viva del otro → **gana la viva** (una lápida de otra bóveda nunca borra) y no se crea
     conflicto; el otro lado la recupera.
4. Lo recibido se marca pendiente para subir al servidor si más tarde se configura uno; sin servidor no hay cola.

Después de la primera fusión, las siguientes (misma pareja vinculada) usan la base por par y propagan ediciones y borrados
como en el 0001 (sin resumen ni copia). La base por par ya **no** cae en `baselines` del servidor.

### 6. Integración

- **Escritorio:** `SyncService` arranca `ProximityService` al desbloquear (`isUnlocked`) y lo cierra al bloquear, con o sin
  servidor; si falla (puerto ocupado, sin multidifusión) muestra el **motivo real** y reintenta cada 20 s o con «Reintentar».
  `SyncDialog` (botón de la barra / Ctrl+Mayús+S) muestra siempre la sección **Dispositivos cercanos** con «Vincular por
  proximidad…», «Permitir vinculación», «Sincronizar con este» y «Desvincular»; el servidor queda en una sección aparte y
  opcional. Sin servidor sincroniza automáticamente con los vinculados visibles cada minuto y tras cada cambio local.
- **Android:** `PeerManager` y Ajustes › Proximidad no exigen servidor (ver README del móvil). El emparejamiento con servidor
  queda en una sección separada y no es requisito.

## Consecuencias

**A favor**

- Dos dispositivos en la misma red se sincronizan sin servidor ni internet, y el modelo de confianza es uniforme (el pin).
- Una sola implementación (core) para Java y Kotlin; el protocolo sigue sin duplicar criptografía.
- La primera unión nunca destruye datos y siempre deja una copia local.

**En contra / límites**

- **Revocación:** no hay un grupo que revocar; desvincular es local. Si un dispositivo vinculado se pierde, hay que desvincularlo
  en todos los demás y cambiar las contraseñas que contuviera (tiene una copia completa de las entradas).
- **Un vínculo da acceso a toda la bóveda:** confirmar el SAS equivale a entregar el contenido descifrado (por el canal
  cifrado). Hay que vincular solo dispositivos propios.
- El SAS de 6 dígitos protege frente a un intermediario **activo** solo mientras el usuario compare de verdad; con compromiso la
  probabilidad por intento es 1/10^6, no cero.
- Cualquiera de la LAN puede ver que hay un dispositivo CipherKey (baliza) y abrir conexiones (limitadas); no obtiene nada sin
  el SAS confirmado en ambos lados.
- Una revinculación pierde la base por par: ediciones divergentes del mismo campo darán conflicto en lugar de fusionarse solas.
- Aislamiento de clientes Wi-Fi o cortafuegos que bloqueen multidifusión impiden el descubrimiento (queda `IP:puerto`);
  puertos `47890/tcp` y `47891/udp`. Android: la proximidad solo vive con la app abierta y la bóveda desbloqueada.

## Alternativas descartadas

- **Grupo ad hoc por código (PAKE con el código):** más complejo y no aporta más que SAS + pin para dos dispositivos propios.
- **Clave compartida introducida a mano (como el código de emparejamiento del servidor):** obliga a copiar un secreto largo; el
  SAS de 6 dígitos con compromiso da una UX mejor con seguridad suficiente para el modelo (LAN, ventana de 120 s).
- **Seguir exigiendo servidor / mantener la prueba de pertenencia:** es justo el requisito que se quiere quitar.
- **Sustituir la bóveda del otro («el último gana»):** inaceptable: pierde datos.
- **Resolver conflictos automáticamente por fecha:** puede descartar la contraseña buena; se prefiere que decida el usuario.

## Verificación

Ver el estado real en los README (`README.md` del escritorio y del móvil). Resumen: pruebas unitarias de `core`
(`PeerSyncTest`, `PeerServerE2ETest`) y de escritorio (`ProximityNoServerE2ETest`, `SyncServiceE2ETest`) con sockets reales en
loopback y bóvedas temporales; pruebas de Android en emulador (un solo emulador, vinculado por dirección porque su red es NAT).

**No verificado:** la sincronización real móvil físico ↔ escritorio del autor (se hace con sus credenciales); el
descubrimiento por multidifusión entre dos máquinas reales de la LAN (en pruebas solo se probó el envío/recepción local y la
conexión directa por `IP:puerto`); el comportamiento con Wi-Fi con aislamiento de clientes; Android 17 (`ACCESS_LOCAL_NETWORK`).
