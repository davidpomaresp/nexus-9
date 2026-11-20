# ADR 0006 — Reloj en el servidor y Server-Sent Events

- Estado: Aceptado
- Fecha: 2026-11-20

## Contexto

En la v0.1 el panel avanzaba la simulación con un temporizador del navegador y volvía a pedir todos los datos tras cada avance. Eso tiene tres problemas: si se cierra la pestaña la simulación se para, dos personas mirando la misma simulación la avanzan el doble y no ven lo mismo, y el navegador hace peticiones aunque no haya cambios.

## Opciones

1. **Seguir con el reloj en el cliente** y sondeo periódico. Simple, pero con los problemas de arriba.
2. **Reloj en el servidor + WebSockets.** Canal bidireccional; útil si el panel enviara muchos comandos en tiempo real.
3. **Reloj en el servidor + Server-Sent Events.** Canal solo servidor → navegador sobre HTTP normal.

## Decisión

Opción 3.

- `simulation.running` y `simulation.speed` se guardan en la base de datos. `SimulationClock` (`@Scheduled`, cada 250 ms) avanza las simulaciones en marcha usando el mismo `SimulationService.advance`, así que el bloqueo `FOR UPDATE`, la idempotencia y las incidencias funcionan igual que con peticiones manuales.
- Tras cada avance, ejecución o pausa confirmados se publica un evento de Spring (`SimulationAdvanced`). `SimulationEvents` lo envía como evento SSE `state` a todos los paneles abiertos en esa simulación.
- El panel solo arranca y pausa; recarga los datos cuando llega un evento, juntando en una sola recarga los eventos que llegan mientras otra está en curso.

## Consecuencias

- (+) Varias pestañas o personas ven la misma simulación en vivo, y sigue avanzando aunque nadie la mire.
- (+) Como el estado está en la base de datos, las simulaciones en marcha continúan tras un reinicio.
- (+) SSE va sobre HTTP: sin protocolo nuevo, el navegador reconecta solo (`EventSource`) y funciona detrás de proxies normales. Los comandos siguen siendo peticiones REST.
- (−) Los suscriptores viven en memoria de cada instancia. Con varias instancias del backend, un panel solo recibiría eventos de los avances hechos en su instancia; habría que repartir los eventos (por ejemplo, `LISTEN/NOTIFY` de PostgreSQL). Hoy hay una sola instancia.
- (−) Con varias instancias, cada una ejecutaría el reloj: el `FOR UPDATE` evita incoherencias, pero la simulación iría más rápido. Se resolvería con un bloqueo de reloj (`pg_try_advisory_lock`) cuando haga falta.
