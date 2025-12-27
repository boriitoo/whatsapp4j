# whatsapp4j

**This library is not affiliated with Whatsapp LLC in any way.**

**whatsapp4j** is an open-source project with the goal of porting the core features and API of [whatsapp-web.js](https://github.com/pedroslopez/whatsapp-web.js/) from JavaScript to Java.

## Project Goal

This project aims to provide Java developers with a similar experience and functionality as whatsapp-web.js, allowing easy interaction with WhatsApp Web through a Java library. The goal is to offer:

- Familiar API inspired by whatsapp-web.js
- Core features for interacting with WhatsApp Web
- An open and community-driven approach

## Example


```java
public class Main {
    public static void main(String[] args) throws InterruptedException {
        Client client = new Client(ClientLaunchOptions.builder()
                .authStrategy(new LocalAuth())
                .build());

        client.on(Event.QR_READY, System.out::println);

        client.on(Event.READY, (obj) -> {
            System.out.println("Client is ready!");
        });

        client.on(Event.MESSAGE_RECEIVED, (Message message) -> {
            if (message.getBody().equalsIgnoreCase("!ping")) {
                message.reply("Pong!");
            }
        });

        client.start();
    }
}
```

## Status

**In early development.**

## Links

- Original project: [whatsapp-web.js](https://github.com/pedroslopez/whatsapp-web.js/)
