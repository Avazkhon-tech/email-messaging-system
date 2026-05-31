package com.emailsystem.message;

import com.emailsystem.message.dto.MessageDetailResponse;
import com.emailsystem.message.dto.MessageSummaryResponse;
import com.emailsystem.message.dto.SendMessageRequest;
import com.emailsystem.security.AuthUser;
import com.emailsystem.security.CurrentUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/messages")
public class MessageController {

    private final MessageService messageService;

    @PostMapping("/send")
    public Map<String, Object> send(@CurrentUser AuthUser user,
                                    @Valid @RequestBody SendMessageRequest request) {
        messageService.send(user.id(), request);
        return Map.of(
                "status", "sent",
                "recipients", request.recipients());
    }

    @GetMapping
    public Page<MessageSummaryResponse> inbox(
            @CurrentUser AuthUser user,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20, sort = "receivedAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return messageService.inbox(user.id(), search, pageable);
    }

    @GetMapping("/{id}")
    public MessageDetailResponse detail(@CurrentUser AuthUser user, @PathVariable Long id) {
        return messageService.detail(user.id(), id);
    }

}
