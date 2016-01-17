/**
 * Created by jroerthmans on 07.01.2016.
 */

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private static final String FILENAME = "restaurantList.json";

    public static final String CREATE_RESTAURANT = "createRestaurant",
            GET_RESTAURANT = "getRestaurant",
            POST_RESTAURANT = "postRestaurant",
            COPY_RESTAURANT = "copyRestaurant",
            SERVER_UP_MSG = "imHere",
            OTHER_SERVER = "127.0.0.1:8082/";

    private Object token = new Object();

    public static boolean backupServerUp = false;
    private static List<JSONObject> failedRequestList = new ArrayList<>();

    //endregion

    public RestaurantWebService() throws IOException {
        super(PORT);
        start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
        System.out.println("\nRunning! Point your browers to http://localhost:" + PORT + "/ \n");

        //Send Online to other Server
//        ConnectionHandler.isAvailable();
        new Thread() {
            @Override
            public void run() {
                while (true) {
                    try {
                        ConnectionHandler.isAvailable();
                        backupServerUp = true;
                        Thread.sleep(1000);
                    } catch (IOException e) {
                        backupServerUp = false;
                    } catch (InterruptedException e) {
                        backupServerUp = false;
                    }
                }
            }
        }.start();
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

        if (debug && !uri.startsWith("i"))
            System.out.println(uri);


        Map<String, String> files = new HashMap<>();
        session.getParms();
        StringBuilder params = new StringBuilder();
        Restaurant rest = new Restaurant();

        try {
            session.parseBody(files);
            params.append(files);
            if (params.length() > 10) {
                params.delete(0, 10);
                params.delete(params.length() - 1, params.length());
                rest = Restaurant.parseJSON(((JSONObject) JSON_PARSER.parse(params.toString())));
            }

//            if (parameter.get("name") != null)
//                restaurantName = parameter.get("name");
//            if (parameter.get("id") != null)
//                restaurantId = Long.parseLong(parameter.get("id"));
//            if (parameter.get("bewertung") != null)
//                restaurantBewertung = Short.parseShort(parameter.get("bewertung"));
//            if (parameter.get("anzahl") != null)
//                anzahl = Integer.parseInt(parameter.get("anzahl"));
        } catch (StringIndexOutOfBoundsException e) {
            return error(21, e.getMessage());
        } catch (IOException e) {
            return error(31, e.getMessage());
        } catch (ResponseException e) {
            return error(41, e.getMessage());
        } catch (ParseException e) {
            return error(51, e.getMessage());
        }


        switch (uri) {
            case CREATE_RESTAURANT:
                return addRestaurant(rest.name);
            case POST_RESTAURANT:
                return rateRestaurant(rest.id, rest.bewertung);
            case GET_RESTAURANT:
                return newFixedLengthResponse(readRestaurants().toJSONString());
            case COPY_RESTAURANT:
                otherServerUp();
                return addRestaurant(rest.id, rest.name, rest.bewertung, rest.anzahl);
            case SERVER_UP_MSG:
                return otherServerUp();
            default:
                return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Fehler Ressource nicht gefunden");
        }
    }

    private Response rateRestaurant(long restaurantId, double restaurantBewertung) {
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

        sendRequstToOtherServer(restaurant.toJSON());
        return newFixedLengthResponse(restaurants.toJSONString());
    }

    private Response addRestaurant(String name) {

        //Einträge lesen
        JSONArray restaurants = readRestaurants();

        //Eintrag hinzufügen
        JSONObject newRestaurant = createNewRestaurant(name);
        restaurants.add(newRestaurant);

        //Datei speichern
        writeRestaurants(restaurants);

        //An anderen Server senden
        sendRequstToOtherServer(newRestaurant);

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

    private Response addRestaurant(long restaurantId, String name, double restaurantBewertung, int anzahl) {

        //Einträge lesen
        JSONArray restaurants = readRestaurants();

        boolean replaced = false;

        Restaurant restaurant = new Restaurant();
        restaurant.bewertung = restaurantBewertung;
        restaurant.anzahl = anzahl;
        restaurant.name = name;
        restaurant.id = restaurantId;

        //Eintrag prüfen
        for (int i = 0; i < restaurants.size(); i++) {
            JSONObject restaurantJSON = (JSONObject) restaurants.get(i);
            long id = Long.parseLong(restaurantJSON.get("id").toString());
            if (id == restaurantId) {
                //Falls Eintrag vorhanden, diesen ersetzen
                restaurants.set(i, restaurant.toJSON());
                replaced = true;
                break;
            }
        }

        if (!replaced)
            restaurants.add(restaurant.toJSON());

        //Datei speichern
        writeRestaurants(restaurants);

        //An anderen Server senden
        return newFixedLengthResponse(restaurants.toJSONString());
    }

    private void writeRestaurants(JSONArray restaurants) {
        try {
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("restaurantListe", restaurants);
            synchronized (token) {
                PrintWriter printWriter = new PrintWriter(FILENAME);
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
                BufferedReader input = new BufferedReader(new FileReader(FILENAME));
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

    private boolean sendRequstToOtherServer(JSONObject jsonObject) {
        try {
            if (backupServerUp) {
                ConnectionHandler.copyRestaurant(jsonObject);
                return true;
            } else
                throw new IOException();
        } catch (IOException e) {
            //TODO: Test
            if (!failedRequestList.contains(jsonObject))
                failedRequestList.add(jsonObject);
            return false;
        }
    }

    private Response otherServerUp() {
        for (JSONObject object : failedRequestList) {
            if (sendRequstToOtherServer(object))
                failedRequestList.remove(object);
            else {
                backupServerUp = false;
                break;
            }
        }
        return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "OK");
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