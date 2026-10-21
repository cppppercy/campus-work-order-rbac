package edu.campus.rbac.infrastructure;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;

/** 只有持有此注册表的认证组件能签发可信会话；UI 中的用户信息不用于授权。 */
public final class SessionRegistry {
    private final ConcurrentHashMap<String, Long> sessions = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();

    public String issue(long userId) {
        byte[] bytes = new byte[32];
        String token;
        do {
            random.nextBytes(bytes);
            token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        } while (sessions.putIfAbsent(token, userId) != null);
        return token;
    }

    public Long resolve(String token) { return token == null ? null : sessions.get(token); }
    public void revoke(String token) { if (token != null) sessions.remove(token); }
    public void revokeUser(long userId) { sessions.entrySet().removeIf(e -> e.getValue() == userId); }
}
