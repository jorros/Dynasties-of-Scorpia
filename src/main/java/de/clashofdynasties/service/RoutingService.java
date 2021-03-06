package de.clashofdynasties.service;

import de.clashofdynasties.models.*;
import de.clashofdynasties.repository.RelationRepository;
import de.clashofdynasties.repository.RoadRepository;

import java.util.*;

public class RoutingService {
    private PriorityQueue<Node> openList;
    private LinkedList<Node> closedList;
    private Node goal;
    private boolean isFormation;
    private Node lastNode;
    private Formation formation;

    private RoadRepository roadRepository;
    private RelationRepository relationRepository;

    private Route route;

    public RoutingService(RoadRepository roadRepository, RelationRepository relationRepository) {
        this.roadRepository = roadRepository;
        this.relationRepository = relationRepository;
    }

    private class Node implements Comparable {
        private MapNode object;

        public double f;
        public double g;
        public Node parent;

        public Node(MapNode obj) {
            object = obj;
            f = 0.0;
            g = 0.0;
        }

        public int getX() {
            return new Double(object.getX()).intValue();
        }

        public int getY() {
            return new Double(object.getY()).intValue();
        }

        public boolean isNegotiable(Player player, boolean isBorder, boolean isCaravan) {
            if(object instanceof City) {
                Player other = object.getPlayer();

                if(player.equals(other))
                    return true;

                Relation relation = relationRepository.findByPlayers(player, other);

                if(relation != null) {
                    switch(relation.getRelation()) {
                        case 0:
                            return isBorder && !isCaravan;

                        case 1:
                            return (other.isComputer() && isBorder && !isCaravan);

                        case 2:
                            return isCaravan;

                        case 3:
                            return (relation.getTicksLeft() == null || isCaravan);
                    }
                }
                else
                    return (other.isComputer() && isBorder && !isCaravan);
            }

            return false;
        }

        public Player getPlayer() {
            return object.getPlayer();
        }

        // Heuristik: geschätzter Restweg (Luftlinie)
        public double getH() {
            return Math.sqrt(Math.pow(getX() - goal.getX(), 2) + Math.pow(getY() - goal.getY(), 2));
        }

        public void setObject(MapNode obj) {
            object = obj;
        }

        public MapNode getObject() {
            return object;
        }

        @Override
        public int compareTo(Object o) {
            Node n = (Node) o;
            return (int) (f - n.f);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Node && (((Node) o).getObject().equals(object));
        }

        public List<Node> getNeighbours() {
            List<Node> neighbours = new ArrayList<>();

            if (object instanceof City) {
                City n = (City) object;

                for (Road r : roadRepository.findByCity(n)) {
                    if (!r.getPoint1().equals(n))
                        neighbours.add(new Node(r.getPoint1()));
                    else
                        neighbours.add(new Node(r.getPoint2()));
                }
            } else if (object instanceof Formation) {
                Formation n = (Formation) object;

                if (n.isDeployed()) {
                    List<Road> roads = roadRepository.findByCity(n.getLastCity());
                    for (Road r : roads) {
                        if (!r.getPoint1().equals(n.getLastCity()))
                            neighbours.add(new Node(r.getPoint1()));
                        else
                            neighbours.add(new Node(r.getPoint2()));
                    }
                } else {
                    neighbours.add(new Node(n.getLastCity()));
                    neighbours.add(new Node(n.getRoute().getNext()));
                }
            }

            return neighbours;
        }
    }

    // Distanz zwischen a und b berechnen
    private double caluclateG(Node a, Node b) {
        double factor = 2.0;
        if (a.getObject() instanceof City && b.getObject() instanceof City) {
            Road road = roadRepository.findByCities(((City) a.getObject()), ((City) b.getObject()));
            if (road != null)
                factor -= road.getWeight();
        } else
            factor = 1.0;
        return Math.sqrt(Math.pow(a.getX() - b.getX(), 2) + Math.pow(a.getY() - b.getY(), 2)) * factor;
    }

    private void expandNode(Node current, Player player, boolean isCaravan, boolean isCurrentCity) {
        List<Node> neighbours = current.getNeighbours();
        for (Node n : neighbours) {
            if (closedList.contains(n))
                continue;

            double tempG = n.g + caluclateG(current, n);

            if (openList.contains(n) && tempG >= n.g)
                continue;

            boolean isBorder = player.equals(current.getPlayer());

            if(!n.isNegotiable(player, isBorder, isCaravan) && !isCurrentCity)
                continue;

            n.parent = current;
            n.g = tempG;
            n.f = tempG + n.getH(); // Gesamtweg geschätzt

            if (!openList.contains(n))
                openList.offer(n);
        }
    }

    public Route getRoute() {
        return route;
    }

    public void setRoute(Route route) {
        this.route = route;
    }

    private Route createRoute() {
        Route route = new Route();
        List<Road> roads = new ArrayList<>();

        Node lastNode = this.lastNode;

        while (true) {
            if (lastNode.getObject() instanceof City) {
                // Wenn Aktueller und Vorgänger Knoten Städte sind, Road ermitteln
                if (lastNode.parent != null && lastNode.parent.getObject() instanceof City && lastNode.parent.parent != null) {
                    City p1 = (City) lastNode.getObject();
                    City p2 = (City) lastNode.parent.getObject();

                    roads.add(roadRepository.findByCities(p1, p2));
                } else if (lastNode.parent != null && (lastNode.parent.getObject() instanceof Formation || lastNode.parent.parent == null)) {
                    // Wenn parent Formation ist, currentNode als nächste Stadt setzen
                    if (lastNode.parent.getObject() instanceof City) {
                        City p1 = (City) lastNode.getObject();
                        City p2 = (City) lastNode.parent.getObject();

                        roads.add(roadRepository.findByCities(p1, p2));
                    }
                    route.setNext((City) lastNode.getObject());
                } else if (lastNode.parent == null)
                    break;

                lastNode = lastNode.parent;
            } else
                break;
        }

        Collections.reverse(roads);
        //if(isFormation && roads.size() > 0)
        //    roads.remove(0);

        route.setTarget((City) goal.getObject());
        route.setRoads(roads);

        return route;
    }

    public boolean calculateRoute(Formation formation, City city, Player player) {
        openList = new PriorityQueue<>();
        closedList = new LinkedList<>();
        goal = null;
        isFormation = true;
        this.formation = formation;

        int rel;
        Double ticksLeft = null;

        if(city.getPlayer().equals(formation.getPlayer()))
            rel = 4;
        else {
            Relation relation = relationRepository.findByPlayers(formation.getPlayer(), city.getPlayer());
            if(relation != null) {
                rel = relation.getRelation();
                ticksLeft = relation.getTicksLeft();
            }
            else
                rel = 1;
        }

        if((rel == 3 && ticksLeft == null) || rel == 4 || rel == 0 || city.getPlayer().isComputer()) {
            Node from;
            if (formation.isDeployed())
                from = new Node(formation.getLastCity());
            else
                from = new Node(formation);

            goal = new Node(city);

            openList.offer(from);

            while (!openList.isEmpty()) {
                Node currentNode = openList.poll();

                if (currentNode.getObject() instanceof City) {
                    City currC = (City) currentNode.getObject();
                    String name = currC.getName();
                    if (currC.equals(city)) {
                        lastNode = currentNode;
                        route = createRoute();
                        if (formation.getRoute() != null)
                            route.setCurrentRoad(formation.getRoute().getCurrentRoad());

                        route.setTime(calculateTime());
                        return true;
                    }

                    closedList.add(currentNode);
                    expandNode(currentNode, player, false, formation.getLastCity().equals(currC));
                } else if (currentNode.getObject() instanceof Formation) {
                    closedList.add(currentNode);
                    expandNode(currentNode, player, false, false);
                }
            }
        }

        return false;
    }

    public void setFormation(Formation formation) {
        this.formation = formation;
        this.isFormation = true;
    }

    public boolean calculateRoute(City start, City end, Player player) {
        openList = new PriorityQueue<>();
        closedList = new LinkedList<>();
        goal = null;
        isFormation = false;

        int rel;

        if(end.getPlayer().equals(player))
            rel = 4;
        else {
            Relation relation = relationRepository.findByPlayers(player, end.getPlayer());
            if(relation != null)
                rel = relation.getRelation();
            else
                rel = 1;
        }

        if(rel >= 2 && start.getPlayer().equals(player)) {
            Node from = new Node(start);
            goal = new Node(end);

            openList.offer(from);

            while (!openList.isEmpty()) {
                Node currentNode = openList.poll();

                if (currentNode.getObject() instanceof City) {
                    City currC = (City) currentNode.getObject();
                    if (currC.equals(end)) {
                        lastNode = currentNode;
                        route = createRoute();
                        route.setTime(calculateTime());
                        return true;
                    }

                    closedList.add(currentNode);
                    expandNode(currentNode, player, true, false);
                }
            }
        }

        return false;
    }

    public int calculateTime() {
        int time = 0;
        List<Road> roads = route.getRoads();

        if(isFormation) {
            Road currentRoad;

            int subtract = 70;
            if (formation.isDeployed()) {
                subtract = 140;
                currentRoad = roadRepository.findByCities(formation.getLastCity(), route.getNext());
            } else
                currentRoad = route.getCurrentRoad();

            double length = Math.sqrt(Math.pow(formation.getX() - route.getNext().getX(), 2) + Math.pow(formation.getY() - route.getNext().getY(), 2));
            time += new Double((length - subtract) / (formation.getSpeed() * currentRoad.getWeight())).intValue();

            for (Road road : roads) {
                time += new Double((road.getLength() - 140) * road.getWeight() / formation.getSpeed()).intValue();
            }
        }
        else {
            for (Road road : roads) {
                time += new Double((road.getLength() - 140) * road.getWeight() / 0.1).intValue();
            }
        }

        return time;
    }
}
