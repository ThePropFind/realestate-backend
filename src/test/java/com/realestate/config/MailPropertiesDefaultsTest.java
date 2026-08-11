package com.realestate.config;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contract test over the raw application.properties mail defaults.
 *
 * <p>SendGridEmailTransportTest sets from/from-name on AppProperties by hand, so it
 * asserts nothing about what the shipped config actually resolves to. That gap let
 * {@code app.mail.from-name} sit at the pre-brand value "RealEstate Portal" — the
 * sender display name every recipient sees on OTP and inquiry mail — while the unit
 * test happily asserted "PropFind". This test reads the real file so the default
 * can't drift away from the brand again.
 */
class MailPropertiesDefaultsTest {

    private static Properties load() throws Exception {
        Properties props = new Properties();
        try (InputStream in = MailPropertiesDefaultsTest.class
                .getClassLoader().getResourceAsStream("application.properties")) {
            assertThat(in).as("application.properties on the test classpath").isNotNull();
            props.load(in);
        }
        return props;
    }

    @Test
    void fromName_defaultsToTheBrand_andIsEnvOverridable() throws Exception {
        String value = load().getProperty("app.mail.from-name");

        assertThat(value)
            .as("app.mail.from-name must be env-overridable with a brand default")
            .isEqualTo("${MAIL_FROM_NAME:PropFind}");
    }

    @Test
    void fromAddress_isEnvOverridable_andDefaultsToTheOwnedDomain() throws Exception {
        String value = load().getProperty("app.mail.from");

        assertThat(value).startsWith("${MAIL_FROM:");
        // Must default to a domain we actually own and can SPF/DKIM-authenticate,
        // never a placeholder — an unauthenticated From is what lands mail in spam.
        assertThat(value).contains("@thepropfind.com");
        assertThat(value).doesNotContain("yoursite.com");
    }

    @Test
    void apiKey_fallsBackToTheSmtpPassword() throws Exception {
        // Prod sends over the SendGrid HTTPS API (Render blocks SMTP ports) but reuses
        // the same credential, so one env var configures both paths.
        assertThat(load().getProperty("app.mail.api-key"))
            .isEqualTo("${SENDGRID_API_KEY:${MAIL_PASSWORD:}}");
    }
}
