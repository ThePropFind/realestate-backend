package com.realestate.service;

import com.realestate.dto.user.UserDtos.MeResponse;
import com.realestate.dto.user.UserDtos.UpdateMeRequest;
import com.realestate.entity.User;
import com.realestate.exception.BadRequestException;
import com.realestate.exception.ConflictException;
import com.realestate.exception.ResourceNotFoundException;
import com.realestate.repository.PropertyRepository;
import com.realestate.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Account self-service — the Settings screen's two endpoints.
 *
 * The behaviour worth pinning is the PATCH semantics: a null field means
 * "leave it alone". Getting that wrong is what made the web edit form null
 * twenty columns on every save (regression #88), and a settings endpoint that
 * repeated the mistake would wipe a user's phone number every time they
 * toggled a notification switch.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceMeTest {

    @Mock private UserRepository     userRepository;
    @Mock private PropertyRepository propertyRepository;
    @Mock private PropertyService    propertyService;

    @InjectMocks private UserService userService;

    private static final String EMAIL = "seller@propfind.in";

    private User existing() {
        return User.builder()
            .id(UUID.randomUUID())
            .name("Abishek")
            .email(EMAIL)
            .phone("9876543210")
            .role(User.Role.SELLER)
            .verified(true)
            .active(true)
            .notifyEmailInquiries(true)
            .build();
    }

    private void stubSaveEchoes() {
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void getMe_returnsTheOwnAccountIncludingContactDetails() {
        User user = existing();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        MeResponse me = userService.getMe(EMAIL);

        assertThat(me.getEmail()).isEqualTo(EMAIL);
        assertThat(me.getPhone()).isEqualTo("9876543210");
        assertThat(me.getRole()).isEqualTo("SELLER");
        assertThat(me.isNotifyEmailInquiries()).isTrue();
    }

    @Test
    void getMe_forADeactivatedAccount_is404() {
        User banned = existing();
        banned.setActive(false);
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(banned));

        assertThatThrownBy(() -> userService.getMe(EMAIL))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    /** The core PATCH guarantee: absent fields are untouched, not nulled. */
    @Test
    void updateMe_withOnlyTheToggle_leavesNameAndPhoneAlone() {
        User user = existing();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        stubSaveEchoes();

        UpdateMeRequest req = new UpdateMeRequest();
        req.setNotifyEmailInquiries(false);

        MeResponse me = userService.updateMe(EMAIL, req);

        assertThat(me.isNotifyEmailInquiries()).isFalse();
        assertThat(me.getName()).isEqualTo("Abishek");
        assertThat(me.getPhone()).isEqualTo("9876543210");
    }

    @Test
    void updateMe_trimsTheName() {
        User user = existing();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        stubSaveEchoes();

        UpdateMeRequest req = new UpdateMeRequest();
        req.setName("  Abishek Senthilkumar  ");

        assertThat(userService.updateMe(EMAIL, req).getName()).isEqualTo("Abishek Senthilkumar");
    }

    /**
     * @Size(min = 1) passes on "   " — the bean validator counts characters, not
     * content — so the service has to reject a whitespace-only name itself.
     */
    @Test
    void updateMe_withABlankName_is400() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(existing()));

        UpdateMeRequest req = new UpdateMeRequest();
        req.setName("   ");

        assertThatThrownBy(() -> userService.updateMe(EMAIL, req))
            .isInstanceOf(BadRequestException.class);
        verify(userRepository, never()).save(any());
    }

    @Test
    void updateMe_withAnEmptyPhone_clearsIt() {
        User user = existing();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        stubSaveEchoes();

        UpdateMeRequest req = new UpdateMeRequest();
        req.setPhone("");

        assertThat(userService.updateMe(EMAIL, req).getPhone()).isNull();
    }

    /** The phone column is unique AND a login identifier — a clash must be a 409. */
    @Test
    void updateMe_withAPhoneOwnedBySomeoneElse_is409() {
        User user = existing();
        User other = User.builder().id(UUID.randomUUID()).email("other@propfind.in").build();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(userRepository.findByPhone("9000000001")).thenReturn(Optional.of(other));

        UpdateMeRequest req = new UpdateMeRequest();
        req.setPhone("9000000001");

        assertThatThrownBy(() -> userService.updateMe(EMAIL, req))
            .isInstanceOf(ConflictException.class);
        verify(userRepository, never()).save(any());
    }

    /** Re-submitting your own number is a no-op, not a conflict with yourself. */
    @Test
    void updateMe_withYourOwnPhone_succeeds() {
        User user = existing();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(userRepository.findByPhone("9876543210")).thenReturn(Optional.of(user));
        stubSaveEchoes();

        UpdateMeRequest req = new UpdateMeRequest();
        req.setPhone("9876543210");

        assertThat(userService.updateMe(EMAIL, req).getPhone()).isEqualTo("9876543210");
    }

    @Test
    void updateMe_neverExposesAWayToChangeRoleOrEmail() {
        // Structural, not behavioural: if someone adds these fields to the
        // request DTO this fails, which is the moment to think about it.
        assertThat(UpdateMeRequest.class.getDeclaredFields())
            .extracting(java.lang.reflect.Field::getName)
            .containsExactlyInAnyOrder("name", "phone", "notifyEmailInquiries");
    }
}
