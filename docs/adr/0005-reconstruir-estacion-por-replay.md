# ADR 0005 — Reconstruir la estación reproduciendo la simulación

- Estado: Aceptado
- Fecha: 2026-11-20

## Contexto

El backend mantiene en memoria un `Station` por simulación. Si el proceso se reinicia, o si una transacción falla después de avanzar la estación en memoria, el estado en memoria deja de coincidir con la base de datos.

## Opciones

1. Serializar el estado completo de la estación en cada tick.
2. Reconstruir el estado a partir de la telemetría guardada.
3. **Reproducir la simulación** desde el tick 0 con la misma semilla y escenario hasta `current_tick`.

## Decisión

Opción 3. La base de datos guarda `seed`, `scenario` y `current_tick`; la estación en memoria es solo una caché. Si falta o su tick no coincide con el de la base de datos, se reconstruye reproduciendo.

## Consecuencias

- (+) No hay que persistir ni versionar el estado interno del simulador.
- (+) Se apoya en el determinismo (ADR 0003), que ya está probado.
- (−) El coste de reconstruir crece con el número de ticks. Con miles de ticks son milisegundos; si algún día fuera un problema, se añadirían snapshots periódicos.
- (−) El replay solo es válido si el simulador sigue siendo el mismo. Si un cambio altera la salida para una semilla (por ejemplo, un ajuste de umbrales), reproducir una simulación antigua daría otro estado y la continuación no coincidiría con la telemetría guardada. Por eso:
  - `Station.MODEL_VERSION` se incrementa con cada cambio que altere la salida.
  - `simulation.model_version` guarda la versión con la que se creó.
  - Si no coinciden, el backend se niega a avanzar (409) en vez de mezclar dos modelos. Las simulaciones antiguas siguen siendo consultables.
- Cada tick se procesa en una transacción con `SELECT ... FOR UPDATE` sobre la simulación, así que dos peticiones simultáneas no pueden avanzar la misma simulación a la vez.
