package com.clip2house.ledger.persona;

import static org.assertj.core.api.Assertions.assertThat;

import com.clip2house.ledger.auth.Player;
import com.clip2house.ledger.market.Listing;
import com.clip2house.ledger.negotiation.Negotiation;
import com.clip2house.ledger.support.IntegrationTestBase;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class PersonaResponseTimeTest extends IntegrationTestBase {

    @Autowired JdbcTemplate jdbc;

    private void insertMessage(Long negotiationId, String sender, Instant at) {
        jdbc.update("INSERT INTO chat_messages (negotiation_id, sender, content, created_at) VALUES (?, ?, ?, ?)",
                negotiationId, sender, "메시지", java.sql.Timestamp.from(at));
    }

    private Negotiation negotiationFor(Persona persona) {
        Listing listing = listings.save(new Listing(seedItem("used-scooter").getId(), persona.getId(),
                Listing.Origin.FAUCET, Instant.now().plusSeconds(3600)));
        Player player = newGuest();
        return openNegotiation(player.getId(), listing);
    }

    @Test
    void 플레이어_발화_다음_페르소나_응답_간격의_평균을_초로_집계한다() {
        Persona persona = newPersona();
        Negotiation n = negotiationFor(persona);
        Instant t0 = Instant.now().minusSeconds(600);

        insertMessage(n.getId(), "PLAYER", t0);
        insertMessage(n.getId(), "PERSONA", t0.plusSeconds(40));
        insertMessage(n.getId(), "PLAYER", t0.plusSeconds(100));
        insertMessage(n.getId(), "PERSONA", t0.plusSeconds(120));

        personas.refreshAvgResponseSeconds(Instant.now().minusSeconds(3600));

        assertThat(personas.findById(persona.getId()).orElseThrow().getAvgResponseSeconds()).isEqualTo(30);
    }

    @Test
    void 연속된_플레이어_발화는_마지막_발화만_응답과_짝이_된다() {
        Persona persona = newPersona();
        Negotiation n = negotiationFor(persona);
        Instant t0 = Instant.now().minusSeconds(600);

        insertMessage(n.getId(), "PLAYER", t0);
        insertMessage(n.getId(), "PLAYER", t0.plusSeconds(50));
        insertMessage(n.getId(), "PERSONA", t0.plusSeconds(60));

        personas.refreshAvgResponseSeconds(Instant.now().minusSeconds(3600));

        assertThat(personas.findById(persona.getId()).orElseThrow().getAvgResponseSeconds()).isEqualTo(10);
    }

    @Test
    void lookback_창_밖의_대화는_평균에_반영하지_않는다() {
        Persona persona = newPersona();
        Negotiation n = negotiationFor(persona);
        Instant old = Instant.now().minusSeconds(10_000);
        insertMessage(n.getId(), "PLAYER", old);
        insertMessage(n.getId(), "PERSONA", old.plusSeconds(80));

        personas.refreshAvgResponseSeconds(Instant.now().minusSeconds(3600));

        assertThat(personas.findById(persona.getId()).orElseThrow().getAvgResponseSeconds()).isNull();
    }

    @Test
    void 응답이_없는_페르소나는_null로_남는다() {
        Persona persona = newPersona();
        Negotiation n = negotiationFor(persona);
        insertMessage(n.getId(), "PLAYER", Instant.now().minusSeconds(100));

        personas.refreshAvgResponseSeconds(Instant.now().minusSeconds(3600));

        assertThat(personas.findById(persona.getId()).orElseThrow().getAvgResponseSeconds()).isNull();
    }
}
