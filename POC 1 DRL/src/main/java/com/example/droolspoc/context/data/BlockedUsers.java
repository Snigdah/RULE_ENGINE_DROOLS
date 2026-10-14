package com.example.droolspoc.context.data;

import java.util.Set;

/**
 * Immutable global dataset: the set of blocked user IDs. Rules ask
 * {@link #isBlocked(String)}; nothing here can be mutated after construction.
 */
public final class BlockedUsers {

    private final Set<String> blockedUserIds;

    public BlockedUsers(Set<String> blockedUserIds) {
        // defensive immutable copy
        this.blockedUserIds = Set.copyOf(blockedUserIds);
    }

    public boolean isBlocked(String userId) {
        return userId != null && blockedUserIds.contains(userId);
    }

    public int size() {
        return blockedUserIds.size();
    }
}
