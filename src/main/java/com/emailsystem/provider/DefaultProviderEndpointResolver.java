package com.emailsystem.provider;

import com.emailsystem.account.Provider;
import org.springframework.stereotype.Component;

@Component
public class DefaultProviderEndpointResolver implements ProviderEndpointResolver {

    @Override
    public MailEndpoints resolve(Provider provider) {
        ProviderEndpoints ep = ProviderEndpoints.forProvider(provider);
        return new MailEndpoints(ep.imapHost(), ep.imapPort(), ep.smtpHost(), ep.smtpPort(), true);
    }
}
