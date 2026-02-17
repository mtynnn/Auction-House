# AuctionHouse - Documentación Técnica

`AuctionHouse` es un plugin de casa de subastas para Minecraft (Paper/Spigot 1.21+) con GUI, ventas directas (BIN), subastas por puja (BID), anuncios en chat por jugador y almacenamiento persistente (SQLite).

## Requisitos y Dependencias

Para un funcionamiento correcto:

- Java: 21 o superior.
- Servidor: Paper/Purpur/Spigot 1.21+.
- Vault + un plugin de economía (EssentialsX, CMI, etc.).
- PlaceholderAPI (opcional): placeholders `%auctionhouse_*%`.
- Locale-API (opcional): búsqueda/ítems traducidos por jugador.

## Compilación

Este proyecto usa Gradle.

1) Clona el repositorio:

```
git clone https://github.com/elaineqheart/Auction-House.git
```

2) Compila:

Windows:

```
gradlew.bat clean build
```

Linux/macOS:

```
./gradlew clean build
```

3) El jar generado queda en:

- `build/libs/AuctionHouse-<version>.jar`

## Características

## Sistema de Subastas

- GUI principal con paginación, ordenamiento y búsqueda.
- BIN (`/ah sell`) y BID (`/ah bid`) según configuración.
- Compra/visualización de un ítem por UUID: `/ah view <uuid>` (abre el mismo menú de compra que el click del anuncio).
- Anuncios en chat por jugador (toggle): `/ah announce`.
- Protección de precio (opcional): validación asíncrona contra promedio histórico con timeout para no congelar el servidor.

## Filtros de GUI (Importante)

Los “menús vacíos” casi siempre se deben a filtros activos:

- Búsqueda: se guarda por sesión y se limpia con clic derecho en la lupa.
- Filtro BIN/BID (si ambos modos están activos).
- Whitelist/categorías (si se usa).

Para evitar confusión, cuando una búsqueda/filtro deja 0 resultados se muestra un botón “Sin resultados” que limpia filtros.

## Seguridad y Performance

- SQLite con pool (HikariCP) y enfoque “PlugMan-compatible” (shutdown limpio + cancelación de tasks).
- Manejo de ítems “corruptos” (dependencias faltantes) usando un placeholder (BARRIER) en vez de crashear.
- Cache de ítems renderizados por jugador para reducir coste de construir lore/item-meta.

## Comandos y Permisos

## Comandos de Usuario

Comando | Descripción | Permiso
--- | --- | ---
`/ah` | Abre la casa de subastas | `auctionhouse.ah`
`/ah sell <precio> [cantidad]` | Publica una venta directa (BIN) | `auctionhouse.ah`
`/ah bid <precio> [cantidad]` | Publica una subasta de puja (BID) | `auctionhouse.ah`
`/ah announce` | Activa/desactiva anuncios en chat | `auctionhouse.ah`

## Comandos Técnicos/Internos

Comando | Descripción
--- | ---
`/ah view <uuid>` | Abre el menú de compra/gestión de una subasta concreta

## Configuración

Archivos principales:

- `src/main/resources/config.yml` (settings generales)
- `src/main/resources/messages.yml` (mensajes y anuncios)
- `src/main/resources/permissions.yml` (slots/límites por permisos)
- `src/main/resources/gui/*.yml` (GUIs)

## Placeholders (PlaceholderAPI)

Identificador: `auctionhouse`

- Lista completa: `PLACEHOLDERS.md`
- Ejemplos: `PLACEHOLDERS_EXAMPLES.md`

## Notas de Integración

- El anuncio de nueva subasta en chat es clickeable: al hacer clic abre la compra del ítem específico (usa `/ah view <uuid>`).
- Si un jugador reporta “no veo ítems”, revisa primero si tiene una búsqueda activa o el filtro BIN/BID cambiado.
