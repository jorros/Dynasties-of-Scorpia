package de.clashofdynasties.game;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import de.clashofdynasties.models.*;
import de.clashofdynasties.repository.*;
import de.clashofdynasties.service.RoutingService;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/game/formations")
public class FormationController {
    @Autowired
    FormationRepository formationRepository;

    @Autowired
    PlayerRepository playerRepository;

    @Autowired
    CityRepository cityRepository;

    @Autowired
    UnitRepository unitRepository;

    @Autowired
    RoadRepository roadRepository;

    @Autowired
    private RelationRepository relationRepository;

    @RequestMapping(method = RequestMethod.GET)
    public
    @ResponseBody
    Map<String, ObjectNode> getFormations(Principal principal, @RequestParam boolean editor, @RequestParam long timestamp) {
        Player player = playerRepository.findByName(principal.getName());
        RoutingService routing = new RoutingService(roadRepository, relationRepository);

        List<Formation> formations = formationRepository.findAll();
        HashMap<String, ObjectNode> data = new HashMap<String, ObjectNode>();

        for (Formation formation : formations) {
            // Deploy Status ermitteln
            if (formation.getRoute() != null) {
                formation.setDeployed(false);
                routing.setRoute(formation.getRoute());
                formation.getRoute().setTime(routing.calculateTime());
            } else {
                formation.setDeployed(true);
            }

            // Diplomatie ermitteln
            if (player.equals(formation.getPlayer()))
                formation.setDiplomacy(1);

            // Wenn Spieler neutral
            if (formation.getPlayer().isComputer()) {
                formation.setDiplomacy(4);
            }

            if(!editor && formation.isVisible(player))
                data.put(formation.getId().toHexString(), formation.toJSON(timestamp));
        }

        return data;
    }

    @RequestMapping(value = "/{formation}/route", method = RequestMethod.GET)
    public
    @ResponseBody
    ObjectNode calculateRoute(Principal principal, @PathVariable("formation") ObjectId id, @RequestParam ObjectId target) {
        Player player = playerRepository.findByName(principal.getName());
        Formation formation = formationRepository.findOne(id);
        City city = cityRepository.findOne(target);

        RoutingService routing = new RoutingService(roadRepository, relationRepository);

        if(routing.calculateRoute(formation, city, player))
            return routing.getRoute().toJSON();
        else
            return JsonNodeFactory.instance.objectNode();
    }

    @RequestMapping(value = "/{formation}/move", method = RequestMethod.GET)
    @ResponseStatus(HttpStatus.OK)
    public void move(Principal principal, @PathVariable("formation") ObjectId formationId, @RequestParam ObjectId target) {
        Formation formation = formationRepository.findOne(formationId);
        City city = cityRepository.findOne(target);
        Player player = playerRepository.findByName(principal.getName());
        RoutingService routing = new RoutingService(roadRepository, relationRepository);

        if (player.equals(formation.getPlayer())) {
            if (routing.calculateRoute(formation, city, player)) {
                if (formation.isDeployed()) {
                    formation.setRoute(routing.getRoute());
                    formation.getRoute().setCurrentRoad(roadRepository.findByCities(formation.getLastCity().getId(), formation.getRoute().getNext().getId()));
                    formation.move(70);
                } else {
                    formation.setRoute(routing.getRoute());
                }

                formation.updateTimestamp();
                formationRepository.save(formation);
            }
        }
    }

    @RequestMapping(value = "/{formation}", method = RequestMethod.DELETE)
    @ResponseStatus(HttpStatus.OK)
    public void remove(Principal principal, @PathVariable("formation") ObjectId formationId) {
        Player player = playerRepository.findByName(principal.getName());
        Formation formation = formationRepository.findOne(formationId);

        if (formation.getPlayer().equals(player) && formation.isDeployed() && formation.getLastCity().getPlayer().equals(formation.getPlayer())) {
            City city = formation.getLastCity();

            formation.getUnits().forEach(u -> city.getUnits().add(u));

            cityRepository.save(city);
            formationRepository.delete(formation);
        }
    }

    @RequestMapping(method = RequestMethod.POST)
    @ResponseStatus(HttpStatus.OK)
    public void create(Principal principal, @RequestParam("name") String name, @RequestParam("city") ObjectId cityId, @RequestParam("units[]") List<ObjectId> unitsId) {
        save(principal, null, name, cityId, unitsId);
    }

    @RequestMapping(value = "/{formation}", method = RequestMethod.PUT)
    @ResponseStatus(HttpStatus.OK)
    public void save(Principal principal, @PathVariable("formation") ObjectId formationId, @RequestParam("name") String name, @RequestParam("city") ObjectId cityId, @RequestParam("units[]") List<ObjectId> unitsId) {
        // Formation, City und Player vorbereiten
        City city = cityRepository.findOne(cityId);
        Player player = playerRepository.findByName(principal.getName());

        // Formation nur fetchen, wenn City dem Spieler gehört
        if (player.equals(city.getPlayer())) {
            Formation formation;
            if (formationId != null)
                formation = formationRepository.findOne(formationId);
            else {
                formation = new Formation();
                formation.setUnits(new ArrayList<Unit>());
                formation.setLastCity(city);
                formation.setRoute(null);
                formation.setPlayer(player);
                formation.setX(city.getX());
                formation.setY(city.getY());
            }

            if (player.equals(formation.getPlayer())) {
                if (formation.getRoute() == null) {
                    List<Unit> units = new ArrayList<Unit>();
                    for (ObjectId unitId : unitsId) {
                        Unit unit = unitRepository.findOne(unitId);
                        if (city.getUnits().contains(unit)) {
                            city.getUnits().remove(unit);
                            formation.getUnits().add(unit);
                        }
                    }

                    formation.setName(name);
                    formation.updateTimestamp();

                    cityRepository.save(city);
                    formationRepository.save(formation);
                }
            }
        }
    }
}
