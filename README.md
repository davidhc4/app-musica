# Música

Reproductor de música para Android pensado para funcionar sin conexión: tu música, sin internet y sin anuncios.

## Estado

Versión inicial. Por ahora puedes elegir canciones de tu celular, armar una cola y reproducirlas.

Pendiente:

- Biblioteca guardada entre sesiones
- Descarga de música libre y legal (Jamendo, Internet Archive, Free Music Archive)
- Reproducción con la pantalla apagada
- Listas de reproducción

## Instalar

Descarga el APK más reciente desde la sección **Releases** de este repositorio y ábrelo en tu celular. Android te pedirá permitir instalar apps de esta fuente.

## Cómo está hecha

Escrita en Kotlin con Jetpack Compose y Media3. Los componentes (cola, reproductor y los que vengan) no se llaman entre sí: se comunican por un mediador central que procesa un evento a la vez y aísla los errores. Así un fallo en uno no tumba a los demás y el orden de las cosas es siempre predecible.

```
app/src/main/java/com/davidhc4/musica/
├── core/      mediador y eventos
├── queue/     cola de reproducción
├── player/    motor de audio
└── MainActivity.kt
```

## Compilar

Requiere JDK 17 y Gradle 8.9.

```
gradle testDebugUnitTest assembleRelease
```

Cada cambio que entra a `main` se prueba y compila solo en GitHub Actions; si la versión en `app/build.gradle.kts` es nueva, se publica el APK.
