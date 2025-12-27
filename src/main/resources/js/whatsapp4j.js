(async () => {
    console.log("Injecting W4J")

    window.W4J = {};

    window.W4J.getChats = async () => {
        const chats = window.Store.Chat.getModelsArray();
        const chatPromises = chats.map(chat => window.W4J.getChatModel(chat));
        return await Promise.all(chatPromises);
    }

    window.W4J.getChat = async (chatId, asModel = true) => {
        const isChannel = /@\w*newsletter\b/.test(chatId);
        let chatWid;
        try {
            chatWid = window.Store.WidFactory.createWid(chatId);
        } catch (Exception) {
            return null;
        }

        let chat;

        if (isChannel) {
            try {
                chat = window.Store.NewsletterCollection.get(chatId);
                if (!chat) {
                    await window.Store.ChannelUtils.loadNewsletterPreviewChat(chatId);
                    chat = await window.Store.NewsletterCollection.find(chatWid);
                }
            } catch (err) {
                chat = null;
            }
        } else {
            chat = window.Store.Chat.get(chatWid) || (await window.Store.Chat.find(chatWid));
        }

        return asModel ? window.W4J.getChatModel(chat) : chat;
    }

    window.W4J.getChatModel = async (chat) => {
        const model = chat.serialize();

        model.id = model.id._serialized;

        model.formattedTitle = chat.formattedTitle;

        model.isGroup = false;
        model.type = 'private';

        model.pinned = !!model.pin;

        model.isMuted = chat.mute?.expiration !== 0;

        model.timestamp = model.t;

        if (chat.groupMetadata) {
            model.isGroup = true;
            model.groupMetadata = chat.groupMetadata.serialize();
            model.type = 'group';
        }

        delete model.msgs;
        delete model.msgUnsyncedButtonReplyMsgs;
        delete model.unsyncedButtonReplies;

        // console.log(model);

        return model;
    }

    window.W4J.getMessageModel = (message) => {
        const msg = message.serialize();

        msg.id = message.id._serialized;

        msg.isEphemeral = message.isEphemeral;
        msg.isStatusV3 = message.isStatusV3;

        msg.isNewMessage = !!msg.isNewMsg;

        if (msg.buttons) {
            msg.buttons = msg.buttons.serialize();
        }

        if (msg.dynamicReplyButtons) {
            msg.dynamicReplyButtons = JSON.parse(JSON.stringify(msg.dynamicReplyButtons));
        }
        if (msg.replyButtons) {
            msg.replyButtons = JSON.parse(JSON.stringify(msg.replyButtons));
        }

        delete msg.pendingAckUpdate;

        // console.log(msg);

        return JSON.stringify(msg);
    }

    /**
     * Send a message to chat or contact
     *
     * For now, I haven't implemented the options part.
     *
     * @param chatId
     * @param content
     * @param options
     * @returns {Promise<void>}
     */
    window.W4J.sendMessage = async (chatId, content, options = {}) => {
        console.log(`Sending message for ${chatId} with options: ${JSON.stringify(options)}`);
        const chat = await window.W4J.getChat(chatId, false);

        if (!chat) {
            console.log("Chat is null. Returning early.")
            return null;
        }

        let quotedMessageOptions = {};

        if (options.quotedMessageId) {
            let quotedMessage = window.Store.Msg.get(options.quotedMessageId);

            if (quotedMessage) {
                const canReply = window.Store.ReplyUtils
                    ? window.Store.ReplyUtils.canReplyMsg(quotedMessage.unsafe())
                    : quotedMessage.canReply();

                if (canReply) {
                    quotedMessageOptions = quotedMessage.msgContextInfo(chat);
                }
            }

            delete options.ignoreQuoteErrors;
            delete options.quotedMessageId;
        }

        const lidUser = window.Store.User.getMaybeMeLidUser();
        const meUser = window.Store.User.getMaybeMePnUser();
        const newId = await window.Store.MsgKey.newId();
        let from = chat.id.isLid() ? lidUser : meUser;
        let participant;

        if (chat.isGroup) {
            from = chat.groupMetadata && chat.groupMetadata.isLidAddressingMode ? lidUser : meUser;
            participant = window.Store.WidFactory.toUserWid(from);
        }

        const newMsgKey = new window.Store.MsgKey({
            from: from,
            to: chat.id,
            id: newId,
            participant: participant,
            selfDir: 'out',
        });

        const message = {
            id: newMsgKey,
            ack: 0,
            body: content,
            from: from,
            to: chat.id,
            local: true,
            self: 'out',
            t: Number(new Date().getTime() / 1000),
            isNewMsg: true,
            type: 'chat',
            ...quotedMessageOptions
        }

        await window.Store.SendMessage.addAndSendMsgToChat(chat, message);
        await window.Store.HistorySync.sendPeerDataOperationRequest(3, {
            chatId: chat.id
        });

        const newMessage = window.Store.Msg.get(newMsgKey._serialized);
        return newMessage ? window.W4J.getMessageModel(newMessage) : undefined;
    }

    window.W4J.deleteMessage = async (msgId, everyone = false, clearMedia = false) => {
        const msg = window.Store.Msg.get(msgId) || (await window.Store.Msg.getMessagesById([msgId]))?.messages?.[0];
        const chat = window.Store.Chat.get(msg.id.remote) || (await window.Store.Chat.find(msg.id.remote));

        const canRevoke =
            window.Store.MsgActionChecks.canSenderRevokeMsg(msg) || window.Store.MsgActionChecks.canAdminRevokeMsg(msg);

        if (everyone && canRevoke) {
            window.Store.Cmd.sendRevokeMsgs(chat, { list: [msg], type: 'message' }, { clearMedia: clearMedia })
        }

        window.Store.Cmd.sendDeleteMsgs(chat, { list: [msg], type: 'message' }, clearMedia)
    }
})