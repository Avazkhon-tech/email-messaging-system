package com.emailsystem.message;

import com.emailsystem.message.dto.MessageDetailResponse;
import com.emailsystem.message.dto.MessageSummaryResponse;
import com.emailsystem.provider.FetchedMessage;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface MessageMapper {

    MessageSummaryResponse toSummary(EmailMessage message);

    MessageDetailResponse toDetail(EmailMessage message);

    @Mapping(target = "externalMessageId", source = "fm.externalId")
    @Mapping(target = "accountId", source = "accountId")
    @Mapping(target = "readStatus", constant = "false")
    @Mapping(target = "id", ignore = true)
    EmailMessage toEntity(FetchedMessage fm, Long accountId);
}
