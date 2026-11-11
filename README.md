# Música

Reproductor de música para Android pensado para funcionar sin conexión: tu música, sin internet y sin anuncios.

## Estado

Ya puedes:

- Agregar canciones de tu celular a una biblioteca que se guarda entre sesiones
- Buscar música en línea (Audius, y Jamendo cuando se active) y oír canciones completas al instante, sin tenerlas en el celular; artistas verificados y las más escuchadas primero; guardar las que te gusten en tu biblioteca
- Buscar también dentro de tu biblioteca por canción, artista o álbum
- Reproducir con el panel completo (carátula, barra de avance y controles) o desde el mini reproductor
- Marcar canciones con "me gusta" y encontrarlas en el álbum "Canciones que te gustan"
- Descargar la música libre (Audius) para oírla sin internet; queda en el álbum "Descargadas"
- Modo aleatorio, repetir todas o una, y ver la cola para saltar a cualquier canción
- Crear listas de reproducción propias y agregar canciones desde el reproductor
- Encontrar sola la música guardada en el celular, ordenar la biblioteca y retomar al abrir la app lo que estabas oyendo
- Menú de opciones en cada canción: reproducir a continuación, agregar a la cola o a una lista, me gusta y descargar
- Temporizador de apagado, velocidad de reproducción y ecualizador del celular, desde el menú del reproductor
- Ver la biblioteca por canciones, artistas o álbumes, y cambiar el nombre de las listas
- Seguir oyendo tu música con la pantalla apagada o fuera de la app, con controles en la notificación y en la pantalla de bloqueo
- Buscar música libre en línea y descargarla desde la misma lista
- Elegir tema oscuro, claro o automático (sigue al celular)
- Actualizar la app desde la propia app: al abrirla avisa si hay una versión nueva, la descarga y la instala encima, sin desinstalar ni perder la biblioteca (también en Ajustes → Actualizaciones)

La carátula sale del propio archivo; si no trae ninguna, la app dibuja una portada con colores propios.

Pendiente:

- Activar Jamendo: crear una llave gratuita en devportal.jamendo.com y ponerla en `JamendoConfig.CLIENT_ID`

## Instalar

Descarga el APK más reciente desde la sección **Releases** de este repositorio y ábrelo en tu celular. Android te pedirá permitir instalar apps de esta fuente.

Eso solo hace falta la primera vez. Desde la versión 0.6.0 la app se actualiza sola: avisa al abrirla y se instala encima de la anterior.

## Cómo está hecha

Escrita en Kotlin con Jetpack Compose y Media3. Los componentes (cola, reproductor y los que vengan) no se llaman entre sí: se comunican por un mediador central que procesa un evento a la vez y aísla los errores. Así un fallo en uno no tumba a los demás y el orden de las cosas es siempre predecible.

```
app/src/main/java/com/davidhc4/musica/
├── core/      mediador, eventos y ajustes
├── queue/     cola de reproducción
├── player/    motor de audio
├── library/   biblioteca guardada y búsqueda local
├── search/    búsqueda en línea
├── source/    fuentes de música en línea
├── net/       descargas simples por internet
├── settings/  tema y personalización
├── update/    actualizaciones de la app
└── ui/        pantallas y componentes
```

## Compilar

Requiere JDK 17 y Gradle 8.9.

```
gradle testDebugUnitTest assembleRelease
```

Cada cambio que entra a `main` se prueba y compila solo en GitHub Actions; si `appVersion` en `app/build.gradle.kts` es nueva, se publica el APK.
