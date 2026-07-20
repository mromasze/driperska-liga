package pl.romcio.driperska.common.security;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import pl.romcio.driperska.account.domain.Account;

/** Adapts an {@link Account} to Spring Security's {@link UserDetails}. */
public class AppUserDetails implements UserDetails {

    private final UUID accountId;
    private final String username;
    private final String passwordHash;
    private final boolean enabled;
    private final List<GrantedAuthority> authorities;

    public AppUserDetails(Account account) {
        this.accountId = account.getId();
        this.username = account.getUsername();
        this.passwordHash = account.getPasswordHash();
        this.enabled = account.isEnabled();
        this.authorities = List.of(new SimpleGrantedAuthority(account.getRole().authority()));
    }

    public UUID getAccountId() {
        return accountId;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
