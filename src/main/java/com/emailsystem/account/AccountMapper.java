package com.emailsystem.account;

import com.emailsystem.account.dto.AccountResponse;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface AccountMapper {

    AccountResponse toResponse(EmailAccount account);
}
