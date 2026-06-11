package fr.citedesiles.discordbot;

import fr.citedesiles.coreplugin.CoreCDI;
import fr.citedesiles.discordbot.listener.LinkCommandListener;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.requests.GatewayIntent;

public class DiscordBot {

    public static void main(String[] args) {
        String token = System.getenv("DISCORD_TOKEN");
        String apiUrl = System.getenv("CORE_API_URL");
        String apiToken = System.getenv("CORE_API_TOKEN");

        if (token == null || token.isEmpty()) {
            System.err.println("DISCORD_TOKEN non défini.");
            return;
        }
        if (apiUrl == null) apiUrl = "http://localhost:3000";
        if (apiToken == null) apiToken = "bipboup";

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