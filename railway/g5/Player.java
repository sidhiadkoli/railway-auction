package railway.g5;

import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.lang.Math;

// To access data classes.
import railway.sim.utils.*;

public class Player implements railway.sim.Player{
    // Random seed of 42.
    private int seed = 42;
    private Random rand;

    private double budget;

    private List<BidInfo> availableBids = new ArrayList<>();
    private List<Integer> availableLinks = new ArrayList<>();
    private Map<Integer, Double> minAmounts = new HashMap<Integer, Double>();
    private Map<String, Double> playerBudgets = new HashMap<String, Double>();
    //we are provided last round maxBid value 
    private Bid lastWinner = new Bid();
    private boolean firstRound;	

    private List<String> ownedCities = new ArrayList<>();
    final static double profitMargin = 0.8;

    private Map<Integer, Double> railValues = new HashMap<Integer, Double>();
    private Map<Integer, Double> railDistance = new HashMap<Integer, Double>();
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

        // Initialize availableLinks
        for (BidInfo bi : allBids) {
          if (bi.owner == null) {
            availableLinks.add(bi.id);
            minAmounts.put(bi.id, bi.amount);
          }
        }

        Map<Integer, Integer> cityTraffic = new HashMap<Integer, Integer>();
        for (int m=0; m<geo.size(); m++){
          cityTraffic.put(m, 0);
          for (int n=0; n<geo.size(); n++){
            if( n == m ) {
              for(int p=0; p<geo.size(); p++){
                cityTraffic.put(m, cityTraffic.get(m)+transit[n][p]);
              }
            } else {
              cityTraffic.put(m, cityTraffic.get(m)+transit[n][m]);
            }
          }
        }

        int id = 0;
        for (int i=0; i<infra.size(); ++i){
          for (int j=0; j<infra.get(i).size(); ++j){
            int irails = infra.get(i).size();
            int jrails = infra.get(infra.get(i).get(j)).size();

		        //		System.out.printf("i is %d, irails starting at %d\n", i, irails);
		        //		System.out.printf("j is %d, jrails starting at %d\n", j, jrails);

            for (int k=0; k<infra.size(); ++k){
              if (infra.get(k).contains(i) ){
                irails +=1;
			      //	System.out.printf("found a %d in line %d, irails now %d\n", i, k, irails);
		          }
              if (infra.get(k).contains(infra.get(i).get(j)) ){
                jrails +=1;
			//	System.out.printf("found a %d in line %d, jrails now %d\n", j, k, jrails);
		    }
		}
		double value = 0;
		if (irails != 0){
		    value += cityTraffic.get(i)/irails;
		}
		if (jrails != 0){
		    value += cityTraffic.get(infra.get(i).get(j))/jrails;
		}
		Coordinates p1 = geo.get(i);
		Coordinates p2 = geo.get(infra.get(i).get(j));
		//		System.out.printf("%f, %f and %f, %f\n", p1.x, p1.y, p2.x, p1.y);
		double dist = Math.sqrt((p1.x-p2.x)*(p1.x-p2.x) + (p1.y-p2.y)*(p1.y-p2.y));
       		value = value*dist*10;
		railValues.put(id, value);
		railDistance.put(id, dist);
		id++;
	    }
	}
    }

    public boolean bidEquals(Bid bid1, Bid bid2){
      boolean result = true;
     
      if(bid1.id1 != bid2.id1){
        result = false;
      }
     
      if(bid1.id2 != bid2.id2){
        result = false;
      }
      
      if(bid1.amount != bid2.amount){
        result = false;
      }
      if(bid1.bidder != bid2.bidder){
        result = false;
      }
      return result;
    }

    public Bid getBid(List<Bid> currentBids, List<BidInfo> allBids, Bid lastRoundMaxBid){
     
      firstRound = (lastRoundMaxBid == null);
      if(!firstRound && !bidEquals(lastWinner, lastRoundMaxBid)){
        // Entered a new round, make necessary updates
        lastWinner = lastRoundMaxBid;

        // Remove purchased link
        if (availableLinks.contains(lastWinner.id1)) {
	  availableLinks.remove(Integer.valueOf(lastWinner.id1));
	}

        if(lastWinner.id2 != -1){
          availableLinks.remove(Integer.valueOf(lastWinner.id2));
        }

        // Update player budget
        // 
      }
      // Check if everyone else has dropped out
      boolean uncontested = false;
      if(currentBids.size() > 0){
        if(currentBids.get(0).bidder == "g5"){
          uncontested = true;
        }
      }

      if(uncontested){
        // Find the winning bid
        Bid curMax = null;
        double unitPrice = 0.0;

        // Determine if there is a link we'd like to buy at that price
        for(Bid pastBid : currentBids){
          String player = pastBid.bidder;
          // Get actual distance of link
	  double distance = 1;
          double curPrice = pastBid.amount / distance;

	  // If curPrice > initPrice
	  //  set winning bid
        }

	// For loop through available bids
	// Ratio of value vs price we would pay
	// Greatest ratio is the bid that we place when uncontested

        // Buy the best available link / pair
        return null;
      }
      else{
        // Sort through bids to update current minimums
        List<Integer> noBidLinks = availableLinks;
        for(Bid pastBid : currentBids){

          // Update links that haven't been bid on
          if(noBidLinks.contains(pastBid.id1)){
            noBidLinks.remove(Integer.valueOf(pastBid.id1));
          }
          if(noBidLinks.contains(pastBid.id2)){
            noBidLinks.remove(Integer.valueOf(pastBid.id2));
          }
          // Update min amount TODO adapt for pair bids
          minAmounts.put(pastBid.id1, pastBid.amount);
	  
          // If there are links without any bids
          if(noBidLinks.size() != 0){
            // Choose one and make a minimum bid
            int linkId = noBidLinks.get(rand.nextInt(noBidLinks.size()));

            Bid bid = new Bid();
            bid.id1 = linkId;
            bid.amount = minAmounts.get(linkId);
            return bid;
          }
          // Increment bid on a valuable enough link
          for (int linkId : availableLinks){
            if(railValues.get(linkId)*10 > minAmounts.get(linkId) + 10000){
	      Bid bid = new Bid();
              bid.id1 = linkId;
              bid.amount = minAmounts.get(linkId) + 10000;
              return bid;
            }
          }
        }
      }
      // If there are no rails we'd like to purchase at the highest price / distance
      // Drop out
      return null;
    }

    public void updateBudget(Bid bid) {
        if (bid != null) {
            budget -= bid.amount;
        }

        availableBids = new ArrayList<>();
    }
}
