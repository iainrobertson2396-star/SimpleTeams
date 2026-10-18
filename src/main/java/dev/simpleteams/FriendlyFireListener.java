package dev.simpleteams;

import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public final class FriendlyFireListener implements Listener {

    private static final Set<PotionEffectType> HARMFUL_EFFECTS = Set.of(
            PotionEffectType.INSTANT_DAMAGE,
            PotionEffectType.POISON,
            PotionEffectType.WITHER,
            PotionEffectType.SLOWNESS,
            PotionEffectType.WEAKNESS,
            PotionEffectType.BLINDNESS,
            PotionEffectType.NAUSEA,
            PotionEffectType.HUNGER,
            PotionEffectType.MINING_FATIGUE,
            PotionEffectType.LEVITATION,
            PotionEffectType.DARKNESS,
            PotionEffectType.UNLUCK
    );

    private final TeamManager teams;

    public FriendlyFireListener(TeamManager teams) {
        this.teams = teams;
    }

    /** Melee, arrows, tridents, snowballs, fireballs, primed TNT, etc. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;

        Player attacker = resolveAttacker(event.getDamager());
        if (attacker == null || attacker.equals(victim)) return;

        if (teams.isProtected(attacker.getUniqueId(), victim.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    /** Splash potions: remove harmful effects from teammates of the thrower. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSplash(PotionSplashEvent event) {
        ThrownPotion potion = event.getPotion();
        if (!(potion.getShooter() instanceof Player thrower)) return;
        if (!hasHarmfulEffect(potion.getEffects())) return;

        for (LivingEntity affected : event.getAffectedEntities()) {
            if (affected instanceof Player victim
                    && !victim.equals(thrower)
                    && teams.isProtected(thrower.getUniqueId(), victim.getUniqueId())) {
                event.setIntensity(victim, 0);
            }
        }
    }

    /** Lingering potion clouds. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCloud(AreaEffectCloudApplyEvent event) {
        AreaEffectCloud cloud = event.getEntity();
        if (!(cloud.getSource() instanceof Player thrower)) return;

        List<PotionEffect> effects = new ArrayList<>(cloud.getCustomEffects());
        PotionType base = cloud.getBasePotionType();
        if (base != null) {
            effects.addAll(base.getPotionEffects());
        }
        if (!hasHarmfulEffect(effects)) return;

        event.getAffectedEntities().removeIf(entity ->
                entity instanceof Player victim
                        && !victim.equals(thrower)
                        && teams.isProtected(thrower.getUniqueId(), victim.getUniqueId()));
    }

    private boolean hasHarmfulEffect(Collection<PotionEffect> effects) {
        for (PotionEffect effect : effects) {
            if (HARMFUL_EFFECTS.contains(effect.getType())) return true;
        }
        return false;
    }

    private Player resolveAttacker(Entity damager) {
        if (damager instanceof Player player) return player;
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) return player;
        if (damager instanceof TNTPrimed tnt && tnt.getSource() instanceof Player player) return player;
        if (damager instanceof AreaEffectCloud cloud && cloud.getSource() instanceof Player player) return player;
        return null;
    }
}
