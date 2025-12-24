#!/usr/bin/env node

require("dotenv").config();
const Anthropic = require("@anthropic-ai/sdk");
const readline = require("readline");
const fs = require("fs");
const path = require("path");

const client = new Anthropic();
const SAVE_DIR = path.join(process.env.HOME || ".", ".text-adventure-saves");
const PLAYER_FILE = path.join(SAVE_DIR, "player.json");

// Default player state
const DEFAULT_PLAYER = {
  name: null,
  houseName: null,
  inventory: [],
  completedAdventures: [], // Array of { title, summary, completedAt }
  currentAdventure: null, // { title, startedAt, conversationHistory }
  stats: {
    adventuresCompleted: 0,
    totalTurns: 0,
  },
};

function ensureSaveDir() {
  if (!fs.existsSync(SAVE_DIR)) {
    fs.mkdirSync(SAVE_DIR, { recursive: true });
  }
}

function loadPlayer() {
  ensureSaveDir();
  if (fs.existsSync(PLAYER_FILE)) {
    try {
      return JSON.parse(fs.readFileSync(PLAYER_FILE, "utf8"));
    } catch {
      return { ...DEFAULT_PLAYER };
    }
  }
  return { ...DEFAULT_PLAYER };
}

function savePlayer(player) {
  ensureSaveDir();
  fs.writeFileSync(PLAYER_FILE, JSON.stringify(player, null, 2));
}

function buildSystemPrompt(player) {
  let prompt = `Du bist ein Spielleiter für ein deutsches Text-Adventure im Harry Potter Universum. Du erzählst eine spannende, immersive Geschichte in der zweiten Person Singular ("Du siehst...", "Du stehst vor...").

SPIELER-INFORMATIONEN:
`;

  if (player.name) {
    prompt += `- Name: ${player.name}\n`;
  }
  if (player.houseName) {
    prompt += `- Haus: ${player.houseName}\n`;
  }

  // Add inventory
  if (player.inventory.length > 0) {
    prompt += `\nINVENTAR DES SPIELERS:\n`;
    player.inventory.forEach((item) => {
      prompt += `- ${item.name}: ${item.description}\n`;
    });
    prompt += `\nWICHTIG: Beziehe das Inventar in die Geschichte ein! Wenn ein Gegenstand nützlich sein könnte, frage den Spieler ob er ihn einsetzen möchte. Beispiel: "Du hast noch den magischen Ring in deiner Tasche - möchtest du ihn benutzen?"\n`;
  }

  // Add past adventures summary
  if (player.completedAdventures.length > 0) {
    prompt += `\nVERGANGENE ABENTEUER (der Spieler erinnert sich):\n`;
    // Only include last 5 adventures to keep context manageable
    const recentAdventures = player.completedAdventures.slice(-5);
    recentAdventures.forEach((adv, i) => {
      prompt += `${i + 1}. "${adv.title}": ${adv.summary}\n`;
    });
    prompt += `\nDu kannst auf vergangene Abenteuer Bezug nehmen wenn es passt (z.B. "Nach deinem Erlebnis mit dem Basilisken bist du vorsichtiger geworden...").\n`;
  }

  prompt += `
SETTING:
- Die Geschichte spielt in der magischen Welt von Harry Potter
- Orte: Hogwarts (Große Halle, Kerker, Türme, Gemeinschaftsräume, Klassenzimmer), der Verbotene Wald, London, die Winkelgasse, Gleis 9¾
- Es können bekannte Charaktere auftauchen: Professoren, Geister, Hauselfen, magische Kreaturen
- Nutze typische Elemente: Zauberstäbe, Zaubersprüche, magische Gegenstände, Quidditch

REGELN:
1. Schreibe immer auf Deutsch
2. Halte deine Antworten kurz und prägnant (max 150 Wörter pro Abschnitt)
3. Beschreibe die Szene atmosphärisch aber kompakt
4. Ende IMMER mit einer kurzen Frage an die Spieler, was sie tun wollen
5. Biete implizit 2-3 Möglichkeiten an, aber lass den Spielern auch freie Wahl
6. Reagiere auf die Entscheidungen der Spieler und treibe die Geschichte voran
7. Es kann Gefahren, Rätsel, Begegnungen und Schätze geben
8. Führe Konsequenzen für Entscheidungen ein

GEGENSTÄNDE & INVENTAR:
- Wenn der Spieler einen besonderen Gegenstand findet oder erhält, markiere ihn mit [NEUER GEGENSTAND: Name | Beschreibung]
- Beispiel: [NEUER GEGENSTAND: Unsichtbarkeitsumhang | Ein silbrig schimmernder Umhang der unsichtbar macht]
- Gib nur wirklich besondere, magische oder story-relevante Gegenstände

ABENTEUER-STRUKTUR:
- Ein Abenteuer sollte nach etwa 10-20 Zügen zu einem befriedigenden Ende kommen
- Führe die Geschichte auf ein Finale zu (Rätsel gelöst, Gefahr gebannt, Schatz gefunden)
- Wenn das Abenteuer zu einem natürlichen Ende kommt, schreibe am Ende: [ABENTEUER ABGESCHLOSSEN]
- Nach [ABENTEUER ABGESCHLOSSEN] beschreibe kurz was der Spieler erreicht hat

Beginne mit einer interessanten Eröffnungsszene, wenn der Spieler "start" sagt.`;

  return prompt;
}

const SUMMARY_PROMPT = `Du bist ein Assistent der Text-Adventure Zusammenfassungen erstellt.

Fasse das folgende Abenteuer in 2-3 Sätzen zusammen. Erwähne:
- Was passiert ist (Hauptereignisse)
- Welche wichtigen Entscheidungen getroffen wurden
- Wie es endete

Schreibe auf Deutsch, in der dritten Person, vergangene Zeit.
Halte es kurz und prägnant (max 50 Wörter).`;

async function generateSummary(conversationHistory) {
  // Extract just the story content
  const storyContent = conversationHistory
    .map((m) => `${m.role === "user" ? "Spieler" : "Erzähler"}: ${m.content}`)
    .join("\n\n");

  const response = await client.messages.create({
    model: "claude-haiku-4-5-20251001",
    max_tokens: 200,
    system: SUMMARY_PROMPT,
    messages: [
      {
        role: "user",
        content: `Fasse dieses Abenteuer zusammen:\n\n${storyContent}`,
      },
    ],
  });

  return response.content[0].text;
}

async function generateTitle(conversationHistory) {
  const firstResponses = conversationHistory
    .filter((m) => m.role === "assistant")
    .slice(0, 2)
    .map((m) => m.content)
    .join("\n");

  const response = await client.messages.create({
    model: "claude-haiku-4-5-20251001",
    max_tokens: 50,
    messages: [
      {
        role: "user",
        content: `Gib diesem Harry Potter Abenteuer einen kurzen, spannenden deutschen Titel (max 5 Wörter, ohne Anführungszeichen):\n\n${firstResponses}`,
      },
    ],
  });

  return response.content[0].text.trim().replace(/['"]/g, "");
}

function parseNewItems(response) {
  const itemRegex = /\[NEUER GEGENSTAND:\s*([^|]+)\s*\|\s*([^\]]+)\]/g;
  const items = [];
  let match;

  while ((match = itemRegex.exec(response)) !== null) {
    items.push({
      name: match[1].trim(),
      description: match[2].trim(),
      foundAt: new Date().toISOString(),
    });
  }

  return items;
}

function isAdventureComplete(response) {
  return response.includes("[ABENTEUER ABGESCHLOSSEN]");
}

async function chat(conversationHistory, player) {
  let fullResponse = "";

  const stream = client.messages.stream({
    model: "claude-haiku-4-5-20251001",
    max_tokens: 500,
    system: buildSystemPrompt(player),
    messages: conversationHistory,
  });

  for await (const event of stream) {
    if (
      event.type === "content_block_delta" &&
      event.delta.type === "text_delta"
    ) {
      process.stdout.write(event.delta.text);
      fullResponse += event.delta.text;
    }
  }

  return fullResponse;
}

async function main() {
  const rl = readline.createInterface({
    input: process.stdin,
    output: process.stdout,
  });

  const question = (prompt) =>
    new Promise((resolve) => rl.question(prompt, resolve));

  let player = loadPlayer();

  console.log("\n╔══════════════════════════════════════════════════════════╗");
  console.log("║           🏰  HARRY POTTER TEXT-ADVENTURE  🏰            ║");
  console.log("╚══════════════════════════════════════════════════════════╝\n");

  // New player setup
  if (!player.name) {
    console.log("Willkommen, neuer Zauberer!\n");

    const name = await question("Wie lautet dein Name? ");
    player.name = name.trim() || "Unbekannter Zauberer";

    console.log("\nWähle dein Haus:");
    console.log("  [1] 🦁 Gryffindor - Mut und Tapferkeit");
    console.log("  [2] 🐍 Slytherin - Ehrgeiz und List");
    console.log("  [3] 🦅 Ravenclaw - Weisheit und Kreativität");
    console.log("  [4] 🦡 Hufflepuff - Treue und Fleiß\n");

    const houseChoice = await question("Deine Wahl (1-4): ");
    const houses = ["Gryffindor", "Slytherin", "Ravenclaw", "Hufflepuff"];
    player.houseName = houses[parseInt(houseChoice) - 1] || "Gryffindor";

    // Starting inventory
    player.inventory = [
      {
        name: "Zauberstab",
        description: "Dein treuer Zauberstab aus Ollivanders Laden",
        foundAt: new Date().toISOString(),
      },
    ];

    savePlayer(player);
    console.log(
      `\n✨ Willkommen in ${player.houseName}, ${player.name}! ✨\n`
    );
  } else {
    console.log(`Willkommen zurück, ${player.name} aus ${player.houseName}!\n`);
  }

  // Show player status
  console.log("╔══════════════════════════════════════════════════════════╗");
  console.log(`║  📊 Status                                                `);
  console.log("╠══════════════════════════════════════════════════════════╣");
  console.log(
    `║  Abgeschlossene Abenteuer: ${player.stats.adventuresCompleted}`
  );
  console.log(`║  Gegenstände im Inventar: ${player.inventory.length}`);
  if (player.inventory.length > 0) {
    console.log("║  ─────────────────────────────────────────────────────────");
    player.inventory.slice(0, 5).forEach((item) => {
      const displayName = item.name.substring(0, 40);
      console.log(`║    • ${displayName}`);
    });
    if (player.inventory.length > 5) {
      console.log(`║    ... und ${player.inventory.length - 5} weitere`);
    }
  }
  console.log("╚══════════════════════════════════════════════════════════╝\n");

  // Check for ongoing adventure
  if (player.currentAdventure) {
    const startDate = new Date(
      player.currentAdventure.startedAt
    ).toLocaleString("de-DE");
    console.log("╔══════════════════════════════════════════════════════════╗");
    console.log("║  📜 Laufendes Abenteuer gefunden!                        ║");
    console.log("╠══════════════════════════════════════════════════════════╣");
    console.log(`║  "${player.currentAdventure.title || "Unbenannt"}"`);
    console.log(`║  Gestartet: ${startDate}`);
    console.log(
      `║  Züge: ${player.currentAdventure.conversationHistory.length / 2}`
    );
    console.log("╚══════════════════════════════════════════════════════════╝\n");

    console.log("Du musst dein aktuelles Abenteuer erst beenden!\n");

    // Show last response as context
    const lastAssistant = [...player.currentAdventure.conversationHistory]
      .reverse()
      .find((m) => m.role === "assistant");
    if (lastAssistant) {
      console.log("─".repeat(60));
      console.log("\n📜 Zuletzt:\n");
      // Clean up display (remove item markers)
      const displayText = lastAssistant.content
        .replace(/\[NEUER GEGENSTAND:[^\]]+\]/g, "")
        .trim();
      console.log(displayText);
      console.log("\n" + "─".repeat(60));
    }

    console.log("\n💡 Befehle:");
    console.log("   • Beschreibe deine Aktion frei");
    console.log("   • 'inventar' - Zeige dein Inventar");
    console.log("   • 'aufgeben' - Abenteuer abbrechen (kein Fortschritt)\n");
  } else {
    console.log("╔══════════════════════════════════════════════════════════╗");
    console.log("║  🎮 Bereit für ein neues Abenteuer!                      ║");
    console.log("╠══════════════════════════════════════════════════════════╣");
    console.log("║  Befehle:                                                ║");
    console.log("║    • 'start' - Neues Abenteuer beginnen                  ║");
    console.log("║    • 'inventar' - Zeige dein Inventar                    ║");
    console.log("║    • 'geschichte' - Zeige vergangene Abenteuer           ║");
    console.log("║    • 'beenden' - Spiel beenden                           ║");
    console.log("╚══════════════════════════════════════════════════════════╝\n");
  }

  const askQuestion = () => {
    rl.question("\n🎮 > ", async (input) => {
      const userInput = input.trim().toLowerCase();

      if (!userInput) {
        askQuestion();
        return;
      }

      // Command: quit
      if (userInput === "beenden") {
        if (player.currentAdventure) {
          console.log("\n💾 Abenteuer wird gespeichert...");
          savePlayer(player);
        }
        console.log("✨ Danke fürs Spielen! Bis zum nächsten Abenteuer! ✨\n");
        rl.close();
        return;
      }

      // Command: inventory
      if (userInput === "inventar") {
        console.log("\n╔════════════════════════════════════════╗");
        console.log("║  🎒 DEIN INVENTAR                      ║");
        console.log("╠════════════════════════════════════════╣");
        if (player.inventory.length === 0) {
          console.log("║  (leer)                                ║");
        } else {
          player.inventory.forEach((item) => {
            console.log(`║  • ${item.name}`);
            console.log(`║    ${item.description}`);
          });
        }
        console.log("╚════════════════════════════════════════╝");
        askQuestion();
        return;
      }

      // Command: history
      if (userInput === "geschichte") {
        console.log("\n╔════════════════════════════════════════════════════════╗");
        console.log("║  📚 DEINE ABENTEUER-GESCHICHTE                         ║");
        console.log("╠════════════════════════════════════════════════════════╣");
        if (player.completedAdventures.length === 0) {
          console.log("║  Du hast noch keine Abenteuer abgeschlossen.           ║");
        } else {
          player.completedAdventures.forEach((adv, i) => {
            const date = new Date(adv.completedAt).toLocaleDateString("de-DE");
            console.log(`║  ${i + 1}. "${adv.title}" (${date})`);
            console.log(`║     ${adv.summary}`);
            console.log("║");
          });
        }
        console.log("╚════════════════════════════════════════════════════════╝");
        askQuestion();
        return;
      }

      // Command: abandon adventure
      if (userInput === "aufgeben" && player.currentAdventure) {
        const confirm = await question(
          "Bist du sicher? Du verlierst allen Fortschritt dieses Abenteuers. (ja/nein): "
        );
        if (confirm.trim().toLowerCase() === "ja") {
          player.currentAdventure = null;
          savePlayer(player);
          console.log("\n❌ Abenteuer abgebrochen.");
          console.log("Tippe 'start' um ein neues Abenteuer zu beginnen.\n");
        }
        askQuestion();
        return;
      }

      // Start new adventure
      if (userInput === "start" && !player.currentAdventure) {
        player.currentAdventure = {
          title: null,
          startedAt: new Date().toISOString(),
          conversationHistory: [],
        };
        savePlayer(player);
      }

      // Need active adventure for gameplay
      if (!player.currentAdventure) {
        console.log("\n💡 Tippe 'start' um ein neues Abenteuer zu beginnen!");
        askQuestion();
        return;
      }

      // Regular gameplay
      player.currentAdventure.conversationHistory.push({
        role: "user",
        content: input.trim(), // Use original case
      });

      try {
        console.log("\n" + "─".repeat(60));
        console.log();

        const response = await chat(
          player.currentAdventure.conversationHistory,
          player
        );
        console.log("\n");
        console.log("─".repeat(60));

        player.currentAdventure.conversationHistory.push({
          role: "assistant",
          content: response,
        });

        // Check for new items
        const newItems = parseNewItems(response);
        if (newItems.length > 0) {
          newItems.forEach((item) => {
            // Avoid duplicates
            if (!player.inventory.find((i) => i.name === item.name)) {
              player.inventory.push(item);
              console.log(`\n🎁 Neuer Gegenstand erhalten: ${item.name}!`);
            }
          });
        }

        // Generate title after first exchange
        if (
          !player.currentAdventure.title &&
          player.currentAdventure.conversationHistory.length >= 2
        ) {
          try {
            player.currentAdventure.title = await generateTitle(
              player.currentAdventure.conversationHistory
            );
          } catch {
            player.currentAdventure.title = `Abenteuer vom ${new Date().toLocaleDateString("de-DE")}`;
          }
        }

        // Check if adventure is complete
        if (isAdventureComplete(response)) {
          console.log("\n🎉 ══════════════════════════════════════════════════ 🎉");
          console.log("   ABENTEUER ABGESCHLOSSEN!");
          console.log("🎉 ══════════════════════════════════════════════════ 🎉\n");

          console.log("📝 Erstelle Zusammenfassung...\n");

          try {
            const summary = await generateSummary(
              player.currentAdventure.conversationHistory
            );

            player.completedAdventures.push({
              title: player.currentAdventure.title,
              summary: summary,
              completedAt: new Date().toISOString(),
            });

            console.log(`📜 "${player.currentAdventure.title}"`);
            console.log(`   ${summary}\n`);
          } catch (err) {
            player.completedAdventures.push({
              title: player.currentAdventure.title,
              summary: "Ein weiteres erfolgreiches Abenteuer.",
              completedAt: new Date().toISOString(),
            });
          }

          player.stats.adventuresCompleted++;
          player.stats.totalTurns +=
            player.currentAdventure.conversationHistory.length / 2;
          player.currentAdventure = null;

          console.log("Tippe 'start' um ein neues Abenteuer zu beginnen!\n");
        }

        savePlayer(player);
      } catch (error) {
        console.error("\n❌ Fehler:", error.message);
        player.currentAdventure.conversationHistory.pop();
      }

      askQuestion();
    });
  };

  askQuestion();
}

main();
