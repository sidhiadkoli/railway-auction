package railway.sim;

import java.awt.Desktop;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Random;
import java.util.Scanner;
import java.util.Set;
// import java.util.stream.Collectors;

import javax.tools.JavaCompiler;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;

import railway.sim.utils.*;

class LinkInfo {
    public int town1;
    public int town2;

    public double distance;
}

public class Simulator {
    private static final String root = "railway";
    private static final String statics_root = "statics";

    private static boolean gui = false;
    private static long timeout = 1000;
    private static double fps = 1;

    // Files - hardcoded.
    private static String geo_f = "geography";
    private static String transit_f = "transit";
    private static String infra_f = "infrastructure";
    private static String dir = "railway/sim/input/";

    //replay
    private static boolean hasReplay = false;
    private static String replayPath = "replay.json"; //default path for replay
    private static List<String> replayBuffer; //list of json strings for replay
    private static boolean playback = false; //playback mode

    private static List<Coordinates> geo = new ArrayList<>();
    private static List<List<Integer>> infra = new ArrayList<>();
    private static int[][] transit;
    private static Map<String, Integer> townRevLookup = new HashMap<>();
    private static List<String> townLookup = new ArrayList<>();
    private static List<LinkInfo> links = new ArrayList<>();

    private static List<String> playerNames;
    private static List<PlayerWrapper> players;
    private static List<PlayerWrapper> origPlayers;

    private static double budget = 0.;

    private static List<BidInfo> allBids = new ArrayList<>();

    private static Random rand = new Random();

    private static int uniq = 1;
    private static String gov = "gov";

    public static void main(String[] args) throws
        ClassNotFoundException, InstantiationException, IllegalAccessException, IOException {
        parseArgs(args);
        loadInputFiles();

        HTTPServer server = null;
        if (gui) {
            server = new HTTPServer();
            Log.record("Hosting HTTP Server on " + server.addr());
            if (!Desktop.isDesktopSupported())
                Log.record("Desktop operations not supported");
            else if (!Desktop.getDesktop().isSupported(Desktop.Action.BROWSE))
                Log.record("Desktop browse operation not supported");
            else {
                try {
                    Desktop.getDesktop().browse(new URI("http://localhost:" + server.port()));
                } catch (URISyntaxException e) {
                    e.printStackTrace();
                }
            }
        }

        if(playback){ //playback mode
            //only load and send json to server and then exit

            replayBuffer = new ArrayList<>();
            BufferedReader br = new BufferedReader(new FileReader(replayPath));
            String line;
            try {
                while((line = br.readLine()) != null){
                    replayBuffer.add(line);
                }
            }
            finally {
                br.close();
            }

            for(String record:replayBuffer){
                gui(server, record);
            }
            System.exit(0);
        }

        if(hasReplay){
            replayBuffer = new ArrayList<>();
        }

        if (playerNames.size() == 0) {
            //save state to replay json frame
            if(hasReplay){
                replayBuffer.add(state(-1, geo, infra, 0));
            }


            if (gui) {
                gui(server, state(-1, geo, infra, 0));
            }

            System.exit(0);
        }

        players = new ArrayList<>();
        try {
            for (String name : playerNames) {
                players.add(loadPlayerWrapper(name, timeout));
            }
        } catch (Exception ex) {
            System.out.println("Unable to load players. " + ex.getMessage());
            System.exit(0);
        }

        origPlayers = new ArrayList<>(players);

        initBids();
        budget = getBudget();
        System.out.println("Budget: " + budget);
        List<PlayerWrapper> updates = new ArrayList<>(players);
        for (PlayerWrapper pw : players) {
            try {
                pw.init(budget, deepClone(geo), deepClone(infra),
                    deepClone(transit), deepClone(townLookup), deepClone(allBids));
            }
            catch(Exception ex) {
                System.out.println("Exception in initializing player " +
                    pw.getName() + " " + ex.getMessage());
                updates.remove(pw);
            }
        }

        // Update the list of players to remove those that threw exceptions.
        players = updates;

        if(hasReplay){
            replayBuffer.add(state(fps, geo, infra, budget));
        }

        if (gui) {
            gui(server, state(fps, geo, infra, budget));
        }

        boolean isComplete = false;
        int round = 1;
        Bid maxBid = null;

        try {
            while (!allLinksTaken(allBids)) {
                System.out.println("\nRound " + round++);

                Collections.shuffle(players);
                updates = new ArrayList<>(players);

                List<Bid> currentBids = new ArrayList<>();

                int i=0;
                int null_players = 0;

                while (updates.size() > 0 && null_players < updates.size()) {
                    PlayerWrapper pw = updates.get(i);
                    try {
                        Bid bid = pw.getBid(
                            deepClone(currentBids), deepClone(allBids), deepClone(maxBid));
                        if (bid == null) {
                            ++null_players;
                        }
                        else {
                            currentBids.add(0, bid);

                            // Reset null players because there are players still participating
                            //  in the auction.
                            null_players = 0;
                        }

                        ++i;
                    }
                    catch (Exception ex) {
                        // This should be an exception only from getBid function.
                        System.out.println("Uh-oh! " + ex.getMessage());
                        // ex.printStackTrace();
                        updates.remove(pw);
                    }

                    if (i >= updates.size()) {
                        // Reset i.
                        i = 0;
                    }
                }

                if (currentBids.size() == 0) {
                    // This can happen if all players error out, not necessarily if
                    //  nobody bids anything.
                    // If we have holes in the graph then Dijkstra's algorithm will
                    //  fail to run in the future.
                    System.out.println("Nobody bid for anything :( ");

                    // Initialize a gov player.
                    PlayerWrapper govpw = loadPlayerWrapper(gov, timeout);
                    origPlayers.add(govpw);
                    govpw.init(budget, deepClone(geo), deepClone(infra),
                        deepClone(transit), deepClone(townLookup), deepClone(allBids));

                    replaceUnusedBidsByGov();

                    break;
                }

                maxBid = getMaxBid(currentBids, allBids);
                System.out.print("Max bidder: " + maxBid.bidder + " amount: " + maxBid.amount);
                updateBids(maxBid, allBids);

                for (PlayerWrapper pw : players) {
                    if (pw.getName().equals(maxBid.bidder)) {
                        pw.updateBudget(maxBid);
                    }
                    else {
                        pw.updateBudget(null);
                    }
                }

                if(hasReplay){
                    replayBuffer.add(state(fps, allBids, origPlayers));
                }
                if (gui) {
                    gui(server, state(fps, allBids, origPlayers));
                }
            }
        } catch (Exception ex) {
            System.out.println("Exception! ");
            ex.printStackTrace();
            System.exit(0);
        }

        double[][] revenue = getRevenue();
        Map<String, Double> playerProfits = getProfitsPerPlayer(revenue);

        printStats(playerProfits);

        revertGovAssignment(playerProfits);

        if(hasReplay){
            replayBuffer.add(state(-1, allBids, origPlayers, playerProfits));
            PrintWriter writer = new PrintWriter(replayPath, "UTF-8");
            for(String record:replayBuffer){
                writer.println(record);
            }
            writer.close();
        }

        if (gui) {
            gui(server, state(-1, allBids, origPlayers, playerProfits));
        }

        System.exit(0);
    }

    public static void printStats(Map<String, Double> playerProfits) {
        System.out.println("\n******** Results ********");
        System.out.println("Group Profit");

        for (Map.Entry<String, Double> entry : playerProfits.entrySet()) {
            System.out.println(entry.getKey() + " " + entry.getValue());
        }
    }

    public static void printRev(double[][] rev) {
        for (int i=0; i<rev.length; ++i) {
            for (int j=0; j<rev[i].length; ++j) {
                System.out.print(rev[i][j] + " ");
            }

            System.out.println();
        }
    }

    // Get maximum bid.
    public static Bid getMaxBid(List<Bid> currentBids, List<BidInfo> allBids) {
        List<Bid> maxBids = new ArrayList<>();
        maxBids.add(currentBids.get(0));
        for (int i=1; i<currentBids.size(); ++i) {
            Bid b = currentBids.get(i);
            if (b.amount/getDistance(b) > maxBids.get(0).amount/getDistance(maxBids.get(0))) {
                maxBids.clear();
                maxBids.add(b);
            }
            else if (b.amount/getDistance(b) ==
                maxBids.get(0).amount/getDistance(maxBids.get(0))) {
                maxBids.add(b);
            }
        }

        return new Bid(maxBids.get(rand.nextInt(maxBids.size())));
    }

    // Get the cost per km of the maximum bid.
    public static double getMaxBidCostPerKm(List<Bid> currentBids, List<BidInfo> allBids) {
        Bid maxBid = getMaxBid(currentBids, allBids);
        return maxBid.amount / getDistance(maxBid);
    }

    private static void updateBids(Bid bid, List<BidInfo> allBids) {
        BidInfo bi = allBids.get(bid.id1);
        double amount = bid.amount;

        System.out.print(" Link: " + bi.town1 + "-" + bi.town2);

        if (bid.id2 != -1) {
            BidInfo bi2 = allBids.get(bid.id2);
            bi2.amount = amount/2;
            bi2.owner = bid.bidder;
            amount /= 2;
            System.out.print(" " + bi2.town1 + "-" + bi2.town2);
        }

        bi.amount = amount;
        bi.owner = bid.bidder;

        System.out.println();
    }

    // The following 4 functions that getDistance depending on the parameter 
    // that you give.
    public static double getDistance(Bid bid) {
        double dist = getDistance(bid.id1);

        if (bid.id2 != -1) {
            dist += getDistance(bid.id2);
        }

        return dist;
    }

    public static double getDistance(String t1, String t2) {
        return getDistance(townRevLookup.get(t1), townRevLookup.get(t2));
    }

    public static double getDistance(int linkId) {
        return links.get(linkId).distance;
    }

    public static double getDistance(int t1, int t2) {
        return Math.pow(
            Math.pow(geo.get(t1).x - geo.get(t2).x, 2) +
                Math.pow(geo.get(t1).y - geo.get(t2).y, 2),
            0.5);
    }

    private static void replaceUnusedBidsByGov() {
        for (BidInfo bi : allBids) {
           if (bi.owner == null) {
               bi.owner = gov;
               bi.amount = 0;
           }
        }
    }

    private static void revertGovAssignment(Map<String, Double> playerProfits) {
        for (BidInfo bi : allBids) {
           if (bi.owner.equals(gov)) {
               bi.owner = null;
           }
        }

        if (playerProfits.containsKey(gov)) {
            playerProfits.remove(gov);
        }
    }

    private static double[][] getRevenue() {
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

    private static Map<String, Double> getProfitsPerPlayer(double[][] revenue) {
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
                getDistance(bi.id));

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

                    for (PlayerWrapper pw : origPlayers) {
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

        Map<String, Double> playerProfits = new HashMap<>();
        for (PlayerWrapper pw : origPlayers) {
            playerProfits.put(pw.getName(), budget - pw.getBudget());
        }

        for (Map.Entry<String, Double> entry : playerRev.entrySet()) {
            playerProfits.put(
                entry.getKey(),
                entry.getValue() - playerProfits.get(entry.getKey()));
        }

        // Sort players by profit.
        List<Map.Entry<String, Double>> plist = new ArrayList<>(playerProfits.entrySet());
        plist.sort(Collections.reverseOrder(Map.Entry.comparingByValue()));
        Map<String, Double> sortedPlayers = new LinkedHashMap<>();
        for (Map.Entry<String, Double> entry : plist) {
            sortedPlayers.put(entry.getKey(), entry.getValue());
        }

        return sortedPlayers;
    }

    private static boolean allLinksTaken(List<BidInfo> allBids) {
        for (BidInfo bi : allBids) {
            if (bi.owner == null) {
                return false;
            }
        }

        return true;
    }

    private static void initBids() {
        int id = 0;

        for (int i=0; i < infra.size(); ++i) {
            for (int j=0; j < infra.get(i).size(); ++j) {
                BidInfo bi = new BidInfo();
                bi.id = id;

                int t1 = i;
                int t2 = infra.get(i).get(j);
                bi.town1 = townLookup.get(t1);
                bi.town2 = townLookup.get(t2);

                LinkInfo li = new LinkInfo();
                li.town1 = t1;
                li.town2 = t2;
                li.distance = getDistance(t1, t2);
                links.add(li);

                bi.amount = transit[t1][t2] * li.distance * 10;

                List<BidInfo> dups =
                    getDuplicateLinks(bi.town1, bi.town2);
                if (dups != null && dups.size() > 0) {
                    int c_size = dups.size();
                    int new_size = c_size + 1;

                    for (BidInfo d : dups) {
                        d.amount = d.amount * c_size / new_size;
                    }

                    bi.amount /= new_size;
                }

                allBids.add(bi);
                id += 1;
            }
        }
    }

    private static List<BidInfo> getDuplicateLinks(String t1, String t2) {
        List<BidInfo> dups = new ArrayList<>();
        for (BidInfo a : allBids) {
            if (a.town1.equals(t1) && a.town2.equals(t2)) {
                dups.add(a);
            }
        }

        return dups;
    }

    private static double getBudget() {
        int g = players.size();

        double totalAmount = 0;
        for (BidInfo bi : allBids) {
            totalAmount += bi.amount;
        }

        totalAmount *= 2;

        return (int)totalAmount/g;
    }

    private static void loadInputFiles() {
        // Process geo.
        try {
            String path = dir + geo_f;
            File file = new File(path);
            Scanner sc = new Scanner(file);
            int index = 0;

            townLookup = new ArrayList<>();
            while (sc.hasNextLine()) {
                String line = sc.nextLine();
                String[] res = line.split(",");

                townRevLookup.put(res[0], index);
                townLookup.add(res[0]);
                geo.add(new Coordinates(Double.parseDouble(res[1]), Double.parseDouble(res[2])));
                infra.add(new ArrayList<Integer>());
                index += 1;
            }

            // Process infrastructure.
            path = dir + infra_f;
            file = new File(path);
            sc = new Scanner(file);
            while (sc.hasNextLine()) {
                String line = sc.nextLine();
                String[] res = line.split(",");

                if (townRevLookup.get(res[0]) > townRevLookup.get(res[1])) {
                    infra.get(townRevLookup.get(res[1])).add(townRevLookup.get(res[0]));
                } else {
                    infra.get(townRevLookup.get(res[0])).add(townRevLookup.get(res[1]));
                }
            }

            transit = new int[index][index];

            // Process transit.
            // How to store??
            path = dir + transit_f;
            file = new File(path);
            sc = new Scanner(file);
            while (sc.hasNextLine()) {
                String line = sc.nextLine();
                String[] res = line.split(",");
                if (townRevLookup.get(res[0]) > townRevLookup.get(res[1])) {
                    transit[townRevLookup.get(res[1])][townRevLookup.get(res[0])] =
                        Integer.parseInt(res[2]);
                } else {
                    transit[townRevLookup.get(res[0])][townRevLookup.get(res[1])] =
                        Integer.parseInt(res[2]);
                }
            }
        }
        catch (Exception ex) {
            System.out.println("Exception! " + ex.getMessage());
        }
    }

    private static List<List<Integer>> getClone(List<List<Integer>> lol) {
        List<List<Integer>> newLol = new ArrayList<>();
        for (List<Integer> l : lol) {
            newLol.add(new ArrayList<Integer>(l));
        }

        return newLol;
    }

    private static <T extends Object> T deepClone(T object) {
        if (object == null) {
            return null;
        }

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

    private static int[] getScores() {
        throw new UnsupportedOperationException();
    }

    private static PlayerWrapper loadPlayerWrapper(String name, long timeout) throws Exception {
        String p_name = name.split("_")[0];

        Log.record("Loading player " + name);
        Player p = loadPlayer(p_name);
        if (p == null) {
            Log.record("Cannot load player " + name);
            System.exit(1);
        }

        return new PlayerWrapper(p, name, timeout);
    }

    // The state that is sent to the GUI (JSON) at the very beginning.
    private static String state(
        double fps,
        List<Coordinates> geo,
        List<List<Integer>> infra,
        double budget) {

        // Here we are again.
        String json = "{\"refresh\":\"" + (1000.0/fps) + "\",\"budget\":\"" + budget +
            "\",\"geo\":\"";

        for (int i=0; i<townLookup.size(); ++i) {
            json += townLookup.get(i) + "," + geo.get(i).x + "," + geo.get(i).y + ";";
        }

        // Remove the ";" at the end.
        json = json.substring(0, json.length() - 1);
        json += "\",\"infra\":\"";

        for (int i=0; i<townLookup.size(); ++i) {
            for (int j=0; j<infra.get(i).size(); ++j) {
                json += townLookup.get(i) + "," + townLookup.get(infra.get(i).get(j)) + ";";
            }
        }

        json = json.substring(0, json.length() - 1);
        json += "\",\"players\":\"" + String.join(",", playerNames) + "\"}";

        // System.out.println(json);

        return json;
    }

    // The state that is sent to the GUI (JSON) during the auction.
    private static String state(
        double fps,
        List<BidInfo> allBids,
        List<PlayerWrapper> players) {

        String json = "{\"refresh\":\"" + (1000.0/fps) + "\",\"owners\":\"";

        for (BidInfo bi : allBids) {
            if (bi.owner != null) {
                json += bi.town1 + "," + bi.town2 + "," + bi.owner + ";";
            }
            else {
                json += bi.town1 + "," + bi.town2 + ",None;";
            }
        }

        json = json.substring(0, json.length() - 1);
        json += "\",\"budget\":\"";

        for (PlayerWrapper pw : players) {
            json += pw.getName() + "," + pw.getBudget() + ";";
        }

        // Remove the ";" at the end.
        json = json.substring(0, json.length() - 1);
        json += "\"}";

        // System.out.println(json);

        return json;
    }

    // The state that is sent to the GUI (JSON) at the very end.
    private static String state(
        double fps,
        List<BidInfo> allBids,
        List<PlayerWrapper> players,
        Map<String, Double> playerProfits) {

        String json = "{\"refresh\":\"" + (1000.0/fps) + "\",\"owners\":\"";

        for (BidInfo bi : allBids) {
            if (bi.owner != null) {
                json += bi.town1 + "," + bi.town2 + "," + bi.owner + ";";
            }
            else {
                json += bi.town1 + "," + bi.town2 + ",None;";
            }
        }

        json = json.substring(0, json.length() - 1);
        json += "\",\"budget\":\"";

        for (Map.Entry<String, Double> entry : playerProfits.entrySet()) {
            json += entry.getKey() + "," + entry.getValue() + ";";
        }

        // Remove the ";" at the end.
        json = json.substring(0, json.length() - 1);
        json += "\"}";

        //System.out.println(json);

        return json;
    }

    private static void gui(HTTPServer server, String content) {
        if (server == null) return;
        String path = null;
        for (;;) {
            for (;;) {
                try {
                    path = server.request();
                    break;
                } catch (IOException e) {
                    Log.record("HTTP request error " + e.getMessage());
                }
            }
            if (path.equals("data.txt")) {
                try {
                    server.reply(content);
                } catch (IOException e) {
                    Log.record("HTTP dynamic reply error " + e.getMessage());
                }
                return;
            }
            if (path.equals("")) path = "webpage.html";
            else if (!Character.isLetter(path.charAt(0))) {
                Log.record("Potentially malicious HTTP request \"" + path + "\"");
                break;
            }

            File file = new File(statics_root + File.separator + path);
            if (file == null) {
                Log.record("Unknown HTTP request \"" + path + "\"");
            } else {
                try {
                    server.reply(file);
                } catch (IOException e) {
                    Log.record("HTTP static reply error " + e.getMessage());
                }
            }
        }
    }

    private static void parseArgs(String[] args) {
        int i = 0;
        playerNames = new ArrayList<String>();
        for (; i < args.length; ++i) {
            switch (args[i].charAt(0)) {
                case '-':
                    if (args[i].equals("-p") || args[i].equals("--players")) {
                        while (i + 1 < args.length && args[i + 1].charAt(0) != '-') {
                            ++i;
                            if (playerNames.contains(args[i])) {
                                playerNames.add(args[i] + "_" + uniq++);
                            }
                            else {
                                playerNames.add(args[i]);
                            }
                        }
                    } else if (args[i].equals("-g") || args[i].equals("--gui")) {
                        gui = true;
                    } else if (args[i].equals("-l") || args[i].equals("--logfile")) {
                        if (++i == args.length) {
                            throw new IllegalArgumentException("Missing logfile name");
                        }
                        Log.setLogFile(args[i]);
                    } else if (args[i].equals("-t") || args[i].equals("--timeout")) {
                        if (++i == args.length) {
                            throw new IllegalArgumentException("Missing timeout value.");
                        }

                        timeout = Integer.parseInt(args[i]) * 1000;
                    } else if (args[i].equals("-m") || args[i].equals("--map")) {
                        if (++i == args.length) {
                            throw new IllegalArgumentException("Missing map path.");
                        }

                        dir = "railway/" + args[i] + "/input/";
                    }
                    else if (args[i].equals("-s") || args[i].equals("--seed")) {
                        if (++i == args.length) {
                            throw new IllegalArgumentException("Missing seed value.");
                        }

                        rand = new Random(Integer.parseInt(args[i]));
                    }
                    else if (args[i].equals("--fps")) {
                        if (++i == args.length) {
                            throw new IllegalArgumentException("Missing frames per second.");
                        }
                        fps = Double.parseDouble(args[i]);
                    } else if (args[i].equals("-v") || args[i].equals("--verbose")) {
                        Log.activate();
                    }
                    //save replay
                    else if (args[i].equals("--save")){
                        hasReplay = true;
                        if(i+1<args.length){
                            if(args[i+1].endsWith(".json"))
                                replayPath = args[++i];
                        }
                    }
                    //replay playback
                    else if (args[i].equals("--playback")){
                        playback = true;
                        gui = true;
                        if(i+1<args.length){
                            if(args[i+1].endsWith(".json"))
                                replayPath = args[++i];
                        }
                    }
                    else {
                        throw new IllegalArgumentException("Unknown argument '" + args[i] + "'");
                    }

                    break;
                default:
                    throw new IllegalArgumentException("Unknown argument '" + args[i] + "'");
            }
        }

        Log.record("Players: " + playerNames.toString());
        Log.record("GUI " + (gui ? "enabled" : "disabled"));

        if (gui)
            Log.record("FPS: " + fps);
    }

    private static Set<File> directory(String path, String extension) {
        Set<File> files = new HashSet<File>();
        Set<File> prev_dirs = new HashSet<File>();
        prev_dirs.add(new File(path));
        do {
            Set<File> next_dirs = new HashSet<File>();
            for (File dir : prev_dirs)
                for (File file : dir.listFiles())
                    if (!file.canRead()) ;
                    else if (file.isDirectory())
                        next_dirs.add(file);
                    else if (file.getPath().endsWith(extension))
                        files.add(file);
            prev_dirs = next_dirs;
        } while (!prev_dirs.isEmpty());
        return files;
    }

    public static Player loadPlayer(String name)
        throws IOException, ClassNotFoundException, InstantiationException, IllegalAccessException {
        String sep = File.separator;
        Set<File> player_files = directory(root + sep + name, ".java");
        File class_file = new File(root + sep + name + sep + "Player.class");
        long class_modified = class_file.exists() ? class_file.lastModified() : -1;
        if (class_modified < 0 || class_modified < last_modified(player_files) ||
                class_modified < last_modified(directory(root + sep + "sim", ".java"))) {
            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            if (compiler == null)
                throw new IOException("Cannot find Java compiler");
            StandardJavaFileManager manager = compiler.
                    getStandardFileManager(null, null, null);
//            long files = player_files.size();
            Log.record("Compiling for player " + name);
            if (!compiler.getTask(null, manager, null, null, null,
                    manager.getJavaFileObjectsFromFiles(player_files)).call())
                throw new IOException("Compilation failed");
            class_file = new File(root + sep + name + sep + "Player.class");
            if (!class_file.exists())
                throw new FileNotFoundException("Missing class file");
        }
        ClassLoader loader = Simulator.class.getClassLoader();
        if (loader == null)
            throw new IOException("Cannot find Java class loader");
        @SuppressWarnings("rawtypes")
        Class raw_class = loader.loadClass(root + "." + name + ".Player");
        return (Player)raw_class.newInstance();
    }

    private static long last_modified(Iterable<File> files) {
        long last_date = 0;
        for (File file : files) {
            long date = file.lastModified();
            if (last_date < date)
                last_date = date;
        }
        return last_date;
    }
}
