package railway.g7;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.TreeMap;
import java.util.Random;
import java.util.Hashtable;
import java.util.HashSet;
import java.util.Set;
import java.lang.management.*;
// To access data classes.
import railway.sim.utils.*;

public class Player implements railway.sim.Player {
    // Random seed of 42.
    private int seed = 42;
    private Random rand;

    private double budget;
    private String name;
    private List<BidInfo> allBids;

    private List<Coordinates> geo;
    private List<List<Integer>> infra;
    private int[][] transit;
    private List<String> townLookup;

    private WeightedGraph graph;
    private double totalMoney;
    private Route[][] routeTable;
    private int totalLinksToBid;
    private Map<Link, Double> linkToValue;
    private Map<Link, Double> maxValueTable;
    private Map<Double, Link> valueToLink;
    private Set<Link> bridges;
    private Map<Link, Integer> linkToID;
    private Map<Integer, Link> idToLink;
    private Map<Integer, LinkInfo> idToLinkInfo;
    private Map<String, Integer> townIDLookup;
    private List<Pivotal> pivotals;

    private List<Link> linksToBid;
    private Set<Link> linkSet;

    private List<BidInfo> availableBids = new ArrayList<>();
    private Set<Integer> availableBidId = new HashSet<>();
    private Set<Integer> ourBidId = new HashSet<>();

    public Player() {
        rand = new Random();
        linkToValue = new HashMap<Link, Double>();
        valueToLink = new TreeMap<Double, Link>(Collections.reverseOrder());
        bridges = new HashSet<Link>();
        linksToBid = new ArrayList<Link>();
        linkSet = new HashSet<Link>();
    }

    public void init(
        String name,
        double budget,
        List<Coordinates> geo,
        List<List<Integer>> infra,
        int[][] transit,
        List<String> townLookup,
        List<BidInfo> allBids) {
        this.name = name;
        this.budget = budget;
        totalMoney = budget;
        this.geo = geo;
        this.infra = infra;
        this.transit = transit;
        this.townLookup = townLookup;
        this.allBids = allBids;
        int numLinks = 0;
        for (int i = 0; i < infra.size(); i++) {
            numLinks += infra.get(i).size();
        }
        totalLinksToBid = numLinks / 2;
        routeTable = new Route[transit.length][transit[0].length];
        initTownIDLookup();
        initLinkTable();
        initializeGraph();
        buildRouteTable();
        initializePivotals();
        identifyPivotals();
        getLinkMaxValue();
        makeLinks();
    }

    private void initTownIDLookup() {
        townIDLookup = new HashMap<String, Integer>();
        for (int i = 0; i < townLookup.size(); i++) {
            townIDLookup.put(townLookup.get(i), i);
        }
    }

    private void initLinkTable() {
        linkToID = new HashMap<Link, Integer>();
        idToLink = new HashMap<Integer, Link>();
        idToLinkInfo = new HashMap<Integer, LinkInfo>();
        for (BidInfo binfo: allBids) {
            int i = townIDLookup.get(binfo.town1);
            int j = townIDLookup.get(binfo.town2);
            Link link = new Link(i, j);
            linkToID.put(link, binfo.id);
            idToLink.put(binfo.id, link);
            idToLinkInfo.put(binfo.id, new LinkInfo(link, binfo.amount, binfo.owner));
        }
    }

    private void initializeGraph() {
        graph = new WeightedGraph(townLookup.size());
        for (int i = 0; i < townLookup.size(); i++) {
            graph.setLabel(townLookup.get(i));
        }

        for (int i = 0; i < infra.size(); i++) {
            for (int j = 0; j < infra.get(i).size(); j++) {
                int source = i;
                int target = infra.get(i).get(j);
                double distance = calcEuclideanDistance(geo.get(source), geo.get(target));
                graph.addEdge(source, target, distance);
            }
        }
    }

    private void initializePivotals() {
        pivotals = new ArrayList<Pivotal>();
        for (int i = 0; i < geo.size(); i++) {
            pivotals.add(new Pivotal(i, 0.));
        }
    }

    private void identifyPivotals() {
        for (int i = 0; i < transit.length; i++) {
            for (int j = i + 1; j < transit[i].length; j++) {
                int traffic = transit[i][j];
                Pivotal pi = pivotals.get(i);
                pi.incrementTraffic(traffic);
                Pivotal pj = pivotals.get(j);
                pj.incrementTraffic(traffic);
            }
        }

        for (Pivotal pivotal: pivotals) {
            int node = pivotal.getNode();
            int[] neighbors = graph.neighbors(node);
            pivotal.setTraffic(pivotal.getTraffic() / neighbors.length);
        }

        Collections.sort(pivotals, Collections.reverseOrder());
    }

    // all pair shortest path
    private void getLinkMaxValue() {
        maxValueTable = new HashMap<Link, Double>();
        for (int i = 0; i < infra.size(); i++) {
            for (int j = 0; j < infra.get(i).size(); j++) {
                int source = i;
                int target = infra.get(i).get(j);
                Link link = new Link(source, target);
                maxValueTable.put(link, 0.);
            }
        }

        for (int i = 0; i < transit.length; i++) {
            for (int j = i + 1; j < transit[i].length; j++) {
                int traffic = transit[i][j];
                List<List<Integer>> links = routeTable[i][j].getRoutes();
                for (int m = 0; m < links.size(); m++) {
                    int factor = links.size();
                    for (int n = 0; n < links.get(m).size() - 1; n++) {
                        int s = links.get(m).get(n);
                        int t = links.get(m).get(n + 1);
                        Link link = new Link(s, t);
                        double value = traffic * routeTable[s][t].getDistance() / factor;
                        maxValueTable.put(link, maxValueTable.get(link) + value);
                    }
                }
            }
        }
    }

    private void makeLinks() {
        int num = 0;
        int stride = 2;
        for (int s = 0; s < pivotals.size() / stride - 1; s++) {
            for (int i = s * stride; i < (s + 1) * stride; i++) {
                for (int j = i + 1; j < (s + 1) * stride; j++) {
                    int node1 = pivotals.get(i).getNode();
                    int node2 = pivotals.get(j).getNode();
                    Route route = routeTable[node1][node2];
                    List<List<Integer>> links = route.getRoutes();
                    for (int n = 0; n < links.get(0).size() - 1; n++) {
                        Link link = new Link(links.get(0).get(n), links.get(0).get(n + 1));
                        if (num < totalLinksToBid && !linkSet.contains(link)) {
                            linksToBid.add(link);
                            linkSet.add(link);
                            num += 1;
                        }
                        else if (linkSet.contains(link)) {
                            continue;
                        }
                        else {
                            return;
                        }
                    }
                }
            }
        } 
    }


    private void initializeStartingPoints() {
        // if we have valuable links, we bid on links and start from there


        // if we don't have valuable links, we bid on links that are spred out from pivotals


        // bid pivotals or bridges? if there are groups bidding on either, we engage in a bidding war, especially on bridges
    }


    private double calcEuclideanDistance(Coordinates a, Coordinates b) {
        return Math.sqrt((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y));
    }

    private List<List<Integer>> getLinks(WeightedGraph g, int source, int target) {
        int[][] prev = Dijkstra.dijkstra(g, source);
        return Dijkstra.getPaths(g, prev, target);
    }

    private void buildRouteTable() {
        for (int i = 0; i < transit.length; i++) {
            for (int j = i + 1; j < transit[i].length; j++) {
                List<List<Integer>> links = getLinks(graph, i, j);
                double distance = 0.;
                for (int n = 0; n < links.get(0).size() - 1; n++) {
                    distance += graph.getWeight(links.get(0).get(n), links.get(0).get(n + 1));
                }
                routeTable[i][j] = new Route(links, distance);
                routeTable[j][i] = new Route(links, distance);
            }
        }
    }

    public Bid getBid(List<Bid> currentBids, List<BidInfo> allBids, Bid lastRoundMaxBid) {
        updateAvailableLinks(lastRoundMaxBid);
        int linkID = -1;
        double value = 0;
        if (linksToBid.size() == 0) {
            return getRandomBid(currentBids, allBids, lastRoundMaxBid);
        }
        Link nextLink = linksToBid.get(0);
        linkID = linkToID.get(nextLink);
        value = maxValueTable.get(nextLink);

        if (linkID == -1) {
            return getRandomBid(currentBids, allBids, lastRoundMaxBid);
        }

        double max = 0.;
        String bidder = "";
        for (Bid bid: currentBids) {
            Link link = idToLink.get(bid.id1);
            double distance = graph.getWeight(link.town1, link.town2);
            if (bid.amount / distance > max) {
                max = bid.amount / distance;
                bidder = bid.bidder;
            }

            if (bid.id2 == -1) {
                continue;
            }
            link = idToLink.get(bid.id2);
            distance = graph.getWeight(link.town1, link.town2);
            if (bid.amount / distance > max) {
                max = bid.amount / distance;
                bidder = bid.bidder;
            }
        }

        Link bridge = idToLink.get(linkID);
        double distance = bridge.getDistance();
        if (bidder.equals("g7") || max * distance > value * 10 || max * distance > totalMoney / totalLinksToBid) {
            return null;
        }

        Bid ourBid = new Bid();
        ourBid.id1 = linkID;
        LinkInfo li = idToLinkInfo.get(linkID);
        if (li.getAmount() - 0.005 > max * distance) {
            if (budget < li.getAmount()) {
                return null;
            }
            ourBid.amount = li.getAmount();
        }
        else {
            if (budget < max * distance + 10000) {
                return null;
            }
            ourBid.amount = max * distance + 10000 + 0.005;
        }

        return ourBid;
    }

    private Bid getRandomBid(List<Bid> currentBids, List<BidInfo> allBids, Bid lastRoundMaxBid) {
        if (availableBids.size() != 0) {
            return null;
        }

        for (BidInfo bi : allBids) {
            if (bi.owner == null) {
                availableBids.add(bi);
            }
        }

        if (availableBids.size() == 0) {
            return null;
        }

        BidInfo randomBid = availableBids.get(rand.nextInt(availableBids.size()));
        double amount = randomBid.amount;

        // Don't bid if the random bid turns out to be beyond our budget.
        if (budget - amount < 0.) {
            return null;
        }

        // Check if another player has made a bid for this link.
        for (Bid b : currentBids) {
            if (b.id1 == randomBid.id || b.id2 == randomBid.id) {
                if (budget - b.amount - 10000 < 0.) {
                    return null;
                }
                else {
                    amount = b.amount + 10000;
                }

                break;
            }
        }

        Bid bid = new Bid();
        bid.amount = amount;
        bid.id1 = randomBid.id;

        return bid;
    }

    private void updateAvailableLinks(Bid lastRoundMaxBid) {
        if (lastRoundMaxBid == null) {
            return;
        }

        LinkInfo lastAwardedLinkInfo = idToLinkInfo.get(lastRoundMaxBid.id1);
        lastAwardedLinkInfo.owner = lastRoundMaxBid.bidder;
        Link lastAwardedLink = idToLink.get(lastRoundMaxBid.id1);
        linkToID.remove(lastAwardedLink);
        linksToBid.remove(lastAwardedLink);

        if (lastRoundMaxBid.id2 == -1) {
            return;
        }
        lastAwardedLinkInfo = idToLinkInfo.get(lastRoundMaxBid.id2);
        lastAwardedLinkInfo.owner = lastRoundMaxBid.bidder;
        lastAwardedLink = idToLink.get(lastRoundMaxBid.id2);
        linkToID.remove(lastAwardedLink);
        linksToBid.remove(lastAwardedLink);
    }

    public void updateBudget(Bid bid) {
        if (bid != null) {
            budget -= bid.amount;
            ourBidId.add(bid.id1);
            if (bid.id2 != -1){
                ourBidId.add(bid.id2);
            }
        }

        availableBids = new ArrayList<>();
        availableBidId = new HashSet<>();
    }

    public BidInfo getBidInfo(int id1, int id2){
        String name1 = townLookup.get(id1);
        String name2 = townLookup.get(id2);
        for (BidInfo bi : allBids){
            if ((bi.town1.equals(name1) && bi.town2.equals(name2))||(bi.town1.equals(name2) && bi.town2.equals(name1))){
                return bi;
            }
        }
        return null;
    }

    private class Link {
        private int town1;
        private int town2;

        public Link(int id1, int id2){
            if (id1 < id2) {
                town1 = id1;
                town2 = id2;
            }
            else {
                town1 = id2;
                town2 = id1;
            }
        }

        public double getDistance() {
            return graph.getWeight(town1, town2);
        }

        @Override
        public boolean equals(Object o) { 
  
            // If the object is compared with itself then return true   
            if (o == this) { 
                return true; 
            } 
  
            /* Check if o is an instance of Complex or not 
            "null instanceof [type]" also returns false */
            if (!(o instanceof Link)) { 
                return false; 
            } 
          
            // typecast o to Complex so that we can compare data members  
            Link lv = (Link) o; 
          
            // Compare the data members and return accordingly  
            return (town1 == lv.town1 && town2 == lv.town2) || (town1 == lv.town2 && town2 == lv.town1); 
        }

        @Override
        public int hashCode() {
            if (town1 < town2) {
                return town1 * 10000000 + town2;
            }
            else {
                return town2 * 10000000 + town1;
            }
        }

        @Override
        public String toString() {
            return new String("This link is from " + town1 + " to " + town2);
        }
    }

    private class LinkInfo {
        private Link link;
        private double amount;
        private String owner;

        public LinkInfo(Link l, double a, String o){
            link = new Link(l.town1, l.town2);
            amount = a;
            owner = o;
        }

        public Link getLink() {
            return new Link(link.town1, link.town2);
        }

        public void setAmount(double a) {
            amount = a;
        }

        public double getAmount() {
            return amount;
        }

        public void setOwner(String o) {
            owner = o;
        }

        public String getOwner() {
            return owner;
        }

        @Override
        public boolean equals(Object o) { 
  
            // If the object is compared with itself then return true   
            if (o == this) { 
                return true; 
            } 
  
            /* Check if o is an instance of Complex or not 
            "null instanceof [type]" also returns false */
            if (!(o instanceof LinkInfo)) { 
                return false; 
            } 
          
            // typecast o to Complex so that we can compare data members  
            LinkInfo lv = (LinkInfo) o; 
          
            // Compare the data members and return accordingly  
            return link.equals(lv.link) && amount == lv.amount && owner.equals(lv.owner); 
        }

        @Override
        public int hashCode() {
            String s = link.toString() + Double.toString(amount) + owner;
            return s.hashCode();
        }

        @Override
        public String toString() {
            return link.toString() + ", the amount is: " + Double.toString(amount) + ", and the owner is: " + owner;
        }
    }

    private class Pivotal implements Comparable<Pivotal> {
        private int node;
        private double trafficPE;

        public Pivotal(int node, double trafficPE){
            this.node = node;
            this.trafficPE = trafficPE;
        }

        public int getNode() {
            return node;
        }

        public double getTraffic() {
            return trafficPE;
        }

        public void setTraffic(double traffic) {
            trafficPE = traffic;
        }

        public void incrementTraffic(double increment) {
            trafficPE += increment;
        }

        @Override
        public boolean equals(Object o) { 
  
            // If the object is compared with itself then return true   
            if (o == this) { 
                return true; 
            } 
  
            /* Check if o is an instance of Complex or not 
            "null instanceof [type]" also returns false */
            if (!(o instanceof Pivotal)) { 
                return false; 
            } 
          
            // typecast o to Complex so that we can compare data members  
            Pivotal p = (Pivotal) o; 
          
            // Compare the data members and return accordingly  
            return node == p.node; 
        }

        @Override
        public int hashCode() {
            return node;
        }

        @Override
        public int compareTo(Pivotal p) {
            return (int) Math.signum(trafficPE - p.trafficPE);
        }

        @Override
        public String toString() {
            return new String("This pivotal point is: " + node + "/" + townLookup.get(node) + ", and its traffic per edge is: " + trafficPE);
        }
    }

    private class Route {
        List<List<Integer>> routes;
        double distance;

        public Route (List<List<Integer>> r, double d){
            routes = copyListofList(r);
            distance = d;
        }

        private List<List<Integer>> copyListofList(List<List<Integer>> list) {
            List<List<Integer>> results = new ArrayList<List<Integer>>();
            for (int i = 0; i < list.size(); i++) {
                List<Integer> result = new ArrayList<Integer>();
                for (int j = 0; j < list.get(i).size(); j++) {
                    result.add(list.get(i).get(j));
                }
                results.add(result);
            }
            return results;
        }

        // return true if link is owned by someone else

        public List<List<Integer>> getRoutes() {
            return copyListofList(routes);
        }

        public double getDistance() {
            return distance;
        }
    }

}