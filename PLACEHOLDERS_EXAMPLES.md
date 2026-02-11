# Ejemplos de Uso - PlaceholderAPI

## 🔔 Placeholder de Notificaciones (Caso de Uso Principal)

El placeholder `%auctionhouse_notifications%` es especialmente útil para mostrar en interfaces gráficas, scoreboards o holograms si un jugador tiene activadas las notificaciones de venta.

### Ejemplo 1: Scoreboard con FeatherBoard

```yaml
# config.yml de FeatherBoard
board:
  title:
    - "&6&lAUCTION HOUSE"
  text:
    - ""
    - "&eMis Subastas:"
    - "  &fActivas: &a%auctionhouse_active_auctions%&7/&a%auctionhouse_max_auctions%"
    - "  &fDisponibles: &a%auctionhouse_auctions_left%"
    - ""
    - "&eNotificaciones:"
    - "  %auctionhouse_notifications_enabled% == yes ? &a✔ Activadas : &c✘ Desactivadas"
    - ""
    - "&7/ah toggle para cambiar"
```

### Ejemplo 2: GUI con DeluxeMenus

```yaml
# Menu que muestra el estado de notificaciones
notification_menu:
  menu_title: "&6Configuración - Casa de Subastas"
  size: 27
  items:
    notification_toggle:
      slot: 13
      material: "%auctionhouse_notifications_bool% == true ? LIME_DYE : GRAY_DYE"
      display_name: "%auctionhouse_notifications% == sí ? &a✔ Notificaciones Activadas : &c✘ Notificaciones Desactivadas"
      lore:
        - ""
        - "&7Estado actual: %auctionhouse_notifications%"
        - ""
        - "%auctionhouse_notifications% == sí ? &7¡Recibirás notificaciones cuando : &7¡No recibirás notificaciones!"
        - "%auctionhouse_notifications% == sí ? &7alguien compre tus items! : &7"
        - ""
        - "&eClick para %auctionhouse_notifications% == sí ? &cdesactivar : &aactivar"
      click_commands:
        - "[player] ah toggle"
        - "[refresh]"
    
    stats_item:
      slot: 11
      material: CHEST
      display_name: "&eTus Estadísticas"
      lore:
        - ""
        - "&7Subastas Activas: &f%auctionhouse_active_auctions%"
        - "&7Límite Máximo: &f%auctionhouse_max_auctions%"
        - "&7Espacios Libres: &a%auctionhouse_auctions_left%"
        - ""
        - "&7Pujas Activas: &f%auctionhouse_my_bids_count%"
        - "&7Items Vendidos: &f%auctionhouse_my_sold_items%"
        - "&7Items Expirados: &f%auctionhouse_my_expired_items%"
```

### Ejemplo 3: Hologram con DecentHolograms

```yaml
# Hologram que muestra stats del jugador cuando se acerca
player_stats_hologram:
  location: world 100 64 200
  update-interval: 20  # Actualiza cada segundo
  lines:
    - "&6&l%player_name%"
    - ""
    - "&eSubastas: &f%auctionhouse_active_auctions%&7/&f%auctionhouse_max_auctions%"
    - ""
    - "%auctionhouse_notifications% == sí ? &a✔ Notificaciones ON : &c✘ Notificaciones OFF"
    - ""
    - "&7Usa &e/ah toggle &7para cambiar"
```

### Ejemplo 4: TAB con TAB Plugin

```yaml
# config.yml de TAB
tablist-name-formatting:
  format: "%auctionhouse_is_admin% == sí ? &c[ADMIN] : &7"
  
scoreboard:
  lines:
    - "&6&lAH INFO"
    - "&eSubastas: %auctionhouse_active_auctions%/%auctionhouse_max_auctions%"
    - "&eNotif: %auctionhouse_notifications%"
```

### Ejemplo 5: Condicional en Chat con PlaceholderAPI Conditional

```yaml
# Usando papi_conditional
prefix: "%auctionhouse_active_auctions% >= %auctionhouse_max_auctions% ? &c[FULL] : &a[OK]"

# Mensaje cuando vende un item
sell_message: "&aItem vendido! %auctionhouse_notifications% == no ? &7(Recuerda activar notificaciones con /ah toggle) : &7"
```

### Ejemplo 6: BossBar con BossBarAPI

```java
// En tu plugin personalizado
String notifStatus = PlaceholderAPI.setPlaceholders(player, "%auctionhouse_notifications%");
String message = notifStatus.equals("sí") 
    ? "§aNotificaciones: ✔ Activadas" 
    : "§cNotificaciones: ✘ Desactivadas - Usa /ah toggle";

BossBar bar = Bukkit.createBossBar(message, BarColor.GREEN, BarStyle.SOLID);
bar.addPlayer(player);
```

### Ejemplo 7: Title/Subtitle al Vender

```yaml
# Con CommandPrompter o similar
on_auction_create:
  - "title %player% subtitle %auctionhouse_notifications% == no ? &c⚠ Activa notificaciones con /ah toggle : &a✔ Recibirás notificaciones"
```

## 🎯 Casos de Uso Recomendados

### 1. **Avisar al Jugador**
Usa el placeholder para recordarle al jugador que tiene las notificaciones desactivadas:

```yaml
lore:
  - "%auctionhouse_notifications% == no ? &c⚠ Tienes las notificaciones desactivadas : &7"
  - "%auctionhouse_notifications% == no ? &7Usa /ah toggle para activarlas : &7"
```

### 2. **Indicador Visual**
Muestra un icono diferente según el estado:

```yaml
material: "%auctionhouse_notifications_bool% == true ? BELL : IRON_BLOCK"
```

### 3. **Estadísticas del Jugador**
Combina múltiples placeholders:

```yaml
display_name: "&e%player_name% &7| Subastas: %auctionhouse_active_auctions%/%auctionhouse_max_auctions%"
lore:
  - "&7Balance: &a$%vault_eco_balance_formatted%"
  - "&7Notificaciones: %auctionhouse_notifications%"
  - "&7Admin: %auctionhouse_is_admin%"
```

## 🔍 Testing de Placeholders

Para probar los placeholders en el juego:

```
/papi parse me %auctionhouse_notifications%
/papi parse me %auctionhouse_active_auctions%
/papi parse me %auctionhouse_max_auctions%
/papi parse me %auctionhouse_notifications_bool%
```

## 💡 Tip Profesional

Combina el placeholder con comandos condicionales para crear experiencias interactivas:

```yaml
# En un GUI, botón que cambia de color según el estado
notification_button:
  material: "%auctionhouse_notifications_bool% == true ? LIME_WOOL : RED_WOOL"
  display_name: "%auctionhouse_notifications% == sí ? &a✔ : &c✘% Notificaciones"
  lore:
    - ""
    - "%auctionhouse_notifications% == sí ? &aEstás recibiendo notificaciones : &cNo recibes notificaciones"
    - "%auctionhouse_notifications% == sí ? &ade tus ventas exitosas : &cde tus ventas"
    - ""
    - "&eClick para cambiar"
  click_commands:
    - "[player] ah toggle"
    - "[sound] BLOCK_NOTE_BLOCK_PLING"
    - "[close]"
```

## 📚 Recursos Adicionales

- [PlaceholderAPI Wiki](https://wiki.placeholderapi.com/)
- [DeluxeMenus Conditional Placeholders](https://wiki.helpch.at/clips-plugins/deluxemenus/options-and-configurations)
- [FeatherBoard Configuration](https://www.spigotmc.org/resources/featherboard.2691/)
