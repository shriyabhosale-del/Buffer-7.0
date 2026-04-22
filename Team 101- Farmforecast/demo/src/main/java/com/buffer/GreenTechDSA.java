package com.buffer;

import java.io.*;
import java.net.*;
import java.util.*;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;


//─────────────────────────────────────────────────────────────────────────────
class WeatherData {
    double temp, humidity, wind, rain;

    WeatherData(double t, double h, double w, double r) {
        temp = t; humidity = h; wind = w; rain = r;
    }
}

//────────────────────────────────────────────────────────────────────────────
class WeatherService {

   private static final String API_KEY = System.getenv("WEATHER_API_KEY") != null
            ? System.getenv("WEATHER_API_KEY")
            : "ab1140c8eddf80fda755b8d09bc997c7"; // fallback for local dev only

    public WeatherData fetchWeather(String city) {
        try {
            String urlStr = "https://api.openweathermap.org/data/2.5/weather?q="
                    + city + "&appid=" + API_KEY + "&units=metric";

            URL url = new URL(urlStr);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) response.append(line);

            JSONObject json = new JSONObject(response.toString());

            double temp     = json.getJSONObject("main").getDouble("temp");
            double humidity = json.getJSONObject("main").getDouble("humidity");
            double wind     = json.getJSONObject("wind").getDouble("speed") * 3.6;
            double rain     = 0.0;
            if (json.has("rain"))
                rain = json.getJSONObject("rain").optDouble("1h", 0.0);

            return new WeatherData(temp, humidity, wind, rain);

        } catch (Exception e) {
            System.out.println("API Error: " + e.getMessage());
            return null;
        }
    }
}

// CROPDATA─────────────────────────────────────────────────────────────────────────────
class CropData {
    String name, season, weather, soil, waterRequirement;
    double yield;
    int marketRate, carbonScore;
    double moistureThreshold;

    CropData(String name, String season, String weather, String soil,
             String waterRequirement, double yield, int marketRate,
             int carbonScore, double moistureThreshold) {
        this.name             = name.toLowerCase();
        this.season           = season;
        this.weather          = weather;
        this.soil             = soil;
        this.waterRequirement = waterRequirement;
        this.yield            = yield;
        this.marketRate       = marketRate;
        this.carbonScore      = carbonScore;
        this.moistureThreshold = moistureThreshold;
    }
}

//  FERTILIZER

class Fertilizer {
    String name;
    int price, efficiency;

    Fertilizer(String n, int p, int e) { name = n; price = p; efficiency = e; }
}

//  IRRIGATION 

class Irrigation {
    String name;
    int cost, efficiency;

    Irrigation(String n, int c, int e) { name = n; cost = c; efficiency = e; }
}

// DECISION TREE FOR CROP RECOMMENDATION

class TreeNode {
    String attribute;       // "temp" | "humidity" | "rain"
    double threshold;       // split value
    String label;           // description (for debugging)
    TreeNode left, right;   // left = condition MET, right = condition NOT MET
    String crop;            // non-null only at leaf nodes

    // Internal decision node
    TreeNode(String label, String attribute, double threshold) {
        this.label     = label;
        this.attribute = attribute;
        this.threshold = threshold;
    }

    // Leaf node (crop recommendation)
    TreeNode(String crop) {
        this.crop = crop;
    }

    boolean isLeaf() { return crop != null; }
}

class CropDecisionTree {

    private final TreeNode root;

    CropDecisionTree() { root = buildTree(); }

    // Tree Layout 
    
    private TreeNode buildTree() {

        // ── Leaf nodes ──
        TreeNode rice      = new TreeNode("rice");
        TreeNode wheat     = new TreeNode("wheat");
        TreeNode barley    = new TreeNode("barley");
        TreeNode maize     = new TreeNode("maize");
        TreeNode millet    = new TreeNode("millet");
        TreeNode cotton    = new TreeNode("cotton");
        TreeNode sugarcane = new TreeNode("sugarcane");
        TreeNode soybean   = new TreeNode("soybean");
        TreeNode groundnut = new TreeNode("groundnut");
        TreeNode mustard   = new TreeNode("mustard");

        // ── HOT branch (temp > 30) ──
        // rain > 3 → maize, else millet
        TreeNode hotDryRain = new TreeNode("Rain > 3mm?", "rain", 3.0);
        hotDryRain.left  = maize;
        hotDryRain.right = millet;

        // humidity > 70 → rice, else check rain
        TreeNode hotHumidity = new TreeNode("Humidity > 70%?", "humidity", 70.0);
        hotHumidity.left  = rice;
        hotHumidity.right = hotDryRain;

        // ── VERY HOT branch (temp > 38) → sugarcane or cotton ──
        TreeNode veryHotHumidity = new TreeNode("Humidity > 65%?", "humidity", 65.0);
        veryHotHumidity.left  = sugarcane;
        veryHotHumidity.right = cotton;

        // temp > 38 → sugarcane/cotton, else rice/maize/millet
        TreeNode hotBranch = new TreeNode("Temp > 38°C?", "temp", 38.0);
        hotBranch.left  = veryHotHumidity;
        hotBranch.right = hotHumidity;

        // ── COOL branch (temp <= 30) ──
        // rain > 5 → wheat, else barley
        TreeNode coolRain = new TreeNode("Rain > 5mm?", "rain", 5.0);
        coolRain.left  = wheat;
        coolRain.right = barley;

        // humidity > 60 in cool → soybean, else coolRain check
        TreeNode coolHumidity = new TreeNode("Humidity > 60%?", "humidity", 60.0);
        coolHumidity.left  = soybean;
        coolHumidity.right = coolRain;

        // temp > 20 (moderate) → groundnut/mustard branch
        TreeNode moderateRain = new TreeNode("Rain > 2mm?", "rain", 2.0);
        moderateRain.left  = groundnut;
        moderateRain.right = mustard;

        TreeNode moderateBranch = new TreeNode("Temp > 20°C?", "temp", 20.0);
        moderateBranch.left  = moderateRain;
        moderateBranch.right = coolHumidity;

        // ── ROOT: temp > 30 ──
        TreeNode rootNode = new TreeNode("Temp > 30°C?", "temp", 30.0);
        rootNode.left  = hotBranch;
        rootNode.right = moderateBranch;

        return rootNode;
    }

    // Walk the tree with real weather values
    public String recommend(double temp, double humidity, double rain) {
        TreeNode current = root;

        while (!current.isLeaf()) {
            double value = switch (current.attribute) {
                case "temp"     -> temp;
                case "humidity" -> humidity;
                case "rain"     -> rain;
                default         -> 0;
            };
            // left = condition MET (value > threshold)
            current = (value > current.threshold) ? current.left : current.right;
        }

        return current.crop;
    }
}

//  MAIN CLASS
public class GreenTechDSA {

    static HashMap<String, CropData> crops      = new HashMap<>();
    static ArrayList<Fertilizer>     fertilizers = new ArrayList<>();
    static ArrayList<Irrigation>     irrigations  = new ArrayList<>();
    static LinkedList<Double>        rainHistory  = new LinkedList<>();

    static double round(double v) { return Math.round(v * 100.0) / 100.0; }

    public static void main(String[] args) {

        Scanner sc = new Scanner(System.in);
        initializeData();

        irrigations.sort((a, b) -> a.efficiency - b.efficiency);

        WeatherService api = new WeatherService();

        System.out.print("Enter Location: ");
        String location = sc.nextLine();

        // FETCH REAL WEATHER
        WeatherData data = api.fetchWeather(location);
        if (data == null) {
            System.out.println("Could not fetch weather. Check your API key or city name.");
            return;
        }

        // Sliding window rainfall
        rainHistory.add(data.rain);
        if (rainHistory.size() > 5) rainHistory.removeFirst();

        double totalRain = 0;
        for (double r : rainHistory) totalRain += r;

        double evap        = Math.max(0, (data.temp * 0.05) - (data.humidity * 0.02));
        double netMoisture = Math.max(0, totalRain - evap);

         //  DECISION TREE CROP SUGGESTION
       
        CropDecisionTree tree = new CropDecisionTree();
        String suggestedCrop  = tree.recommend(data.temp, data.humidity, data.rain);

        System.out.println("\n Based on current weather, we suggest: " 
                           + suggestedCrop.toUpperCase());
        System.out.print("Enter Crop (press Enter to use suggestion): ");

        // flush leftover newline from previous nextLine if needed
        String cropInput = sc.nextLine().trim().toLowerCase();

        // If user just hits Enter, use suggested crop
        String cropName = cropInput.isEmpty() ? suggestedCrop : cropInput;
        // ── End of Decision Tree block 


        CropData crop = crops.get(cropName);
        if (crop == null) {
            System.out.println("Crop not found: " + cropName);
            return;
        }

        // Questionnaire
        System.out.println("\nRate (1-5):");
        int soil  = getInput(sc, "Soil Quality: ");
        int water = getInput(sc, "Water Availability: ");
        int pest  = getInput(sc, "Pest Condition: ");

        // Input validation
        if (soil < 1 || soil > 5 || water < 1 || water > 5 || pest < 1 || pest > 5) {
            System.out.println("Ratings must be between 1 and 5.");
            return;
        }

        int healthScore = (soil + water + pest) / 3;

        // Budget
        System.out.print("Enter Fertilizer Budget: ");
        int budget = sc.nextInt();

        //MAX HEAP FERTILIZER SELECTION
               List<Fertilizer> selectedFerts = selectFertilizersHeap(budget);
       
        //  BINARY SEARCH IRRIGATION SELECTION
                Irrigation irrigation = chooseIrrigationBinarySearch(water);

        System.out.print("Enter Farm Area (in acres): ");
        double area = sc.nextDouble();

        double moistureRatio  = netMoisture / crop.moistureThreshold;
        double moistureFactor = Math.max(0.3, Math.min(1.0, moistureRatio));

        double yield   = round(crop.yield * area * (healthScore / 5.0) * moistureFactor);
        double revenue = yield * crop.marketRate;
        int    cost    = selectedFerts.stream().mapToInt(f -> f.price).sum();
        double profit  = round(revenue - cost);

        //  SCORING 
        double moistureScore = moistureFactor * 100;                                      
        double soilScore     = (soil / 5.0) * 100;
        double waterScore    = (water / 5.0) * 100;
        double pestScore     = ((6 - pest) / 5.0) * 100;
        double carbonScore   = (crop.carbonScore / 10.0) * 100;
        double profitScore   = Math.max(0, Math.min(100, (profit / 10000.0) * 100));      

        double score = round(
            (0.25 * moistureScore) +
            (0.15 * soilScore)     +
            (0.15 * waterScore)    +
            (0.10 * pestScore)     +
            (0.10 * carbonScore)   +
            (0.25 * profitScore)
        );
        score = Math.max(0, Math.min(score, 100));

        String condition = (score > 70) ? "GOOD" : (score > 50) ? "WARNING" : "CRITICAL";

        // OUTPUT 
        System.out.println("\n=================================");
        System.out.println("Location : " + location);

        System.out.println("\n--- LIVE WEATHER ---");
        System.out.println("Temp: " + round(data.temp) + "°C | Humidity: "
                + round(data.humidity) + "% | Wind: " + round(data.wind) + " km/h");
        System.out.println("Rainfall Histo ry : " + rainHistory);
        System.out.println("Accumulated Water: " + round(totalRain) + " mm");
        System.out.println("Evaporation      : " + round(evap));
        System.out.println("Net Soil Moisture: " + round(netMoisture));

        System.out.println("\n--- CROP: " + crop.name.toUpperCase() + " ---");
        if (netMoisture < crop.moistureThreshold) {
            double deficit = round(crop.moistureThreshold - netMoisture);
            System.out.println("DEFICIT DETECTED");
            System.out.println("Water Required: " + deficit + " mm");
        } else {
            System.out.println("OPTIMAL");
        }

        System.out.println("\n--- RECOMMENDATIONS ---");
        System.out.println("Irrigation: " + irrigation.name 
                           + " (efficiency: " + irrigation.efficiency + ")");
        System.out.println("Fertilizers:");
        selectedFerts.forEach(f -> System.out.println("  " + f.name + "  Rs." + f.price));

        System.out.println("\nYield : " + yield + " tons");
        System.out.println("Profit: Rs." + profit);

        System.out.println("\n--- FINAL INDEX ---");
        System.out.println("Score    : " + score + "%");
        System.out.println("Condition: " + condition);
        System.out.println("=================================");
    }

    // MAX HEAP FERTILIZER SELECTION
       static List<Fertilizer> selectFertilizersHeap(int budget) {

        // Max heap: highest efficiency-to-price ratio comes out first
        PriorityQueue<Fertilizer> heap = new PriorityQueue<>(
            (a, b) -> Double.compare(
                (double) b.efficiency / b.price,
                (double) a.efficiency / a.price
            )
        );

        heap.addAll(fertilizers);

        List<Fertilizer> result = new ArrayList<>();

        while (!heap.isEmpty() && budget > 0) {
            Fertilizer best = heap.poll(); // O(log n) — always the best ratio
            if (budget >= best.price) {
                result.add(best);
                budget -= best.price;
            }
        }

        return result;
    }

  // BINARY SEARCH IRRIGATION SELECTION
        static Irrigation chooseIrrigationBinarySearch(int water) {

        int targetEfficiency = (water >= 3) ? 4 : 1;

        int lo   = 0;
        int hi   = irrigations.size() - 1;
        int best = -1;

        while (lo <= hi) {
            int mid = (lo + hi) / 2;

            if (irrigations.get(mid).efficiency >= targetEfficiency) {
                best = mid;   // this one qualifies — try to find an earlier one
                hi = mid - 1;
            } else {
                lo = mid + 1; // too low, go right
            }
        }

        return (best != -1) ? irrigations.get(best) : irrigations.get(irrigations.size() - 1);
    }

    
    static void initializeData() {

        crops.put("rice",         new CropData("Rice",         "Kharif",   "Humid",     "Clayey",     "High",   2.5,  20000, 8, 10));
        crops.put("wheat",        new CropData("Wheat",        "Rabi",     "Cool",      "Loamy",      "Medium", 2.8,  18000, 6,  6));
        crops.put("cotton",       new CropData("Cotton",       "Kharif",   "Hot",       "Black",      "Medium", 1.5,  60000, 9,  7));
        crops.put("maize",        new CropData("Maize",        "Kharif",   "Warm",      "Loamy",      "Medium", 3.0,  17000, 7,  6));
        crops.put("sugarcane",    new CropData("Sugarcane",    "Annual",   "Hot-Humid", "Alluvial",   "High",  10.0,   3000, 9, 12));
        crops.put("soybean",      new CropData("Soybean",      "Kharif",   "Moderate",  "Loamy",      "Medium", 2.2,  40000, 6,  5));
        crops.put("barley",       new CropData("Barley",       "Rabi",     "Cool-Dry",  "Sandy",      "Low",    2.5,  16000, 5,  4));
        crops.put("millet",       new CropData("Millet",       "Kharif",   "Dry",       "Sandy",      "Low",    1.8,  15000, 4,  3));
        crops.put("pulses",       new CropData("Pulses",       "Rabi",     "Dry",       "Loamy",      "Low",    1.2,  50000, 5,  3));
        crops.put("groundnut",    new CropData("Groundnut",    "Kharif",   "Warm",      "Sandy",      "Medium", 2.0,  55000, 6,  5));
        crops.put("mustard",      new CropData("Mustard",      "Rabi",     "Cool",      "Loamy",      "Low",    1.5,  60000, 5,  4));
        crops.put("potato",       new CropData("Potato",       "Rabi",     "Cool",      "Loamy",      "High",   3.5,  12000, 7,  8));
        crops.put("tomato",       new CropData("Tomato",       "All",      "Moderate",  "Loamy",      "Medium", 4.0,  20000, 6,  6));
        crops.put("onion",        new CropData("Onion",        "Rabi",     "Dry",       "Sandy-Loam", "Medium", 3.0,  15000, 6,  5));
        crops.put("chickpea",     new CropData("Chickpea",     "Rabi",     "Cool-Dry",  "Loamy",      "Low",    1.3,  48000, 5,  3));
        crops.put("banana",       new CropData("Banana",       "Annual",   "Humid",     "Loamy",      "High",   8.0,  10000, 8, 11));
        crops.put("mango",        new CropData("Mango",        "Seasonal", "Tropical",  "Alluvial",   "Medium", 5.0,  25000, 7,  7));
        crops.put("tea",          new CropData("Tea",          "Perennial","Humid",     "Acidic",     "High",   2.0, 300000, 9, 10));
        crops.put("cucumber",     new CropData("Cucumber",     "Zaid",     "Warm",      "Loamy",      "Medium", 5.0,  22000, 6,  5));
        crops.put("okra",         new CropData("Okra",         "Kharif",   "Warm",      "Loamy",      "Medium", 3.5,  30000, 6,  5));
        crops.put("bottle gourd", new CropData("Bottle Gourd", "Zaid",     "Warm",      "Sandy-Loam", "Medium", 6.0,  18000, 5,  6));
        crops.put("bitter gourd", new CropData("Bitter Gourd", "Zaid",     "Warm",      "Loamy",      "Medium", 4.0,  38000, 6,  5));

        fertilizers.add(new Fertilizer("Urea",                         500,  3));
        fertilizers.add(new Fertilizer("DAP",                         1200,  5));
        fertilizers.add(new Fertilizer("Compost",                      300,  2));
        fertilizers.add(new Fertilizer("MOP (Potash)",                 900,  4));
        fertilizers.add(new Fertilizer("NPK 10:26:26",                1500,  6));
        fertilizers.add(new Fertilizer("NPK 19:19:19",                1600,  7));
        fertilizers.add(new Fertilizer("Ammonium Sulphate",            800,  4));
        fertilizers.add(new Fertilizer("Calcium Ammonium Nitrate",    1000,  5));
        fertilizers.add(new Fertilizer("Single Super Phosphate (SSP)", 700,  3));
        fertilizers.add(new Fertilizer("Zinc Sulphate",                600,  3));
        fertilizers.add(new Fertilizer("Biofertilizer",                400,  4));

        irrigations.add(new Irrigation("Drip",               2000, 5));
        irrigations.add(new Irrigation("Sprinkler",          1500, 4));
        irrigations.add(new Irrigation("Flood",               500, 2));
        irrigations.add(new Irrigation("Center Pivot",       3000, 5));
        irrigations.add(new Irrigation("Rain Gun",           2500, 4));
        irrigations.add(new Irrigation("Subsurface Drip",   3500, 6));
        irrigations.add(new Irrigation("Manual Irrigation",  200, 1));
        irrigations.add(new Irrigation("Canal Irrigation",   800, 3));
        irrigations.add(new Irrigation("Furrow Irrigation",  600, 2));
    }

    static int getInput(Scanner sc, String msg) {
        System.out.print(msg);
        return sc.nextInt();
    }

}
