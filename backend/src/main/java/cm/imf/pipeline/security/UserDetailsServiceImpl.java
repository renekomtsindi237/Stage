package cm.imf.pipeline.security;

import cm.imf.pipeline.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        // Message générique — ne pas révéler si le username existe ou non (user enumeration)
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Identifiants incorrects"));
    }
}
