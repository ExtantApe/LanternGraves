package com.extantape.lanterngraves.marker;

import com.extantape.lanterngraves.Fixtures;
import com.extantape.lanterngraves.grave.Grave;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * K819: every message that could carry a grave owner's name to another player follows the same answer as the
 * label, and fails closed. 0.3.0 put the name in the refusal, the shared list and the sharing notice whatever
 * Posse said. The made-up owner in {@link Fixtures} is called Tester.
 */
class NameLeakTest {
    private static final GraveNames.Rules ALLOW = (v, o, w) -> true;
    private static final GraveNames.Rules REFUSE = (v, o, w) -> false;
    private static final GraveNames.Rules BROKEN = (v, o, w) -> { throw new IllegalStateException("down"); };
    private static final GraveNames.Rules OLD = (v, o, w) -> { throw new NoSuchMethodError("an older Posse"); };

    private final Grave grave = Fixtures.grave();
    private final Player stranger = player(UUID.randomUUID());
    private final Player owner = player(grave.owner());

    /** A player with an id and nothing else: all the name rule reads before it asks Posse. */
    private static Player player(UUID id) {
        return (Player) Proxy.newProxyInstance(Player.class.getClassLoader(), new Class<?>[] {Player.class}, (proxy, method, args) -> switch (method.getName()) {
            case "getUniqueId" -> id;
            case "hashCode" -> id.hashCode();
            case "equals" -> proxy == args[0];
            case "toString" -> "player " + id;
            default -> throw new UnsupportedOperationException(method.getName());
        });
    }

    private static GraveNames names(GraveNames.Rules rules) {
        GraveNames n = new GraveNames(Logger.getAnonymousLogger());
        n.rules(rules);
        return n;
    }

    private String refusal(GraveNames.Rules rules, Player viewer, boolean accessEnded) {
        return GraveNames.refused(names(rules).nameFor(viewer, grave), accessEnded);
    }

    private String listLine(GraveNames.Rules rules, Player viewer) {
        return GraveNames.shared(names(rules).nameFor(viewer, grave), 2, "10, 64, -4 in testworld");
    }

    private String notice(GraveNames.Rules rules, Player viewer) {
        return GraveNames.trusted(names(rules).mayNameAll(viewer, List.of(grave)) ? grave.ownerName() : null, "2", " for 30 minutes");
    }

    // ---------------------------------------------------------------- the refusal

    @Test
    void aViewerPosseRefusesGetsNoNameInTheRefusal() {
        assertEquals("This grave isn't yours.", refusal(REFUSE, stranger, false));
        assertEquals("Your access to this grave has ended.", refusal(REFUSE, stranger, true));
    }

    @Test
    void aViewerPosseAllowsStillGetsTheNameInTheRefusal() {
        assertEquals("This grave belongs to Tester.", refusal(ALLOW, stranger, false));
        assertEquals("Your access to Tester's grave has ended.", refusal(ALLOW, stranger, true));
    }

    @Test
    void withoutPosseOrWithAPosseThatFailsTheRefusalHasNoName() {
        for (GraveNames.Rules rules : new GraveNames.Rules[] {null, BROKEN, OLD}) {
            assertEquals("This grave isn't yours.", refusal(rules, stranger, false));
            assertEquals("Your access to this grave has ended.", refusal(rules, stranger, true));
        }
    }

    @Test
    void theOwnerIsAlwaysNamedToThemselves() {
        for (GraveNames.Rules rules : new GraveNames.Rules[] {null, REFUSE, BROKEN, OLD, ALLOW}) {
            assertEquals("Tester", names(rules).nameFor(owner, grave));
        }
    }

    // ---------------------------------------------------------------- /grave list, graves shared with the reader

    @Test
    void theSharedListNamesTheOwnerOnlyWhenPosseAllows() {
        assertEquals("You can collect Tester's grave #2 at 10, 64, -4 in testworld.", listLine(ALLOW, stranger));
        for (GraveNames.Rules rules : new GraveNames.Rules[] {null, REFUSE, BROKEN, OLD}) {
            assertEquals("You can collect a grave shared with you at 10, 64, -4 in testworld.", listLine(rules, stranger));
        }
    }

    // ---------------------------------------------------------------- the notice that graves were shared

    @Test
    void theSharingNoticeNamesTheOwnerOnlyWhenPosseAllows() {
        assertEquals("Tester let you collect their grave #2 for 30 minutes. /grave list shows where.", notice(ALLOW, stranger));
        for (GraveNames.Rules rules : new GraveNames.Rules[] {null, REFUSE, BROKEN, OLD}) {
            assertEquals("Someone let you collect one of their graves for 30 minutes. /grave list shows where.", notice(rules, stranger));
        }
        assertEquals("Someone let you collect their graves until you untrust them. /grave list shows where.",
                GraveNames.trusted(null, "all", " until you untrust them"));
    }

    @Test
    void oneGravePosseRefusesKeepsTheNameOutOfANoticeAboutSeveral() {
        Grave elsewhere = new Grave(UUID.randomUUID(), grave.owner(), "Tester", 1L, "otherworld", "SURVIVAL", "otherworld", 0, 64, 0);
        GraveNames onlyTestworld = names((v, o, w) -> w.equals("testworld"));
        assertTrue(onlyTestworld.mayNameAll(stranger, List.of(grave)));
        assertFalse(onlyTestworld.mayNameAll(stranger, List.of(grave, elsewhere)));
        assertFalse(onlyTestworld.mayNameAll(stranger, List.of()), "no graves, no name");
    }

    @Test
    void noWordingWithoutANameCarriesOne() {
        for (boolean ended : new boolean[] {false, true}) assertFalse(GraveNames.refused(null, ended).contains("null"));
        assertFalse(GraveNames.shared(null, 1, "here").contains("null"));
        assertFalse(GraveNames.trusted(null, "1", "").contains("null"));
        assertFalse(GraveNames.trusted(null, "all", "").contains("null"));
    }

    // ---------------------------------------------------------------- a tripwire for the next output

    /**
     * Where the plugin's code reads a grave owner's name, and why each is safe. A new use fails this test, so whoever
     * adds one decides whether another player could see it, and sends it through {@link GraveNames#nameFor} if so.
     */
    private static final Map<String, Integer> NAME_READS = Map.of(
            "command/AdminCommand.java", 3,   // admins only (lanterngraves.admin): the list, the move reply, tab completion
            "grave/GraveService.java", 1,     // the server log when a friend collects
            "listen/GraveListener.java", 1,   // keeps the stored name current when its owner rejoins
            "marker/GraveNames.java", 1,      // nameFor: the one way out to another player
            "marker/Markers.java", 1,         // the label, shown per player by the same rule
            "store/GraveCodec.java", 1);      // the grave file

    @Test
    void everyReadOfAnOwnersNameIsAccountedFor() throws IOException {
        Path root = Path.of("src/main/java/com/extantape/lanterngraves");
        Map<String, Integer> found = new TreeMap<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path f : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                if (f.endsWith("grave/Grave.java")) continue; // where it's kept
                int n = Files.readString(f).split("ownerName\\(\\)|::ownerName", -1).length - 1;
                if (n > 0) found.put(root.relativize(f).toString().replace('\\', '/'), n);
            }
        }
        assertEquals(new TreeMap<>(NAME_READS), found,
                "A grave owner's name is read somewhere new. If another player could see it, use GraveNames.nameFor.");
    }
}
