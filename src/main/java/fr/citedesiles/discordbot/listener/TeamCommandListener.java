package fr.citedesiles.discordbot.listener;

import fr.citedesiles.coreplugin.CoreCDI;
import fr.citedesiles.coreplugin.Player;
import fr.citedesiles.coreplugin.Team;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.components.buttons.Button;

import java.awt.Color;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

public class TeamCommandListener extends ListenerAdapter {

    private final CoreCDI api;

    public TeamCommandListener(CoreCDI api) {
        this.api = api;
    }

    private static class HandledException extends Exception {
        public HandledException() {
            super();
        }
    }

    private Player getPlayerOrError(String discordId, SlashCommandInteractionEvent event) throws HandledException {
        try {
            return api.getPlayerByDiscord(discordId);
        } catch (CoreCDI.ApiException e) {
            if (e.getStatusCode() == 404) {
                event.reply("❌ Tu dois lier ton compte Minecraft à Discord avec `/link` avant de pouvoir faire cela.")
                        .setEphemeral(true).queue();
            } else {
                event.reply("❌ Une erreur est survenue lors de la récupération de ton profil : " + e.getMessage())
                        .setEphemeral(true).queue();
            }
            throw new HandledException();
        }
    }

    private Player getTargetPlayerOrError(String discordId, SlashCommandInteractionEvent event, String targetMention) throws HandledException {
        try {
            return api.getPlayerByDiscord(discordId);
        } catch (CoreCDI.ApiException e) {
            if (e.getStatusCode() == 404) {
                event.reply("❌ Le joueur " + targetMention + " n'a pas lié son compte Discord à Minecraft.")
                        .setEphemeral(true).queue();
            } else {
                event.reply("❌ Une erreur est survenue lors de la récupération du profil de " + targetMention + " : " + e.getMessage())
                        .setEphemeral(true).queue();
            }
            throw new HandledException();
        }
    }

    @Override
    public void onSlashCommandInteraction(SlashCommandInteractionEvent event) {
        if (!event.getName().equals("team")) return;
        if (event.getSubcommandName() == null) return;

        try {
            switch (event.getSubcommandName()) {
                case "create" -> handleCreate(event);
                case "invite" -> handleInvite(event);
                case "leave" -> handleLeave(event);
                case "kick" -> handleKick(event);
                case "transfer" -> handleTransfer(event);
                case "edit" -> handleEdit(event);
                case "info" -> handleInfo(event);
            }
        } catch (HandledException ignored) {
            // L'erreur a déjà été traitée et répondue à l'utilisateur
        }
    }

    private void handleCreate(SlashCommandInteractionEvent event) throws HandledException {
        String name = event.getOption("nom").getAsString();
        String tag = event.getOption("tag").getAsString().toUpperCase();
        String colorStr = String.format("#%06X", new java.util.Random().nextInt(0xFFFFFF + 1));

        Player player = getPlayerOrError(event.getUser().getId(), event);
        if (player.team() != -1) {
            event.reply("❌ Tu es déjà dans une équipe. Quitte-la d'abord avec `/team leave`.")
                    .setEphemeral(true).queue();
            return;
        }

        // Validations
        if (!Pattern.matches("^[a-zA-Z0-9éèàâêîôûç -]{3,32}$", name)) {
            event.reply("❌ Le nom de l'équipe doit faire entre 3 et 32 caractères et ne contenir que des lettres, chiffres, espaces et tirets.")
                    .setEphemeral(true).queue();
            return;
        }

        if (!Pattern.matches("^[A-Z0-9]{3,4}$", tag)) {
            event.reply("❌ Le tag de l'équipe doit faire 3 ou 4 caractères alphanumériques.")
                    .setEphemeral(true).queue();
            return;
        }

        String finalColor = colorStr;
        event.deferReply(false).queue(hook -> {
            try {
                // Créer la team
                Team team = api.createTeam(name, tag, finalColor, player.uuid());
                // Mettre le joueur dans la team
                api.setPlayerTeam(player.uuid(), team.id());

                EmbedBuilder embed = new EmbedBuilder();
                embed.setTitle("✅ Équipe créée avec succès !");
                embed.setDescription("L'équipe **" + name + "** (`" + tag + "`) a été créée ! Tu en es le chef.\n\n"
                        + "🎨 Une couleur aléatoire a été assignée : `" + finalColor + "`.\n"
                        + "Tu peux la modifier à tout moment avec `/team edit champ:Couleur valeur:<hex>`.");
                try {
                    embed.setColor(Color.decode(finalColor));
                } catch (Exception e) {
                    embed.setColor(new Color(88, 101, 242));
                }
                hook.sendMessageEmbeds(embed.build()).queue();
            } catch (CoreCDI.ApiException e) {
                String msg = switch (e.getStatusCode()) {
                    case 400 -> e.getMessage().contains("Tag") ? "❌ Ce tag d'équipe est déjà utilisé." : "❌ Paramètres invalides : " + e.getMessage();
                    default -> "❌ Une erreur est survenue lors de la création : " + e.getMessage();
                };
                hook.sendMessage(msg).queue();
            }
        });
    }

    private void handleInvite(SlashCommandInteractionEvent event) throws HandledException {
        User targetUser = event.getOption("joueur").getAsUser();
        if (targetUser.isBot()) {
            event.reply("❌ Tu ne peux pas inviter un bot dans ton équipe.")
                    .setEphemeral(true).queue();
            return;
        }

        Player player = getPlayerOrError(event.getUser().getId(), event);
        if (player.team() == -1) {
            event.reply("❌ Tu n'es pas dans une équipe.")
                    .setEphemeral(true).queue();
            return;
        }

        Team team;
        try {
            team = api.getTeam(player.team());
        } catch (Exception e) {
            event.reply("❌ Impossible de charger les informations de ton équipe.")
                    .setEphemeral(true).queue();
            return;
        }

        if (!team.leader().equals(player.uuid())) {
            event.reply("❌ Seul le chef d'équipe (" + player.name() + ") peut inviter de nouveaux membres.")
                    .setEphemeral(true).queue();
            return;
        }

        // Vérification limite membres
        List<Player> members = team.players(api);
        if (team.staff() != 1 && members.size() >= 4) {
            event.reply("❌ Ton équipe a déjà atteint la limite de 4 membres.")
                    .setEphemeral(true).queue();
            return;
        }

        // Vérification joueur cible (doit être lié et sans équipe)
        Player targetPlayer = getTargetPlayerOrError(targetUser.getId(), event, targetUser.getAsMention());
        if (targetPlayer.team() != -1) {
            event.reply("❌ Le joueur " + targetUser.getAsMention() + " est déjà dans une équipe.")
                    .setEphemeral(true).queue();
            return;
        }

        // Envoyer l'invitation en MP
        event.deferReply(true).queue(hook -> {
            targetUser.openPrivateChannel().queue(
                    channel -> {
                        channel.sendMessage("✉️ **" + player.name() + "** t'invite à rejoindre l'équipe **" + team.name() + "** (`" + team.tag() + "`) sur Cité des Îles !\n"
                                        + "Utilise les boutons ci-dessous pour répondre.")
                                .setComponents(
                                        net.dv8tion.jda.api.components.actionrow.ActionRow.of(
                                                Button.success("team_invite:accept:" + team.id() + ":" + targetPlayer.uuid() + ":" + event.getUser().getId(), "Accepter"),
                                                Button.danger("team_invite:deny:" + team.id() + ":" + targetPlayer.uuid() + ":" + event.getUser().getId(), "Refuser")
                                        )
                                ).queue(
                                        success -> hook.sendMessage("✅ L'invitation a été envoyée en message privé à " + targetUser.getAsMention() + ".").queue(),
                                        failure -> hook.sendMessage("❌ Impossible d'envoyer un message privé à " + targetUser.getAsMention() + ". Ses messages privés sont peut-être fermés.").queue()
                                );
                    },
                    failure -> hook.sendMessage("❌ Impossible d'ouvrir un salon privé avec " + targetUser.getAsMention() + ".").queue()
            );
        });
    }

    private void handleLeave(SlashCommandInteractionEvent event) throws HandledException {
        Player player = getPlayerOrError(event.getUser().getId(), event);
        if (player.team() == -1) {
            event.reply("❌ Tu n'es pas dans une équipe.")
                    .setEphemeral(true).queue();
            return;
        }

        Team team;
        try {
            team = api.getTeam(player.team());
        } catch (Exception e) {
            event.reply("❌ Impossible de récupérer les informations de ton équipe.")
                    .setEphemeral(true).queue();
            return;
        }

        event.deferReply(false).queue(hook -> {
            try {
                if (team.leader().equals(player.uuid())) {
                    // C'est le chef
                    List<Player> members = team.players(api);
                    if (members.size() > 1) {
                        hook.sendMessage("❌ Tu es le chef de l'équipe. Tu dois désigner un nouveau chef avec `/team transfer` ou exclure les autres membres avec `/team kick` avant de quitter l'équipe.").queue();
                    } else {
                        // Dissoudre la team car il est seul
                        api.setPlayerTeam(player.uuid(), -1);
                        api.deleteTeam(team.id());
                        hook.sendMessage("👋 Tu as quitté et dissous l'équipe **" + team.name() + "** car tu en étais le dernier membre.").queue();
                    }
                } else {
                    // Membre normal
                    api.setPlayerTeam(player.uuid(), -1);
                    hook.sendMessage("👋 Tu as quitté l'équipe **" + team.name() + "**.").queue();
                }
            } catch (Exception e) {
                hook.sendMessage("❌ Une erreur est survenue : " + e.getMessage()).queue();
            }
        });
    }

    private void handleKick(SlashCommandInteractionEvent event) throws HandledException {
        User targetUser = event.getOption("joueur").getAsUser();
        Player player = getPlayerOrError(event.getUser().getId(), event);
        if (player.team() == -1) {
            event.reply("❌ Tu n'es pas dans une équipe.")
                    .setEphemeral(true).queue();
            return;
        }

        Team team;
        try {
            team = api.getTeam(player.team());
        } catch (Exception e) {
            event.reply("❌ Impossible de charger les informations de ton équipe.")
                    .setEphemeral(true).queue();
            return;
        }

        if (!team.leader().equals(player.uuid())) {
            event.reply("❌ Seul le chef d'équipe peut exclure des membres.")
                    .setEphemeral(true).queue();
            return;
        }

        Player targetPlayer = getTargetPlayerOrError(targetUser.getId(), event, targetUser.getAsMention());
        if (targetPlayer.team() != team.id()) {
            event.reply("❌ Ce joueur n'est pas membre de ton équipe.")
                    .setEphemeral(true).queue();
            return;
        }

        if (targetPlayer.uuid().equals(player.uuid())) {
            event.reply("❌ Tu ne peux pas t'exclure toi-même de ton équipe. Utilise `/team leave`.")
                    .setEphemeral(true).queue();
            return;
        }

        event.deferReply(false).queue(hook -> {
            try {
                api.setPlayerTeam(targetPlayer.uuid(), -1);
                hook.sendMessage("✅ **" + targetPlayer.name() + "** a été exclu de l'équipe.").queue();

                // Envoyer un message privé à l'exclu pour l'informer
                targetUser.openPrivateChannel().queue(c -> c.sendMessage("🔔 Tu as été exclu de l'équipe **" + team.name() + "**.").queue(), err -> {});
            } catch (Exception e) {
                hook.sendMessage("❌ Une erreur est survenue : " + e.getMessage()).queue();
            }
        });
    }

    private void handleTransfer(SlashCommandInteractionEvent event) throws HandledException {
        User targetUser = event.getOption("joueur").getAsUser();
        Player player = getPlayerOrError(event.getUser().getId(), event);
        if (player.team() == -1) {
            event.reply("❌ Tu n'es pas dans une équipe.")
                    .setEphemeral(true).queue();
            return;
        }

        Team team;
        try {
            team = api.getTeam(player.team());
        } catch (Exception e) {
            event.reply("❌ Impossible de charger les informations de ton équipe.")
                    .setEphemeral(true).queue();
            return;
        }

        if (!team.leader().equals(player.uuid())) {
            event.reply("❌ Seul le chef d'équipe actuel peut transférer la direction.")
                    .setEphemeral(true).queue();
            return;
        }

        Player targetPlayer = getTargetPlayerOrError(targetUser.getId(), event, targetUser.getAsMention());
        if (targetPlayer.team() != team.id()) {
            event.reply("❌ Ce joueur n'est pas membre de ton équipe.")
                    .setEphemeral(true).queue();
            return;
        }

        if (targetPlayer.uuid().equals(player.uuid())) {
            event.reply("❌ Tu es déjà le chef de cette équipe.")
                    .setEphemeral(true).queue();
            return;
        }

        event.deferReply(false).queue(hook -> {
            try {
                api.setTeamLeader(team.id(), targetPlayer.uuid());
                hook.sendMessage("👑 **" + targetPlayer.name() + "** est maintenant le chef de l'équipe !").queue();
            } catch (Exception e) {
                hook.sendMessage("❌ Une erreur est survenue : " + e.getMessage()).queue();
            }
        });
    }

    private void handleEdit(SlashCommandInteractionEvent event) throws HandledException {
        String field = event.getOption("champ").getAsString();
        String value = event.getOption("valeur").getAsString();

        Player player = getPlayerOrError(event.getUser().getId(), event);
        if (player.team() == -1) {
            event.reply("❌ Tu n'es pas dans une équipe.")
                    .setEphemeral(true).queue();
            return;
        }

        Team team;
        try {
            team = api.getTeam(player.team());
        } catch (Exception e) {
            event.reply("❌ Impossible de récupérer les informations de ton équipe.")
                    .setEphemeral(true).queue();
            return;
        }

        if (!team.leader().equals(player.uuid())) {
            event.reply("❌ Seul le chef d'équipe peut modifier les détails de l'équipe.")
                    .setEphemeral(true).queue();
            return;
        }

        if (field.equals("name")) {
            final String nameVal = value;
            if (!Pattern.matches("^[a-zA-Z0-9éèàâêîôûç -]{3,32}$", nameVal)) {
                event.reply("❌ Le nom doit faire entre 3 et 32 caractères (lettres, chiffres, espaces et tirets).")
                        .setEphemeral(true).queue();
                return;
            }
            event.deferReply(false).queue(hook -> {
                try {
                    api.setTeamName(team.id(), nameVal);
                    hook.sendMessage("✅ Le nom de l'équipe a été mis à jour : **" + nameVal + "**.").queue();
                } catch (Exception e) {
                    hook.sendMessage("❌ Erreur : " + e.getMessage()).queue();
                }
            });
        } else if (field.equals("tag")) {
            String tagVal = value.toUpperCase();
            if (!Pattern.matches("^[A-Z0-9]{3,4}$", tagVal)) {
                event.reply("❌ Le tag de l'équipe doit faire 3 ou 4 caractères alphanumériques.")
                        .setEphemeral(true).queue();
                return;
            }
            event.deferReply(false).queue(hook -> {
                try {
                    api.setTeamTag(team.id(), tagVal);
                    hook.sendMessage("✅ Le tag de l'équipe a été mis à jour : `" + tagVal + "`.").queue();
                } catch (CoreCDI.ApiException e) {
                    String msg = e.getStatusCode() == 400 && e.getMessage().contains("Tag") ? "❌ Ce tag d'équipe est déjà utilisé." : "❌ Erreur : " + e.getMessage();
                    hook.sendMessage(msg).queue();
                } catch (Exception e) {
                    hook.sendMessage("❌ Erreur : " + e.getMessage()).queue();
                }
            });
        } else if (field.equals("color")) {
            if (!value.startsWith("#")) {
                value = "#" + value;
            }
            if (!Pattern.matches("^#[0-9a-fA-F]{6}$", value)) {
                event.reply("❌ La couleur doit être au format hexadécimal (ex: `#ff0000`).")
                        .setEphemeral(true).queue();
                return;
            }
            String colorVal = value;
            event.deferReply(false).queue(hook -> {
                try {
                    api.setTeamColor(team.id(), colorVal);
                    hook.sendMessage("✅ La couleur de l'équipe a été mise à jour : `" + colorVal + "`.").queue();
                } catch (Exception e) {
                    hook.sendMessage("❌ Erreur : " + e.getMessage()).queue();
                }
            });
        }
    }

    private void handleInfo(SlashCommandInteractionEvent event) throws HandledException {
        OptionMapping userOpt = event.getOption("joueur");
        OptionMapping nameOrTagOpt = event.getOption("nom_ou_tag");

        if (userOpt != null) {
            Player p = getTargetPlayerOrError(userOpt.getAsUser().getId(), event, userOpt.getAsUser().getAsMention());
            if (p.team() == -1) {
                event.reply("❌ Le joueur " + userOpt.getAsUser().getAsMention() + " n'a pas d'équipe.")
                        .setEphemeral(true).queue();
                return;
            }
            event.deferReply(false).queue(hook -> {
                try {
                    Team team = api.getTeam(p.team());
                    showTeamInfo(team, hook);
                } catch (Exception e) {
                    hook.sendMessage("❌ Impossible de trouver l'équipe de ce joueur.").queue();
                }
            });
        } else if (nameOrTagOpt != null) {
            String query = nameOrTagOpt.getAsString().toLowerCase();
            event.deferReply(false).queue(hook -> {
                try {
                    List<Team> teams = api.getTeams();
                    Optional<Team> match = teams.stream()
                            .filter(t -> t.name().toLowerCase().equals(query) || t.tag().toLowerCase().equals(query))
                            .findFirst();

                    if (match.isPresent()) {
                        showTeamInfo(match.get(), hook);
                    } else {
                        hook.sendMessage("❌ Aucune équipe trouvée avec le nom ou le tag `" + nameOrTagOpt.getAsString() + "`.").queue();
                    }
                } catch (Exception e) {
                    hook.sendMessage("❌ Erreur lors de la recherche : " + e.getMessage()).queue();
                }
            });
        } else {
            // Aucun paramètre, on montre l'équipe de l'exécutant
            Player player = getPlayerOrError(event.getUser().getId(), event);
            if (player.team() == -1) {
                event.reply("❌ Tu n'es pas dans une équipe. Spécifie un joueur ou un nom d'équipe pour voir leurs informations.")
                        .setEphemeral(true).queue();
                return;
            }
            event.deferReply(false).queue(hook -> {
                try {
                    Team team = api.getTeam(player.team());
                    showTeamInfo(team, hook);
                } catch (Exception e) {
                    hook.sendMessage("❌ Impossible de charger ton équipe.").queue();
                }
            });
        }
    }

    private void showTeamInfo(Team team, InteractionHook hook) {
        try {
            List<Player> members = team.players(api);
            double money = api.getTeamMoney(team.id());

            EmbedBuilder embed = new EmbedBuilder();
            embed.setTitle("👥 Équipe " + team.name() + " [" + team.tag() + "]");

            // Parse Color
            try {
                embed.setColor(Color.decode(team.color()));
            } catch (Exception e) {
                embed.setColor(new Color(88, 101, 242)); // Blurple par défaut
            }

            // Trouver le chef
            String leaderName = "Inconnu";
            String leaderDiscordMention = "Non lié";
            for (Player m : members) {
                if (m.uuid().equals(team.leader())) {
                    leaderName = m.name();
                    leaderDiscordMention = "<@" + m.discordId() + ">";
                    break;
                }
            }

            embed.addField("👑 Chef d'équipe", leaderDiscordMention + " (" + leaderName + ")", true);
            embed.addField("⭐ Solde de l'équipe", String.format("%.2f Étoiles", money), true);

            String status = team.staff() == 1 ? "🛡️ Staff (Membres illimités)" : "⚔️ Joueurs (Limite de 4 membres)";
            embed.addField("📋 Statut", status, true);

            StringBuilder membersList = new StringBuilder();
            for (Player m : members) {
                boolean isLeader = m.uuid().equals(team.leader());
                membersList.append(isLeader ? "👑 " : "👤 ")
                        .append("**").append(m.name()).append("**")
                        .append(" (<@").append(m.discordId()).append(">)\n");
            }

            String maxMembers = team.staff() == 1 ? "∞" : "4";
            embed.addField("👥 Membres (" + members.size() + "/" + maxMembers + ")", membersList.toString(), false);
            embed.setFooter("Cité des Îles • ID : " + team.id());

            hook.sendMessageEmbeds(embed.build()).queue();
        } catch (Exception e) {
            hook.sendMessage("❌ Impossible d'afficher les informations de l'équipe : " + e.getMessage()).queue();
        }
    }

    @Override
    public void onButtonInteraction(ButtonInteractionEvent event) {
        String componentId = event.getComponentId();
        if (!componentId.startsWith("team_invite:")) return;

        String[] parts = componentId.split(":");
        if (parts.length < 5) return;

        String action = parts[1]; // accept or deny
        int teamId;
        try {
            teamId = Integer.parseInt(parts[2]);
        } catch (NumberFormatException e) {
            event.reply("❌ ID de l'équipe invalide.").setEphemeral(true).queue();
            return;
        }
        String targetPlayerUuid = parts[3];
        String leaderDiscordId = parts[4];

        // Vérifier que la personne qui clique est bien l'invité
        Player invitedPlayer;
        try {
            invitedPlayer = api.getPlayer(targetPlayerUuid);
        } catch (Exception e) {
            event.reply("❌ Impossible de trouver ton profil Minecraft lié.").setEphemeral(true).queue();
            return;
        }

        if (!invitedPlayer.discordId().equals(event.getUser().getId())) {
            event.reply("❌ Cette invitation ne t'est pas destinée !").setEphemeral(true).queue();
            return;
        }
        final String invitedPlayerName = invitedPlayer.name();

        // Charger l'équipe
        Team team;
        try {
            team = api.getTeam(teamId);
        } catch (Exception e) {
            event.reply("❌ Cette équipe n'existe plus ou est inaccessible.").setEphemeral(true).queue();
            return;
        }

        if (action.equals("deny")) {
            // Editer le message pour désactiver/enlever les boutons
            event.editMessage("❌ Tu as refusé l'invitation à rejoindre l'équipe **" + team.name() + "**.")
                    .setComponents().queue();

            // Notifier le chef d'équipe
            event.getJDA().retrieveUserById(leaderDiscordId).queue(leader -> {
                leader.openPrivateChannel().queue(c -> c.sendMessage("🔔 **" + invitedPlayerName + "** a refusé ton invitation pour rejoindre l'équipe **" + team.name() + "**.").queue());
            }, err -> {});
            return;
        }

        if (action.equals("accept")) {
            // Vérifier à nouveau si le joueur est déjà dans une équipe
            try {
                // Rafraîchir les infos du joueur
                invitedPlayer = api.getPlayer(targetPlayerUuid);
            } catch (Exception ignored) {}

            if (invitedPlayer.team() != -1) {
                event.editMessage("❌ Tu es déjà dans une équipe. Tu ne peux pas accepter cette invitation.").setComponents().queue();
                return;
            }

            // Vérifier limite membres
            List<Player> members = team.players(api);
            if (team.staff() != 1 && members.size() >= 4) {
                event.editMessage("❌ L'équipe **" + team.name() + "** est déjà complète (limite de 4 membres).").setComponents().queue();
                return;
            }

            try {
                // Ajouter à l'équipe
                api.setPlayerTeam(invitedPlayer.uuid(), team.id());

                event.editMessage("✅ Tu as accepté l'invitation et rejoins l'équipe **" + team.name() + "** !")
                        .setComponents().queue();

                // Notifier le chef
                event.getJDA().retrieveUserById(leaderDiscordId).queue(leader -> {
                    leader.openPrivateChannel().queue(c -> c.sendMessage("🔔 **" + api.getPlayer(targetPlayerUuid).name() + "** a accepté ton invitation et a rejoint l'équipe **" + team.name() + "** !").queue());
                }, err -> {});
            } catch (Exception e) {
                event.reply("❌ Une erreur est survenue lors de l'ajout à l'équipe : " + e.getMessage()).setEphemeral(true).queue();
            }
        }
    }
}
