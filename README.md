# Música

Reproductor de música para Android pensado para funcionar sin conexión: tu música, sin internet y sin anuncios.

## Estado

Ya puedes:

- Agregar canciones de tu celular a una biblioteca que se guarda entre sesiones
- Buscar por canción, artista o álbum
- Reproducir con el panel completo (carátula, barra de avance y controles) o desde el mini reproductor
- Cambiar entre tema claro, oscuro o el del sistema, elegir color de acento y usar fondo negro puro

La carátula sale del propio archivo; si no trae ninguna, la app dibuja una portada con colores propios.

Pendiente:

- Reproducción con la pantalla apagada
- Descarga de música libre y legal (Jamendo, Internet Archive, Free Music Archive)
- Listas de reproducción, aleatorio y repetir

## Instalar

Descarga el APK más reciente desde la sección **Releases** de este repositorio y ábrelo en tu celular. Android te pedirá permitir instalar apps de esta fuente.

## Cómo está hecha

Escrita en Kotlin con Jetpack Compose y Media3. Los componentes (cola, reproductor y los que vengan) no se llaman entre sí: se comunican por un mediador central que procesa un evento a la vez y aísla los errores. Así un fallo en uno no tumba a los demás y el orden de las cosas es siempre predecible.

```
app/src/main/java/com/davidhc4/musica/
├── core/      mediador, eventos y ajustes
├── queue/     cola de reproducción
├── player/    motor de audio
├── library/   biblioteca guardada y búsqueda
├── settings/  tema y personalización
└── ui/        pantallas y componentes
```

## Compilar

Requiere JDK 17 y Gradle 8.9.

```
gradle testDebugUnitTest assembleRelease
```

Cada cambio que entra a `main` se prueba y compila solo en GitHub Actions; si `appVersion` en `app/build.gradle.kts` es nueva, se publica el APK.
