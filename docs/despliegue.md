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

Como cualquiera puede crear simulaciones, `render.yaml` pone dos límites para no llenar la base de datos gratuita:

- `NEXUS_CLOCK_MAXTICK=3000`: el reloj del servidor pausa las simulaciones al llegar al tick 3000 (unas 50 horas de misión). Avanzar a mano sigue funcionando.
- `NEXUS_RETENTION=24h`: cada hora se borran las simulaciones de más de un día.

En local no hay límites: si no se definen esas variables, no se aplican.
