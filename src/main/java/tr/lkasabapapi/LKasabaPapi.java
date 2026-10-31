package tr.lkasabapapi;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * lKasaba icin PlaceholderAPI destegi.
 * lKasaba'nin jar'ina derleme sirasinda baglanmaz, reflection ile okur.
 */
public final class LKasabaPapi extends JavaPlugin {

    private Expansion expansion;

    @Override
    public void onEnable() {
        Plugin lk = getServer().getPluginManager().getPlugin("lKasaba");
        if (lk == null || !getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            getLogger().severe("lKasaba veya PlaceholderAPI bulunamadi, eklenti kapatiliyor.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        try {
            expansion = new Expansion(lk);
            expansion.register();
            getLogger().info("lkasaba placeholder'lari kaydedildi.");
        } catch (Exception e) {
            getLogger().severe("lKasaba'ya baglanilamadi: " + e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        if (expansion != null) {
            expansion.unregister();
        }
    }

    private static final class Expansion extends PlaceholderExpansion {

        private final Plugin lk;
        private final Method townsMethod;
        private final Method townOf;
        private final Method moneyCents;
        private final Field fName;
        private final Field fOwner;
        private final Field fBank;
        private final Field fMembers;
        private final Field fClaims;
        private final Field fPvp;

        Expansion(Plugin lk) throws Exception {
            this.lk = lk;

            this.townsMethod = lk.getClass().getMethod("towns");
            this.townsMethod.setAccessible(true);
            Object towns = this.townsMethod.invoke(lk);

            this.townOf = towns.getClass().getMethod("town", UUID.class);
            this.townOf.setAccessible(true);

            Method mc = null;
            try {
                mc = towns.getClass().getMethod("moneyCents", long.class);
                mc.setAccessible(true);
            } catch (NoSuchMethodException ignored) {
                // bicimlendirme yoksa kendi bicimimizi kullaniriz
            }
            this.moneyCents = mc;

            Class<?> townClass = this.townOf.getReturnType();
            this.fName = townClass.getField("name");
            this.fOwner = townClass.getField("owner");
            this.fBank = townClass.getField("bankCents");
            this.fMembers = townClass.getField("members");
            this.fClaims = townClass.getField("claims");
            this.fPvp = townClass.getField("pvp");
            for (Field f : new Field[]{fName, fOwner, fBank, fMembers, fClaims, fPvp}) {
                f.setAccessible(true);
            }
        }

        @Override
        public String getIdentifier() {
            return "lkasaba";
        }

        @Override
        public String getAuthor() {
            return "AsgardMC";
        }

        @Override
        public String getVersion() {
            return "1.0";
        }

        @Override
        public boolean persist() {
            return true;
        }

        @Override
        public String onRequest(OfflinePlayer player, String params) {
            if (player == null) {
                return "";
            }
            String key = params.toLowerCase(Locale.ROOT);

            Object town;
            Object towns;
            try {
                towns = townsMethod.invoke(lk);
                town = townOf.invoke(towns, player.getUniqueId());
            } catch (Exception e) {
                return "?";
            }

            if (key.equals("has_town")) {
                return town != null ? "true" : "false";
            }

            if (town == null) {
                return switch (key) {
                    case "name" -> "Yok";
                    case "balance" -> "-";
                    case "balance_raw" -> "0.00";
                    case "balance_int" -> "0";
                    case "members", "claims" -> "0";
                    case "owner", "pvp" -> "-";
                    default -> null;
                };
            }

            try {
                switch (key) {
                    case "name":
                        return String.valueOf(fName.get(town));
                    case "owner": {
                        UUID id = (UUID) fOwner.get(town);
                        String n = id == null ? null : Bukkit.getOfflinePlayer(id).getName();
                        return n == null ? "?" : n;
                    }
                    case "balance": {
                        long cents = fBank.getLong(town);
                        if (moneyCents != null) {
                            return String.valueOf(moneyCents.invoke(towns, cents));
                        }
                        return String.format(Locale.US, "%.2f", cents / 100.0);
                    }
                    case "balance_raw":
                        return String.format(Locale.US, "%.2f", fBank.getLong(town) / 100.0);
                    case "balance_int":
                        return String.valueOf(fBank.getLong(town) / 100);
                    case "members":
                        return String.valueOf(((Map<?, ?>) fMembers.get(town)).size());
                    case "claims":
                        return String.valueOf(((Set<?>) fClaims.get(town)).size());
                    case "pvp":
                        return fPvp.getBoolean(town) ? "Acik" : "Kapali";
                    default:
                        return null;
                }
            } catch (Exception e) {
                return "?";
            }
        }
    }
}
