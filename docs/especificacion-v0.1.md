# Especificación — NEXUS-9 v0.1 "Telemetry Core"

> Estado: **borrador**, pendiente de revisión.
> Las decisiones de fondo están razonadas en [`docs/adr/`](adr/).

## 1. Objetivo

Un núcleo pequeño, comprobable y ejecutable en local que simule tres sistemas de la estación con una dependencia física real, detecte incidencias con reglas y exponga estado e historial por API. Todo con tests automáticos.

## 2. Alcance

### Dentro

- Simulador determinista en Java puro (sin Spring) con tres módulos: **POWER**, **OXYGEN**, **COMMS**.
- Escenarios con averías programadas.
- Backend Spring Boot que avanza la simulación por ticks y persiste eventos y estado en PostgreSQL.
- Reglas de incidencias con prioridad e histéresis.
- Idempotencia de eventos e incidencias garantizada por la base de datos.
- API REST de consulta y control.
- CI en GitHub Actions y `docker compose up` para ejecutarlo.

### Fuera (explícitamente)

React, WebSockets, autenticación y permisos, operadores, procedimientos de recuperación, simulador como proceso separado, event sourcing completo, despliegue en la nube, detección estadística de anomalías.

## 3. Modelo de simulación

### 3.1 Tiempo

- Reloj **simulado**. La simulación avanza en *ticks*; **1 tick = 60 s** de tiempo de estación.
- Nunca se usa la hora real del sistema dentro del simulador.
- Órbita: **92 ticks** por vuelta (≈ 92 min), de los cuales los primeros **57** son de luz y los **35** restantes de sombra.

### 3.2 Aleatoriedad

- Todo el ruido sale de un generador con **semilla explícita**.
- Garantía: **misma semilla + mismo escenario ⇒ mismas lecturas, bit a bit.**

### 3.3 Módulos (valores iniciales, ajustables)

**POWER**
- Dos paneles solares, A y B, con 6 kW nominales cada uno en luz y 0 en sombra. Cada panel tiene una *eficiencia* entre 0 y 1 (1 = sano). Ruido ±2 %.
- Batería de 20 kWh, carga inicial 80 %. `carga(t+1) = clamp(carga(t) + (generación − consumo_servido) · Δt, 0, capacidad)`.
- Consumo base de la estación (fijo, no recortable): 2 kW.

**OXYGEN**
- El generador de O₂ consume **3 kW** cuando está alimentado.
- Nivel de O₂ en la atmósfera expresado en % del nominal (100 % = sano).
- Alimentado: sube **0,5 puntos por tick** hasta 100 %. Sin alimentar: baja **0,25 puntos por tick** (consumo de la tripulación).

**COMMS**
- Consumo: **1,5 kW** en modo completo, **0,4 kW** en modo mínimo (baliza de emergencia).

### 3.4 Gestión de cargas (load shedding)

Prioridad de cargas: base > OXYGEN > COMMS. El consumo base (2 kW) siempre se pide.

1. **Ahorro preventivo** según la carga de la batería: COMMS pasa a modo mínimo por debajo del 30 % y se apaga por debajo del 15 %.
2. **Ajuste a la energía disponible** (generación + batería): OXYGEN se alimenta si caben sus 3 kW después del consumo base; COMMS baja de modo hasta que quepa en lo que sobra.

Con el escenario `solar-degradation` la cascada queda así: la batería se descarga tras la avería, COMMS pasa a mínimo, luego se apaga, y finalmente el generador de O₂ se queda sin energía y el nivel de oxígeno empieza a bajar.

### 3.5 Grafo de dependencias

Declarado, no inferido: `OXYGEN → POWER`, `COMMS → POWER`. Se usa para calcular qué módulos pueden verse afectados por una incidencia (recorrido BFS).

### 3.6 Escenarios

Un escenario es: semilla + estado inicial + lista de averías programadas, por ejemplo:

```
tick 120: SOLAR_PANEL_A → eficiencia 0.3
tick 200: SOLAR_PANEL_B → eficiencia 0.0
```

Escenario de referencia para la demo y los tests: **`solar-degradation`** (el de arriba).

## 4. Estados

- Módulo: `NOMINAL`, `DEGRADED`, `CRITICAL`, `OFFLINE`.
- Incidencia v0.1: `OPEN → RESOLVED` (cierre automático con histéresis).

## 5. Reglas de incidencias (v0.1)

| Código | Módulo | Abre cuando | Cierra cuando | Prioridad |
|---|---|---|---|---|
| `BATTERY_LOW` | POWER | carga < 20 % | carga > 25 % | HIGH |
| `BATTERY_CRITICAL` | POWER | carga < 5 % | carga > 10 % | CRITICAL |
| `GENERATION_DEFICIT` | POWER | generación en luz < 70 % de la nominal durante 3 ticks | ≥ 80 % durante 3 ticks | MEDIUM |
| `COMMS_DEGRADED` | COMMS | modo mínimo u off | modo completo | MEDIUM |
| `OXYGEN_LOW` | OXYGEN | nivel < 95 % | nivel > 97 % | CRITICAL |

Cada incidencia guarda los módulos potencialmente afectados según el grafo.

## 6. Persistencia e idempotencia

- `telemetry_event`: append-only, `event_id` único y determinista (`simulación:módulo:tick`). Duplicados ignorados con `ON CONFLICT DO NOTHING`.
- `incident`: índice único parcial `(simulation_id, module, rule_code) WHERE status = 'OPEN'`.
- Procesar un tick (guardar eventos + actualizar estado + abrir/cerrar incidencias) ocurre en **una transacción**.
- Migraciones con Flyway.

## 7. API v0.1

| Método | Ruta | Descripción |
|---|---|---|
| `POST` | `/api/simulations` | Crea una simulación (`seed`, `scenario`) |
| `POST` | `/api/simulations/{id}/advance?ticks=N` | Avanza N ticks |
| `GET` | `/api/simulations/{id}` | Tick actual y resumen |
| `GET` | `/api/simulations/{id}/modules` | Estado actual de cada módulo |
| `GET` | `/api/simulations/{id}/modules/{module}/readings?fromTick&toTick` | Historial de lecturas |
| `GET` | `/api/simulations/{id}/incidents?status=` | Incidencias |

Errores con formato `application/problem+json`.

## 8. Criterios de aceptación

1. Misma semilla y escenario producen lecturas idénticas (test).
2. Con `solar-degradation`: aparece `GENERATION_DEFICIT`, después `COMMS_DEGRADED` por recorte de cargas, y la API lo refleja (test de integración).
3. Procesar el mismo evento dos veces no cambia el historial ni duplica incidencias (test).
4. Una regla no abre una segunda incidencia mientras la primera siga abierta, y la histéresis evita el parpadeo (test).
5. `docker compose up` arranca todo; la CI pasa en cada PR.
6. README con instrucciones y ADRs 0001–0003.
