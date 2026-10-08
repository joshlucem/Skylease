# Skylease

Tiempo de vuelo por jugador para servidores Paper. [English](README.md)

Cada jugador tiene un saldo de segundos de vuelo. `/fly` activa el vuelo, el saldo baja mientras vuela y, al acabarse, desciende despacio en lugar de caer. Los administradores suman o restan tiempo con un comando, así que se conecta fácil a una tienda o a un sistema de recompensas.

## Qué incluye

- Saldo de vuelo por jugador, con consumo y espera entre activaciones configurables
- Aterrizaje suave al acabarse el tiempo
- Avisos de tiempo bajo que cada jugador activa o desactiva
- 33 efectos de partículas, cada uno con su permiso
- Mundos permitidos y bloqueados, y un flag de región de WorldGuard para negar el vuelo
- Placeholders de PlaceholderAPI
- SQLite, o MySQL/MariaDB para compartir datos entre servidores
- Inglés y español; puedes añadir tu idioma en `lang/`

## Requisitos

- Paper 1.21 o superior
- Java 21

PlaceholderAPI y WorldGuard son opcionales.

## Instalación

1. Descarga el JAR de [Releases](https://github.com/joshlucem/Skylease/releases).
2. Cópialo a `plugins` y reinicia.
3. En el primer arranque Paper descarga los controladores de base de datos, así que el servidor necesita Internet.
4. Edita los archivos de `plugins/Skylease` y aplícalos con `/flyadmin reload`.

## Comandos

| Comando | Qué hace | Permiso |
|---|---|---|
| `/fly` | Activa o desactiva tu vuelo | `skylease.user.fly` |
| `/fly settings` | Abre el menú de avisos y efectos | `skylease.user.menu.settings` |
| `/fly shop` | Muestra cómo conseguir más tiempo | `skylease.user.fly` |
| `/flyadmin add <jugador> <tiempo>` | Suma tiempo | `skylease.admin.command.add` |
| `/flyadmin remove <jugador> [tiempo]` | Resta tiempo; sin cantidad, todo | `skylease.admin.command.remove` |
| `/flyadmin info <jugador>` | Muestra los datos de un jugador | `skylease.admin.command.info` |
| `/flyadmin reload` | Recarga la configuración | `skylease.admin.command.reload` |
| `/flyadmin debug on\|off` | Activa o desactiva la depuración | `skylease.admin.debug` |
| `/skylease about` | Muestra los créditos | `skylease.about` |

Los tiempos usan `s`, `m`, `h`, `d`, `mo` (30 días) e `y` (365 días), solos o combinados: `30s`, `2h`, `1h30m`.

## Otros permisos

| Permiso | Efecto |
|---|---|
| `skylease.unlimited` | Vuela sin gastar tiempo |
| `skylease.user.cooldown.bypass` | Ignora la espera entre activaciones |
| `skylease.admin.bypass-restrictions` | Vuela en mundos y regiones bloqueados |
| `skylease.admin.updates` | Recibe el aviso de versión nueva al entrar |
| `skylease.effects.<efecto>` | Usa un efecto de partículas |
| `skylease.effects.*` | Usa todos |

## Configuración

| Archivo | Contenido |
|---|---|
| `config.yml` | Idioma, base de datos, vuelo, avisos y placeholders |
| `lang/*.yml` | Todo el texto: mensajes, menús y nombres de efectos |
| `menus.yml` | Diseño de los menús |
| `effects.yml` | Los efectos de partículas |

El idioma se elige en `config.yml`, en `language.selected`. Para añadir uno, copia `lang/en.yml`, tradúcelo y usa su nombre. Lo que falte se toma del inglés. Un mensaje vacío no se envía.

## WorldGuard

Con WorldGuard instalado, Skylease añade el flag de región `skylease-fly`:

```
/rg flag <región> skylease-fly deny
```

Dentro de esa región no se puede activar el vuelo, y quien ya vuela al entrar baja despacio. Las regiones sin el flag permiten volar. `skylease.admin.bypass-restrictions` lo ignora. Se desactiva con `flight.worldguard-integration: false`.

## Placeholders

Con PlaceholderAPI, solo para jugadores conectados. Son los mismos que en la versión en inglés: `%skylease_fly-time%` (da `∞` con vuelo ilimitado), `fly-time-seconds`, `fly-has-time`, `fly-unlimited`, `fly-status`, `fly-effect`, `fly-effect-name`, `fly-alerts`, `fly-alerts-list`, `fly-percentage`, `fly-bar` y `fly-time-color`.

## Datos

Por jugador se guardan el UUID, el nombre, el tiempo restante, el efecto elegido y los avisos. Nada más.

Con MySQL, varios servidores pueden compartir los datos. Está pensado para un solo servidor a la vez por jugador: cada servidor guarda a sus jugadores en memoria y los escribe cada `save-interval-seconds` y al salir. En una red, quien cambia de servidor puede perder los cambios de los últimos segundos en el servidor que dejó.

El verificador de actualizaciones hace una petición a la API pública de GitHub al arrancar y no envía nada del servidor ni de los jugadores. Se desactiva con `update-checker.enabled: false`.

## Compilar

Requiere Java 21 y Maven: `mvn package`. El JAR queda en `target/`.

## Licencia y créditos

Código abierto bajo la [Apache License 2.0](LICENSES/Apache-2.0.txt) más el [Anexo de preservación de créditos](LICENSES/LicenseRef-Skylease-Credits.txt). Puedes usarlo, modificarlo y redistribuirlo, también con fines comerciales.

El anexo pide una cosa: conservar los créditos incluidos en el plugin (el autor en `plugin.yml`, los avisos de los archivos, `NOTICE`, `/skylease about` y el banner de la consola), incluso en versiones modificadas. Una versión modificada debe indicarlo y mantener al autor original. Mencionar el proyecto en tu página de descarga se agradece, pero no es obligatorio.

Creado por [joshlucem](https://github.com/joshlucem).
