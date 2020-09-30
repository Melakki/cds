/*
 * Author: {Ruben Veiga}
 * Contributor: {Liscuate}
 */

package com.misterveiga.rdss.listeners;

import java.time.Instant;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.stereotype.Component;

import com.besaba.revonline.pastebinapi.Pastebin;
import com.besaba.revonline.pastebinapi.impl.factory.PastebinFactory;
import com.besaba.revonline.pastebinapi.paste.Paste;
import com.besaba.revonline.pastebinapi.paste.PasteExpire;
import com.besaba.revonline.pastebinapi.paste.PasteVisiblity;
import com.misterveiga.rdss.utils.Properties;
import com.misterveiga.rdss.utils.RoleUtils;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.Message.MentionType;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.entities.MessageReaction.ReactionEmote;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

/**
 * The listener interface for receiving reaction events. The class that is
 * interested in processing a reaction event implements this interface, and the
 * object created with that class is registered with a component using the
 * component's <code>addReactionListener<code> method. When the reaction event
 * occurs, that object's appropriate method is invoked.
 *
 * @see ReactionEvent
 */
@Component
@PropertySource("classpath:application.properties")
public class ReactionListener extends ListenerAdapter {

	/** The pastebin api key. */
	@Value("${pastebin.apikey}")
	String pastebinApiKey;

	/** The pastebin factory. */
	@Autowired
	PastebinFactory pastebinFactory;

	/** The log. */
	private static Logger log = LoggerFactory.getLogger(ReactionListener.class);

	/** The Constant ID_REACTION_QM_30. */
	private static final String ID_REACTION_QM_30 = "760204798984454175"; // 30 minute quick-mute emoji

	/** The Constant ID_REACTION_QM_60. */
	private static final String ID_REACTION_QM_60 = "452813334429827072"; // 60 minute quick-mute emoji

	/** The Constant COMMAND_MUTE_USER_DEFAULT. */
	private static final String COMMAND_MUTE_USER_DEFAULT = ";mute %s %s %s";

	/** The Constant COMMAND_CLEAN_MESSAGES_USER. */
	private static final String COMMAND_CLEAN_MESSAGES_USER = ";clean user %s";

	/** The Constant COMMAND_REASON. */
	private static final String COMMAND_REASON = "(By %s) Message Evidence: %s";

	/**
	 * On message reaction add.
	 *
	 * @param event the event
	 */
	@Override
	public void onMessageReactionAdd(final MessageReactionAddEvent event) {
		final TextChannel commandChannel = event.getGuild().getTextChannelById(Properties.CHANNEL_COMMANDS_ID);

		final MessageReaction reaction = event.getReaction();
		final Member reactee = event.getMember();
		final Long messageId = event.getMessageIdLong();
		final MessageChannel channel = event.getTextChannel();

		final ReactionEmote emote = reaction.getReactionEmote();
		final Message message = event.getChannel().retrieveMessageById(messageId).complete(); // (reaction.getMessageId()).complete();
		final Member messageAuthor = message.getMember();

		if (RoleUtils.findRole(event.getMember(), RoleUtils.ROLE_SERVER_MANAGER) != null
				|| RoleUtils.findRole(event.getMember(), RoleUtils.ROLE_COMMUNITY_SUPERVISOR) != null) {

			switch (emote.getId()) {
			case ID_REACTION_QM_30:
				log.info("[Reaction Command] 30m Quick-Mute executed by {} on {} for Message\"{}\"", reactee,
						messageAuthor, message);
				muteUser(reactee, messageAuthor, "30m", message, commandChannel);
				clearMessages(messageAuthor, channel);
				break;

			case ID_REACTION_QM_60:
				log.info("[Reaction Command] 1h Quick-Mute executed by {} on {} for Message\"{}\"", reactee,
						messageAuthor, message);
				muteUser(reactee, messageAuthor, "1h", message, commandChannel);
				clearMessages(messageAuthor, channel);
				break;
			}
		}

	}

	/**
	 * Mute user.
	 *
	 * @param reactee        the reactee
	 * @param messageAuthor  the message author
	 * @param muteDuration   the mute duration
	 * @param message        the message
	 * @param commandChannel the command channel
	 */
	private void muteUser(final Member reactee, final Member messageAuthor, final String muteDuration,
			final Message message, final TextChannel commandChannel) {

		final String messageContent = message.getContentStripped();

		if (messageContent.replace("\n", " ").length() < 120) {
			commandChannel
					.sendMessage(String.format(COMMAND_MUTE_USER_DEFAULT, messageAuthor.getId(), muteDuration,
							String.format(COMMAND_REASON, reactee.getEffectiveName(),
									messageContent.replace("\n", " "))))
					.allowedMentions(new ArrayList<MentionType>()).queue();
		} else {
			final Pastebin pastebin = pastebinFactory.createPastebin(pastebinApiKey);
			final String pasteTitle = new StringBuilder().append("Evidence against ")
					.append(messageAuthor.getEffectiveName()).append(" (").append(messageAuthor.getId()).append(")")
					.append(" on ").append(Instant.now()).toString();
			final Paste paste = pastebinFactory.createPaste().setTitle(pasteTitle).setRaw(messageContent)
					.setMachineFriendlyLanguage("text").setExpire(PasteExpire.Never).setVisiblity(PasteVisiblity.Public)
					.build();
			final String pasteKey = pastebin.post(paste).get();

			log.info(String.format("Pastebin \"%s\" posted to %s", pasteTitle, pasteKey));

			commandChannel
					.sendMessage(String.format(COMMAND_MUTE_USER_DEFAULT, messageAuthor.getId(), muteDuration,
							String.format(COMMAND_REASON, reactee.getEffectiveName(),
									messageContent.replace("\n", " ").substring(0, 17) + "... Pastebin: " + pasteKey)))
					.allowedMentions(new ArrayList<MentionType>()).queue();
		}

	}

	/**
	 * Clear messages.
	 *
	 * @param messageAuthor the message author
	 * @param channel       the channel
	 */

	private void clearMessages(final Member messageAuthor, final MessageChannel channel) {
		channel.sendMessage(String.format(COMMAND_CLEAN_MESSAGES_USER, messageAuthor.getId()))
				.queue(message -> message.delete().queueAfter(500, TimeUnit.MILLISECONDS));
		log.info(String.format(COMMAND_CLEAN_MESSAGES_USER, messageAuthor.getId()));
	}
}