package com.example.itrain_analyse;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * Entpackt eine iTrain-Projektdatei (.tcdz = ZIP mit einer .tcd-XML-Datei) und liest die iTrain-Version
 * aus dem Wurzelelement {@code <train-control version="6.0.4-b300">}.
 */
public final class Projektdatei {

    /** Ergebnis: die entpackte (oder kopierte) .tcd-Datei und die gespeicherte iTrain-Version (oder {@code null}). */
    public record Ergebnis(Path tcd, String itrainVersion) {
    }

    private Projektdatei() {
    }

    /**
     * Legt die .tcd im Zielordner ab. {@code alsXml}: Dateiendung .tcd.xml für KI-Oberflächen, die .tcd ablehnen
     * (Inhalt unverändert).
     */
    public static Ergebnis entpacke(Path quelle, Path zielordner, boolean alsXml) throws IOException {
        String name = quelle.getFileName().toString();
        String basis = name.replaceFirst("(?i)\\.tcdz?$", "");
        Path ziel = zielordner.resolve(basis + (alsXml ? ".tcd.xml" : ".tcd"));
        if (istZip(quelle)) {
            try (ZipFile zip = new ZipFile(quelle.toFile())) {
                ZipEntry treffer = null;
                Enumeration<? extends ZipEntry> e = zip.entries();
                while (e.hasMoreElements()) {
                    ZipEntry z = e.nextElement();
                    if (!z.isDirectory() && z.getName().toLowerCase(Locale.ROOT).endsWith(".tcd")) {
                        treffer = z;
                        break;
                    }
                }
                if (treffer == null) {
                    throw new IOException(I18n.t("fehler.keineTcd", name));
                }
                try (InputStream in = zip.getInputStream(treffer)) {
                    Files.copy(in, ziel, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } else {
            Files.copy(quelle, ziel, StandardCopyOption.REPLACE_EXISTING);
        }
        return new Ergebnis(ziel, leseVersion(ziel));
    }

    private static boolean istZip(Path datei) throws IOException {
        try (InputStream in = Files.newInputStream(datei)) {
            byte[] kopf = in.readNBytes(2);
            return kopf.length == 2 && kopf[0] == 'P' && kopf[1] == 'K';
        }
    }

    /** Liest nur das Wurzelelement; die übrige Datei wird nicht geparst. */
    static String leseVersion(Path tcd) {
        XMLInputFactory f = XMLInputFactory.newFactory();
        f.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        f.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        try (InputStream in = Files.newInputStream(tcd)) {
            XMLStreamReader r = f.createXMLStreamReader(in);
            try {
                while (r.hasNext()) {
                    if (r.next() == XMLStreamConstants.START_ELEMENT) {
                        return r.getAttributeValue(null, "version");
                    }
                }
            } finally {
                r.close();
            }
        } catch (IOException | XMLStreamException ex) {
            return null;
        }
        return null;
    }
}
