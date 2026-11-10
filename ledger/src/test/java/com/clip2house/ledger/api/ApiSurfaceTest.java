package com.clip2house.ledger.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.clip2house.ledger.support.IntegrationTestBase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

class ApiSurfaceTest extends IntegrationTestBase {

    private static final Pattern VALUE_LIKE_KEY = Pattern.compile("(?i:value|price|worth|amount)|Ratio|^ratio");

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void 게스트는_가입_없이_시작하고_토큰으로_내_정보를_읽는다() throws Exception {
        String created = mockMvc.perform(post("/api/guests"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(created).get("token").asText();

        String me = mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode meNode = objectMapper.readTree(me);
        assertThat(meNode.get("holdings")).hasSize(1);
        assertThat(meNode.get("holdings").get(0).get("itemName").asText()).isEqualTo("빨간 클립");
    }

    @Test
    void 토큰_없는_보호_요청은_거부된다() throws Exception {
        mockMvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void 피드와_리더보드_어떤_응답에도_참조_가치_필드가_존재하지_않는다() throws Exception {
        newPersona();
        newListing("gaming-monitor");
        newGuest();

        for (String path : List.of("/api/feed", "/api/leaderboard?board=VALUE", "/api/leaderboard?board=CLEAR_ORDER",
                "/api/leaderboard?board=CLEAR_TIME", "/api/leaderboard?board=CLEAR_TRADES")) {
            String body = mockMvc.perform(get(path))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            assertThat(collectKeys(objectMapper.readTree(body)))
                    .as("응답 %s 의 JSON 키", path)
                    .noneMatch(key -> VALUE_LIKE_KEY.matcher(key).find());
        }
    }

    @Test
    void 내_정보와_협상_응답에도_가치_필드가_없다() throws Exception {
        String created = mockMvc.perform(post("/api/guests"))
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(created).get("token").asText();

        String me = mockMvc.perform(get("/api/me").header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getContentAsString();
        assertThat(collectKeys(objectMapper.readTree(me)))
                .noneMatch(key -> VALUE_LIKE_KEY.matcher(key).find());
    }

    @Test
    void 내부_API는_공유_비밀_없이는_존재하지_않는_것처럼_보인다() throws Exception {
        mockMvc.perform(get("/internal/negotiations/1/context"))
                .andExpect(status().isNotFound());
    }

    private static List<String> collectKeys(JsonNode node) {
        List<String> keys = new ArrayList<>();
        collect(node, keys);
        return keys;
    }

    private static void collect(JsonNode node, List<String> keys) {
        if (node.isObject()) {
            Iterator<String> names = node.fieldNames();
            while (names.hasNext()) {
                String name = names.next();
                keys.add(name);
                collect(node.get(name), keys);
            }
        } else if (node.isArray()) {
            node.forEach(child -> collect(child, keys));
        }
    }
}
