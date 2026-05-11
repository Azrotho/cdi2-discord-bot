package fr.citedesiles.discordbot;

import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.requests.GatewayIntent;

public class DiscordBot {

    public static void main(String[] args) {
        String token = System.getenv("DISCORD_TOKEN");
        
        if (token == null || token.isEmpty()) {
            System.err.println("Le token Discord n'est pas défini dans la variable d'environnement 'DISCORD_TOKEN'");
            return;
        }

        try {
            JDABuilder.createLight(token, GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT)
                    .setActivity(Activity.playing("sur Cité des Îles"))
                    .build();
            System.out.println("Le bot Discord est connecté !");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}