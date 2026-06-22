package io.livelattice.core.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.livelattice.core.exception.ConflictException;
import io.livelattice.core.model.entity.User;
import io.livelattice.core.repository.UserRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Test
    void provision_shouldCreateUserFromClaims() {
        UserService service = new UserService(userRepository);
        when(userRepository.findByExternalSubject("sub-1")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        User user = service.provision(new UserClaims("sub-1", "user@example.com", "User One"));

        assertEquals("sub-1", user.getExternalSubject());
        assertEquals("user@example.com", user.getEmail());
        assertEquals("User One", user.getDisplayName());
    }

    @Test
    void provision_shouldUpdateExistingUser() {
        UserService service = new UserService(userRepository);
        User user = new User("sub-1", "old@example.com", "Old Name");
        when(userRepository.findByExternalSubject("sub-1")).thenReturn(Optional.of(user));
        when(userRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.provision(new UserClaims("sub-1", "new@example.com", "New Name"));

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertEquals("new@example.com", captor.getValue().getEmail());
        assertEquals("New Name", captor.getValue().getDisplayName());
    }

    @Test
    void provision_shouldRejectEmailOwnedByDifferentSubject() {
        UserService service = new UserService(userRepository);
        when(userRepository.findByExternalSubject("sub-1")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(new User("sub-2", "user@example.com", "Other")));

        assertThrows(ConflictException.class, () -> service.provision(new UserClaims("sub-1", "user@example.com", "User One")));
    }
}
