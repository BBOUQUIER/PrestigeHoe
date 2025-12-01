package fr.batistou15.prestigehoe.data;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

public interface DataStorage {

    PlayerProfile loadProfile(UUID uuid);

    void saveProfile(PlayerProfile profile);

    void saveAll(Map<UUID, PlayerProfile> profiles);
    Collection<PlayerProfile> loadAllProfiles();
}
