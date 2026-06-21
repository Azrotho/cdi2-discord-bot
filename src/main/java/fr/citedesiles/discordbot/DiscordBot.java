package fr.citedesiles.discordbot;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import fr.citedesiles.coreplugin.CoreCDI;
import fr.citedesiles.discordbot.listener.LinkCommandListener;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.requests.GatewayIntent;

import java.nio.file.Files;
import java.nio.file.Path;

public class DiscordBot {

    public static void main(String[] args) {
        // Lire la configuration depuis config.json
        String token = null;
        String apiUrl = "http://localhost:3000";
        String apiToken = "bipboup";

        try {
            Path configPath = Path.of("config.json");
            if (Files.exists(configPath)) {
                String content = Files.readString(configPath);
                JsonObject config = new Gson().fromJson(content, JsonObject.class);
                if (config.has("discord_token")) token = config.get("discord_token").getAsString();
                if (config.has("core_api_url")) apiUrl = config.get("core_api_url").getAsString();
                if (config.has("core_api_token")) apiToken = config.get("core_api_token").getAsString();
            } else {
                System.err.println("config.json introuvable. Copiez config.json.example en config.json et remplissez-le.");
            }
        } catch (Exception e) {
            System.err.println("Erreur lors de la lecture de config.json : " + e.getMessage());
        }

        if (token == null || token.isEmpty()) {
            System.err.println("discord_token non défini dans config.json.");
            return;
        }

        // Initialiser le client API
        CoreCDI api = new CoreCDI(apiUrl, apiToken);
        try {
            if (!api.ping()) {
                System.err.println("⚠️ Impossible de contacter l'API CDI2.");
            } else {
                System.out.println("✅ Connecté à l'API CDI2 : " + apiUrl);
            }
        } catch (Exception e) {
            System.err.println("⚠️ API CDI2 injoignable : " + e.getMessage());
        }

        try {
            var jda = JDABuilder.createLight(token, GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT)
                    .setActivity(Activity.playing("sur Cité des Îles"))
                    .addEventListeners(new LinkCommandListener(api))
                    .build();

            // Enregistrer la commande slash /link
            jda.upsertCommand("link", "Lie ton compte Minecraft à Discord")
                    .addOption(OptionType.STRING, "code", "Code reçu en jeu avec /link", true)
                    .queue();

            System.out.println("Le bot Discord est connecté !");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}