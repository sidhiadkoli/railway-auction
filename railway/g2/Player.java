package railway.g2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.HashMap;
import java.util.Map;

// To access data classes.
import railway.sim.utils.*;


class AvailableLinks {
    public int id;
    public String town1;
    public int town1_id;
    public String town2;
    public int town2_id;
    public double traffic;
    public double expectedTraffic;
    public double distance;
    public double originalValue;
    public double bidValue;
    public double expectedValue;
}

public class Player implements railway.sim.Player {
    // Random seed of 42.
    private int seed = 42;
    private Random rand;

    private double budget;
    private String name;
    private List<List<Integer>> infra;
    private List<String> townLookup;
    private Map<String, Integer> townRevLookup = new HashMap<>();
    private List<Coordinates> geo;
    private static int[][] transit;
    private List<AvailableLinks> availableLinks;

    private List<BidInfo> availableBids = new ArrayList<>();
    private WeightedGraph weightedGraph;
    // private static List<LinkInfo> links = new ArrayList<>();

    public Player() {
        rand = new Random();
    }

    public void init(
        String name,
        double budget,
        List<Coordinates> geo,
        List<List<Integer>> infra,
        int[][] transit,
        List<String> townLookup,
        List<BidInfo> allBids) {

        this.budget = budget;
        this.name = name;
        this.infra = infra;
        this.townLookup = townLookup;
        this.geo = geo;
        this.transit = transit;

        for(int i=0; i<townLookup.size(); i++)
            this.townRevLookup.put(townLookup.get(i), i);
    }

    public Bid getBid(List<Bid> currentBids, List<BidInfo> allBids, Bid lastRoundMaxBid) {
        // The random player bids only once in a round.
        // This checks whether we are in the same round.
        // Random player doesn't care about bids made by other players.
        // if (availableBids.size() != 0) {
        //     return null;
        // }

        initializeAvailableLinks(currentBids, allBids);
        //s is the most valuable node
        int s = getMostValuableNodeNaive();
        //System.out.println("Most valuable node: " + s + " i.e " + townLookup.get(s));
        // s=getMostValuableNodeNaive();

        //get the weightedGraph given node s
        weightedGraph=initializeWeightedGraph(s,townLookup,infra);
        //weightedGraph.print();
        //implement dijkstra to the weightedGraph
        int[][] prediction = Dijkstra.dijkstra(weightedGraph, s);

        //printAvailableLinks();

        for (int n=0;n<townLookup.size();n++){
            //System.out.println(weightedGraph.getLabel(s) + " to " + weightedGraph.getLabel(n));
            //Dijkstra.printPath(weightedGraph, prediction, n);

            List<List<Integer>> allPaths = Dijkstra.getPaths(weightedGraph, prediction, n);
            int number_of_paths = allPaths.size();
            for(List<Integer> a: allPaths){
            	if(a.size() < 2)
            		continue;

            	int t1 = a.get(0);
            	int t2 = a.get(1);
            	int total_traffic = transit[t1][n] + transit[n][t1];
            	updateExpectedTraffic(t1, t2, total_traffic);
            	//System.out.println("Add to from " + t1 + " to " + t2);
            	//System.out.println("Traffic 1: " + transit[t1][n] + " Traffic 2: " + transit[n][t1]);

            	//find t1 - t2 or t2 - t1 link and add ())

            }
            //System.out.println();
        }

        //printAvailableLinks();


        //System.out.println(name + " has budget: " + budget);
        
        //for(int i=0;i<infra.size();i++){
        //    for(int j=0;j<infra.get(i).size();j++){
        //        //System.out.println(" infra shows as :"+i+" to "+infra.get(i).get(j));
        //    }
        //}
        

        /*for (BidInfo bi : allBids) {
            //System.out.println("Bid id: " + bi.id + " Bid from " + bi.town1 + " to " + bi.town2 + " made by " + bi.owner + " for: " + bi.amount);
            if (bi.owner == null) {
                bi.amount += 1111;
                availableBids.add(bi);
            }
        }

        if (availableBids.size() == 0) {
            return null;
        }*/

        if(availableLinks.size() == 0) {
        	return null;
        }

        //printAvailableLinks();
        Collections.sort(availableLinks, new Comparator<AvailableLinks>(){
        	public int compare(AvailableLinks l1, AvailableLinks l2){
        		if(l1.expectedValue > l2.expectedValue)
        			return 1;
        		if(l1.expectedValue == l2.expectedValue)
        			return 0;
        		return -1;
        	}
        });
        //System.out.println("------------------- AFTER SORTING ---------------------");
        //printAvailableLinks();

        /*Collections.sort(availableBids,new Comparator<BidInfo>(){
            public int compare(BidInfo o1, BidInfo o2) {
                if(o1.amount > o2.amount){
                    return 1;
                }
                if(o1.amount == o2.amount){
                    return 0;
                }
                return -1;
            }
        });*/

        AvailableLinks link_to_bid;
        link_to_bid = availableLinks.get(availableLinks.size()-1);

        //System.out.println("BUDGET:");
        //System.out.println(budget);
        //for (AvailableLinks link : availableLinks) {
        //    System.out.println(link.town1 + "-" + link.town2);
        //}
        //System.out.println(availableLinks);
        //System.out.println(allBids);
        for (Integer i = availableLinks.size()-1; i >= 0; i--)
        {
            AvailableLinks candidate = availableLinks.get(i);
            //if (candidate.owner.equals(this.getName())) {
            //    continue;
            //}

            //System.out.print("\t");
            //System.out.println(candidate.originalValue);

            if (budget - candidate.originalValue >= 0.) {
                link_to_bid = candidate;
                break;
            }
        }

        BidInfo ourBid = new BidInfo();
        ourBid.id = link_to_bid.id;
        ourBid.town1 = link_to_bid.town1;
        ourBid.town2 = link_to_bid.town2;
        ourBid.amount = link_to_bid.originalValue;
        //System.out.println("PRICE:");
        //System.out.println(ourBid.amount);
        ourBid.owner = null;


        //BidInfo randomBid = availableBids.get(availableBids.size()-1);
        double amount = ourBid.amount;

        // Don't bid if the random bid turns out to be beyond our budget.
        if (budget - amount < 0.) {
            return null;
        }

        // Check if another player has made a bid for this link.
        for (Bid b : currentBids) {
            if (b.id1 == ourBid.id || b.id2 == ourBid.id) {
                if (budget - b.amount - 10000 < 0.) {
                    return null;
                }
                if(b.bidder.equals("g2")){
                    return null;
                }

                amount = b.amount + 10000;
                if(amount > link_to_bid.expectedValue) {
                	return null;
                }
                break;
            }
        }

        //System.out.println("Link from " + link_to_bid.town1 + " to " + link_to_bid.town2);
        //System.out.println("Original traffic: " + link_to_bid.traffic + " Expected traffic: " + link_to_bid.expectedTraffic);
        //System.out.println("Original val: " + link_to_bid.originalValue + " Expected Val: " + link_to_bid.expectedValue);
        //System.out.println();

        Bid bid = new Bid();
        bid.amount = amount;
        bid.id1 = ourBid.id;

        return bid;
    }

    public void updateBudget(Bid bid) {
        if (bid != null) {
            budget -= bid.amount;
        }

        availableBids = new ArrayList<>();
    }

    //iterate through available links and get most valuable 2 links
    //available links will be in bidInfo
    public void initializeAvailableLinks(List<Bid> currentBids, List<BidInfo> allBids){
        availableLinks = new ArrayList<>();
        for (BidInfo bid : allBids) {
            if(bid.owner == null){
                AvailableLinks link = new AvailableLinks();
                link.id = bid.id;
                link.town1 = bid.town1;
                link.town2 = bid.town2;
                link.town1_id = townRevLookup.get(link.town1);
                link.town2_id = townRevLookup.get(link.town2);

                for (Bid currentBid : currentBids) {
                    if(currentBid.id1 == link.id || currentBid.id2 == link.id)
                        link.bidValue = currentBid.amount;
                }

                link.distance = getDistance(link.town1_id, link.town2_id);
                link.traffic = transit[link.town1_id][link.town2_id];
                link.originalValue = link.distance*link.traffic*10;
                link.expectedTraffic = link.traffic;	//reset expected traffic
                link.expectedValue = link.originalValue;

                //System.out.println(link.id + " " + link.town1 + " " + link.town2 + " " + link.originalValue + " " + link.bidValue + " " + 
                //    link.distance + " " + link.traffic);
                //System.out.println("Expected Value: " + (link.distance*link.traffic*10));
                //link.expectedValue = null;
                availableLinks.add(link);
            }
        }

        //for(int i=0; i<availableLinks.size(); i++)
        //    System.out.println(availableLinks.get(i).town1 + " " + availableLinks.get(i).town2);

    }

    private WeightedGraph initializeWeightedGraph(int s,List<String> town, List<List<Integer>> infra)
    {
    	//System.out.println("PRINTING INFRA INFRA INFRA");
        List<List<Integer>> graph_infra = infra;
        List<String> graph_townLookup = town;
        double distance = 0;
        WeightedGraph t = new WeightedGraph(town.size());
        for(int i=0;i<town.size();i++){
            t.setLabel(town.get(i));
        }

		//for(int i=0;i<infra.size();i++){
        //    for(int j=0;j<infra.get(i).size();j++){
        //    	System.out.print(infra.get(i).get(j) + " ");
        //    }
        //    System.out.println("");
        // }

        Double hyperparam = 0.75;

        for(int i=0;i<infra.size();i++){
            for(int j=0;j<infra.get(i).size();j++){
                distance = getDistance(i, infra.get(i).get(j))+ (hyperparam * 200);
                t.addEdge(i, infra.get(i).get(j), distance);
            }
        }
        return t;
    }

    private int getMostValuableNodeNaive(){

		int rows = transit.length;
		int cols = transit[0].length;

    	int mvn = availableLinks.get(0).town1_id;
    	int maxRowSum = -1;
    	for(int i=0; i<availableLinks.size(); i++){
    		int town1_id = availableLinks.get(i).town1_id;
    		int town2_id = availableLinks.get(i).town2_id;

			int rowSum1 = 0;
			int rowSum2 = 0;
			for(int k=0; k<cols; k++){
				rowSum1 += transit[town1_id][k] + transit[k][town1_id];
				rowSum2 += transit[town2_id][k] + transit[k][town2_id];
			}

			int tempRowSum;
			int tempTownId;
			if(rowSum1 > rowSum2){
				tempRowSum = rowSum1;
				tempTownId = town1_id;
			}
			else{
				tempRowSum = rowSum2;
				tempTownId = town2_id;
			}

			if(tempRowSum > maxRowSum)
			{
				maxRowSum = tempRowSum;
				mvn = tempTownId;
			}
    	}

    	return mvn;
    }

    private void updateExpectedTraffic(int town1_id, int town2_id, double addedTraffic){
    	for(int i=0; i<availableLinks.size(); i++){
    		AvailableLinks currentLink = availableLinks.get(i);
    		int t1_id = currentLink.town1_id;
    		int t2_id = currentLink.town2_id;

    		if((t1_id==town1_id && t2_id==town2_id) || (t1_id==town2_id && t2_id==town1_id)){
    			currentLink.expectedTraffic += addedTraffic;
    			currentLink.expectedValue = currentLink.expectedTraffic*currentLink.distance*10;
    		}

    	}
    }

    private double getDistance(int t1, int t2) {
        return Math.pow(
            Math.pow(geo.get(t1).x - geo.get(t2).x, 2) +
                Math.pow(geo.get(t1).y - geo.get(t2).y, 2),
            0.5);
    }

    private void printAvailableLinks(){
    	for(int i=0; i<availableLinks.size(); i++){
    		AvailableLinks currentLink = availableLinks.get(i);
    		System.out.println("From " + currentLink.town1_id + currentLink.town1 + " to " + currentLink.town2_id + currentLink.town2 +
    			" original traffic: " + currentLink.traffic + " expected traffic: " + currentLink.expectedTraffic + 
    			" original value: " + currentLink.originalValue + " expected value: " + currentLink.expectedValue);
    	}
    }
}
