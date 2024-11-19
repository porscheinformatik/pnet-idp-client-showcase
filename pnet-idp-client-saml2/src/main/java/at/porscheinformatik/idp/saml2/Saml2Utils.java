package at.porscheinformatik.idp.saml2;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.web.util.UriComponentsBuilder;

import jakarta.servlet.http.HttpServletRequest;
import net.shibboleth.utilities.java.support.security.IdentifierGenerationStrategy;
import net.shibboleth.utilities.java.support.security.impl.SecureRandomIdentifierGenerationStrategy;

public class Saml2Utils
{
    static final String RELAY_STATE_PARAM = "RelayState";
    static final String AUTO_GENERATED_RELAY_STATE_FORMAT = "AutoGenerated%s-%s";
    static final Pattern AUTO_GENERATED_RELAY_STATE_PATTERN =
        Pattern.compile(String.format(AUTO_GENERATED_RELAY_STATE_FORMAT, ".{36}", "(.*)"));

    public static final String SUBJECT_ID_NAME = "urn:oasis:names:tc:SAML:attribute:subject-id";
    public static final String PAIRWISE_ID_NAME = "urn:oasis:names:tc:SAML:attribute:pairwise-id";

    public static final Duration CLOCK_SKEW = Duration.ofMinutes(5);

    private static final String AUTHN_REQUEST_ID_ATTR = "poi.saml2.authn_request_id";
    private static final String FORCE_AUTHENTICATION_PARAM = "forceAuthn";
    private static final String NIST_LEVEL_PARAM = "nistLevel";
    private static final String MAX_SESSION_AGE_PARAM = "maxSessionAge";
    private static final String MAX_AGE_MFA_PARAM = "maxAgeMfa";
    private static final String TENANT_PARAM = "tenant";

    //Specification says between 128 and 160 bit are perfect
    private static final IdentifierGenerationStrategy ID_GENERATOR = new SecureRandomIdentifierGenerationStrategy(20);

    /**
     * @return a random indentifier for saml messages
     */
    public static String generateId()
    {
        return ID_GENERATOR.generateIdentifier();
    }

    public static void storeAuthnRequestId(HttpServletRequest request, String id)
    {
        request.getSession().setAttribute(AUTHN_REQUEST_ID_ATTR, id);
    }

    public static Optional<String> retrieveAuthnRequestId(HttpServletRequest request)
    {
        return Optional.ofNullable((String) request.getSession().getAttribute(AUTHN_REQUEST_ID_ATTR));
    }

    public static UriComponentsBuilder forceAuthentication(UriComponentsBuilder uriComponentsBuilder)
    {
        return uriComponentsBuilder.replaceQueryParam(FORCE_AUTHENTICATION_PARAM, true);
    }

    public static boolean isForceAuthentication(HttpServletRequest request)
    {
        return Boolean.parseBoolean(request.getParameter(FORCE_AUTHENTICATION_PARAM));
    }

    public static UriComponentsBuilder maxSessionAge(UriComponentsBuilder uriComponentsBuilder,
        Integer sessionAgeInSeconds)
    {
        return uriComponentsBuilder.replaceQueryParam(MAX_SESSION_AGE_PARAM, sessionAgeInSeconds);
    }

    public static UriComponentsBuilder maxAgeMfa(UriComponentsBuilder uriComponentsBuilder,
        Integer maxAgeInSeconds)
    {
        return uriComponentsBuilder.replaceQueryParam(MAX_AGE_MFA_PARAM, maxAgeInSeconds);
    }

    public static Optional<Integer> retrieveMaxSessionAge(HttpServletRequest request)
    {
        String value = request.getParameter(MAX_SESSION_AGE_PARAM);

        if (value != null)
        {
            return Optional.of(Integer.parseInt(value));
        }

        return Optional.empty();
    }

    public static Optional<Integer> retrieveMaxAgeMfa(HttpServletRequest request)
    {
        String value = request.getParameter(MAX_AGE_MFA_PARAM);

        if (value != null)
        {
            return Optional.of(Integer.parseInt(value));
        }

        return Optional.empty();
    }

    public static UriComponentsBuilder requestTenant(UriComponentsBuilder uriComponentsBuilder, String tenant)
    {
        return uriComponentsBuilder.replaceQueryParam(TENANT_PARAM, tenant);
    }

    public static Optional<String> retrieveTenant(HttpServletRequest request)
    {
        String value = request.getParameter(TENANT_PARAM);

        if (value != null && !value.isEmpty())
        {
            return Optional.of(value);
        }

        return Optional.empty();
    }

    public static UriComponentsBuilder requestNistAuthenticationLevel(UriComponentsBuilder uriComponentsBuilder,
        int nistLevel)
    {
        List<AuthnContextClass> supportedValues = AuthnContextClass.getAsLeastAsStrongAs(nistLevel);

        if (supportedValues.isEmpty())
        {
            int maxValue =
                supportedValues.stream().map(AuthnContextClass::getNistLevel).max(Integer::compare).orElse(0);

            throw new IllegalArgumentException(
                String.format("Nist level %s not supported. Please use a lower or equals to %s", nistLevel, maxValue));
        }

        return uriComponentsBuilder.replaceQueryParam(NIST_LEVEL_PARAM, nistLevel);
    }

    public static Optional<Integer> getRequestedNistAuthenticationLevel(HttpServletRequest request)
    {
        String value = request.getParameter(NIST_LEVEL_PARAM);

        if (value != null)
        {
            return Optional.of(Integer.parseInt(value));
        }

        return Optional.empty();
    }

    public static UriComponentsBuilder setRelayState(UriComponentsBuilder uriComponentsBuilder, String relayState)
    {
        return uriComponentsBuilder.replaceQueryParam(RELAY_STATE_PARAM, relayState);
    }

    public static Optional<String> getRelayState(HttpServletRequest request)
    {
        return Optional.ofNullable(request.getParameter(RELAY_STATE_PARAM)).map(rs -> {
            Matcher matcher = AUTO_GENERATED_RELAY_STATE_PATTERN.matcher(rs);
            return matcher.matches() ? matcher.group(1) : rs;
        }).filter(rs -> !rs.isEmpty());
    }

    /**
     * Removes all SAML Processing related parameters from the query part of the given url, if any.
     *
     * @param url the url to sanitize
     * @return the sanitized url
     */
    public static String sanitizeUrl(String url)
    {
        if (!url.contains("?"))
        {
            return url;
        }

        return UriComponentsBuilder
            .fromUriString(url)
            .replaceQueryParam(FORCE_AUTHENTICATION_PARAM)
            .replaceQueryParam(MAX_SESSION_AGE_PARAM)
            .replaceQueryParam(MAX_AGE_MFA_PARAM)
            .replaceQueryParam(TENANT_PARAM)
            .replaceQueryParam(NIST_LEVEL_PARAM)
            .replaceQueryParam(RELAY_STATE_PARAM)
            .toUriString();
    }
}
