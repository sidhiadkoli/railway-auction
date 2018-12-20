package railway.g8;

import railway.sim.utils.Bid;
import railway.sim.utils.BidInfo;
import railway.sim.utils.Coordinates;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Arrays;
import java.util.Map; 
import java.util.Set; 
import java.util.TreeMap; 


import java.util.HashMap;


class Connection {
    int row;
    int column;
    int id; 

    public Connection(int row, int column, int id) {
        this.row = row;
        this.column = column;
        this.id = id; 
    }
}

class Pair {
    int row; 
    int column; 

    public Pair(int row, int column){
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
    public List<Integer> weOwn;
    //hashmap of all our connections- and then map connection 1 etc. to bid ID 
    //key: coordinates, valueL bidID
    private HashMap<Pair, Integer> coordinateBidId = new HashMap<Pair, Integer>();
    //private HashMap<Integer, Connection> bidIdCoordinate = new HashMap<Integer, Connection>(); 

    //okay new HashMap that just uses bid ID instead 
    //maps bid ID to amount of traffic on that link 
    private HashMap<Integer, Integer> bidIdTraffic = new HashMap<Integer, Integer>();
    private HashMap<Integer, Double> bidIdMinBid = new HashMap<Integer, Double>();
    private HashMap<Integer, Double> bidDistance = new HashMap<Integer, Double>();

    public Bid lastBidHistory;
    //Hahsmap for edgeweights
    private HashMap<Integer, Integer> bidIdEdgeWeight = new HashMap<Integer, Integer>();

    //of Bid id to dup bid
    private HashMap<Integer, Integer> duplicateTracks = new HashMap<Integer, Integer>(); 

    private List<Connection> allLinks = new ArrayList<>(); 


    //To find adjacent 
    private List<Integer> hubs = new ArrayList<>(); 
    //hashmap of two good adjacent links to bid on! 
    private HashMap<Integer,Integer> goodAdj = new HashMap<Integer,Integer>(); 
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
        weOwn = new ArrayList<>();
        gu = new GraphUtility(geo, infra, transit, townLookup);
        lastBid = -1;
        lastBidHistory = new Bid();
        lastBidHistory.id1 = -1;
        lastBidHistory.id2 = -1;
        lastBidHistory.amount = -1;

        buildEdgeHashMap(gu.edgeWeight, geo);
        buildHashMap();
        
        //HEY GUYS this demos the structures I've built- you have a list all of all the connections, 
        //and a hashmap of the coordinates to the bid ID. 
        //Don't forget to comment out print statements once you know what its doing! 
        /*
        System.out.println("The list of all connections: " );
        for(Connection c: allLinks){

            //int id = coordinateBidId.get(c); 
            System.out.println("Link ID: " + c.id + ": " + c.row + ", " + c.column);  
        } 
        */
        findDuplicates(); 
        //System.out.println("The dups are: " + duplicateTracks); 

        bestAdjacent(); 
    }

    private static double getDistance(int t1, int t2, List<Coordinates> geo) {
        return Math.pow(
                Math.pow(geo.get(t1).x - geo.get(t2).x, 2) +
                        Math.pow(geo.get(t1).y - geo.get(t2).y, 2),
                0.5);
    }

     private void findDuplicates(){ 
        for(int i = 0; i < allLinks.size()-1; i++){
            Connection c1 = allLinks.get(i);
            Connection c2 = allLinks.get(i+1); 
            if(c1.row == c2.row && c1.column == c2.column){
                duplicateTracks.put(c1.id, c2.id); 
                duplicateTracks.put(c2.id, c1.id);
            }
        }
     }

     private void bestAdjacent(){
     //sort the row to num conn map  
       
        int traffic1 = -1; 
        int traffic2 = -1;
        int link1 = -1; 
        int link2 = -1; 
        for(int i: hubs){

            traffic1 = bidIdEdgeWeight.get(i); 
            traffic2 = bidIdEdgeWeight.get(i+1); 
            int traffic3 = bidIdEdgeWeight.get(i+2); 

            if(traffic1 >= traffic2 && traffic1 >= traffic3){
                link1 = i; 
                if(traffic2 >= traffic3){
                    link2 = i+1; 
                }
                else{
                    link2 = i+2; 
                }
            }
            if (traffic2 >= traffic1 && traffic2 >= traffic3){ 
                link1 = i+1;
                if(traffic1 >= traffic3){
                    link2 = i; 
                }
                else{
                    link2 = i+2; 
                }
            } 
            else{
                link1 = i+2; 
                if(traffic1 >= traffic2){
                    link2 = i; 
                }
                else{
                    link2 = i+1; 
                }
            } 

            goodAdj.put(link1,link2); 
        }
    } 

     private void updateAdjacent(){ 

     }

    //finds single link with highest traffic 
    private int calculateHighestTraffic(){
        int currentMax = 0;
        int best = 0;
        for (BidInfo b : availableBids) {
            int i = b.id;
            if (bidIdTraffic.get(i) > currentMax) {
                currentMax = bidIdTraffic.get(i);
                best = i;
            }
        }
        return best;
    }

    private int highestTrafficEdgeWeight() {
        int currentMax = 0;
        int best = 0;
        for (BidInfo b : availableBids) {
            int i = b.id;
            if (bidIdEdgeWeight.get(i) > currentMax) {
                currentMax = bidIdEdgeWeight.get(i);
                best = i;
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
        int largestList = 0;         

        for (int l = 0; l < infra.size(); l++) {
            List<Integer> row = infra.get(l);
            
            //check for hubs 
            if(row.size() > 3){
                hubs.add(bidID);   
            } 

            for (int i = 0; i < row.size(); i++) {
                int there = row.get(i);
                
                Pair p = new Pair(l,there); 
                coordinateBidId.put(p, bidID); 

                if(l<=there){
                    Connection con = new Connection(l, there, bidID);
                    allLinks.add(con); 
                }
                else{
                    Connection con = new Connection(there, l, bidID);
                    allLinks.add(con);
                }
                 
                int traffic = transit[l][there];

                //mapping bid Ids and traffic 
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
        double percent = ((float) n*traffic / (float) totalTraffic);
        percent = percent*Math.log10(1+Math.log10(n));
        //percent = ((float) n*traffic*percent / (float) totalTraffic);
        //percent = ((float) n*traffic*percent / (float) totalTraffic);
        //System.out.println("Traffic: " + traffic + " totalTraffic: " + totalTraffic + " percent: " + percent); 
        //double fractionOfBudget = (double) (n*dist * percent)/totalDistance;
        double fractionOfBudget = percent;
        bid *= (1+fractionOfBudget);

        //System.out.println("New bid: " + bidIdMinBid.get(id) + " our addition: " + bid);
        if (bid < budget) {
            return bid;
        } else {
            return bidIdMinBid.get(id);
        }
    }
    private boolean checkBidNotEquality(Bid b1, Bid b2){
        if (b2==null){
            return false;
        }
        if ((b1.id1==b2.id1)&&(b1.id2==b2.id2)&&(b1.amount==b2.amount)){
            return false;
        }
        else {
            return true;
        }
    }

    public Bid getBid(List<Bid> currentBids, List<BidInfo> allBids, Bid lastRoundMaxBid) {

        if(checkBidNotEquality(lastBidHistory,lastRoundMaxBid)){
            gu.update(allBids);
            buildEdgeHashMap(gu.edgeWeight, gu.geo);
            buildHashMap();
            lastBidHistory.id1 = lastRoundMaxBid.id1;
            lastBidHistory.id2 = lastRoundMaxBid.id2;
            lastBidHistory.amount = lastRoundMaxBid.amount;
        }
        availableBids = new ArrayList<>();
        for (BidInfo bi : allBids) {
            if ((bi.owner == null)&&(!weOwn.contains(bi.id))) {
                if(duplicateTracks.get(bi.id)==null){
                    availableBids.add(bi);
                    bidIdMinBid.put(bi.id, bi.amount);                    
                }
                else{
                    int otherID = duplicateTracks.get(bi.id);
                    if(!weOwn.contains(otherID)){
                        if((duplicateTracks.get(bi.id-1)!=null)&&(duplicateTracks.get(bi.id-1)==bi.id)){
                            int otherID2 = bi.id-1;
                            if(!weOwn.contains(otherID2)){
                                availableBids.add(bi);
                                bidIdMinBid.put(bi.id, bi.amount);                                
                            }
                            else{
                                weOwn.contains(bi.id);
                            }
                        }
                        else{
                            availableBids.add(bi);
                            bidIdMinBid.put(bi.id, bi.amount);
                        }
                    }

                }
            }
        }

        if (availableBids.size() == 0) {
            return null;
        }

        int bidID = highestTrafficEdgeWeight();
        //System.out.println(duplicateTracks.get(bidID));
        double cashMoney = calculateBid(bidID);
        Bid bid = new Bid();
        bid.id1 = bidID;
        bid.amount = bidIdMinBid.get(bidID);
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
            weOwn.add(bid.id1);
            int id = bid.id1;
            while(true){
                if(duplicateTracks.get(id)!=null){
                    weOwn.add(duplicateTracks.get(id));
                    id += 1;
                }
                else{
                    break;
                }
            }
        }
        availableBids = new ArrayList<>();
    }
}
