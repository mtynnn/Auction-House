# PlaceholderAPI Integration

## 📊 Placeholders Disponibles

El plugin ahora incluye soporte completo para PlaceholderAPI. A continuación se listan todos los placeholders disponibles:

### 🔔 Notificaciones del Jugador

| Placeholder | Descripción | Valores |
|------------|-------------|---------|
| `%auctionhouse_notifications%` | Si el jugador tiene activadas las notificaciones de venta | `sí` / `no` |
| `%auctionhouse_notifications_enabled%` | Lo mismo en inglés | `yes` / `no` |
| `%auctionhouse_notifications_bool%` | Valor booleano de las notificaciones | `true` / `false` |
| `%auctionhouse_announcements%` | Alias de notifications | `sí` / `no` |
| `%auctionhouse_announcements_enabled%` | Alias en inglés | `yes` / `no` |
| `%auctionhouse_announcements_bool%` | Alias booleano | `true` / `false` |

### 👤 Subastas del Jugador

| Placeholder | Descripción | Ejemplo |
|------------|-------------|---------|
| `%auctionhouse_active_auctions%` | Número de subastas activas del jugador | `5` |
| `%auctionhouse_max_auctions%` | Límite máximo de subastas permitidas | `15` |
| `%auctionhouse_auctions_left%` | Cuántas subastas más puede crear | `10` |
| `%auctionhouse_my_bids_count%` | Número de pujas activas del jugador | `3` |
| `%auctionhouse_my_sold_items%` | Total de ítems vendidos del jugador | `25` |
| `%auctionhouse_my_expired_items%` | Total de ítems expirados del jugador | `2` |

### 🔐 Permisos

| Placeholder | Descripción | Valores |
|------------|-------------|---------|
| `%auctionhouse_is_admin%` | Si el jugador tiene permisos de administrador | `sí` / `no` |
| `%auctionhouse_is_admin_bool%` | Permisos de admin en booleano | `true` / `false` |

### 📈 Estadísticas Globales

| Placeholder | Descripción | Ejemplo |
|------------|-------------|---------|
| `%auctionhouse_total_auctions%` | Total de subastas activas en el sistema | `145` |
| `%auctionhouse_total_expired%` | Total de subastas expiradas | `23` |
| `%auctionhouse_total_sold%` | Total de subastas vendidas | `1250` |
| `%auctionhouse_total_all%` | Total de todas las subastas | `1418` |

## 💡 Ejemplos de Uso

**Ver guía completa de ejemplos:** [PLACEHOLDERS_EXAMPLES.md](PLACEHOLDERS_EXAMPLES.md)

### En Deluxe Menus
```yaml
main_menu:
  menu_title: "&6Menú Principal - Notificaciones: %auctionhouse_notifications%"
  items:
    auction_status:
      material: DIAMOND
      display_name: "&eTus Subastas: &a%auctionhouse_active_auctions%&7/&a%auctionhouse_max_auctions%"
      lore:
        - "&7Puedes crear &a%auctionhouse_auctions_left% &7más subastas"
        - "&7Notificaciones: %auctionhouse_notifications%"
```

### En FeatherBoard / ScoreBoards
```yaml
scoreboard:
  lines:
    - "&6&lAUCTION HOUSE"
    - ""
    - "&eMis Subastas: &f%auctionhouse_active_auctions%&7/&f%auctionhouse_max_auctions%"
    - "&eDisponibles: &f%auctionhouse_auctions_left%"
    - ""
    - "&eMis Pujas: &f%auctionhouse_my_bids_count%"
    - ""
    - "&eNotificaciones: %auctionhouse_notifications%"
```

### En DecentHolograms
```yaml
hologram_auction:
  location: world 100 64 200
  lines:
    - "&6&lCASA DE SUBASTAS"
    - ""
    - "&eSubastas Activas: &f%auctionhouse_total_auctions%"
    - "&eVendidas Hoy: &f%auctionhouse_total_sold%"
    - ""
    - "&7Click para abrir"
```

### En Chat con Conditional Placeholders
```yaml
# Usando PlaceholderAPI con condicionales
%auctionhouse_active_auctions% >= 10 ? "&c¡Límite casi alcanzado!" : "&aOK"
```

## 📝 Notas

- Los placeholders de jugador requieren que el jugador esté online
- Los placeholders globales (total_*) funcionan sin necesidad de jugador
- El placeholder `notifications` es **case-insensitive** (puede escribirse en mayúsculas/minúsculas)
- Todos los placeholders están disponibles inmediatamente después de instalar el plugin

## 🔧 Requisitos

- **PlaceholderAPI** 2.11.6 o superior instalado en el servidor
- El plugin se carga automáticamente con PlaceholderAPI como dependencia suave (softdepend)

## ⚙️ Instalación

1. Instala PlaceholderAPI en tu servidor
2. Reinicia o recarga el servidor
3. Los placeholders estarán disponibles automáticamente
4. Verifica con: `/papi parse me %auctionhouse_notifications%`

## 🐛 Solución de Problemas

Si los placeholders no funcionan:

1. Verifica que PlaceholderAPI esté instalado: `/plugins`
2. Comprueba que la expansión esté registrada: `/papi list`
3. Prueba el placeholder: `/papi parse me %auctionhouse_notifications%`
4. Revisa los logs del servidor para errores de PlaceholderAPI

## 🎨 Personalización

Puedes combinar estos placeholders con otros de PlaceholderAPI para crear displays personalizados:

```yaml
# Ejemplo combinado con Player placeholders
display_name: "&e%player_name% &7- Subastas: %auctionhouse_active_auctions%"
lore:
  - "&7Balance: &a$%vault_eco_balance_formatted%"
  - "&7Notificaciones: %auctionhouse_notifications%"
  - "&7Rango: %luckperms_prefix%"
```
