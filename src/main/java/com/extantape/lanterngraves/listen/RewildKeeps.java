package com.extantape.lanterngraves.listen;

import com.extantape.lanterngraves.grave.Grave;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;

import java.lang.reflect.Constructor;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * Tells Rewild which chunks hold a grave, so it never resets one. Rewild refuses to reset anything while LanternGraves
 * runs without reporting, so this is what lets the two work together. Rewild's interface is reached by name through
 * its own class loader, so neither plugin needs the other installed.
 *
 * <p>Fails closed: while any grave file couldn't be read, its place is unknown, so the provider throws and Rewild
 * resets nothing in that world until the file is fixed and the server restarts.
 */
public final class RewildKeeps implements Listener {
    static final String PROVIDER = "com.extantape.rewild.api.KeepProvider";
    static final String KEEP = PROVIDER + "$Keep";
    static final String REASON = "a grave";

    private final Plugin owner;
    private final Supplier<Collection<Grave>> graves;
    private final BooleanSupplier damaged;
    private final Logger log;
    private boolean registered;

    public RewildKeeps(Plugin owner, Supplier<Collection<Grave>> graves, BooleanSupplier damaged) {
        this.owner = owner;
        this.graves = graves;
        this.damaged = damaged;
        this.log = owner.getLogger();
    }

    /** Registers now if Rewild is already running; otherwise when it enables. */
    public void start() {
        Plugin rewild = Bukkit.getPluginManager().getPlugin("Rewild");
        if (rewild != null && rewild.isEnabled()) register(rewild);
    }

    @EventHandler
    public void onEnable(PluginEnableEvent e) {
        if (e.getPlugin().getName().equals("Rewild")) register(e.getPlugin());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void register(Plugin rewild) {
        if (registered) return;
        try {
            ClassLoader cl = rewild.getClass().getClassLoader();
            Class<?> iface = Class.forName(PROVIDER, true, cl), keep = Class.forName(KEEP, true, cl);
            Object provider = provider(iface, keep, graves, damaged);
            Bukkit.getServicesManager().register((Class) iface, provider, owner, ServicePriority.Normal);
            registered = true;
            log.info("Telling Rewild which chunks hold graves, so it never resets them.");
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            // Rewild then sees LanternGraves running without a report and resets nothing, which is the safe side.
            log.warning("Rewild is installed but its keep interface doesn't match what LanternGraves expects (" + e
                    + "), so Rewild will reset nothing while both run.");
        }
    }

    /** A KeepProvider for the given interface and Keep record, answering from the graves. Package-private for tests. */
    static Object provider(Class<?> iface, Class<?> keepClass, Supplier<Collection<Grave>> graves, BooleanSupplier damaged)
            throws ReflectiveOperationException {
        Constructor<?> make = keepClass.getDeclaredConstructor(int.class, int.class, String.class);
        return Proxy.newProxyInstance(iface.getClassLoader(), new Class<?>[] {iface}, (proxy, method, args) -> switch (method.getName()) {
            case "keeps" -> {
                if (damaged.getAsBoolean()) {
                    throw new IllegalStateException("some grave files couldn't be read, so where those graves are is unknown; see /lanterngraves damaged");
                }
                List<Object> out = new ArrayList<>();
                for (long[] c : chunksIn(graves.get(), ((World) args[0]).getName())) out.add(make.newInstance((int) c[0], (int) c[1], REASON));
                yield out;
            }
            // Rewild 0.3.0 also asks for block areas to keep in place. A grave keeps its whole chunk, so there are none.
            case "areas" -> List.of();
            case "hashCode" -> System.identityHashCode(proxy);
            case "equals" -> proxy == args[0];
            case "toString" -> "LanternGraves' grave keeps";
            default -> throw new UnsupportedOperationException(method.getName());
        });
    }

    /** The chunks holding a grave in this world, each once. */
    static List<long[]> chunksIn(Collection<Grave> graves, String world) {
        Set<Long> seen = new LinkedHashSet<>();
        List<long[]> out = new ArrayList<>();
        for (Grave g : graves) {
            if (!world.equals(g.world())) continue;
            int cx = g.blockX() >> 4, cz = g.blockZ() >> 4;
            if (seen.add(((long) cx << 32) ^ (cz & 0xffffffffL))) out.add(new long[] {cx, cz});
        }
        return out;
    }
}
