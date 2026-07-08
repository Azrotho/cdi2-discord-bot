package fr.citedesiles.discordbot.listener;

import fr.citedesiles.coreplugin.CoreCDI;
import fr.citedesiles.discordbot.DiscordBot;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

public class LinkCommandListener extends ListenerAdapter {

    private final CoreCDI api;

    public LinkCommandListener(CoreCDI api) {
        this.api = api;
    }

    @Override
    @SuppressWarnings("null")
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("link")) return;

        if (!DiscordBot.inscriptionsOuvertes) {
            event.reply("❌ Les inscriptions sont fermées.")
                    .setEphemeral(true).queue();
            return;
        }

        OptionMapping codeOption = event.getOption("code");
        if (codeOption == null) {
            event.reply("❌ Tu dois fournir le code reçu en jeu avec `/link`.")
                    .setEphemeral(true).queue();
            return;
        }

        String code = codeOption.getAsString();
        String discordId = event.getUser().getId();

        event.deferReply(true).queue(hook -> {
            try {
                String playerName = api.linkDiscord(code, discordId);
                hook.sendMessage("✅ Ton compte Discord est maintenant lié à **" + playerName + "** !\n"
                        + "Tu peux maintenant créer ou rejoindre une équipe.").queue();
            } catch (CoreCDI.ApiException e) {
                String msg = switch (e.getStatusCode()) {
                    case 404 -> "❌ Ce code n'existe pas. Refais `/link` en jeu sur Minecraft.";
                    case 400 -> e.getMessage().contains("expired")
                            ? "❌ Ce code a expiré. Refais `/link` en jeu sur Minecraft."
                            : "❌ Code invalide. Vérifie que tu as bien copié le code.";
                    case 409 -> e.getMessage().contains("Discord")
                            ? "❌ Ton compte Discord est déjà lié à un compte Minecraft."
                            : "❌ Ce compte Minecraft est déjà lié à un compte Discord.";
                    default -> "❌ Une erreur est survenue : " + e.getMessage();
                };
                hook.sendMessage(msg).queue();
            }
        });
    }
}
