package com.iotiq.partnererp.modules.payouts.service;

import com.iotiq.partnererp.modules.auth.repository.UserRepository;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Display names of the users who acted on payout records. */
@Component
public class UserNames {

    private final UserRepository userRepository;

    public UserNames(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public Map<UUID, String> of(Collection<UUID> ids) {
        Map<UUID, String> names = new HashMap<>();
        Set<UUID> present = ids.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (!present.isEmpty()) {
            userRepository.findAllById(present).forEach(u -> names.put(u.getId(), u.getFullName()));
        }
        return names;
    }
}
