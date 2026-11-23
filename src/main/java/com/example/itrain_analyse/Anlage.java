package com.example.itrain_analyse;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

/**
 * Inhalt einer iTrain-Projektdatei (.tcd), soweit ihn die Vorprüfung braucht (Anwender 18.11.2026, Weg 3). Liest
 * nur und rechnet nichts; die Regeln stehen in {@link Vorpruefung}. Die XML-Formen sind aus Projektdateien
 * iTrain 4.1 bis 6.0.4 abgeleitet (siehe XML-KURZREFERENZ im Prompt).
 */
public final class Anlage {

    /** Schnittstelle mit Rückmelder-Verzögerung ({@code <feedback-delay on off>}); fehlende Werte sind null. */
    public record Schnittstelle(String name, String typ, Integer ein, Integer aus, boolean verzoegerungVorhanden) {
    }

    /** Rückmelder; adresse ist "Adresse" oder BiDiB-Id, null wenn keine. Eigene Verzögerung: ein/aus, sonst null. */
    public record Melder(String name, String typ, String schnittstelle, String adresse, boolean eigeneVerzoegerung,
            Integer ein, Integer aus, Double laenge) {
    }

    /** Zubehör (Weiche, Signal, Relais …); adresse null, wenn keine eingetragen ist. */
    /** datei: Sound mit eigener Audiodatei (spielt der Rechner ab, braucht keine Adresse). */
    public record Zubehoer(String art, String name, String typ, String schnittstelle, String adresse,
            Set<String> optionen, boolean datei) {
    }

    /** Nachbarblock in einer Blockrichtung: Name und die Seite des Nachbarn, die anliegt (prev/next/both/null). */
    public record Nachbar(String block, String seite) {
    }

    /** Bahnsteig in einer Blockrichtung (cm). */
    public record Bahnsteig(double start, double laenge) {
    }

    /** Eine Richtung eines Blocks ("prev" oder "next"). Werte in cm; null, wenn nicht gesetzt. */
    public record Richtung(String seite, Double grenzzeichen, Double bremsen, Double stopp, Bahnsteig bahnsteig,
            boolean kritisch, List<Nachbar> nachbarn, List<String> signale) {
    }

    /** Rückmelder im Block mit Position ab Blockanfang (cm), null wenn keine Position. */
    public record BlockMelder(String name, Double position) {
    }

    /**
     * richtungsart: Attribut direction ("single" = nur Richtung "Nächste" befahrbar, Handbuch S. 112),
     * polaritaet: Attribut polarity ("normal", "inverted", "unknown"), beide null wenn nicht in der Datei.
     */
    public record Block(String name, String typ, String spurweite, boolean aktiv, Double laenge,
            List<BlockMelder> melder, Richtung prev, Richtung next, Set<String> optionen, String richtungsart,
            String polaritaet) {

        public boolean nurNaechste() {
            return "single".equals(richtungsart);
        }

        public Richtung richtung(String seite) {
            return "prev".equals(seite) ? prev : next;
        }

        public boolean richtungswechselVerboten() {
            return optionen.contains("no_dir_change");
        }
    }

    /** Block mit Richtung (side null = beide), z. B. in Bahnhöfen, Routen und Erlaubnissen. */
    public record BlockSeite(String block, String seite) {
    }

    public record Bahnhof(String name, String typ, List<BlockSeite> bloecke) {
    }

    /** Erlaubnis: VERBOTEN = "Keine Erlaubnis zu", EXKLUSIV = "Nur Erlaubnis zu", UNKLAR = Bedeutung nicht belegt. */
    public enum ErlaubnisArt { VERBOTEN, EXKLUSIV, UNKLAR }

    public record Erlaubnis(ErlaubnisArt art, List<BlockSeite> bloecke) {

        /** Darf der Block in dieser Richtung befahren werden? Bei UNKLAR immer true. */
        public boolean erlaubt(String block, String seite) {
            boolean gelistet = bloecke.stream().anyMatch(b -> b.block().equals(block)
                    && (b.seite() == null || seite == null || b.seite().equals(seite)));
            return switch (art) {
                case VERBOTEN -> !gelistet;
                case EXKLUSIV -> gelistet;
                case UNKLAR -> true;
            };
        }
    }

    public record Gattung(String name, Set<String> optionen, Erlaubnis erlaubnis) {
    }

    public record Lok(String name, boolean aktiv, String spurweite, String typ, String schnittstelle,
            String adresse, Double laenge, int fahrstufen, boolean offsetVorhanden, Erlaubnis erlaubnis,
            Integer reaktionVor, Integer reaktionRueck, Integer decoderStufen, Integer traegheitAnfahren,
            Integer traegheitBremsen, Integer schrittweite, Integer cv3, Integer cv4, boolean analog) {

        /** Größere der beiden Reaktionsverzögerungen in ms, null wenn nicht in der Datei. */
        public Integer reaktionMax() {
            if (reaktionVor == null) {
                return reaktionRueck;
            }
            return reaktionRueck == null ? reaktionVor : Math.max(reaktionVor, reaktionRueck);
        }
    }

    public record Wagen(String name, boolean aktiv, String spurweite, Double laenge, Erlaubnis erlaubnis) {
    }

    /** Teil einer Zusammenstellung: LOK, WAGEN oder ANONYM (eigene Länge). */
    public record Teil(String art, String name, Double laenge) {
    }

    public record Zug(String name, boolean aktiv, String spurweite, String gattung, List<Teil> teile,
            List<String> routen, Erlaubnis erlaubnis) {
    }

    /** Routeneintrag: BLOCK, AUSWAHL (Liste) oder BAHNHOF; marker "start"/"finish" oder null. */
    public record Eintrag(String art, String name, List<BlockSeite> bloecke, Set<String> optionen, boolean warten,
            String marker) {
    }

    public record Route(String name, String typ, boolean aktiv, List<Eintrag> eintraege, int wiederholung,
            Integer reservierung, Integer reservierungStart, String richtungswechsel, Set<String> optionen) {

        public boolean rangieren() {
            return "shunt".equals(typ);
        }
    }

    public final String datei;
    public final String version;
    public final String massstab;
    public final String spurweite;
    public final List<Schnittstelle> schnittstellen = new ArrayList<>();
    public final List<Melder> melder = new ArrayList<>();
    public final List<Zubehoer> zubehoer = new ArrayList<>();
    public final Map<String, Block> bloecke = new LinkedHashMap<>();
    public final List<Bahnhof> bahnhoefe = new ArrayList<>();
    public final Map<String, Gattung> gattungen = new LinkedHashMap<>();
    public final Map<String, Lok> loks = new LinkedHashMap<>();
    public final Map<String, Wagen> wagen = new LinkedHashMap<>();
    public final List<Zug> zuege = new ArrayList<>();
    public final Map<String, Route> routen = new LinkedHashMap<>();
    public final int aktionen;
    /** Funktionsnamen je Lok bzw. Wagen (Registerkarte "Funktionen"). */
    public final Map<String, Set<String>> fahrzeugFunktionen = new HashMap<>();
    /** Funktionsnamen in den Start- und Stopp-Aktionen je Zug ("" = ohne Namen). */
    public final Map<String, List<String>> zugFunktionen = new LinkedHashMap<>();
    /** Funktionsnamen in den Aktionen der Routeneinträge je Route ("" = ohne Namen). */
    public final Map<String, List<String>> routenFunktionen = new LinkedHashMap<>();
    /** Objektnamen mit Leerzeichen am Anfang oder Ende, als "Art: 'Name'". */
    public final List<String> leerzeichenNamen = new ArrayList<>();
    /** Zugoption "Korrigiere Züge basierend auf RailCom" (settings/train/options). */
    public final boolean railcomKorrektur;
    /** Aktiv geschaltete Aktionen, deren Bedingung nur aus "Zufällig" besteht. */
    public final List<String> aktionenNurZufall = new ArrayList<>();
    /** Aktionen, die sich mit "Aktion ausführen" selbst aufrufen. */
    public final List<String> aktionenSelbstaufruf = new ArrayList<>();
    /** Lok-Funktionsschritte in Aktionen: Aktion, Lok, Funktion (nur mit genannter Lok, nur aktive Zeilen). */
    public final List<List<String>> lokAktionFunktionen = new ArrayList<>();

    private Anlage(String datei, Element wurzel) {
        this.datei = datei;
        this.version = wurzel.getAttribute("version");
        Element settings = kind(wurzel, "settings");
        this.massstab = settings == null ? null : text(kind(settings, "scale"));
        Element zug = settings == null ? null : kind(settings, "train");
        this.spurweite = zug == null ? null : leer(zug.getAttribute("gauge"));
        this.railcomKorrektur = zug != null && optionen(zug).contains("correct_with_railcom");
        Element items = kind(wurzel, "control-items");
        if (items == null) {
            this.aktionen = 0;
            return;
        }
        for (Element e : kinder(kind(items, "interfaces"), null)) {
            Element d = kind(e, "feedback-delay");
            schnittstellen.add(new Schnittstelle(e.getAttribute("name"), e.getAttribute("type"),
                    d == null ? null : zahl(d.getAttribute("on")), d == null ? null : zahl(d.getAttribute("off")),
                    d != null));
        }
        for (Element e : kinder(kind(items, "feedbacks"), "feedback")) {
            Element i = kind(e, "interface");
            Element d = kind(e, "delay");
            melder.add(new Melder(e.getAttribute("name"), leer(e.getAttribute("type")),
                    i == null ? null : leer(i.getAttribute("name")), adresse(e, i), d != null,
                    d == null ? null : zahl(d.getAttribute("on")), d == null ? null : zahl(d.getAttribute("off")),
                    laenge(e)));
        }
        for (Element e : kinder(kind(items, "accessories"), null)) {
            Element i = kind(e, "interface");
            zubehoer.add(new Zubehoer(e.getTagName(), e.getAttribute("name"), leer(e.getAttribute("type")),
                    i == null ? null : leer(i.getAttribute("name")), adresse(e, i), optionen(e),
                    !e.getAttribute("file").isEmpty()));
        }
        for (Element e : kinder(kind(items, "blocks"), "block")) {
            List<BlockMelder> bm = new ArrayList<>();
            for (Element f : kinder(kind(e, "feedbacks"), "feedback")) {
                bm.add(new BlockMelder(f.getAttribute("name"), dezimal(f.getAttribute("position"))));
            }
            Block b = new Block(e.getAttribute("name"), leer(e.getAttribute("type")), leer(e.getAttribute("gauge")),
                    !"false".equals(e.getAttribute("active")), laenge(e), bm, richtung(kind(e, "prev"), "prev"),
                    richtung(kind(e, "next"), "next"), optionen(e), leer(e.getAttribute("direction")),
                    leer(e.getAttribute("polarity")));
            bloecke.put(b.name(), b);
        }
        for (Element e : kinder(kind(items, "stations"), "station")) {
            bahnhoefe.add(new Bahnhof(e.getAttribute("name"), leer(e.getAttribute("type")),
                    blockSeiten(kind(e, "blocks"))));
        }
        for (Element e : kinder(kind(items, "train-types"), "train-type")) {
            gattungen.put(e.getAttribute("name"), new Gattung(e.getAttribute("name"), optionen(e), erlaubnis(e)));
        }
        for (Element e : kinder(kind(items, "locomotives"), "locomotive")) {
            Element i = kind(e, "interface");
            Element sc = kind(e, "speed-control");
            Element fb = kind(e, "feedback");
            Element rv = kind(e, "delay");
            loks.put(e.getAttribute("name"), new Lok(e.getAttribute("name"), !"false".equals(e.getAttribute("active")),
                    leer(e.getAttribute("gauge")), leer(e.getAttribute("type")),
                    i == null ? null : leer(i.getAttribute("name")), adresse(e, i), laenge(e),
                    sc == null ? 0 : kinder(sc, "speed").size(), offsetGesetzt(fb),
                    erlaubnis(e), reaktion(rv, "forward"), reaktion(rv, "backward"),
                    ganzzahl(kind(e, "decoder"), "steps"), ganzzahl(kind(e, "acceleration"), "delay"),
                    ganzzahl(kind(e, "deceleration"), "delay"), ganzzahl(kind(e, "acceleration"), "step"),
                    cv(e, "3"), cv(e, "4"),
                    kind(e, "decoder") != null && "analog".equals(kind(e, "decoder").getAttribute("protocol"))));
        }
        for (Element e : kinder(kind(items, "wagons"), "wagon")) {
            wagen.put(e.getAttribute("name"), new Wagen(e.getAttribute("name"),
                    !"false".equals(e.getAttribute("active")), leer(e.getAttribute("gauge")), laenge(e),
                    erlaubnis(e)));
        }
        for (Element e : kinder(kind(items, "trains"), "train")) {
            List<Teil> teile = new ArrayList<>();
            for (Element t : kinder(kind(e, "composition"), null)) {
                switch (t.getTagName()) {
                    case "locomotive" -> teile.add(new Teil("LOK", t.getAttribute("name"),
                            lokLaenge(t.getAttribute("name"))));
                    case "wagon" -> teile.add(new Teil("WAGEN", t.getAttribute("name"),
                            wagenLaenge(t.getAttribute("name"))));
                    default -> teile.add(new Teil("ANONYM", leer(t.getAttribute("name")),
                            t.hasAttribute("length") ? inCm(dezimal(t.getAttribute("length")),
                                    t.getAttribute("unit")) : laenge(t)));
                }
            }
            List<String> rs = new ArrayList<>();
            for (Element r : kinder(kind(e, "routes"), "route")) {
                rs.add(r.getAttribute("name"));
            }
            Element g = kind(e, "train-type");
            zuege.add(new Zug(e.getAttribute("name"), !"false".equals(e.getAttribute("active")),
                    leer(e.getAttribute("gauge")), g == null ? null : leer(g.getAttribute("name")), teile, rs,
                    erlaubnis(e)));
        }
        for (Element e : kinder(kind(items, "train-routes"), "route")) {
            List<Eintrag> ein = new ArrayList<>();
            for (Element t : kinder(kind(e, "items"), null)) {
                String art = switch (t.getTagName()) {
                    case "block" -> "BLOCK";
                    case "blocks" -> "AUSWAHL";
                    case "station" -> "BAHNHOF";
                    default -> null;
                };
                if (art == null) {
                    continue;
                }
                List<BlockSeite> bs = "BLOCK".equals(art)
                        ? List.of(new BlockSeite(t.getAttribute("name"), leer(t.getAttribute("side"))))
                        : blockSeiten(t);
                if ("BAHNHOF".equals(art) && bs.isEmpty()) {
                    bs = bahnhoefe.stream().filter(b -> b.name().equals(t.getAttribute("name"))).findFirst()
                            .map(Bahnhof::bloecke).orElse(List.of());
                }
                String name = t.getAttribute("name");
                if (name.isEmpty()) {
                    List<String> n = bs.stream().map(BlockSeite::block).toList();
                    name = n.size() <= 3 ? String.join(" / ", n)
                            : String.join(" / ", n.subList(0, 2)) + " / … (" + n.size() + ")";
                }
                ein.add(new Eintrag(art, name, bs, optionen(t), wartezeit(kind(t, "wait")),
                        leer(t.getAttribute("marker"))));
            }
            Element rep = kind(e, "repeat");
            Element res = kind(e, "reservation");
            Element dc = kind(e, "direction-change");
            routen.put(e.getAttribute("name"), new Route(e.getAttribute("name"), leer(e.getAttribute("type")),
                    !"false".equals(e.getAttribute("active")), ein,
                    rep == null || zahl(rep.getAttribute("count")) == null ? 1 : zahl(rep.getAttribute("count")),
                    res == null ? null : zahl(res.getAttribute("count")),
                    res == null ? null : zahl(res.getAttribute("start")), dc == null ? null : leer(text(dc)),
                    optionen(e)));
        }
        this.aktionen = kinder(kind(items, "actions"), "action").size();
        for (Element ak : kinder(kind(items, "actions"), "action")) {
            String an = ak.getAttribute("name");
            Element bed = kind(ak, "condition");
            if (bed != null) {
                List<String> arten = new ArrayList<>();
                NodeList alle = bed.getElementsByTagName("*");
                for (int i = 0; i < alle.getLength(); i++) {
                    Element c = (Element) alle.item(i);
                    String tg = c.getTagName();
                    if (!List.of("and", "or", "not").contains(tg) && c.getParentNode() instanceof Element pe
                            && (pe == bed || List.of("and", "or", "not").contains(pe.getTagName()))
                            && aktiv(c, bed)) {
                        arten.add(tg);
                    }
                }
                if (!arten.isEmpty() && arten.stream().allMatch("random"::equals)) {
                    aktionenNurZufall.add(an);
                }
            }
            NodeList schritte = ak.getElementsByTagName("action");
            for (int i = 0; i < schritte.getLength(); i++) {
                Element x = (Element) schritte.item(i);
                if (an.equals(x.getAttribute("name")) && aktiv(x, ak)) {
                    aktionenSelbstaufruf.add(an);
                    break;
                }
            }
            NodeList lf = ak.getElementsByTagName("locomotive-function");
            for (int i = 0; i < lf.getLength(); i++) {
                Element x = (Element) lf.item(i);
                Element lok = kind(x, "locomotive");
                if (lok != null && !lok.getAttribute("name").isEmpty() && aktiv(x, ak)) {
                    lokAktionFunktionen.add(List.of(an, lok.getAttribute("name"), x.getAttribute("name")));
                }
            }
        }
        for (String liste : List.of("locomotives", "wagons")) {
            for (Element e : kinder(kind(items, liste), null)) {
                Set<String> fn = new HashSet<>();
                for (Element f : kinder(kind(e, "functions"), "function")) {
                    // inaktive Funktionen zählen nicht (Release Notes 6.0.2)
                    if (!f.getAttribute("name").isEmpty() && !"false".equals(f.getAttribute("active"))) {
                        fn.add(f.getAttribute("name"));
                    }
                }
                fahrzeugFunktionen.put(e.getAttribute("name"), fn);
            }
        }
        for (Element e : kinder(kind(items, "trains"), "train")) {
            zugFunktionen.put(e.getAttribute("name"), funktionen(kind(e, "actions")));
        }
        for (Element e : kinder(kind(items, "train-routes"), "route")) {
            routenFunktionen.put(e.getAttribute("name"), funktionen(e));
        }
        Map<String, String> arten = Map.of("feedbacks", "Rückmelder", "accessories", "Zubehör", "blocks", "Block",
                "locomotives", "Lok", "wagons", "Wagen", "trains", "Zug", "train-routes", "Route", "actions", "Aktion",
                "stations", "Bahnhof");
        arten.forEach((liste, art) -> {
            for (Element e : kinder(kind(items, liste), null)) {
                String n = e.getAttribute("name");
                if (!n.isEmpty() && !n.equals(n.strip())) {
                    leerzeichenNamen.add(art + " '" + n + "'");
                }
            }
        });
        Collections.sort(leerzeichenNamen);
    }

    /** Namen aller train-function-Elemente unterhalb von e, in Dokumentreihenfolge. */
    /** true, wenn mindestens ein Rückmeldungs-Offset (vorn oder hinten) ungleich 0 eingetragen ist. */
    private static boolean offsetGesetzt(Element fb) {
        for (Element o : kinder(fb, "offset")) {
            for (String a : List.of("front", "rear", "front-left", "front-right", "rear-left", "rear-right")) {
                Double d = o.hasAttribute(a) ? dezimal(o.getAttribute(a)) : null;
                if (d != null && d != 0) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Gespeicherter Decoderwert (Registerkarte "Konfiguration"), null wenn nicht ausgelesen. */
    private static Integer cv(Element lok, String nr) {
        for (Element p : kinder(kind(lok, "configuration"), "parameter")) {
            if (nr.equals(p.getAttribute("nr"))) {
                return ganzzahl(p, "value");
            }
        }
        return null;
    }

    private static Integer ganzzahl(Element e, String attr) {
        if (e == null || !e.hasAttribute(attr)) {
            return null;
        }
        Double d = dezimal(e.getAttribute(attr));
        return d == null ? null : (int) Math.round(d);
    }

    private static List<String> funktionen(Element e) {
        List<String> l = new ArrayList<>();
        if (e == null) {
            return l;
        }
        NodeList nl = e.getElementsByTagName("train-function");
        for (int i = 0; i < nl.getLength(); i++) {
            Element f = (Element) nl.item(i);
            if (aktiv(f, e)) {
                l.add(f.getAttribute("name"));
            }
        }
        return l;
    }

    /**
     * false, wenn das Element oder ein umgebendes Element bis {@code bis} mit active="false" abgeschaltet ist
     * (Aktionszeilen lassen sich seit iTrain 6.0.0 zum Testen abschalten, Release Notes 6.0.0 und 6.0.3).
     */
    private static boolean aktiv(Element e, Element bis) {
        for (org.w3c.dom.Node n = e; n instanceof Element x; n = n.getParentNode()) {
            if ("false".equals(x.getAttribute("active"))) {
                return false;
            }
            if (x == bis) {
                break;
            }
        }
        return true;
    }

    public static Anlage lies(Path tcd) throws IOException {
        try (InputStream in = Files.newInputStream(tcd)) {
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
            f.setNamespaceAware(false);
            f.setExpandEntityReferences(false);
            f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder b = f.newDocumentBuilder();
            return new Anlage(tcd.getFileName().toString(), b.parse(in).getDocumentElement());
        } catch (ParserConfigurationException | SAXException ex) {
            throw new IOException(ex.getMessage(), ex);
        }
    }

    public long signale() {
        return zubehoer.stream().filter(z -> z.art().equals("signal")).count();
    }

    public Lok lok(String name) {
        return loks.get(name);
    }

    private Double lokLaenge(String name) {
        Lok l = loks.get(name);
        return l == null ? null : l.laenge();
    }

    private Double wagenLaenge(String name) {
        Wagen w = wagen.get(name);
        return w == null ? null : w.laenge();
    }

    // --- Lesen --------------------------------------------------------------------------------------------------

    private static Richtung richtung(Element e, String seite) {
        if (e == null) {
            return new Richtung(seite, null, null, null, null, false, List.of(), List.of());
        }
        Element p = kind(e, "positions");
        Bahnsteig bs = null;
        if (p != null && kind(p, "platform") != null) {
            Element pl = kind(p, "platform");
            Double s = dezimal(pl.getAttribute("start"));
            Double l = dezimal(pl.getAttribute("length"));
            bs = new Bahnsteig(s == null ? 0 : s, l == null ? 0 : l);
        }
        List<Nachbar> nb = new ArrayList<>();
        for (Element sb : kinder(e, "side-block")) {
            if (sb.hasAttribute("name")) {
                nb.add(new Nachbar(sb.getAttribute("name"), leer(sb.getAttribute("side"))));
            }
            sammleBloecke(sb, nb);
        }
        List<String> sig = new ArrayList<>();
        for (Element s : kinder(e, null)) {
            if (s.getTagName().endsWith("signal") && s.hasAttribute("name")) {
                sig.add(s.getTagName() + ":" + s.getAttribute("name"));
            }
        }
        return new Richtung(seite, p == null ? null : dezimal(p.getAttribute("clearance")),
                p == null ? null : dezimal(p.getAttribute("brake")), p == null ? null : dezimal(p.getAttribute("stop")),
                bs, "true".equals(e.getAttribute("critical")), nb, sig);
    }

    /** Alle Blöcke in verschachtelten Weichen und Drehscheiben unter einem side-block. */
    private static void sammleBloecke(Element e, List<Nachbar> ziel) {
        for (Element k : kinder(e, null)) {
            if (k.getTagName().equals("block") && k.hasAttribute("name")) {
                Nachbar n = new Nachbar(k.getAttribute("name"), leer(k.getAttribute("side")));
                if (!ziel.contains(n)) {
                    ziel.add(n);
                }
            } else {
                sammleBloecke(k, ziel);
            }
        }
    }

    /** Hat der Eintrag eine Wartezeit (duration, min oder max größer 0)? Ein {@code <wait>} nur mit Aktionen nicht. */
    private static boolean wartezeit(Element w) {
        if (w == null) {
            return false;
        }
        for (String a : List.of("duration", "min", "max")) {
            Double d = dezimal(w.getAttribute(a));
            if (d != null && d > 0) {
                return true;
            }
        }
        return false;
    }

    private static List<BlockSeite> blockSeiten(Element e) {
        List<BlockSeite> l = new ArrayList<>();
        for (Element b : kinder(e, "block")) {
            l.add(new BlockSeite(b.getAttribute("name"), leer(b.getAttribute("side"))));
        }
        return l;
    }

    private static Erlaubnis erlaubnis(Element e) {
        Element p = kind(e, "permissions");
        if (p == null || p.hasAttribute("allowance")) {
            return null;
        }
        ErlaubnisArt art;
        if (p.hasAttribute("access-type")) {
            art = switch (p.getAttribute("access-type")) {
                case "forbidden" -> ErlaubnisArt.VERBOTEN;
                case "exclusive" -> ErlaubnisArt.EXKLUSIV;
                default -> ErlaubnisArt.UNKLAR;
            };
        } else if ("true".equals(p.getAttribute("access-forbidden"))) {
            art = ErlaubnisArt.VERBOTEN;
        } else {
            art = ErlaubnisArt.UNKLAR;
        }
        List<BlockSeite> b = blockSeiten(p);
        return b.isEmpty() ? null : new Erlaubnis(art, b);
    }

    private static Set<String> optionen(Element e) {
        Set<String> s = new LinkedHashSet<>();
        for (Element o : kinder(kind(e, "options"), "option")) {
            s.add(text(o));
        }
        return s;
    }

    /**
     * Adresse aus {@code <interface address>} oder {@code <interface><address nr>}. Bei BiDiB ist {@code <id>} am
     * Objekt die Kennung des Knotens, nicht die Adresse (LudwigR, Forum 20.11.2026): Sie wird nur vorangestellt, damit
     * gleiche Nummern an verschiedenen Knoten nicht als doppelt gelten. Ohne Adresse in der Schnittstelle gilt die
     * Kennung selbst (Objekte, die BiDiB nur über die Kennung anspricht).
     */
    private static String adresse(Element objekt, Element i) {
        Element eigeneId = kind(objekt, "id");
        String knoten = eigeneId == null ? null : leer(text(eigeneId));
        String nr = null;
        if (i != null) {
            if (!i.getAttribute("address").isEmpty()) {
                nr = i.getAttribute("address");
            } else {
                // <address nr="…"/> oder <address>70</address>; mehrere Einträge (z. B. zwei Decoder einer Lok)
                // werden mit "+" verbunden
                List<String> l = new ArrayList<>();
                for (Element a : kinder(i, "address")) {
                    String w = !a.getAttribute("nr").isEmpty() ? a.getAttribute("nr") : leer(text(a));
                    if (w != null) {
                        l.add(w);
                    }
                }
                if (!l.isEmpty()) {
                    nr = String.join("+", l);
                }
            }
        }
        if (nr != null) {
            return knoten == null ? nr : knoten + " / " + nr;
        }
        if (knoten != null) {
            return knoten;
        }
        Element id = i == null ? null : kind(i, "id");
        return id == null ? null : leer(text(id));
    }

    /** Länge aus {@code <length unit="cm|mm|m">}, in cm. */
    private static Double laenge(Element e) {
        Element l = kind(e, "length");
        if (l == null) {
            return null;
        }
        return inCm(dezimal(text(l)), l.getAttribute("unit"));
    }

    private static Double inCm(Double v, String einheit) {
        if (v == null) {
            return null;
        }
        return switch (einheit == null ? "" : einheit) {
            case "mm" -> v / 10;
            case "m" -> v * 100;
            default -> v;
        };
    }

    static Element kind(Element e, String name) {
        if (e == null) {
            return null;
        }
        for (Node n = e.getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n instanceof Element k && k.getTagName().equals(name)) {
                return k;
            }
        }
        return null;
    }

    static List<Element> kinder(Element e, String name) {
        List<Element> l = new ArrayList<>();
        if (e == null) {
            return l;
        }
        NodeList nl = e.getChildNodes();
        for (int i = 0; i < nl.getLength(); i++) {
            if (nl.item(i) instanceof Element k && (name == null || k.getTagName().equals(name))) {
                l.add(k);
            }
        }
        return l;
    }

    private static String text(Element e) {
        return e == null ? null : e.getTextContent().strip();
    }

    private static String leer(String s) {
        return s == null || s.isEmpty() ? null : s;
    }

    /** Reaktionsverzögerung der Lok in ms: {@code <delay value=…>} oder {@code <delay forward=… backward=…>}. */
    static Integer reaktion(Element d, String richtung) {
        if (d == null) {
            return null;
        }
        Double w = dezimal(d.getAttribute(richtung));
        if (w == null) {
            w = dezimal(d.getAttribute("value"));
        }
        if (w == null) {
            return null;
        }
        return (int) Math.round("s".equals(d.getAttribute("unit")) ? w * 1000 : w);
    }

    static Integer zahl(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return (int) Math.round(Double.parseDouble(s.strip()));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    static Double dezimal(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(s.strip());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
