package com.example.droolspoc.context.data;

import com.example.droolspoc.context.GlobalContext;
import com.example.droolspoc.context.GlobalReferenceLoader;
import com.example.droolspoc.model.UserBlock;
import com.example.droolspoc.repository.UserBlockRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Loads the blocked-user list from the {@code user_block} table into the global
 * context at startup. This is one concrete example of the extension point —
 * copy its shape to add another global dataset.
 */
@Component
public class BlockedUsersLoader implements GlobalReferenceLoader {

    private static final Logger LOGGER = LoggerFactory.getLogger(BlockedUsersLoader.class);

    private final UserBlockRepository userBlockRepository;

    public BlockedUsersLoader(UserBlockRepository userBlockRepository) {
        this.userBlockRepository = userBlockRepository;
    }

    @Override
    public void load(GlobalContext context) {
        Set<String> ids = userBlockRepository.findByBlockedTrue().stream()
                .map(UserBlock::getUserId)
                .collect(Collectors.toUnmodifiableSet());

        context.register(BlockedUsers.class, new BlockedUsers(ids));
        LOGGER.info("Loaded BlockedUsers: {} blocked user(s)", ids.size());
    }
}
