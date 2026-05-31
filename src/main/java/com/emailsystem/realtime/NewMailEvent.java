package com.emailsystem.realtime;

import com.emailsystem.message.dto.MessageSummaryResponse;

import java.util.List;

public record NewMailEvent(
        Long userId,
        Long accountId,
        List<MessageSummaryResponse> newMessages
) {
}
