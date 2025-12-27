package nl.xx1.whatsapp4j.model;

import com.google.gson.annotations.Expose;
import java.util.HashMap;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import nl.xx1.whatsapp4j.Client;

@NoArgsConstructor(force = true)
@AllArgsConstructor
@ToString
@Getter
public class Message {

    @Expose
    private String id;

    @Expose
    private int ack;

    @Expose
    private String author;

    @Expose
    private String body;

    @Expose
    private boolean broadcast;

    @Expose
    private String deviceType;

    @Expose
    private String duration;

    @Expose
    private int forwardingScore;

    @Expose
    private String from;

    @Expose
    private String to;

    @Expose
    private boolean fromMe;

    @Expose
    private boolean hasMedia;

    @Expose
    private boolean hasQuotedMsg;

    @Expose
    private boolean hasReaction;

    @Expose
    private boolean isEphemeral;

    @Expose
    private boolean isForwarded;

    @Expose
    private boolean isGif;

    @Expose
    private boolean isNewMessage;

    @Expose
    private MessageType type;

    protected final Client client;

    private String getChatId() {
        if (isFromMe()) {
            return to;
        }

        return from;
    }

    public void reply(String content) {
        if (this.client == null) {
            return;
        }

        Map<String, Object> options = new HashMap<>();
        options.put("quotedMessageId", this.id);

        this.client.sendMessage(getChatId(), content, options);
    }
}
