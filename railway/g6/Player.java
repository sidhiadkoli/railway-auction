package railway.g6;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;



import java.awt.Desktop;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import javafx.util.*;

// To access data classes.
import railway.sim.utils.*;

public class Player implements railway.sim.Player {
    // Random seed of 42.
    private int seed = 42;
    private Random rand;

    String name;
    private double budget;
    private List<Coordinates> geo;
    private List<List<Integer>> infra;
    private int[][] transit;
    private List<String> townLookup;
     private double[][] revenue;
     private int[][] owner;
     private double[][] indirect_revenue;
     private Integer[][] paths;
     private Integer [][][] prev_paths;
     private HashMap<String, Integer> map;
     private Integer owner_idx;
     private double discount_factor = 0.9;


    private class playerNames
    {
        String name;

        public String getName()
        {
            return name;
        }

    }
    private List<playerNames> origPlayers;


    private List<BidInfo> availableBids = new ArrayList<>();

    public Player() {
        rand = new Random();
        origPlayers = new ArrayList<playerNames>();
        for(int i=1;i<=8;i++)
        {
            playerNames tmp = new playerNames();
            tmp.name = "g" + Integer.toString(i);
            origPlayers.add(tmp);
        }
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
        this.geo = deepClone(geo);
        this.infra = deepClone(infra);
        this.transit = deepClone(transit);
        this.townLookup = deepClone(townLookup);

        Integer size = townLookup.size();
        indirect_revenue = new double[size][size];
        owner = new int[size][size];
        map = new HashMap<>();
        owner_idx = 1;

         revenue = getRevenue();
         prev_paths = new Integer[size][size][size];
          for(int i=0;i<size;i++)
          {
            for(int j=0;j<size;j++)
            {
                for(int k=0;k<size;k++)
                    prev_paths[i][j][k]=-1;
            }
          }
          try
         {
            paths = Floyd_Warshall(); 
            update_propogation(paths);
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }

         // for(int i=0;i<size;i++)
         // {
         //    System.out.println("expected revenues");
         //    for(int j=0;j<size;j++)
         //    {
         //        System.out.print(indirect_revenue[i][j] + " ");

         //    }
         //    System.out.println();
         // }


    }

    // public Bid getBid(List<Bid> currentBids, List<BidInfo> allBids) {
    //     // The random player bids only once in a round.
    //     // This checks whether we are in the same round.
    //     // Random player doesn't care about bids made by other players.
    //     if (availableBids.size() != 0) {
    //         return null;
    //     }

    //     for (BidInfo bi : allBids) {
    //         if (bi.owner == null) {
    //             availableBids.add(bi);
    //         }
    //     }

    //     if (availableBids.size() == 0) {
    //         return null;
    //     }

    //     BidInfo randomBid = availableBids.get(rand.nextInt(availableBids.size()));
    //     double amount = randomBid.amount;

    //     // Don't bid if the random bid turns out to be beyond our budget.
    //     if (budget - amount < 0.) {
    //         return null;
    //     }

    //     // Check if another player has made a bid for this link.
    //     for (Bid b : currentBids) {
    //         if (b.id1 == randomBid.id || b.id2 == randomBid.id) {
    //             if (budget - b.amount - 10000 < 0.) {
    //                 return null;
    //             }
    //             else {
    //                 amount = b.amount + 10000;
    //             }

    //             break;
    //         }
    //     }

    //     Bid bid = new Bid();
    //     bid.amount = amount;
    //     bid.id1 = randomBid.id;

    //     return bid;
    // }


   public Bid getBid(List<Bid> currentBids, List<BidInfo> allBids, Bid lastRoundMaxBid) 
{
        

       availableBids.clear();
        double total_expected_utility=0;

        for (BidInfo bi : allBids) {
            if (bi.owner == null) {
                availableBids.add(bi);
                Pair<Integer,Integer> T= getTownIndices(bi.town1,bi.town2);
                total_expected_utility+= indirect_revenue[T.getKey()][T.getValue()];
            }
            else
            {  

                Pair<Integer,Integer> towns = getTownIndices(bi.town1,bi.town2);
                if(map.containsKey(bi.owner))
                {
                    owner[towns.getKey()][towns.getValue()] = map.get(bi.owner);
                }
                else
                {
                    map.put(bi.owner,owner_idx);
                    owner[towns.getKey()][towns.getValue()] = owner_idx;
                     owner_idx++;
                }

            }
        }


        paths = Floyd_Warshall(); 
        update_propogation(paths);

        if (availableBids.size() == 0) {
            return null;
        }


        double max_ratio = 1;

        double our_ratio = 0;

        //for(Bid curr : currentBids)
        for(int i=0;i<currentBids.size();i++)
        {
                Bid curr = currentBids.get(i);

                double dist =0;
                for(BidInfo tmp: allBids)
                {
                    if(tmp.id == curr.id1 || tmp.id == curr.id2)
                    {
                        dist+= getDistance(tmp.town1,tmp.town2);
                    }
                }

                max_ratio = Math.max(max_ratio, curr.amount/dist);
                if(curr.bidder.equals(name))
                {
                    our_ratio = curr.amount/dist;
                    break;
                }
        }


        Boolean bid_in_round =  false;
        double profit = 0;
        double fin_amt = 0;
        int fin_bid_idx = -1;

        double max_bid = 0,our_bid=0;
        Boolean opp_bid_min =false;

       for(int i=0;i<availableBids.size();i++)
       {
             BidInfo opp = deepClone(availableBids.get(i));
            double min_amt = opp.amount,prev_bid=0;
            for(Bid curr: currentBids)
            {
                if(!curr.bidder.equals(name) && curr.id1 == opp.id && curr.id2 < 0)
                {
                    min_amt = Math.max(curr.amount,min_amt);

                    if(curr.amount>=opp.amount)
                        opp_bid_min = true;
                }
                else if (!curr.bidder.equals(name) && curr.id2>=0 && (curr.id1 == opp.id || curr.id2 == opp.id))
                {
                     double dist_tot =0,dist=getDistance(opp.town1,opp.town2);

                    for(BidInfo tmp: allBids)
                    {
                     if(tmp.id == curr.id1 || tmp.id == curr.id2)
                        {
                            dist_tot+= getDistance(tmp.town1,tmp.town2);
                        }
                    }

                    min_amt = Math.max(min_amt, curr.amount*dist/dist_tot);
                     if(curr.amount*dist/dist_tot>=opp.amount)
                        opp_bid_min = true;
                    

                }
                else if(curr.bidder.equals(name) && curr.id1 == opp.id)
                {
                    prev_bid = curr.amount;
                    break;
                }
                else if(curr.bidder.equals(name))
                {
                    our_bid = curr.amount;
                    break;
                }
            }

            max_bid = Math.max(min_amt,max_bid);

            double act_dist = getDistance(opp.town1,opp.town2);
            opp.owner = name;
            double bid_amount =  Math.max(min_amt+10000,max_ratio*act_dist+10000);

            opp.amount = bid_amount;
            //System.out.println("amount bid on current link is " + min_amt);
            // System.out.println("max bid is" + max_bid + " our prev bid was" + prev_bid);

             
             int t1=-1,t2=-1;

             for(int z=0;z<townLookup.size();z++)
                {
                    if(townLookup.get(z).equals(opp.town1))
                        t1 = z;
                    else if (townLookup.get(z).equals(opp.town2))
                        t2 = z;
                }



                

                 if(our_ratio>=max_ratio) break;   /// Don't overbid yourself!


                if((min_amt == revenue[t1][t2] || min_amt == revenue[t1][t2]/2) && !opp_bid_min) /// don't bid 10,000 extra if it's minimum bid
                    bid_amount = min_amt;
                // else if (opp_bid_min)
                //     System.out.println("can't make minimum bid as opposition has a bid already");
                // else
                //     System.out.println("can't make minimum bid as min amount is more than current amount min amount is " + min_amt + "   revenue is " + revenue[t1][t2] + "profit is " + curr_Profit + "indirect profit is" + indirect_revenue[t1][t2] );

                 Double curr_Profit =  indirect_revenue[t1][t2] + revenue[t1][t2] - bid_amount;
          
                if(prev_bid>=min_amt || budget<bid_amount || max_bid<=our_bid || our_bid>=min_amt)  /// check budget and previous bids
                    {
                        // if(budget>=bid_amount)
                        // System.out.println("we already have a higher bid, so not bidding");
                        continue;
                    }
                

                if(map.containsKey(name) && (owner[t1][t2] == map.get(name) || owner[t2][t1] == map.get(name)) ) /// prevent from bidding on both links between towns
                    {
                        // System.out.println("can't make a bid as already own a link between towns");

                        continue; 
                    }

                
                if(min_amt> budget*curr_Profit/total_expected_utility && bid_amount != min_amt)  /// don't overspend
                    {
                        // System.out.println("can't make minimum bid as min amount is more than revenue, min amount is " + min_amt + "   revenue is " + revenue[t1][t2] + "profit is " + curr_Profit + "indirect profit is" + indirect_revenue[t1][t2] );
                        continue;
                    }


           
            if(curr_Profit>=profit)
            {
                profit = curr_Profit;
                fin_amt = bid_amount;
                fin_bid_idx = i;
                bid_in_round = true;
            }

       }

       Bid bid = new Bid();

       if(bid_in_round)
       {

        bid.amount = fin_amt;
        bid.id1 = availableBids.get(fin_bid_idx).id;
        bid.bidder = name;
         return bid;
       }

       else 
        return null;


        // BidInfo randomBid = availableBids.get(rand.nextInt(availableBids.size()));
        // double amount = randomBid.amount;

        // Don't bid if the random bid turns out to be beyond our budget.
        // if (budget - amount < 0.) {
        //     return null;
        // }

        // // Check if another player has made a bid for this link.
        // for (Bid b : currentBids) {
        //     if (b.id1 == randomBid.id || b.id2 == randomBid.id) {
        //         if (budget - b.amount - 10000 < 0.) {
        //             return null;
        //         }
        //         else {
        //             amount = b.amount + 10000;
        //         }

        //         break;
        //     }
        // }

       

       
    }

    public void updateBudget(Bid bid) {
        if (bid != null) {
            budget -= bid.amount;
        }

        availableBids = new ArrayList<>();
    }


    private  <T extends Object> T deepClone(T object) {
        try {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);
            objectOutputStream.writeObject(object);
            ByteArrayInputStream bais = new ByteArrayInputStream(byteArrayOutputStream.toByteArray());
            ObjectInputStream objectInputStream = new ObjectInputStream(bais);
            return (T) objectInputStream.readObject();
        }
        catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }



    // private static double getDistance(Bid bid) {
    //     double dist = getDistance(bid.id1);

    //     if (bid.id2 != -1) {
    //         dist += getDistance(bid.id2);
    //     }

    //     return dist;
    // }

    private  double getDistance(String t1, String t2) {
        // return getDistance(townRevLookup.get(t1), townRevLookup.get(t2));

        int s=-1,e=-1;
        for(int i=0;i<townLookup.size();i++)
        {
            if(townLookup.get(i).equals(t1))
                s = i;
            else if (townLookup.get(i).equals(t2))
                e = i;
        }

        return getDistance(s,e);
    }

    private  Pair<Integer,Integer> getTownIndices(String t1, String t2) {
        // return getDistance(townRevLookup.get(t1), townRevLookup.get(t2));

        int s=-1,e=-1;
        for(int i=0;i<townLookup.size();i++)
        {
            if(townLookup.get(i).equals(t1))
                s = i;
            else if (townLookup.get(i).equals(t2))
                e = i;
        }

        Pair<Integer,Integer> P = new Pair<>(s,e);

        return P;
    }

    // private static double getDistance(int linkId) {
    //      return links.get(linkId).distance;
    //  }

    private  double getDistance(int t1, int t2) {
        return Math.pow(
            Math.pow(geo.get(t1).x - geo.get(t2).x, 2) +
                Math.pow(geo.get(t1).y - geo.get(t2).y, 2),
            0.5);
    }


    private  double[][] getRevenue() {
        final int n = geo.size();

        // Create the graph.
        WeightedGraph g = new WeightedGraph(n);

        for (int i=0; i<n; ++i) {
            g.setLabel(townLookup.get(i));
        }

        for (int i=0; i<infra.size(); ++i) {
            for (int j=0; j<infra.get(i).size(); ++j) {
                g.addEdge(i, infra.get(i).get(j), getDistance(i, infra.get(i).get(j)));
            }
        }

        //g.print();

        // Compute the revenue between any two nodes.
        double[][] revenue = new double[n][n];

        for (int i=0; i<n; ++i) {
            int[][] prev = Dijkstra.dijkstra(g, i);
            for (int j=i+1; j<n; ++j) {
                List<List<Integer>> allPaths = Dijkstra.getPaths(g, prev, j);

                double cost = 0;
                for (int k=0; k<allPaths.get(0).size()-1; ++k) {
                    cost += getDistance(allPaths.get(0).get(k), allPaths.get(0).get(k+1));
                }

                revenue[i][j] = cost * transit[i][j] * 10;
            }
        }

        return revenue;
    }





    private  Double getProfitsPerPlayer(double[][] revenue,List<BidInfo> allBids) {
        // The graph now has nodes replicated for each player and a start and end node.
        final int n = geo.size() * (origPlayers.size() + 2);

        Map<String, Double> playerRev = new HashMap<>();
        for (int i=0; i<origPlayers.size(); ++i) {
            playerRev.put(origPlayers.get(i).getName(), 0.);
        }

        WeightedGraph g = new WeightedGraph(n);
        for (int i=0; i<geo.size(); ++i) {
            g.setLabel(townLookup.get(i) + "-s");
            g.setLabel(townLookup.get(i) + "-e");

            for (int j=0; j<origPlayers.size(); ++j) {
                g.setLabel(townLookup.get(i) + "-" + origPlayers.get(j).getName());
            }
        }

        for (BidInfo bi : allBids) {
            g.addEdge(bi.town1 + "-" + bi.owner,
                bi.town2 + "-" + bi.owner,
                getDistance(bi.town1,bi.town2));

            g.addDirectedEdge(bi.town1 + "-s",
                bi.town1 + "-" + bi.owner,
                0);
            g.addDirectedEdge(bi.town2 + "-s",
                bi.town2 + "-" + bi.owner,
                0);
            g.addDirectedEdge(bi.town1 + "-" + bi.owner,
                bi.town1 + "-e",
                0);
            g.addDirectedEdge(bi.town2 + "-" + bi.owner,
                bi.town2 + "-e",
                0);
        }

        for (int i=0; i<geo.size(); ++i) {
            for (int j=0; j<origPlayers.size(); ++j) {
                for (int k=j+1; k<origPlayers.size(); ++k) {
                    g.addEdge(townLookup.get(i) + "-" + origPlayers.get(j).getName(),
                        townLookup.get(i) + "-" + origPlayers.get(k).getName(),
                        200);
                }
            }
        }

        // g.print();

        for (int i=0; i<geo.size(); ++i) {
            int[][] prev = Dijkstra.dijkstra(g, g.getVertex(townLookup.get(i) + "-s"));
            for (int j=i+1; j<geo.size(); ++j) {
                List<List<Integer>> allPaths =
                    Dijkstra.getPaths(g, prev, g.getVertex(townLookup.get(j) + "-e"));

                // Cost per path.
                double cost = revenue[i][j] / allPaths.size();

                for (int p=0; p<allPaths.size(); ++p) {
                    double trueDist = 0.0;
                    Map<String, Double> playerDist = new HashMap<>();

                    for (playerNames pw : origPlayers) {
                        playerDist.put(pw.getName(), 0.);
                    }

                    for (int k=0; k<allPaths.get(p).size()-1; ++k) {
                        String a = g.getLabel(allPaths.get(p).get(k));
                        String b = g.getLabel(allPaths.get(p).get(k+1));

                        if (a.split("-")[1].equals(b.split("-")[1])) {
                            trueDist +=
                                getDistance(a.split("-")[0], b.split("-")[0]);

                            playerDist.put(
                                a.split("-")[1],
                                playerDist.get(a.split("-")[1]) +
                                    getDistance(a.split("-")[0], b.split("-")[0]));
                        }
                    }

                    for (Map.Entry<String, Double> entry : playerDist.entrySet()) {
                        playerRev.put(
                            entry.getKey(),
                            playerRev.get(entry.getKey()) + entry.getValue()/trueDist * cost);
                    }
                }
            }
        }

       

        return playerRev.get(name) - budget;
    }

    private void update_propogation(Integer[][] paths)
    {
        int n = geo.size();
        // System.out.println("updating traffic");
        for(int i=0;i<n;i++)
        {
            for(int j=i+1;j<n;j++)
            {
                Integer[] path_ij = new Integer[n];
                for(int d=0;d<n;d++)
                    path_ij[d] = -1;

                int z =i,l=0;
                double path_length=0;
                while(z!=j && l<n)
                {
                    path_ij[l] = z;
                    z = paths[z][j];

                    if(l>=1)
                    {
                        path_length += getDistance(path_ij[l],path_ij[l-1]);
                    }

                    l++;
                   // System.out.print(z + " ");
                    if(z==-1 || paths[z][j]<0 || paths[z][j]>=n) break;
                }
                // System.out.println();
                // System.out.println("updating revenue");


                double node_degree = infra.get(i).size();

                double switch_prob = Math.min(1/node_degree,1);
                
                try
                {
                if(!same_path(i,j,path_ij))
                {
                    //System.out.println("different path encountered");
                    int k=0;
                    if(indirect_revenue[i][j]>0)
                    //for(int k=0;k<n-1;k++)
                    while(k+1<n && path_ij[k+1]!=-1)
                    {
                        Integer x = prev_paths[i][j][k];
                        Integer x_next = prev_paths[i][j][k+1];

                        switch_prob = Math.min(switch_prob, switch_prob/infra.get(x).size());

                        indirect_revenue[x][x_next] -= switch_prob*revenue[i][j]*getDistance(x,x_next)/path_length;
                        //Math.pow(discount_factor,k)*revenue[i][j];
                        //System.out.println("update revenue for " + x + " " + x_next + " " +indirect_revenue[x][x_next]);
                        k++;
                    }

                    k=0;
                    switch_prob = Math.min(1/node_degree,1);
                    //for(int k=0;k<path_ij.size()-1;k++)
                     while(k+1<n && path_ij[k+1]!=-1)
                    {
                        Integer x = path_ij[k];
                        Integer x_next = path_ij[k+1];

                        switch_prob = Math.min(switch_prob, switch_prob/infra.get(x).size());
                        indirect_revenue[x][x_next] += switch_prob*revenue[i][j]*getDistance(x,x_next)/path_length;
                        //Math.pow(discount_factor,k)*revenue[i][j];
                        k++;
                    }

                    for(int c=0;c<n;c++)
                    {
                        if(path_ij[c]==-1) break;

                        prev_paths[i][j][c] = path_ij[c];
                    }


                }
            }
            catch(Exception e)
            {
               e.printStackTrace();
            }

            }
        }


    }

    private Boolean same_path(Integer i, Integer j, Integer[] path_ij)
    {
        int n = geo.size();

        for(int k=0;k<n;k++)
        {
            //if(prev_paths[i][j][k]==-1) break;

            if(prev_paths[i][j][k]!=path_ij[k])
                return false;
        }
        return true;
    }

    private Integer[][] Floyd_Warshall()
    {
         int n = geo.size();
        double[][][] D = new double[n][n][n];
        int [][][] O = new int[n][n][n];
        Integer[][] succ = new Integer[n][n];
        for(int i=0;i<n;i++)
        {
            for(int j=i+1;j<n;j++)
            if(infra.get(i).contains(j))
                {
                    D[0][i][j] = D[0][j][i]=getDistance(i,j);
                    succ[i][j] = j;
                    succ[j][i] = i;
                }
            else
            {
                D[0][i][j] = D[0][j][i]=Double.POSITIVE_INFINITY;
                succ[i][j] = -1;
                succ[j][i] = -1;
            }

        }


        for(int i=0;i<n;i++)
        {
            for(int j=0;j<n;j++)
            {
                O[0][i][j] = owner[i][j];
            }
        }

        for(int i=0;i<n;i++)
            succ[i][i] = -1;

        

        for( int k = 1; k<n; k++ )
        {
            for(int i=0; i<n;i++)
            {
                for(int j=0;j<n;j++)
                {
                    //D[k][i][j] = Math.min(D[k-1][i][j], D[k-1][i][k] + D[k-1][k][j]);

                    if(O[k-1][i][k]==O[k-1][j][k]) 
                        D[k][i][j] = Math.min(D[k-1][i][j], D[k-1][i][k] + D[k-1][k][j]);   
                    else
                        D[k][i][j] = Math.min(D[k-1][i][j], D[k-1][i][k] + D[k-1][k][j] + 200);

                    if(D[k][i][j]!=D[k-1][i][j])
                    {
                        succ[i][j] = succ[i][k];
                        O[k][i][j] = O[k-1][k][j];
                        O[k][j][i] = O[k-1][k][i];
                    }

                    else
                          O[k][i][j] = O[k-1][i][j];

                }
            }

        }


        // for(int i=0;i<n;i++)
        // {
        //     for(int j=0;j<n;j++)
        //     {
        //         System.out.print(succ[i][j] + " ");
        //     }
        //     System.out.println();
        // }

        return succ;


    }

}

