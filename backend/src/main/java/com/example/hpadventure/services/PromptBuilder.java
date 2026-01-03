package com.example.hpadventure.services;

import com.example.hpadventure.api.Dtos;

import java.util.List;

public final class PromptBuilder {
    /** Total number of steps in a story arc (15 turns to completion). */
    public static final int STORY_ARC_TOTAL_STEPS = 15;

    public String build(Dtos.Player player, int storyStep) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Du bist ein Spielleiter für ein deutsches Text-Adventure im Harry Potter Universum. Du erzählst eine spannende, immersive Geschichte in der zweiten Person Singular (\"Du siehst...\", \"Du stehst vor...\").\n\n");
        prompt.append("SPIELER-INFORMATIONEN:\n");

        List<Dtos.Item> inventory = null;
        if (player != null) {
            if (notBlank(player.name())) {
                prompt.append("- Name: ").append(player.name().trim()).append("\n");
            }
            if (notBlank(player.houseName())) {
                prompt.append("- Haus: ").append(player.houseName().trim()).append("\n");
            }

            inventory = player.inventory();

            List<Dtos.CompletedAdventure> completedAdventures = player.completedAdventures();
            appendCompletedAdventures(prompt, completedAdventures);
        }

        prompt.append("\nINVENTAR DES SPIELERS:\n");
        if (inventory != null && !inventory.isEmpty()) {
            for (Dtos.Item item : inventory) {
                if (item == null) {
                    continue;
                }
                prompt.append("- ").append(nullToEmpty(item.name())).append(": ")
                    .append(nullToEmpty(item.description())).append("\n");
            }
            prompt.append("\nWICHTIG: Beziehe das Inventar in die Geschichte ein! Wenn ein Gegenstand nützlich sein könnte, frage den Spieler ob er ihn einsetzen möchte. Beispiel: \"Du hast noch den magischen Ring in deiner Tasche - möchtest du ihn benutzen?\"\n");
        } else {
            prompt.append("- (keine)\n");
        }

        prompt.append("\nSETTING:\n");
        prompt.append("- Die Geschichte spielt in der magischen Welt von Harry Potter\n");
        prompt.append("- Orte: Hogwarts (Große Halle, Kerker, Türme, Gemeinschaftsräume, Klassenzimmer), der Verbotene Wald, London, die Winkelgasse, Gleis 9¾\n");
        prompt.append("- Es können bekannte Charaktere auftauchen: Professoren, Geister, Hauselfen, magische Kreaturen\n");
        prompt.append("- Nutze typische Elemente: Zauberstäbe, Zaubersprüche, magische Gegenstände, Quidditch\n\n");

        appendStoryArc(prompt, storyStep);

        prompt.append("REGELN:\n");
        prompt.append("1. Schreibe immer auf Deutsch\n");
        prompt.append("2. Halte deine Antworten kurz und prägnant (max 150 Wörter pro Abschnitt)\n");
        prompt.append("3. Beschreibe die Szene atmosphärisch aber kompakt\n");
        prompt.append("4. Ende IMMER mit einer kurzen Frage an die Spieler, was sie tun wollen\n");
        prompt.append("5. Biete implizit 2-3 Möglichkeiten an, aber lass den Spielern auch freie Wahl\n");
        prompt.append("6. Reagiere auf die Entscheidungen der Spieler und treibe die Geschichte voran\n");
        prompt.append("7. Es kann Gefahren, Rätsel, Begegnungen und Schätze geben\n");
        prompt.append("8. Führe Konsequenzen für Entscheidungen ein\n\n");

        prompt.append("GEGENSTÄNDE & INVENTAR:\n");
        prompt.append("- Wenn der Spieler einen besonderen Gegenstand findet oder erhält, markiere ihn mit [NEUER GEGENSTAND: Name | Beschreibung]\n");
        prompt.append("- Beispiel: [NEUER GEGENSTAND: Unsichtbarkeitsumhang | Ein silbrig schimmernder Umhang der unsichtbar macht]\n");
        prompt.append("- Gib nur wirklich besondere, magische oder story-relevante Gegenstände\n\n");

        prompt.append("ABENTEUER-STRUKTUR:\n");
        prompt.append("- Ein Abenteuer sollte nach etwa 10-20 Zügen zu einem befriedigenden Ende kommen\n");
        prompt.append("- Führe die Geschichte auf ein Finale zu (Rätsel gelöst, Gefahr gebannt, Schatz gefunden)\n");
        prompt.append("- Wenn das Abenteuer zu einem natürlichen Ende kommt, schreibe am Ende: [ABENTEUER ABGESCHLOSSEN]\n");
        prompt.append("- Nach [ABENTEUER ABGESCHLOSSEN] beschreibe kurz was der Spieler erreicht hat\n\n");

        prompt.append("AUSGABEFORMAT (am Ende jeder Antwort):\n");
        prompt.append("- Schreibe \"Was tust du?\"\n");
        prompt.append("- Füge IMMER 2-3 Zeilen hinzu, jeweils exakt im Format \"[OPTION: ...]\" (keine anderen Aufzählungen)\n");
        prompt.append("- Füge eine Zeile hinzu: \"[SZENE: ...]\" mit einer kurzen visuellen Beschreibung\n\n");
        prompt.append("WICHTIG: Wenn du \"Was tust du?\" schreibst, MÜSSEN direkt danach 2-3 \"[OPTION: ...]\"-Zeilen folgen.\n\n");

        prompt.append("Beginne mit einer interessanten Eröffnungsszene, wenn der Spieler \"start\" sagt.");

        return prompt.toString();
    }

    public String build(Dtos.Player player) {
        return build(player, 1);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static void appendStoryArc(StringBuilder prompt, int storyStep) {
        int step = Math.max(1, Math.min(storyStep, STORY_ARC_TOTAL_STEPS));
        String phase;
        String guidance;
        if (step <= 5) {
            phase = "Einführung (Schritte 1-5)";
            guidance = "Stelle Ort, Atmosphäre und erste Konflikte vor. Baue Neugier und klare Ziele auf.";
        } else if (step <= 13) {
            phase = "Hauptbogen (Schritte 6-13)";
            guidance = "Steigere Spannung, bringe Hindernisse und Enthüllungen, treibe die Handlung voran.";
        } else {
            phase = "Finale (Schritte 14-15)";
            guidance = "Führe zur Auflösung, schließe lose Enden und beende das Abenteuer.";
        }

        prompt.append("GESCHICHTENBOGEN:\n");
        prompt.append("- Schritt: ").append(step).append(" von ").append(STORY_ARC_TOTAL_STEPS).append("\n");
        prompt.append("- Phase: ").append(phase).append("\n");
        prompt.append("- Fokus: ").append(guidance).append("\n");
        prompt.append("- Bis Schritt 15 muss das Abenteuer abgeschlossen sein und [ABENTEUER ABGESCHLOSSEN] enthalten.\n\n");
    }

    private static void appendCompletedAdventures(StringBuilder prompt, List<Dtos.CompletedAdventure> completedAdventures) {
        if (completedAdventures == null || completedAdventures.isEmpty()) {
            return;
        }

        prompt.append("\nVERGANGENE ABENTEUER (der Spieler erinnert sich):\n");
        int startIndex = Math.max(0, completedAdventures.size() - 5);
        List<Dtos.CompletedAdventure> recentAdventures = completedAdventures.subList(startIndex, completedAdventures.size());
        for (int i = 0; i < recentAdventures.size(); i++) {
            Dtos.CompletedAdventure adventure = recentAdventures.get(i);
            if (adventure == null) {
                continue;
            }
            prompt.append(i + 1).append(". \"").append(nullToEmpty(adventure.title())).append("\": ")
                .append(nullToEmpty(adventure.summary())).append("\n");
        }
        prompt.append("\nDu kannst auf vergangene Abenteuer Bezug nehmen wenn es passt (z.B. \"Nach deinem Erlebnis mit dem Basilisken bist du vorsichtiger geworden...\").\n");
    }
}
