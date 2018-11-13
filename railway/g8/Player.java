package railway.g8;

import railway.sim.utils.Bid;
import railway.sim.utils.BidInfo;
import railway.sim.utils.Coordinates;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Arrays;

import javafx.util.Pair;

import java.util.HashMap;


class Connection {
    int row;
    int column;

    public Connection(int row, int column) {
        this.row = row;
        this.column = column;
    }
}

public class Player implements railway.sim.Player {
    // Random seed of 42.
    private int seed = 42;
    private Random rand;
    private double totalDistance = 0;
    private double budget;
    private List<BidInfo> availableBids = new ArrayList<>();
    private double lastBid;
    private List<List<Integer>> infra;
    private int[][] transit;

    //hashmap of all our connections- and then map connection 1 etc. to amount of traffic between 
    private HashMap<Connection, Integer> connections = new HashMap<Connection, Integer>();

    //okay new HashMap that just uses bid ID instead 
    //maps bid ID to amount of traffic on that link 
    private HashMap<Integer, Integer> bidIdTraffic = new HashMap<Integer, Integer>();
    private HashMap<Integer, Double> bidIdMinBid = new HashMap<Integer, Double>();
    private HashMap<Integer, Double> bidDistance = new HashMap<Integer, Double>();


    //Hahsmap for edgeweights
    private HashMap<Integer, Integer> bidIdEdgeWeight = new HashMap<Integer, Integer>();

    private GraphUtility gu;


    private int totalTraffic = 0;


    public Player() {
        rand = new Random();
    }

    public void init(
            String name,
            double budget,
            List<Coordinates> geo,
            List<List<Integer>> infra, //
            int[][] transit, //
            List<String> townLookup,
            List<BidInfo> allBids) {

        this.budget = budget;
        this.transit = transit;
        this.infra = infra;
        gu = new GraphUtility(geo, infra, transit, townLookup);
        lastBid = -1;
        // System.out.println("DEBUGGGGG ////////");
        // System.out.println(townLookup.get(0));
        // System.out.println(townLookup.get(1));
        // for(Object i: gu.path[2][0]){
        //     System.out.print(townLookup.get((Integer) i));
        //     System.out.print(i);
        //     System.out.print(",");
        // }
        // System.out.println("");

        buildEdgeHashMap(gu.edgeWeight, geo);
        buildHashMap();
    }

    /*
        public void calcDistances(){
            for (int i=0; i < infra.size(); ++i) {
                for (int j=0; j < infra.get(i).size(); ++j) {
                    int t1 = i;
                    int t2 = infra.get(i).get(j);
                    double distance = getDistance(t1, t2);
               }
            }
        }
    */
    private static double getDistance(int t1, int t2, List<Coordinates> geo) {
        return Math.pow(
                Math.pow(geo.get(t1).x - geo.get(t2).x, 2) +
                        Math.pow(geo.get(t1).y - geo.get(t2).y, 2),
                0.5);
    }


    //finds single link with highest traffic 
    private int calculateHighestTraffic() {
        int currentMax = 0;
        int best = 0;

        //System.out.println("Printing my hashmap"); 
        for (BidInfo b : availableBids) {
            int i = b.id;
            //System.out.println("Key: " + i + " Value: " + bidIdTraffic.get(i)); 
            if (bidIdTraffic.get(i) > currentMax) {
                currentMax = bidIdTraffic.get(i);
                best = i;
                //System.out.println("Found best: " + i + "," + currentMax); 
            }
        }
        return best;
    }

    private int highestTrafficEdgeWeight() {
        int currentMax = 0;
        int best = 0;

        //System.out.println("Printing my hashmap"); 
        for (BidInfo b : availableBids) {
            int i = b.id;
            //System.out.println("Key: " + i + " Value: " + bidIdTraffic.get(i)); 
            if (bidIdEdgeWeight.get(i) > currentMax) {
                currentMax = bidIdEdgeWeight.get(i);
                best = i;
                //System.out.println("Found best: " + i + "," + currentMax); 
            }
        }
        return best;
    }

    private void buildEdgeHashMap(int[][] edgeWeight, List<Coordinates> geo) {
        int bidID = 0;
        for (int l = 0; l < infra.size(); l++) {
            List<Integer> row = infra.get(l);
            for (int i = 0; i < row.size(); i++) {
                int there = row.get(i);
                Connection pair = new Connection(l, there);
                int traffic = edgeWeight[l][there];
                double distance = getDistance(l, there, geo);
                totalDistance = totalDistance + distance;
                bidIdEdgeWeight.put(bidID, traffic);
                bidDistance.put(bidID, distance);
                bidID++;
            }
        }
    }

    //adds to both hashmaps, and updates total traffic
    private void buildHashMap() {
        int bidID = 0;
        for (int l = 0; l < infra.size(); l++) {
            List<Integer> row = infra.get(l);
            for (int i = 0; i < row.size(); i++) {
                int there = row.get(i);
                Connection pair = new Connection(l, there);
                int traffic = transit[l][there];
                connections.put(pair, traffic);
                bidIdTraffic.put(bidID, traffic);
                totalTraffic += traffic;
                bidID++;
            }
        }
    }

    private void printRow(int[] row) {
        for (int i : row) {
            System.out.print(i);
            System.out.print("\t");
        }
        System.out.println();
    }

    private void printAllInfo(List<Bid> currentBids, List<BidInfo> allBids) {
        System.out.println("This is our budget: " + budget);
        System.out.println("This is the infra: " + infra);


        System.out.println("This is the transit: ");
        for (int[] row : transit) {
            printRow(row);
        }

        System.out.println("The current bids are");
        for (Bid b : currentBids) {
            System.out.println(b.bidder + " bids $" + b.amount + "for: " + "link 1 ID: " + b.id1 + " Link 2 ID: " + b.id2);
        }

        System.out.println("All bids info: ");
        for (BidInfo b : allBids) {
            System.out.println("Bid id: " + b.id + " town 1: " + b.town1 + " town 2: " + b.town2 + " bidded amount: "
                    + b.amount + " owner: " + b.owner);
        }
    }


    //bets percentage of budget = percent of traffic this link corresponds to
    private double calculateBid(int id) {
        double bid = bidIdMinBid.get(id);
        int traffic = bidIdTraffic.get(id);
        double dist = bidDistance.get(id);
        int n = infra.size();
        float percent = ((float) n*traffic / (float) totalTraffic);
        //System.out.println("Traffic: " + traffic + " totalTraffic: " + totalTraffic + " percent: " + percent); 
        double fractionOfBudget = (double) (n*dist * percent)/totalDistance;
        bid *= (3+fractionOfBudget);

        System.out.println("New bid: " + bidIdMinBid.get(id) + " our addition: " + bid);
        if (bid < budget) {
            return bid;
        } else {
            return bidIdMinBid.get(id);
        }
    }

    public Bid getBid(List<Bid> currentBids, List<BidInfo> allBids, Bid lastRoundMaxBid) {
        /*
        if (availableBids.size() != 0) {
            return null;
        }
        */
        //adding all available bids and adding to hashmap of bid id to minimum bid
        gu.update(allBids);
        buildEdgeHashMap(gu.edgeWeight, gu.geo);
        buildHashMap();
        for (BidInfo bi : allBids) {
            if (bi.owner == null) {
                availableBids.add(bi);
                bidIdMinBid.put(bi.id, bi.amount);
            }
        }

        if (availableBids.size() == 0) {
            return null;
        }


        int bidID = highestTrafficEdgeWeight();
        //int bidID = calculateHighestTraffic();
        double cashMoney = calculateBid(bidID);
        Bid bid = new Bid();
        bid.id1 = bidID;
        bid.amount = bidIdMinBid.get(bidID);
        //System.out.println("Bid before checking other playerse: " + cashMoney + "  " + bid.amount);
        double max = 0.0;
        // Check if another player has made a bid for this link.
        for (Bid b : currentBids) {
            if (b.id2 == -1){
                if (b.amount / bidDistance.get(b.id1) > max) {
                    max = b.amount / bidDistance.get(b.id1);
                    if (max > cashMoney / bidDistance.get(bid.id1)) {
                        lastBid = -1;
                        return null;
                    } else if (budget - max * bidDistance.get(bid.id1) - 10000 < 0.) {
                        if (max*bidDistance.get(bid.id1) == lastBid) return null;
                        lastBid = -1;
                        return null;
                    } else {
                        if (max * bidDistance.get(bid.id1) + 10000>bidIdMinBid.get(bid.id1))
                        bid.amount = max * bidDistance.get(bid.id1) + 10000;
                    }
                }
            }
            else {
                if (b.amount / (bidDistance.get(b.id1)+bidDistance.get(b.id2)) > max) {
                    max = b.amount / (bidDistance.get(b.id1)+bidDistance.get(b.id2));
                    if (max > cashMoney / bidDistance.get(bid.id1)) {
                        lastBid = -1;
                        return null;
                    } else if (budget - max * bidDistance.get(bid.id1) - 10000 < 0.) {
                        lastBid = -1;
                        return null;
                    } else {
                        if (max * bidDistance.get(bid.id1) + 10000>bidIdMinBid.get(bid.id1))
                        bid.amount = max * bidDistance.get(bid.id1) + 10000;
                    }
                }
            }
        }
        if (bid.amount == lastBid + 10000) return null;
        if (bid.amount > budget) {
            lastBid = -1;
            return null;
        }
        lastBid = bid.amount;
        return bid;
    }

    public void updateBudget(Bid bid) {
        if (bid != null) {
            budget -= bid.amount;
        }

        availableBids = new ArrayList<>();
    }
}
