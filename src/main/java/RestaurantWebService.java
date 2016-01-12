/**
 * Created by jroerthmans on 07.01.2016.
 */

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.net.ConnectException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import fi.iki.elonen.NanoHTTPD;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;


public class RestaurantWebService extends NanoHTTPD {

    //region ## Globale Variablen ##
    private static final boolean debug = true;

    private static final JSONParser JSON_PARSER = new JSONParser();

    private static final int PORT = 8081;

    private static final String CREATE_RESTAURANT = "createRestaurant",
            GET_RESTAURANT = "getRestaurants",
            POST_RESTAURANT = "postRestaurant",
            OTHER_SERVER = "127.0.0.1:8082/";

    private Object token = new Object();

    //endregion

    public RestaurantWebService() throws IOException {
        super(PORT);
        start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
        System.out.println("\nRunning! Point your browers to http://localhost:" + PORT + "/ \n");
    }

    public static void main(String[] args) {
        try {
            new RestaurantWebService();
        } catch (IOException ioe) {
            System.err.println("Couldn't start server:\n" + ioe);
        }
    }

    @Override
    public Response serve(IHTTPSession session) {
        //Lese Methode aus URI
        String uri = session.getUri().substring(1, session.getUri().length()).trim();

        if (debug)
            System.out.println(uri);

        String restaurantName = null;
        long restaurantId = 0;
        short restaurantBewertung = 0;
        Map<String, String> files = new HashMap<>(), parameter = session.getParms();

        try {
            session.parseBody(files);
            if (parameter.get("name") != null)
                restaurantName = parameter.get("name");
            if (parameter.get("id") != null)
                restaurantId = Long.parseLong(parameter.get("id"));
            if (parameter.get("bewertung") != null)
                restaurantBewertung = Short.parseShort(parameter.get("bewertung"));
        } catch (StringIndexOutOfBoundsException e) {
            return error(21, e.getMessage());
        } catch (IOException e) {
            return error(31, e.getMessage());
        } catch (ResponseException e) {
            return error(41, e.getMessage());
        }


        switch (uri) {
            case CREATE_RESTAURANT:
                sendRequstToOtherServer(restaurantName);
                return addRestaurant(restaurantName);
            case POST_RESTAURANT:
                sendRequstToOtherServer(restaurantId, restaurantBewertung);
                return rateRestaurant(restaurantId, restaurantBewertung);
            case GET_RESTAURANT:
                return newFixedLengthResponse(readRestaurants().toJSONString());
            default:
                return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Fehler Ressource nicht gefunden");
        }
    }

    private Response rateRestaurant(long restaurantId, short restaurantBewertung) {
        JSONArray restaurants = readRestaurants();
        Restaurant restaurant = new Restaurant();
        int i = 0;
        for (; i < restaurants.size(); i++) {
            JSONObject restaurantJSON = (JSONObject) restaurants.get(i);
            long id = Long.parseLong(restaurantJSON.get("id").toString());
            if (id == restaurantId)
                restaurant = Restaurant.parseJSON(restaurantJSON);
            break;
        }

        restaurant.bewertung = ((restaurant.anzahl * restaurant.bewertung) + restaurantBewertung) / ++restaurant.anzahl;
        restaurants.set(i, restaurant.toJSON());
        writeRestaurants(restaurants);

        return newFixedLengthResponse(restaurants.toJSONString());
    }

    private Response addRestaurant(String name) {

        //Einträge lesen
        JSONArray restaurants = readRestaurants();

        //Eintrag hinzufügen
        restaurants.add(createNewRestaurant(name));

        //Datei speichern
        writeRestaurants(restaurants);

        return newFixedLengthResponse(restaurants.toJSONString());
    }

    private JSONObject createNewRestaurant(String name) {
        JSONObject restaurant = new JSONObject();
        restaurant.put("id", new Date().getTime());
        restaurant.put("name", name);
        restaurant.put("bewertung", 0);
        restaurant.put("anzahl", 0);

        return restaurant;
    }

    private void writeRestaurants(JSONArray restaurants) {
        try {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("restaurantListe", restaurants);
            synchronized (token) {
                PrintWriter printWriter = new PrintWriter("restaurants.json");
                printWriter.print(jsonObject.toJSONString());
                printWriter.close();
            }
        } catch (IOException e) {
            System.out.println(e);
        }
    }

    private JSONArray readRestaurants() {
        try {
            synchronized (token) {
                //Lesen der Liste Restaurants
                BufferedReader input = new BufferedReader(new FileReader("restaurants.json"));
                StringBuilder stringBuilder = new StringBuilder();

                String line;
                while ((line = input.readLine()) != null) {
                    stringBuilder.append(line);
                }
                input.close();

                if (stringBuilder.length() < 20)
                    return new JSONArray();

                JSONObject jsonObject = (JSONObject) JSON_PARSER.parse(stringBuilder.toString());
                return (JSONArray) jsonObject.get("restaurantListe");
            }
        } catch (IOException e) {
            System.out.println(e);
        } catch (ParseException e) {
            System.out.println(e);
        }
        return new JSONArray();
    }

    private void sendRequstToOtherServer(long restaurantId, short restaurantBewertung) {
        try {
            //TODO: Verbindung aufbauen und Request versenden
        } catch (Exception e) {
            //catch (ConnectException e){
            //TODO: Falls ein Problem auftritt muss der Request in eine Liste geschrieben werden
            //TODO: diese Liste muss dann bei wiedererreichbarkeit des Servers abgearbeitet werden
        }
    }

    private void sendRequstToOtherServer(String restaurantName) {
        try {
            //TODO: Verbindung aufbauen und Request versenden
        } catch (Exception e) {
            //catch (ConnectException e){
            //TODO: Falls ein Problem auftritt muss der Request in eine Liste geschrieben werden
            //TODO: diese Liste muss dann bei wiedererreichbarkeit des Servers abgearbeitet werden
        }
    }

    private Response error(int code, String text) {
        System.out.println(text);

        JSONObject response = new JSONObject();
        JSONObject error = new JSONObject();

        error.put("code", code);
        error.put("text", text);

        response.put("status", 0);
        response.put("data", "[]");
        response.put("error", error);

        return newFixedLengthResponse(response.toJSONString());
    }
}