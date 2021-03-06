package de.clashofdynasties.repository;

import de.clashofdynasties.models.Building;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

@Component
public class BuildingRepository extends Repository<Building> {
    private static BuildingRepository instance;

    @PostConstruct
    public void initialize() {
        load(Building.class);
        instance = this;
    }

    public static BuildingRepository get() {
        return instance;
    }

    public synchronized Building findById(ObjectId id) {
        return items.stream().filter(b -> b.getId().equals(id)).findFirst().orElse(null);
    }
}
