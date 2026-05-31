package com.emailsystem.provider;

import com.emailsystem.account.Provider;

public interface ProviderEndpointResolver {
    MailEndpoints resolve(Provider provider);
}
