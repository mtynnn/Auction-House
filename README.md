# Auction-House
use /ah to open the Acution House

Plugin originally made for SpireMC; This is a spigot plugin and I'm using gradle. The plugin works both on spigot and paper servers on 1.21.4, maybe even never versions.

*spigot mc page for some images:* 
https://www.spigotmc.org/resources/auction-house.125238/

*also modrinth page:* https://modrinth.com/plugin/auction-house-plugin

---

## 🚀 Features

- ✅ **PlugMan Reload Compatible** - Full support for hot-reload using PlugManX
- ✅ **Corrupted Item Protection** - Items from missing plugins (custom enchantments) won't crash the server
- ✅ **Optimized Performance** - `/ah sell` command responds instantly (async price validation)
- ✅ **Pterodactyl/Docker Ready** - Compatible with containerized environments
- ✅ **HikariCP Connection Pool** - Efficient database connection management
- ✅ **SQLite w/ WAL Mode** - Prevents database locking issues

---

## 📦 Installation

1. Download the latest `.jar` from [Releases](https://github.com/yourusername/Auction-House/releases)
2. Place in `plugins/` folder
3. Restart server
4. Configure `plugins/Auction-House/config.yml`

**Requirements:**
- Minecraft 1.21+ (Spigot/Paper/Purpur)
- Vault + Economy plugin (EssentialsX, CMI, etc.)
- **LuckPerms** (recommended for permission-based auction slots)
- **PlaceholderAPI** (optional, for placeholders integration)

---

## 📊 PlaceholderAPI Support

The plugin includes full PlaceholderAPI integration with 15+ placeholders including:

- `%auctionhouse_notifications%` - Si el jugador tiene notificaciones activadas (yes/no)
- `%auctionhouse_active_auctions%` - Número de subastas activas del jugador
- `%auctionhouse_max_auctions%` - Límite máximo de subastas
- `%auctionhouse_total_auctions%` - Total de subastas en el servidor
- And more...

**Full placeholder list:** [PLACEHOLDERS.md](PLACEHOLDERS.md)

---

## 🔐 LuckPerms Configuration

Configure auction slot limits per group using permission nodes:

```bash
# Give each group their auction slot permission
/lp group guardian permission set auctionhouse.slots.guardian    # 6 slots
/lp group elite permission set auctionhouse.slots.elite          # 15 slots
/lp group leyenda permission set auctionhouse.slots.leyenda      # 18 slots
```

**Full setup guide:** [LUCKPERMS_SETUP.md](LUCKPERMS_SETUP.md)

**Quick config in `permissions.yml`:**
```yaml
auction-slots:
  auctionhouse.slots.guardian: 6
  auctionhouse.slots.elite: 15
  auctionhouse.slots.leyenda: 18
```

---

## 🐳 Pterodactyl / Docker Setup

**Primero, verifica tu entorno:**

```bash
bash check-environment.sh
```

Esto te dirá si estás en Pterodactyl, el usuario actual, y el estado de los permisos.

---

If you're running on Pterodactyl or Docker, you may need to fix file permissions:

```bash
cd /home/container/plugins/Auction-House
chmod -R 777 data/
```

**OR** run the automated script:
```bash
bash fix-pterodactyl-permissions.sh
```

📖 **Full guide:** [PTERODACTYL.md](PTERODACTYL.md)

---

## 🔄 PlugMan Reload

Tested and working with [PlugManX](https://www.spigotmc.org/resources/plugmanx.88135/):

```
/plugman reload AuctionHouse
```

The plugin will display `[PlugMan-Compatible]` logs during shutdown/reload to confirm proper cleanup.

---

## 🛠️ Building from Source

```bash
git clone https://github.com/yourusername/Auction-House.git
cd Auction-House
./gradlew shadowJar
```

Output: `build/libs/AuctionHouse-1.4.3.jar`

---

## ⚠️ Known Issues & Fixes

### Database Permission Errors (Pterodactyl)
**Error:** `[SQLITE_READONLY_DIRECTORY] Process does not have permission...`

**Fix:** Run `chmod -R 777 plugins/Auction-House/data/` or see [PTERODACTYL.md](PTERODACTYL.md)

### Items from Missing Plugins
**Issue:** Server had custom enchantments from `nova_structures` plugin (now removed)

**Fixed:** Plugin automatically handles corrupted items by displaying them as red BARRIER blocks with informative lore. Players can still see and manage their auctions.

### Commands Taking 4 Seconds
**Issue:** `/ah sell <price>` had 4-second delay

**Fixed:** Price validation now runs asynchronously (500ms timeout instead of blocking main thread)

---

## 🤝 Contributing

Contributions welcome! Please:
1. Fork the repository
2. Create a feature branch
3. Test on Paper 1.21+
4. Submit a pull request

---

## 📝 License

See [LICENSE](LICENSE) file for details.

---

## 🙏 Credits

- Original plugin for SpireMC
- Built with Gradle
- Uses HikariCP for connection pooling
- Vault API for economy integration
