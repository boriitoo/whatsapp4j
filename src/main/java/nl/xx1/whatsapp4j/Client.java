package nl.xx1.whatsapp4j;

import static nl.xx1.whatsapp4j.utils.ExposeFunctionUtil.exposeFunctionIfAbsent;

import com.google.gson.reflect.TypeToken;
import com.microsoft.playwright.*;
import com.microsoft.playwright.options.LoadState;
import java.lang.reflect.Type;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.Getter;
import nl.xx1.whatsapp4j.auth.NoAuth;
import nl.xx1.whatsapp4j.json.GsonProvider;
import nl.xx1.whatsapp4j.model.Chat;
import nl.xx1.whatsapp4j.model.Message;
import nl.xx1.whatsapp4j.utils.JsUtils;
import nl.xx1.whatsapp4j.webcache.WebCache;
import nl.xx1.whatsapp4j.webcache.WebCacheFactory;

public class Client {
    private static final String WHATSAPP_URL = "https://web.whatsapp.com/";

    private final Map<Event, List<ClientEventListener<?>>> listeners = new EnumMap<>(Event.class);

    private String currentIndexHtml = "";

    private BrowserContext browserContext;
    private Page page;

    @Getter
    private final ClientLaunchOptions options;

    public Client() {
        this(ClientLaunchOptions.builder().build());
    }

    public Client(ClientLaunchOptions options) {
        this.options = options;
    }

    public <T> void on(Event event, ClientEventListener<T> listener) {
        listeners.computeIfAbsent(event, k -> new CopyOnWriteArrayList<>()).add(listener);
    }

    public <T> void off(Event event, ClientEventListener<T> listener) {
        List<ClientEventListener<?>> eventListeners = listeners.get(event);
        if (eventListeners != null) eventListeners.remove(listener);
    }

    @SuppressWarnings("unchecked")
    public <T> void fireEvent(Event event, T arg) {
        List<ClientEventListener<?>> eventListeners = listeners.get(event);

        if (eventListeners == null) {
            return;
        }

        for (ClientEventListener<?> listener : eventListeners) {
            ((ClientEventListener<T>) listener).onEvent(arg);
        }
    }

    public void start() {
        options.authStrategy().setup(this);

        if (options.authStrategy() instanceof NoAuth) {
            Browser browser = Playwright.create()
                    .chromium()
                    .launch(new BrowserType.LaunchOptions().setHeadless(options.headless()));
            browserContext = browser.newContext();
        } else {
            Path userDataDir = options.authStrategy().beforeBrowser();
            browserContext = Playwright.create()
                    .chromium()
                    .launchPersistentContext(
                            userDataDir,
                            new BrowserType.LaunchPersistentContextOptions()
                                    .setBypassCSP(true)
                                    .setHeadless(options.headless()));
        }

        page = browserContext.newPage();

        this.options.authStrategy().afterBrowser();
        this.initWebVersionCache();

        page.navigate(WHATSAPP_URL);
        page.waitForLoadState(LoadState.LOAD);

        this.inject();

        this.page.onFrameNavigated(frame -> {
            this.inject();
        });

        while (true) {
            this.page.waitForTimeout(1000);
        }
    }

    private void initWebVersionCache() {
        WebCache webCache = WebCacheFactory.create("local");
        Optional<String> optional = webCache.resolve("1.0.0");

        if (optional.isEmpty()) {
            this.page.onResponse(response -> {
                if (!response.ok() || !response.url().equals(WHATSAPP_URL)) {
                    return;
                }

                currentIndexHtml = response.text();
            });
            return;
        }

        this.page.route(WHATSAPP_URL, route -> {
            route.fulfill(new Route.FulfillOptions()
                    .setBody(optional.get())
                    .setContentType("text/html")
                    .setStatus(200));
        });
    }

    private void inject() {
        this.page.waitForFunction("window.Debug?.VERSION != undefined");

        this.page.evaluate(JsUtils.loadJsFromResources("js/auth_store.js"));

        this.page.waitForFunction("window.AuthStore != undefined");

        if (!isAuthenticated()) {
            exposeFunctionIfAbsent(page, "onQRChangedEvent", arg -> {
                fireEvent(Event.QR_READY, arg);
                return "result";
            });

            this.page.evaluate(JsUtils.loadJsFromResources("js/inject_qr.js"));
        }

        exposeFunctionIfAbsent(page, "onAppStateHasSyncedEvent", arg -> {
            boolean injected = (boolean)
                    page.evaluate("() => typeof window.Store !== 'undefined' && typeof window.W4J !== 'undefined'");

            if (!this.currentIndexHtml.isEmpty()) {
                WebCache webCache = WebCacheFactory.create("local");
                webCache.persist(this.currentIndexHtml, "1.0.0");
            }

            if (!injected) {
                this.page.evaluate(JsUtils.loadJsFromResources("js/store.js"));
                this.page.waitForFunction("window.Store != undefined");
                this.page.evaluate(JsUtils.loadJsFromResources("js/whatsapp4j.js"));
                this.attachEventListeners();
            }

            this.options.authStrategy().onSuccessfulLogin();
            this.fireEvent(Event.READY, null);
            return "result";
        });

        this.page.evaluate(
                """
           () => {
               window.AuthStore.AppState.on('change:hasSynced', () => {
                    window.onAppStateHasSyncedEvent(1);
                });
           }
        """);
    }

    private void attachEventListeners() {
        exposeFunctionIfAbsent(this.page, "onAddMessageEvent", (String arg) -> {
            Type messageType = new TypeToken<Message>() {}.getType();
            Message message = GsonProvider.getGson(this).fromJson(arg, messageType);

            if (!message.isNewMessage()) {
                return null;
            }

            this.fireEvent(Event.MESSAGE_RECEIVED, message);

            return "null";
        });

        this.page.evaluate(JsUtils.loadJsFromResources("js/listeners.js"));
    }

    private boolean isAuthenticated() {
        AppState appState = getAppState();

        while (AppState.getOpeningStates().contains(appState)) {
            appState = getAppState();
        }

        return !AppState.getUnpairedStates().contains(appState);
    }

    public AppState getAppState() {
        try {
            String appState = (String) this.page.evaluate("window.AuthStore.AppState.state");
            return AppState.valueOf(appState);
        } catch (PlaywrightException e) {
            return AppState.UNLAUNCHED;
        }
    }

    public List<Chat> getChats() {
        String json = (String) this.page.evaluate("async () => JSON.stringify(await window.W4J.getChats())");
        Type chatListType = new TypeToken<List<Chat>>() {}.getType();
        return GsonProvider.getGson(this).fromJson(json, chatListType);
    }

    public Optional<Chat> getChatById(String chatId) {
        String json = (String)
                this.page.evaluate("async () => JSON.stringify(await window.W4J.getChat('%s'))".formatted(chatId));
        Type chatType = new TypeToken<Chat>() {}.getType();
        return Optional.of(GsonProvider.getGson(this).fromJson(json, chatType));
    }

    public void sendMessage(String chatId, String message) {
        sendMessage(chatId, message, Map.of());
    }

    public void sendMessage(String chatId, String message, Map<String, Object> options) {
        page.evaluate(
                "async (args) => await window.W4J.sendMessage(args.chatId, args.content, args.options)",
                Map.of("chatId", chatId, "content", message, "options", options));
    }
}
