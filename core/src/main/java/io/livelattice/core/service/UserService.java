package io.livelattice.core.service;

import io.livelattice.core.exception.ConflictException;
import io.livelattice.core.exception.NotFoundException;
import io.livelattice.core.model.entity.User;
import io.livelattice.core.repository.UserRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User provision(UserClaims claims) {
        return userRepository.findByExternalSubject(claims.subject())
            .map(user -> update(user, claims))
            .orElseGet(() -> create(claims));
    }

    @Transactional(readOnly = true)
    public User requireBySubject(String subject) {
        return userRepository.findByExternalSubject(subject)
            .orElseThrow(() -> new NotFoundException("User not found: " + subject));
    }

    private User create(UserClaims claims) {
        userRepository.findByEmail(claims.email()).ifPresent(user -> {
            throw new ConflictException("Email already belongs to another identity: " + claims.email());
        });
        return userRepository.save(new User(claims.subject(), claims.email(), claims.displayName()));
    }

    private User update(User user, UserClaims claims) {
        user.setEmail(claims.email());
        user.setDisplayName(claims.displayName());
        user.setStatus("ACTIVE");
        user.setUpdatedAt(Instant.now());
        return userRepository.save(user);
    }
}
