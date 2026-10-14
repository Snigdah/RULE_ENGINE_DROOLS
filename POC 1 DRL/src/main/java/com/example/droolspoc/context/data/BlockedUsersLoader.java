package com.example.droolspoc.context.data;

import com.example.droolspoc.model.UserBlock;
import com.example.droolspoc.repository.UserBlockRepository;
import leads.ruleengine.core.context.GlobalContext;
import leads.ruleengine.core.context.GlobalReferenceLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Loads blocked users from re_user_block into the library GlobalContext.
 * The library's GlobalContextLoader calls every GlobalReferenceLoader at startup
 * and again on POST /admin/global-context/reload.
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
