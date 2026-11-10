package com.clip2house.ledger.auth;

import com.clip2house.ledger.catalog.Item;
import com.clip2house.ledger.catalog.ItemRepository;
import com.clip2house.ledger.common.ApiException;
import com.clip2house.ledger.market.Holding;
import com.clip2house.ledger.market.HoldingRepository;
import com.clip2house.ledger.season.SeasonService;
import java.security.SecureRandom;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    static final String STARTER_CLIP_KEY = "starter-clip";

    private static final String[] NICK_HEADS = {
            "빠른", "느긋한", "야무진", "수줍은", "대담한", "꼼꼼한", "허둥대는", "명랑한", "진지한", "엉뚱한"
    };
    private static final String[] NICK_TAILS = {
            "수달", "너구리", "고슴도치", "두더지", "물개", "청설모", "직박구리", "오소리", "돌고래", "올빼미"
    };

    private final PlayerRepository players;
    private final HoldingRepository holdings;
    private final ItemRepository items;
    private final TokenService tokenService;
    private final SeasonService seasonService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
    private final SecureRandom random = new SecureRandom();

    public AuthService(PlayerRepository players, HoldingRepository holdings, ItemRepository items,
                       TokenService tokenService, SeasonService seasonService) {
        this.players = players;
        this.holdings = holdings;
        this.items = items;
        this.tokenService = tokenService;
        this.seasonService = seasonService;
    }

    public record GuestCreated(Player player, TokenService.IssuedToken token) {}

    @Transactional
    public GuestCreated createGuest() {
        Player player = players.save(new Player(randomNickname()));
        Item clip = items.findByNameKey(STARTER_CLIP_KEY)
                .orElseThrow(() -> new IllegalStateException("시드 클립 아이템이 없습니다. 마이그레이션을 확인하세요."));
        holdings.save(new Holding(player.getId(), clip.getId()));
        Long seasonId = seasonService.current().getId();
        var participant = seasonService.ensureParticipant(seasonId, player.getId());
        participant.updateInventoryValue(clip.effectiveValue());
        return new GuestCreated(player, tokenService.issue(player.getId(), AccessToken.Kind.GUEST));
    }

    @Transactional
    public Player promote(Long playerId, String email, String rawPassword) {
        Player player = players.findById(playerId)
                .orElseThrow(() -> ApiException.notFound("PLAYER_NOT_FOUND", "플레이어가 없습니다."));
        if (player.isPromoted()) {
            throw ApiException.conflict("ALREADY_PROMOTED", "이미 계정으로 승격된 플레이어입니다.");
        }
        if (players.existsByEmail(email)) {
            throw ApiException.conflict("EMAIL_IN_USE", "이미 사용 중인 이메일입니다.");
        }
        player.promote(email, passwordEncoder.encode(rawPassword));
        return player;
    }

    @Transactional
    public TokenService.IssuedToken login(String email, String rawPassword) {
        Player player = players.findByEmail(email)
                .filter(p -> p.getPasswordHash() != null && passwordEncoder.matches(rawPassword, p.getPasswordHash()))
                .orElseThrow(() -> ApiException.unauthorized("BAD_CREDENTIALS", "이메일 또는 비밀번호가 올바르지 않습니다."));
        return tokenService.issue(player.getId(), AccessToken.Kind.ACCOUNT);
    }

    private String randomNickname() {
        return NICK_HEADS[random.nextInt(NICK_HEADS.length)]
                + NICK_TAILS[random.nextInt(NICK_TAILS.length)]
                + (1000 + random.nextInt(9000));
    }
}
