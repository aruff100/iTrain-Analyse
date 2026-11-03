package com.example.itrain_analyse;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Versionsnummer wie "6.0.4", "6.0" oder "5.1.19-b404" (Zusatz nach dem Bindestrich wird ignoriert).
 * Fehlende Stellen zählen als 0, also 6.0 = 6.0.0.
 */
public record Version(List<Integer> teile) implements Comparable<Version> {

    private static final Pattern ZAHLEN = Pattern.compile("(\\d+(?:\\.\\d+)*)");

    public Version {
        teile = List.copyOf(teile);
    }

    /** Liest die erste Versionsnummer aus einem Text; {@code null}, wenn keine enthalten ist. */
    public static Version aus(String text) {
        if (text == null) {
            return null;
        }
        Matcher m = ZAHLEN.matcher(text);
        if (!m.find()) {
            return null;
        }
        List<Integer> t = new ArrayList<>();
        for (String s : m.group(1).split("\\.")) {
            t.add(Integer.parseInt(s));
        }
        return new Version(t);
    }

    @Override
    public int compareTo(Version o) {
        int n = Math.max(teile.size(), o.teile.size());
        for (int i = 0; i < n; i++) {
            int a = i < teile.size() ? teile.get(i) : 0;
            int b = i < o.teile.size() ? o.teile.get(i) : 0;
            if (a != b) {
                return Integer.compare(a, b);
            }
        }
        return 0;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < teile.size(); i++) {
            if (i > 0) {
                sb.append('.');
            }
            sb.append(teile.get(i));
        }
        return sb.toString();
    }
}
