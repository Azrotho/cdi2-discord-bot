package fr.citedesiles.discordbot;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import fr.citedesiles.coreplugin.CoreCDI;
import fr.citedesiles.discordbot.listener.LinkCommandListener;
import fr.citedesiles.discordbot.listener.TeamCommandListener;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.SubcommandData;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
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
                try {
                    String content = Files.readString(configPath);
                    configJson = new Gson().fromJson(content, JsonObject.class);
                } catch (Exception e) {
                    System.err.println("⚠️ Impossible de lire config.json, création d'une nouvelle config.");
                }
            }
            configJson.addProperty("inscriptions_ouvertes", ouvertes);
            Files.writeString(configPath, new GsonBuilder().setPrettyPrinting().create().toJson(configJson));
        } catch (Exception e) {
            System.err.println("⚠️ Impossible d'enregistrer config.json : " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        String token = System.getenv("DISCORD_TOKEN");
        String apiUrl = System.getenv("API_URL");
        String apiToken = System.getenv("API_TOKEN");

        if (token == null || token.isEmpty()) {
            System.err.println("❌ Le token Discord n'est pas configuré.");
            System.exit(1);
        }

        if (apiUrl == null || apiUrl.isEmpty()) {
            System.err.println("❌ L'URL de l'API n'est pas configurée.");
            System.exit(1);
        }

        if (apiToken == null || apiToken.isEmpty()) {
            System.err.println("❌ Le token de l'API n'est pas configuré.");
            System.exit(1);
        }

        // Charger config.json au démarrage
        try {
            Path configPath = Path.of("config.json");
            if (Files.exists(configPath)) {
                String content = Files.readString(configPath);
                JsonObject configJson = new Gson().fromJson(content, JsonObject.class);
                if (configJson != null && configJson.has("inscriptions_ouvertes")) {
                    inscriptionsOuvertes = configJson.get("inscriptions_ouvertes").getAsBoolean();
                }
            }
        } catch (Exception e) {
            System.err.println("⚠️ Impossible de charger la configuration, valeur par défaut (ouvertes) utilisée.");
        }

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
                    .addEventListeners(new LinkCommandListener(api), new TeamCommandListener(api))
                    .build();

            // Enregistrer globalement les commandes en écrasant les anciennes (Clean)
            jda.updateCommands().addCommands(
                    Commands.slash("link", "Lie ton compte Minecraft à Discord")
                            .addOption(OptionType.STRING, "code", "Code reçu en jeu avec /link", true),
                    Commands.slash("verifier", "Vérifie une équipe complète (4/4 membres)")
                            .addOption(OptionType.STRING, "nom_ou_tag", "Le nom ou le tag de l'équipe à vérifier", true),
                    Commands.slash("endinscription", "Ferme les inscriptions et configure le serveur Discord pour l'événement"),
                    Commands.slash("team", "Gère ton équipe Cité des Îles")
                            .addSubcommands(
                                    new SubcommandData("create", "Crée une nouvelle équipe")
                                            .addOption(OptionType.STRING, "nom", "Le nom de l'équipe", true)
                                            .addOption(OptionType.STRING, "tag", "Le tag de l'équipe (3 ou 4 caractères)", true),
                                    new SubcommandData("invite", "Invite un joueur dans ton équipe")
                                            .addOption(OptionType.USER, "joueur", "Le joueur Discord à inviter", true),
                                    new SubcommandData("leave", "Quitte ton équipe actuelle"),
                                    new SubcommandData("disband", "Dissout ton équipe (réservé au chef)"),
                                    new SubcommandData("kick", "Exclut un membre de ton équipe")
                                            .addOption(OptionType.USER, "joueur", "Le membre de ton équipe à exclure", true),
                                    new SubcommandData("transfer", "Transfère la direction de l'équipe à un autre membre")
                                            .addOption(OptionType.USER, "joueur", "Le membre à promouvoir leader", true),
                                    new SubcommandData("info", "Affiche les informations d'une équipe")
                                            .addOption(OptionType.USER, "joueur", "Affiche l'équipe de ce joueur", false)
                                            .addOption(OptionType.STRING, "nom_ou_tag", "Affiche l'équipe par son nom ou son tag", false),
                                    new SubcommandData("edit", "Modifie les détails de ton équipe")
                                            .addOptions(
                                                    new OptionData(OptionType.STRING, "champ", "Le champ à modifier", true)
                                                            .addChoice("Nom", "name")
                                                            .addChoice("Tag", "tag")
                                                            .addChoice("Couleur", "color"),
                                                    new OptionData(OptionType.STRING, "valeur", "La nouvelle valeur", true)
                                            )
                            )
            ).queue();

            System.out.println("Le bot Discord est connecté !");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}