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
    private double initBudget;

    private List<BidInfo> availableBids = new ArrayList<>();
    private List<Integer> availableLinks = new ArrayList<>();
    private Map<Integer, Double> minAmounts = new HashMap<Integer, Double>();
    private Map<Integer, Double> originalMins = new HashMap<Integer, Double>();
    private Map<String, Double> playerBudgets = new HashMap<String, Double>();
    //we are provided last round maxBid value
    private Bid lastWinner = new Bid();
    private boolean firstRound;

    //Variables for choosing link
    private int bestLink;
    private double bestValue;


    private List<String> ownedCities = new ArrayList<>();
    final static double margin = 0.8;

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
        this.initBudget = budget;

        // Initialize availableLinks
        for (BidInfo bi : allBids) {
          if (bi.owner == null) {
            availableLinks.add(bi.id);
            minAmounts.put(bi.id, bi.amount);
            originalMins.put(bi.id, bi.amount);
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
      if (bid1 == null || bid2 == null){
        if (bid1 == null && bid2 == null){
          return true;
        }
        else{
          return false;
        }
      }

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

      // BOOK KEEPING AND ROUND CHANGES //

      if(!bidEquals(lastWinner, lastRoundMaxBid)){
        // Entered a new round, make necessary updates
        // System.out.println("Making updates!");
        this.lastWinner = lastRoundMaxBid;

        if(lastWinner != null){
          // Remove purchased link
          if (availableLinks.contains(lastWinner.id1)) {
            availableLinks.remove(Integer.valueOf(lastWinner.id1));
          }
          if(lastWinner.id2 != -1){
            availableLinks.remove(Integer.valueOf(lastWinner.id2));
          }
          // Restore original minimum prices
          this.minAmounts = this.originalMins;

          // Update opposing player budgets
          if(playerBudgets.containsKey(lastRoundMaxBid.bidder)){
            double oppBudget = playerBudgets.get(lastRoundMaxBid.bidder);
            playerBudgets.put(lastRoundMaxBid.bidder, oppBudget - lastRoundMaxBid.amount);
          }
          else{
            double oppBudget = this.initBudget;
            playerBudgets.put(lastRoundMaxBid.bidder, oppBudget - lastRoundMaxBid.amount);
          }
        }

        // Find the most valuable link for us
        this.bestLink = -1;
        this.bestValue = 0;
        for (int linkId : availableLinks){
          double unitValue = railValues.get(linkId) / railDistance.get(linkId);
          if (unitValue > this.bestValue){
            this.bestLink = linkId;
            this.bestValue = unitValue;
          }
        }
      }

      // MAIN BIDDING STRATEGY //

      // Search through bids to find current winner
      Bid curMax = new Bid();
      curMax.amount = 0.0;
      curMax.bidder = "None";
      double unitPrice = 0.0;

      for(Bid pastBid : currentBids){
        // Update minimum bid amounts
        if (pastBid.amount > minAmounts.get(pastBid.id1)){
          minAmounts.put(pastBid.id1, pastBid.amount);
        }

        double bidPrice = pastBid.amount / railDistance.get(pastBid.id1);
        if (pastBid.id2 != -1){
          double dist = railDistance.get(pastBid.id1) + railDistance.get(pastBid.id2);
          bidPrice = pastBid.amount / dist;
        }
        // Update max
        if (bidPrice > unitPrice){
          curMax = pastBid;
          unitPrice = bidPrice;
        }
        // Only iterate until our latest bid
        String player = pastBid.bidder;
        if (player.equals("g5")){
          break;
        }
      }

      System.out.println("The current max bidder is:" + curMax.bidder);
      // If we have the winning bid, return null
      if (curMax.bidder.equals("g5")){
        return null;
      }
      else{ // If we aren't winning, increment the bid on our most valuable link
        double maxAmount = railValues.get(this.bestLink) * margin;
        double maxUnit = maxAmount / railDistance.get(this.bestLink);
        if (maxUnit > unitPrice){
          double amount = unitPrice * railDistance.get(this.bestLink) + 1;
          if(amount < minAmounts.get(this.bestLink) + 10000){
            amount = minAmounts.get(this.bestLink) + 10000; //increment
          }
          if (amount < this.budget){
            Bid ourBid = new Bid();
            ourBid.id1 = this.bestLink;
            ourBid.amount = amount;
            return ourBid;
          }
          else{
            return null;
          }
        }
      }
      // If we don't want to increment, drop out
      return null;
    }

    public void updateBudget(Bid bid) {
        if (bid != null) {
            budget -= bid.amount;
        }

        availableBids = new ArrayList<>();
    }
}
