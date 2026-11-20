# ADR 0002 — Idempotencia garantizada por la base de datos

- Estado: Propuesto
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

## Consecuencias

- (+) La garantía la impone la base de datos, no la buena voluntad del código.
- (+) La tabla de eventos append-only deja abierta la puerta a reconstruir el estado (v0.6) sin rediseñar.
- (−) Acoplado a PostgreSQL (índices parciales). Aceptable: es la base de datos elegida.
- (−) Los tests de idempotencia necesitan una PostgreSQL real → Testcontainers.
