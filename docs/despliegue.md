# Despliegue

La demo pública corre en [Render](https://render.com) (el backend, a partir del `Dockerfile`) con la base de datos en [Neon](https://neon.tech). Los dos tienen plan gratuito sin tarjeta.

El plan gratuito de Render duerme el servicio tras 15 minutos sin visitas, así que la primera carga tarda unos 50 segundos. Mientras duerme el reloj del servidor también se para; al despertar, las simulaciones que estaban en marcha siguen donde estaban.

## Base de datos (Neon)

1. Crear un proyecto en Neon con PostgreSQL 17.
2. En *Connection details* copiar host, base de datos, usuario y contraseña. Neon da una URL del tipo `postgresql://usuario:clave@host/neondb?sslmode=require`; para JDBC hay que separarla:
   - `NEXUS_DB_URL` = `jdbc:postgresql://host/neondb?sslmode=require`
   - `NEXUS_DB_USER` = `usuario`
   - `NEXUS_DB_PASSWORD` = `clave`

Flyway crea las tablas en el primer arranque.

## Backend (Render)

1. *New → Blueprint* y elegir este repositorio. Render lee `render.yaml`.
2. Rellenar las tres variables `NEXUS_DB_*` de arriba.
3. Esperar al primer despliegue (unos minutos, compila el backend y el panel dentro de Docker).

Cada push a `main` vuelve a desplegar.

## Límites para la demo

Como cualquiera puede usar la demo sin registrarse, `render.yaml` pone varios límites para que nadie llene la base de datos gratuita ni tumbe el servicio:

- `NEXUS_MAXTICK=3000`: ninguna simulación pasa del tick 3000 (unas 50 horas de misión). El reloj la pausa al llegar, y avanzar a mano o volver a arrancarla más allá devuelve 409. El límite se comprueba con la fila bloqueada en cada tick, así que tampoco se cuela con peticiones simultáneas.
- `NEXUS_MAXSIMULATIONS=50`: como mucho 50 simulaciones a la vez; la 51 devuelve 409 hasta que la limpieza borre alguna. Con 50 × 3000 ticks × 3 módulos son 450.000 lecturas como mucho, que caben de sobra en los 0,5 GB de Neon.
- `NEXUS_RATELIMIT_PERMINUTE=30`: cada cliente puede hacer 30 peticiones de escritura (POST) por minuto; a partir de ahí, 429. Los contadores están en memoria, que basta con una sola instancia. La IP sale de la última entrada de `X-Forwarded-For`, la que añade el proxy de Render; las anteriores las pone el cliente y se pueden falsear.
- `NEXUS_RETENTION=24h`: cada hora se borran las simulaciones pausadas de más de un día. Las que están en marcha se saltan (se pausan solas al llegar al tick 3000 y caen en una limpieza posterior), y también las que un tick tiene bloqueadas en ese momento (`FOR UPDATE SKIP LOCKED`). La limpieza bloquea primero la fila de la simulación y luego borra telemetría e incidencias, en el mismo orden en que toma los bloqueos un tick, para que no haya interbloqueos.

En local no hay límites: si no se definen esas variables, no se aplican.
