import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;


/**
 * Created by jroerthmans on 14.01.2016.
 */
public class ConnectionHandler {

    private static final JSONParser JSON_PARSER = new JSONParser();

    public static JSONArray copyRestaurant(JSONObject jsonObject) throws IOException {
        try {
            return (JSONArray) JSON_PARSER.parse(httpRequestor(jsonObject.toJSONString(), RestaurantWebService.COPY_RESTAURANT));
        } catch (ParseException e) {
            return null;
        }
    }

    public static boolean isAvailable() throws IOException {
        if (httpRequestor("", RestaurantWebService.SERVER_UP_MSG) == "OK")
            return true;
        else
            return false;
    }

    private static String httpRequestor(String payload, String transaction) throws IOException {
        HttpURLConnection connection = null;
        try {
            if (payload == null)
                payload = "";

            URL url = new URL("http://" + RestaurantWebService.OTHER_SERVER + transaction);
            connection = (HttpURLConnection) url.openConnection();

            connection.setRequestMethod("POST");
            connection.setDoOutput(true);

            connection.setRequestProperty("Content-Type", "application/json");
            connection.setUseCaches(false);
            connection.setDoInput(true);


            OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream());
            writer.write(payload);
            writer.flush();

            InputStream inputStream = connection.getInputStream();
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
            String line;
            StringBuilder response = new StringBuilder();
            while ((line = bufferedReader.readLine()) != null) {
                response.append(line);
                response.append('\n');
            }

            bufferedReader.close();
            return response.toString();
        } catch (MalformedURLException e) {
        } finally {
            if (connection != null)
                connection.disconnect();
        }
        return "";

    }
}
