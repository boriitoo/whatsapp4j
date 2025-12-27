package nl.xx1.whatsapp4j;

import nl.xx1.whatsapp4j.auth.AuthStrategy;
import nl.xx1.whatsapp4j.auth.NoAuth;

public record ClientLaunchOptions(AuthStrategy authStrategy, String session, boolean headless) {
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private boolean headless = false;
        private String session = "session";
        private AuthStrategy authStrategy = new NoAuth();

        public Builder() {}

        public Builder authStrategy(AuthStrategy authStrategy) {
            this.authStrategy = authStrategy;
            return this;
        }

        public Builder session(String session) {
            this.session = session;
            return this;
        }

        public Builder headless(boolean headless) {
            this.headless = headless;
            return this;
        }

        public ClientLaunchOptions build() {
            return new ClientLaunchOptions(authStrategy, session, headless);
        }
    }
}
