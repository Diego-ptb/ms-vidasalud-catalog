# ms-vidasalud-catalog

Microservicio de **catálogo** del caso VidaSalud · DSY1107 Desarrollo Cloud Native I.

Administra las prestaciones, los boxes clínicos y los cupos disponibles. Es dueño exclusivo
de su base de datos y no se expone a internet: solo recibe llamadas de `ms-vidasalud-bff`,
que ya validó el JWT.

## Endpoints

| Método | Ruta | Descripción |
|---|---|---|
| `GET` | `/api/catalog/services` | Listar prestaciones |
| `GET` | `/api/catalog/services/{id}` | Detalle |
| `POST` | `/api/catalog/services` | Crear |
| `PUT` | `/api/catalog/services/{id}` | Actualizar precio, box o cupos |
| `PUT` | `/api/catalog/services/{id}/slots/take` | Descontar un cupo |
| `DELETE` | `/api/catalog/services/{id}` | Eliminar |

`slots/take` implementa la regla del caso: **el cupo del box disminuye al confirmar la
atención**. Si no quedan cupos responde **409 Conflict**.

La autorización vive en el BFF: leer el catálogo lo pueden ADMIN, RECEPCIONISTA y PACIENTE
—este último necesita ver las prestaciones para agendar—; escribir, solo ADMIN.

## Mensajería

El descuento de cupo llega normalmente **por cola**, no por HTTP. `ms-vidasalud-appointments`
publica un comando `cupo.tomar` en el exchange direct `vidasalud.comandos.direct` al
confirmar una atención, y este servicio lo consume de `q.catalogo.cupo`.

La diferencia práctica frente a la llamada HTTP directa: si el catálogo está caído, la
confirmación de la atención no falla. El mensaje espera en la cola y se procesa cuando el
servicio vuelve.

El endpoint `PUT /{id}/slots/take` se mantiene para operar a mano y para poder probar la
regla sin broker. La regla misma vive en `SlotService`, con una sola implementación para
las dos puertas de entrada.

### ACK y errores

ACK manual:

| Situación | Decisión |
|---|---|
| Cupo descontado | `basicAck` |
| Prestación inexistente | `basicNack` sin requeue → DLQ |
| Sin cupos disponibles | `basicNack` sin requeue → DLQ |
| Fallo transitorio, 1ª vez | `basicNack` con requeue |
| Fallo transitorio, 2ª vez | `basicNack` sin requeue → DLQ |

"Sin cupos" va a la DLQ y no a reintento a propósito: el cupo no va a aparecer por
insistir, y alguien tiene que revisar esa atención a mano. Una atención confirmada sin
cupo descontado deja el box mal contado, así que el consumidor de
`q.catalogo.cupo.dlq` lo registra con nivel `error`.

## Persistencia

Entidad `ServiceItem` → tabla `vs_service`, vía Spring Data JPA. Hibernate crea el esquema
al arrancar (`ddl-auto: update`).

Dos perfiles:

| Perfil | Base |
|---|---|
| `dev` (por defecto) | H2 en memoria, para desarrollar sin dependencias |
| `supabase` | PostgreSQL gestionado en Supabase |

Usa un **proyecto Supabase distinto** al de `ms-vidasalud-appointments`: cada microservicio
es dueño de su base y ninguno consulta las tablas del otro.

## Ejecutar en local

Requiere **JDK 17+**.

```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-17"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

mvn spring-boot:run                 # perfil dev, H2 en memoria
```

Contra PostgreSQL:

```powershell
$env:SPRING_PROFILES_ACTIVE = "supabase"
$env:DB_CATALOG_URL = "jdbc:postgresql://<host>.pooler.supabase.com:5432/postgres"
$env:DB_CATALOG_USER = "postgres.<project-ref>"
$env:DB_CATALOG_PASSWORD = "<password>"
$env:INTERNAL_KEY = "<clave compartida con el BFF>"

mvn spring-boot:run
```

Queda en `http://localhost:8082`.

> Usa el **Session pooler** de Supabase (puerto 5432), no el Transaction pooler (6543).

## Variables de entorno

| Variable | Descripción |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `dev` o `supabase` |
| `DB_CATALOG_URL` · `_USER` · `_PASSWORD` | Conexión a PostgreSQL |
| `INTERNAL_KEY` | Clave compartida; sin ella toda llamada responde 401 |
| `INTERNAL_KEY_ENABLED` | `false` para desactivar la comprobación en desarrollo |

## Stack

Java 17 · Spring Boot 3.3.5 · Spring Data JPA · PostgreSQL / H2 · Maven · Docker
