package com.davidhc4.musica

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.testScheduler

/**
 * Alcance para el mediador en pruebas. No se usa backgroundScope porque advanceUntilIdle()
 * ignora las tareas de fondo y el mediador nunca llegaría a procesar eventos.
 */
@OptIn(ExperimentalCoroutinesApi::class)
fun TestScope.mediatorScope(): CoroutineScope =
    CoroutineScope(StandardTestDispatcher(testScheduler) + Job())
