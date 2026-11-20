# NEXUS-9 — Análisis de Claude y borrador de especificación v0.1

> Documento para contrastar con la propuesta de ChatGPT. Pensado para vivir en `docs/` del repositorio.
> Fecha: 2026-11-20. Estado: **borrador para discusión**, nada decidido todavía.

---

## 1. Resumen

La propuesta es buena y, sobre todo, **tiene un problema de ingeniería de verdad**: mantener un historial coherente y no duplicar incidencias aunque lleguen eventos repetidos o se reinicie el backend. Eso es lo que hará que NEXUS-9 destaque frente a un CRUD típico.

Estoy de acuerdo con: monolito modular, hito pequeño (v0.1), lógica de negocio real en lugar de números aleatorios, y trabajar con ramas, PRs y decisiones documentadas.

Mis críticas principales, de más a menos importante:

1. **La v0.1, tal como está escrita, aún permite el "dashboard falso"** que quieres evitar. Tres módulos independientes con lecturas reproducibles no son una simulación: hace falta que **al menos una dependencia real** (energía → oxígeno y comunicaciones) exista desde la v0.1.
2. **Hay que separar visión y alcance.** El mensaje mezcla la v0.1 con WebSockets, React, permisos, reconstrucción del estado y detección de anomalías. Propongo una lista explícita de lo que **no** entra en la v0.1.
3. **"Reproducible" necesita definirse con precisión**: semilla + reloj simulado + tick fijo. Sin eso, los tests no serán deterministas.
4. **No recomiendo event sourcing completo al principio.** Sí recomiendo diseñar desde el día 1 lo que lo hace posible después (ids de evento, tabla de eventos append-only, idempotencia).
5. **"Detectar dependencias" debe ser un grafo declarado, no inferido.** Inferir dependencias es un problema de IA difícil; declararlas y propagar el impacto es ingeniería defendible.

---

## 2. Críticas y alternativas en detalle

### 2.1 Una dependencia física real desde la v0.1

Propuesta de modelo mínimo (simple, pero causal):

- **Energía**: paneles solares generan `G(t)` (depende de la órbita: luz/sombra y del estado de los paneles). Una batería almacena la diferencia: `batería(t+1) = batería(t) + (G − consumo) · Δt`, con límites 0–capacidad.
- **Oxígeno**: el generador consume energía y produce O₂. La tripulación consume O₂ a ritmo fijo. Si no recibe energía suficiente, el nivel de O₂ baja.
- **Comunicaciones**: consumen energía; sin ella, pasan a `DEGRADED` u `OFFLINE`.
- **Gestión de cargas (load shedding)**: si la batería baja de un umbral, el sistema corta consumo por prioridad: primero comunicaciones no críticas, el oxígeno nunca.

Con esto, la demo "fallan los paneles → baja la energía → se recortan comunicaciones → si sigue, el O₂ empieza a bajar" sale sola del modelo, sin guion. **Eso es lo que lo diferencia de un panel con números aleatorios.**

*Alternativa considerada:* módulos independientes en v0.1 y dependencias en v0.2. La descarto porque el diseño del motor cambia mucho si las dependencias llegan tarde.

### 2.2 Reproducibilidad: definición concreta

- **Reloj simulado**, no `System.currentTimeMillis()`. La simulación avanza por *ticks* (p. ej. 1 tick = 10 s de tiempo de estación).
- **Semilla** para todo el ruido aleatorio (`java.util.Random` / `SplittableRandom` con seed explícita).
- **Escenario** = estado inicial + lista de averías programadas (`tick 120: SOLAR_ARRAY_A falla al 40 %`).
- Garantía: **misma semilla + mismo escenario ⇒ exactamente las mismas lecturas**. Se comprueba con un test.

El simulador debería ser **Java puro, sin Spring**: así se prueba en milisegundos y es fácil de explicar.

### 2.3 Eventos, idempotencia y no duplicar incidencias

Este es el núcleo técnico. Propuesta para la v0.1 (sin event sourcing completo):

- Cada lectura/evento lleva un **`event_id` único** (determinista: p. ej. `simulación + módulo + tick`).
- Tabla `telemetry_event` **append-only** con `UNIQUE(event_id)` → un evento repetido se ignora (`INSERT ... ON CONFLICT DO NOTHING`).
- Tabla `incident` con un **índice único parcial** en PostgreSQL:
  `UNIQUE (module, rule_code) WHERE status <> 'RESOLVED'`
  → la base de datos garantiza que no puede haber dos incidencias abiertas de la misma regla y módulo, aunque haya dos hilos o un reinicio a mitad.
- Procesar evento + actualizar estado + abrir incidencia en **la misma transacción**.

Por qué así: la garantía no depende de que el código Java "se acuerde" de comprobar duplicados; la impone la base de datos. Es un argumento muy bueno en una entrevista.

*Alternativa:* event sourcing completo (el estado solo se obtiene reproduciendo eventos). Más elegante, pero añade snapshots, versionado de eventos y proyecciones. Lo dejaría para una versión posterior, cuando el dominio esté estable.

### 2.4 Reglas: Java sencillo, con histéresis

- No usar motores como Drools. Una interfaz `Rule` con `evaluate(StationState) → Optional<IncidentCandidate>` es suficiente y testeable.
- **Histéresis** para evitar alarmas que parpadean: abrir incidencia con batería < 20 %, cerrarla solo cuando > 25 %.
- **Prioridad** calculada por regla (CRITICAL / HIGH / MEDIUM / LOW), p. ej. O₂ bajo siempre CRITICAL.

### 2.5 Dependencias e impacto: grafo declarado

- Un grafo explícito (`OXYGEN depende de POWER`, `COMMS depende de POWER`) definido en código o configuración.
- Cuando se abre una incidencia en POWER, el sistema calcula **qué módulos podrían verse afectados** recorriendo el grafo (BFS — conecta con Algoritmia).
- Más adelante: correlación de incidencias (una causa raíz y varias consecuencias) en vez de N alarmas sueltas.

### 2.6 Máquina de estados

- Módulo: `NOMINAL → DEGRADED → CRITICAL → OFFLINE` (y vuelta).
- Incidencia v0.1: `OPEN → RESOLVED` (automática por histéresis). En la versión de operadores: `OPEN → ACKNOWLEDGED → RESOLVED`.
- Transiciones validadas en una sola clase con tests de transiciones ilegales.

### 2.7 Sobre trabajar con ChatGPT

Totalmente de acuerdo con no aceptar decisiones por autoridad. Propuestas prácticas:

- **Tú eres el puente.** Yo no puedo hablar directamente con ChatGPT; el canal compartido será GitHub (PRs, issues y `docs/`).
- **ADRs** (Architecture Decision Records) en `docs/adr/`: una página por decisión con contexto, opciones, decisión y consecuencias. Cuando ChatGPT y yo discrepemos, el ADR recoge ambos argumentos y **la decisión la firmas tú**.
- **Regla de aprendizaje:** ningún PR se fusiona si no puedes explicar con tus palabras qué hace y por qué. En las piezas clave (reglas, idempotencia) propongo que tú escribas el código y nosotros revisemos.

---

## 3. Borrador de especificación — NEXUS-9 v0.1 "Telemetry Core"

### 3.1 Dentro del alcance

- Simulador determinista (Java puro) con 3 módulos: **POWER, OXYGEN, COMMS**, con la dependencia de energía descrita en 2.1.
- Escenarios con averías programadas (al menos: fallo parcial de paneles solares).
- Backend Spring Boot que ejecuta la simulación tick a tick, guarda eventos y estado en PostgreSQL.
- 4–5 reglas de incidencias con prioridad e histéresis.
- Idempotencia de eventos e incidencias garantizada por la base de datos.
- API REST de consulta.
- Tests automáticos + CI en GitHub Actions + `docker compose up` para ejecutarlo en local.

### 3.2 Fuera del alcance (explícitamente)

React, WebSockets, login/permisos, operadores, procedimientos de recuperación, simulador como proceso separado, event sourcing completo, despliegue en la nube, detección de anomalías estadística.

### 3.3 API v0.1 (propuesta)

| Método | Ruta | Para qué |
|---|---|---|
| `POST` | `/api/simulations` | Crear simulación (semilla + escenario) |
| `POST` | `/api/simulations/{id}/advance?ticks=N` | Avanzar N ticks |
| `GET` | `/api/simulations/{id}/modules` | Estado actual de cada módulo |
| `GET` | `/api/simulations/{id}/modules/{module}/readings?fromTick&toTick` | Historial de lecturas |
| `GET` | `/api/simulations/{id}/incidents?status=OPEN` | Incidencias |

Avanzar por ticks bajo demanda (en vez de un temporizador en segundo plano) hace que todo sea testeable y reproducible. El modo "tiempo real" se añade luego encima.

### 3.4 Stack propuesto

Java 21 (LTS) · Spring Boot (última versión estable) · Maven · PostgreSQL · Flyway (migraciones) · JUnit 5 + AssertJ · Testcontainers (PostgreSQL real en los tests) · GitHub Actions · Docker Compose.

Nada más. Cada tecnología extra tiene que justificarse con un ADR.

### 3.5 Estructura (monolito modular)

```
nexus-9/
  simulator/     # Java puro: modelo físico, escenarios, reloj simulado
  backend/       # Spring Boot
    telemetry/   # ingesta de eventos, idempotencia, historial
    rules/       # reglas e incidencias
    station/     # estado de módulos, grafo de dependencias
    api/         # controladores REST
  docs/
    adr/
    especificacion.md
```

### 3.6 Criterios de aceptación ("hecho" significa)

1. Misma semilla + mismo escenario producen lecturas idénticas (test).
2. Escenario "fallo de paneles": aparece incidencia en POWER, luego COMMS pasa a DEGRADED por recorte de carga, y la API lo refleja (test de integración).
3. Enviar el mismo evento dos veces no cambia el historial ni duplica incidencias (test).
4. Una regla no abre una segunda incidencia mientras la primera siga abierta, y la histéresis evita el parpadeo (test).
5. `docker compose up` arranca todo; la CI pasa en cada PR.
6. README con cómo ejecutarlo + al menos 3 ADRs (monolito modular, idempotencia en BD, simulador determinista).

---

## 4. Hoja de ruta posterior (orientativa)

- **v0.2 Robustez:** simulador como proceso separado que envía eventos por HTTP; reintentos, eventos duplicados y desordenados; test de reinicio del backend a mitad de escenario.
- **v0.3 Operadores:** Spring Security, roles, ciclo `OPEN → ACKNOWLEDGED → RESOLVED`, procedimientos de recuperación que afectan a la simulación.
- **v0.4 Panel:** React + WebSockets para alarmas en vivo.
- **v0.5 Producción:** despliegue, observabilidad (logs estructurados, métricas).
- **v0.6 Escenarios complejos:** cadenas de averías, correlación de causa raíz, reconstrucción del estado desde el historial.

---

## 5. Preguntas abiertas para David

1. **¿Cuántas horas a la semana** puedes dedicarle? Con eso estimamos la v0.1.
2. **¿Qué partes quieres escribir tú** y cuáles prefieres que propongamos nosotros para que las revises?
3. **¿Te convence la dependencia de energía en la v0.1** o prefieres empezar aún más pequeño?
4. **Repo:** nombre propuesto `nexus-9`, público desde el principio (se ve el progreso en tu GitHub).
