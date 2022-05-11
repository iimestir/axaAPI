import netscape.javascript.JSObject;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class main {
    public static void main(String[] args) throws IOException, JSONException {
        String team_uuid = "40b72791-dda8-402e-b5d2-eea1b8f99291";
        JSONObject json = getSecurityToken(team_uuid);

        String securityToken = json.getString("hash");
        String time = json.getString("time");

        System.out.println(securityToken);
        JSONArray clients = getClients(team_uuid, securityToken, time);
        JSONArray areas = getAreas(team_uuid, securityToken, time);

        System.out.println(clients);
        System.out.println(areas);

        calculateRiskA(clients, areas);
        double rainMaxSafe = 0.000075; // L
    }

    private static JSONObject getArea(JSONArray areas, String town) throws JSONException {
        for(int i = 0; i < areas.length(); i++) {
            String areaTown = areas.getJSONObject(i).getString("town");
            areaTown = areaTown.replaceAll("\\s+","");

            if(areaTown.equals(town))
                return areas.getJSONObject(i);
        }

        return null;
    }

    private static List<Integer> calculateRiskA(JSONArray clients, JSONArray areas) throws JSONException, IOException {
        List<String> clientsz = new ArrayList<>();

        for(int i = 0; i < clients.length(); i++) {
            JSONObject client = clients.getJSONObject(i);
            String address = client.getString("address");

            String[] add = address.split(",");
            String town = add[add.length-2];
            town = town.replaceAll("\\s+","");

            JSONObject area = getArea(areas, town);

            double floodingRisk = getFloodingRisk(area);
            double fireRisk = getFireRisk(area);
            double windRisk = getWindRisk(area);
            double healthRisk = getHealthRisk(area);

            double riskA = (floodingRisk + fireRisk + windRisk + healthRisk) / 4;

            clientsz.add(client.getString("id"));
        }

        postData(clientsz, "10");

        return null;
    }

    private static void postData(List<String> clientIDs, String riskA) throws IOException {
        URL url = new URL("http://192.168.0.190:8080/api/game/clients");
        HttpURLConnection conn = (HttpURLConnection)url.openConnection();
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("Content-Type", "application/json");

        String data = "";

        for(String clientID : clientIDs) {
            data += "{\n  \"clientId\": \"" + clientID + "\",\n  \"riskValue\": \"" + riskA + "\"}";
        }

        byte[] out = data.getBytes(StandardCharsets.UTF_8);

        OutputStream stream = conn.getOutputStream();
        stream.write(out);

        System.out.println(conn.getResponseCode() + " " + conn.getResponseMessage());
        conn.disconnect();
    }

    private static double getFloodingRisk(JSONObject area) throws JSONException {
        double rainMaxAverage = Double.parseDouble(area.getString("rainMaxAverage"));
        double rainMaxSafe = 0.075;
        double seismicArea = Double.parseDouble(area.getString("seismicArea"));

        boolean seaNearby = area.getBoolean("seaNearby");
        boolean lakeNearby = area.getBoolean("lakeOrRiverNearby");
        boolean tropicalStorm = area.getBoolean("tropicalStorm");

        int seaN = seaNearby ? 1 : 0;
        int lakeN = lakeNearby ? 1 : 0;
        int tropN = tropicalStorm ? 1 : 0;

        return (rainMaxAverage / (rainMaxSafe/10.)) + (0.75 * seismicArea * seaN)
                + (0.5 * seismicArea * lakeN) + (2.5 * seismicArea * tropN);
    }

    private static double getFireRisk(JSONObject area) throws JSONException {
        double maxavg20temp = getMaxAvg20(area.getJSONArray("annualTemperatureAverageList"));

        return 0;
    }

    private static double getMaxAvg20(JSONArray temps) {
        List<Double> tempsList = new ArrayList<Double>();



        return 0;
    }

    private static double getWindRisk(JSONObject area) throws JSONException {
        String windUnit = area.getString("windUnit");

        double windMax = windUnit.equals("m/s") ?
            Math.ceil(Math.cbrt(Math.pow(getMaxWindSpeed(area.getJSONArray("annualWindAverageList"))/0.836, 2)))
            : getMaxWindSpeed(area.getJSONArray("annualWindAverageList"));
        double windMaxFactor = 11;
        double windSafe = 6;
        boolean urban = area.getJSONObject("location").getBoolean("urban");
        boolean forest = area.getJSONObject("location").getBoolean("forest");

        int urbanFactor = urban ? 20 : -20;
        int forestFactor = forest ? 20 : -20;

        return windMax * (windMaxFactor - windSafe) + urbanFactor + forestFactor;
    }

    private static double getMaxWindSpeed(JSONArray wind) throws JSONException {
        double max = 0;

        for(int i = 0; i < wind.length(); i++) {
            double current = Double.parseDouble(wind.getJSONObject(i).getString("speed"));
            if(current > max) {
                max = current;
            }
        }

        return max;
    }

    private static double getHealthRisk(JSONObject area) throws JSONException {
        boolean compound = area.getJSONObject("location").getBoolean("industrialCompound");
        boolean urban = area.getJSONObject("location").getBoolean("urban");
        double airQuality = Double.parseDouble(area.getString("airQuality"));
        double airQSafe = 50.0;
        double seismicArea = Double.parseDouble(area.getString("seismicArea"));
        double maxFactor = 8.0;
        double safeFactor = 4.0;

        int indFactor = compound ? 10 : -10;
        int urbanFactor = urban ? 10 : -10;

        return indFactor + urbanFactor + airQuality - airQSafe + (seismicArea * (maxFactor - safeFactor))*2;
    }

    private static JSONArray getClients(String team_uuid, String hash, String time) throws IOException, JSONException {
        StringBuilder result = new StringBuilder();
        URL url = new URL("http://192.168.0.190:8080/api/game/clients");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("teamUUID", team_uuid);
        conn.setRequestProperty("hash", hash);
        conn.setRequestProperty("time", time);
        conn.setRequestMethod("GET");
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream()))) {
            for (String line; (line = reader.readLine()) != null; ) {
                result.append(line);
            }
        }

        JSONArray array = new JSONArray(result.toString());
        conn.disconnect();

        return array;
    }

    private static JSONArray getAreas(String team_uuid, String hash, String time) throws IOException, JSONException {
        StringBuilder result = new StringBuilder();
        URL url = new URL("http://192.168.0.190:8080/api/game/areainfos");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("teamUUID", team_uuid);
        conn.setRequestProperty("hash", hash);
        conn.setRequestProperty("time", time);
        conn.setRequestMethod("GET");
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream()))) {
            for (String line; (line = reader.readLine()) != null; ) {
                result.append(line);
            }
        }

        JSONArray array = new JSONArray(result.toString());
        conn.disconnect();

        return array;
    }

    private static JSONObject getSecurityToken(String team_uuid) throws IOException, JSONException {
        StringBuilder result = new StringBuilder();
        URL url = new URL("http://192.168.0.190:8080/api/security/token/" + team_uuid);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream()))) {
            for (String line; (line = reader.readLine()) != null; ) {
                result.append(line);
            }
        }

        JSONObject jsonObject = new JSONObject(result.toString());
        conn.disconnect();

        return jsonObject;
    }
}
