package com.emailsystem.integration;

import com.emailsystem.account.EmailAccount;
import com.emailsystem.account.EmailAccountRepository;
import com.emailsystem.message.EmailMessage;
import com.emailsystem.message.EmailMessageRepository;
import com.emailsystem.provider.EmailProviderClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class ApiFlowTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);

        registry.add("app.sync.initial-delay-ms", () -> "600000");
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired EmailAccountRepository accountRepository;
    @Autowired EmailMessageRepository messageRepository;

    @MockBean EmailProviderClient providerClient;

    @Test
    void fullFlow_registerConnectSyncReadAndIsolation() throws Exception {

        String token = register("Alice", "alice@example.com", "password123");

        String accountJson = mockMvc.perform(post("/api/accounts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"provider":"GMAIL","emailAddress":"alice@gmail.com","appPassword":"secret"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn().getResponse().getContentAsString();
        long accountId = objectMapper.readTree(accountJson).get("id").asLong();

        long messageId = messageRepository.save(EmailMessage.builder()
                .externalMessageId("<msg-1@server>")
                .accountId(accountId)
                .sender("bob@example.com")
                .recipients("alice@gmail.com")
                .subject("Project update")
                .body("Here is the latest")
                .preview("Here is the latest")
                .receivedAt(Instant.now())
                .readStatus(false)
                .build()).getId();

        mockMvc.perform(get("/api/messages").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].subject").value("Project update"))
                .andExpect(jsonPath("$.content[0].readStatus").value(false));

        mockMvc.perform(get("/api/messages?search=bob").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));

        mockMvc.perform(get("/api/messages/" + messageId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readStatus").value(true));

        String otherToken = register("Eve", "eve@example.com", "password123");
        mockMvc.perform(get("/api/messages/" + messageId).header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/messages").header("Authorization", "Bearer " + otherToken))
                .andExpect(jsonPath("$.totalElements").value(0));

        mockMvc.perform(post("/api/messages/send")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountId\":" + accountId
                                + ",\"recipients\":[\"not-an-email\"],\"subject\":\"x\",\"body\":\"y\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/messages/send")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountId\":" + accountId
                                + ",\"recipients\":[\"bob@example.com\"],\"subject\":\"Hi\",\"body\":\"Body\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("sent"));

        mockMvc.perform(get("/api/messages")).andExpect(status().isUnauthorized());

        EmailAccount stored = accountRepository.findById(accountId).orElseThrow();
        org.assertj.core.api.Assertions.assertThat(stored.getEmailAddress()).isEqualTo("alice@gmail.com");
    }

    private String register(String name, String email, String password) throws Exception {
        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterPayload(name, email, password))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        JsonNode node = objectMapper.readTree(response);
        return node.get("token").asText();
    }

    private record RegisterPayload(String fullname, String email, String password) {
    }
}
