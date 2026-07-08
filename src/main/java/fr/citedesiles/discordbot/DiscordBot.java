package fr.citedesiles.discordbot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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

    public static boolean inscriptionsOuvertes = true;

    public static void setInscriptionsOuvertes(boolean ouvertes) {
        inscriptionsOuvertes = ouvertes;
        try {
            Path configPath = Path.of("config.json");
            JsonObject configJson = new JsonObject();
            if (Files.exists(configPath)) {
                String content = Files.readString(configPath);
                configJson = new Gson().fromJson(content, JsonObject.class);
            }
            configJson.addProperty("inscriptions_ouvertes", ouvertes);
            Files.writeString(configPath, new GsonBuilder().setPrettyPrinting().create().toJson(configJson));
        } catch (Exception e) {
            System.err.println("Erreur lors de la sauvegarde de la config : " + e.getMessage());
        }
    }

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
                if (config.has("inscriptions_ouvertes")) {
                    inscriptionsOuvertes = config.get("inscriptions_ouvertes").getAsBoolean();
                }
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
                    .addEventListeners(new LinkCommandListener(api), new fr.citedesiles.discordbot.listener.TeamCommandListener(api))
                    .build();

            // Enregistrer globalement les commandes en écrasant les anciennes (Clean)
            jda.updateCommands().addCommands(
                    net.dv8tion.jda.api.interactions.commands.build.Commands.slash("link", "Lie ton compte Minecraft à Discord")
                            .addOption(OptionType.STRING, "code", "Code reçu en jeu avec /link", true),
                    net.dv8tion.jda.api.interactions.commands.build.Commands.slash("verifier", "Vérifie une équipe complète (4/4 membres)")
                            .addOption(OptionType.STRING, "nom_ou_tag", "Le nom ou le tag de l'équipe à vérifier", true),
                    net.dv8tion.jda.api.interactions.commands.build.Commands.slash("endinscription", "Ferme les inscriptions et configure le serveur Discord pour l'événement"),
                    net.dv8tion.jda.api.interactions.commands.build.Commands.slash("team", "Gère ton équipe Cité des Îles")
                            .addSubcommands(
                                    new net.dv8tion.jda.api.interactions.commands.build.SubcommandData("create", "Crée une nouvelle équipe")
                                            .addOption(OptionType.STRING, "nom", "Le nom de l'équipe", true)
                                            .addOption(OptionType.STRING, "tag", "Le tag de l'équipe (3 ou 4 caractères)", true),
                                    new net.dv8tion.jda.api.interactions.commands.build.SubcommandData("invite", "Invite un joueur dans ton équipe")
                                            .addOption(OptionType.USER, "joueur", "Le joueur Discord à inviter", true),
                                    new net.dv8tion.jda.api.interactions.commands.build.SubcommandData("leave", "Quitte ton équipe actuelle"),
                                    new net.dv8tion.jda.api.interactions.commands.build.SubcommandData("disband", "Dissout ton équipe (réservé au chef)"),
                                    new net.dv8tion.jda.api.interactions.commands.build.SubcommandData("kick", "Exclut un membre de ton équipe")
                                            .addOption(OptionType.USER, "joueur", "Le membre de ton équipe à exclure", true),
                                    new net.dv8tion.jda.api.interactions.commands.build.SubcommandData("transfer", "Transfère la direction de l'équipe à un autre membre")
                                            .addOption(OptionType.USER, "joueur", "Le membre à promouvoir leader", true),
                                    new net.dv8tion.jda.api.interactions.commands.build.SubcommandData("info", "Affiche les informations d'une équipe")
                                            .addOption(OptionType.USER, "joueur", "Affiche l'équipe de ce joueur", false)
                                            .addOption(OptionType.STRING, "nom_ou_tag", "Affiche l'équipe par son nom ou son tag", false),
                                    new net.dv8tion.jda.api.interactions.commands.build.SubcommandData("edit", "Modifie les détails de ton équipe")
                                            .addOptions(
                                                    new net.dv8tion.jda.api.interactions.commands.build.OptionData(OptionType.STRING, "champ", "Le champ à modifier", true)
                                                            .addChoice("Nom", "name")
                                                            .addChoice("Tag", "tag")
                                                            .addChoice("Couleur", "color"),
                                                    new net.dv8tion.jda.api.interactions.commands.build.OptionData(OptionType.STRING, "valeur", "La nouvelle valeur", true)
                                            )
                            )
            ).queue();

            System.out.println("Le bot Discord est connecté !");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}