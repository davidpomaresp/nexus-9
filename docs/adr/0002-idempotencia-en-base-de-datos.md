# ADR 0002 — Idempotencia garantizada por la base de datos

- Estado: Aceptado
- Fecha: 2026-11-20

## Contexto

El objetivo técnico central de NEXUS-9 es que, aunque lleguen eventos duplicados o se reinicie el backend a mitad de un escenario, el historial sea coherente y no haya incidencias duplicadas.

## Opciones

1. **Comprobar en Java** antes de insertar ("¿existe ya?"). Sufre condiciones de carrera entre hilos y reinicios.
2. **Event sourcing completo**: el estado solo se deriva reproduciendo eventos. Potente, pero exige snapshots, versionado de eventos y proyecciones.
3. **Restricciones en PostgreSQL** + transacción por tick.

## Decisión

Opción 3:

- `telemetry_event.event_id` es `UNIQUE` y determinista (`simulación:módulo:tick`). Se inserta con `ON CONFLICT DO NOTHING`.
- `incident` tiene un índice único parcial `(simulation_id, module, rule_code) WHERE status = 'OPEN'`.
- Procesar un tick ocurre en una única transacción.

## Alcance de la garantía

La idempotencia es la de los **eventos y las incidencias**, no la de las peticiones HTTP:

- `POST /advance?ticks=N` es relativo: si el cliente reintenta tras un timeout, avanza otra vez. Para reintentar sin riesgo existe `POST /advance?toTick=T`, que lleva la simulación a un tick absoluto y no hace nada si ya está ahí.
- Cada tick se confirma por separado. Si una petición de 100 ticks falla en el 60, los 59 anteriores quedan guardados y `currentTick` lo refleja. Es intencionado: el estado guardado siempre es coherente, aunque la petición quede a medias.
- La telemetría tiene además un índice único `(simulation_id, module, tick)`, de modo que la unicidad no depende solo del formato del `event_id`.

Riesgo para cuando la telemetría llegue de fuera (simulador como proceso aparte): las reglas se evalúan sobre el snapshot que se está procesando. Si se reenviara un evento antiguo y se evaluara de nuevo, podría reabrir una incidencia ya resuelta. Hoy no pasa porque el backend solo evalúa ticks nuevos; cuando haya ingesta externa habrá que evaluar solo eventos insertados de verdad (`append` > 0) y en orden de tick.

## Consecuencias

- (+) La garantía la impone la base de datos, no la buena voluntad del código.
- (+) La tabla de eventos append-only deja abierta la puerta a reconstruir el estado (v0.6) sin rediseñar.
- (−) Acoplado a PostgreSQL (índices parciales). Aceptable: es la base de datos elegida.
- (−) Los tests de idempotencia necesitan una PostgreSQL real → Testcontainers.
