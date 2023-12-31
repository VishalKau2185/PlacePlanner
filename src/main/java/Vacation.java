import java.util.Scanner;
import org.bson.Document;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.FindIterable;
import org.json.JSONArray;
import org.json.JSONObject;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class Vacation {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.println("Do you want to see past preferences? (y/n):");
        String viewPastPreferences = scanner.nextLine();

        if (viewPastPreferences.equalsIgnoreCase("y")) {
            // Option to view past preferences
            displayPastPreferences();
        } else if (viewPastPreferences.equalsIgnoreCase("n")) {
            // Get user input
            System.out.print("Enter state (e.g., New York): ");
            String state = scanner.nextLine();
            System.out.print("Enter city: ");
            String city = scanner.nextLine();
            System.out.print("Enter preferred activity (e.g., Beach, Mountain, City): ");
            String activityPreference = scanner.nextLine();

            // Connect to MongoDB
            try (MongoClient mongoClient = MongoClients.create("mongodb://localhost:27017")) {
                MongoDatabase database = mongoClient.getDatabase("vacationdb");
                MongoCollection<Document> collection = database.getCollection("userPreferences");

                // Check if the user preferences are in the database
                Document userPreferencesDoc = collection
                        .find(new Document("state", state)
                                .append("city", city)
                                .append("activity", activityPreference))
                        .first();

                if (userPreferencesDoc != null && userPreferencesDoc.containsKey("businessRecommendation")) {
                    // If found in the database, display the recommendation
                    displayRecommendation(userPreferencesDoc);
                } else {
                    // If not found in the database, proceed with the recommendation
                    String[] recommendation = recommendDestination(state, city, activityPreference);

                    // Display the recommendation
                    displayRecommendation(recommendation);

                    // Save the recommendation in the database
                    saveOrUpdateUserPreferences(collection, state, city, activityPreference, recommendation);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            System.out.println("Invalid input. Please enter 'y' or 'n'.");
        }

        // Close resources
        scanner.close();
    }

    private static void displayRecommendation(Document userPreferencesDoc) {
        System.out.println("\nRecommendation based on your preferences: ");
        System.out.println(userPreferencesDoc.getString("businessRecommendation"));
        System.out.println("Best weather day within the next 5 days: " + userPreferencesDoc.getString("bestWeatherDay"));
    }

    private static void displayRecommendation(String[] recommendation) {
        System.out.println();
        System.out.println();
        System.out.println();
        System.out.println("\nRecommendation: ");
        System.out.println("Business Recommendations:");
        System.out.println(recommendation[0]);
        System.out.println("Best Weather Day: " + recommendation[1]);
        System.out.println();
        System.out.println();
        System.out.println();
        System.out.println();
    }

    private static void displayPastPreferences() {
        try (MongoClient mongoClient = MongoClients.create("mongodb://localhost:27017")) {
            MongoDatabase database = mongoClient.getDatabase("vacationdb");
            MongoCollection<Document> collection = database.getCollection("userPreferences");

            // Retrieve and display past preferences
            FindIterable<Document> pastPreferences = collection.find();
            System.out.println("\nPast Preferences:");
            for (Document document : pastPreferences) {
                displayRecommendation(document);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void saveOrUpdateUserPreferences(
            MongoCollection<Document> collection, String state, String city,
            String activityPreference, String[] recommendation) {
        // Save or update the user preferences in the database
        if (collection != null) {
            Document userPreferencesDoc = new Document("state", state)
                    .append("city", city)
                    .append("activity", activityPreference)
                    .append("businessRecommendation", recommendation[0])
                    .append("bestWeatherDay", recommendation[1]);

            if (collection.find(userPreferencesDoc).first() == null) {
                // If user preferences are not in the database, insert them
                collection.insertOne(userPreferencesDoc);
            } else {
                // If user preferences are in the database, update the recommendation
                collection.updateOne(new Document("state", state)
                                .append("city", city)
                                .append("activity", activityPreference),
                        new Document("$set", userPreferencesDoc));
            }
        }
    }

    private static String[] recommendDestination(String state, String city, String activityPreference) {
        // Call AccuWeather API
        String accuWeatherApiKey = "mGchAJjZU7MbIIaGOZvGN73utrRykWgn";
        String accuWeatherApiUrl = "http://dataservice.accuweather.com/locations/v1/search?q=" + state +
                "&apikey=" + accuWeatherApiKey;
        String accuWeatherResponse = callApi(accuWeatherApiUrl);

        // Process AccuWeather API results
        String accuWeatherRecommendation = processAccuWeatherResults(accuWeatherResponse, activityPreference);

        // Call Yelp API
        String yelpApiKey = "d-w3dCvI5tnQtksQY9tf0trotwPKLOoJQkffNLlrWd8tnLZfnD11nbIBQet4otYKERO4s_i-ku6SrV_Am3iQejNVL5hZM2aXrwqLVo9R4jfuNZDGWUCXiUGV-ImRZXYx";
        String yelpApiUrl = "https://api.yelp.com/v3/businesses/search?term=" + activityPreference +
                "&location=" + city;
        String yelpResponse = callApiWithAuthorization(yelpApiUrl, yelpApiKey);

        // Process Yelp API results
        String yelpRecommendation = processYelpResults(yelpResponse);

        // Get the best weather day within the next 5 days
        String bestWeatherDay = getBestWeatherDay(accuWeatherResponse);

        // Combine recommendations from both sources
        return new String[]{yelpRecommendation, bestWeatherDay};
    }

    private static String processAccuWeatherResults(String accuWeatherResult, String activityPreference) {
        try {
            JSONArray locationsArray = new JSONArray(accuWeatherResult);

            // Check if there are any locations found
            if (locationsArray.length() > 0) {
                JSONObject location = locationsArray.getJSONObject(0);
                return "Recommended location: " + location.getString("LocalizedName");
            } else {
                return "No specific location recommendation found based on the provided preferences.";
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "Error processing AccuWeather results.";
        }
    }

    private static String processYelpResults(String yelpResult) {
        try {
            JSONObject yelpJson = new JSONObject(yelpResult);

            // Check if there are any businesses found
            if (yelpJson.has("businesses")) {
                JSONArray businesses = yelpJson.getJSONArray("businesses");

                if (businesses.length() > 0) {
                    StringBuilder recommendations = new StringBuilder();
                    for (int i = 0; i < Math.min(businesses.length(), 3); i++) {
                        JSONObject business = businesses.getJSONObject(i);
                        recommendations.append("Recommended business for activities: ")
                                .append(business.getString("name"))
                                .append("\n");
                    }
                    return recommendations.toString();
                } else {
                    return "No specific business recommendations found based on the provided preferences.";
                }
            } else {
                return "Business information not found in the Yelp API response.";
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "Error processing Yelp results.";
        }
    }

    private static String getBestWeatherDay(String accuWeatherResult) {
        try {
            JSONArray locationsArray = new JSONArray(accuWeatherResult);
    
            // Check if there are any locations found
            if (locationsArray.length() > 0) {
                JSONObject location = locationsArray.getJSONObject(0);
                String locationKey = location.getString("Key");
    
                // Call AccuWeather API to get 5-day forecast
                String accuWeatherForecastUrl = "http://dataservice.accuweather.com/forecasts/v1/daily/5day/"
                        + locationKey + "?apikey=mGchAJjZU7MbIIaGOZvGN73utrRykWgn";
                String accuWeatherForecastResponse = callApi(accuWeatherForecastUrl);
    
                JSONObject forecastJson = new JSONObject(accuWeatherForecastResponse);
    
                JSONArray dailyForecasts = forecastJson.getJSONArray("DailyForecasts");
                if (dailyForecasts.length() > 0) {
                    JSONObject bestWeatherDay = dailyForecasts.getJSONObject(0);
                    JSONObject temperature = bestWeatherDay.getJSONObject("Temperature");
                    JSONObject minimumTemperature = temperature.getJSONObject("Minimum");
    
                    return "Date: " + bestWeatherDay.getString("Date") +
                            ", Minimum Temperature: " + minimumTemperature.getDouble("Value") +
                            " " + minimumTemperature.getString("Unit");
                } else {
                    return "No weather forecast available.";
                }
            } else {
                return "No specific location recommendation found based on the provided preferences.";
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "Error getting the best weather day.";
        }
    }    

    private static String callApi(String apiUrl) {
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder().url(apiUrl).build();
        try {
            Response response = client.newCall(request).execute();
            return response.body().string();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private static String callApiWithAuthorization(String apiUrl, String apiKey) {
        OkHttpClient client = new OkHttpClient();
        Request request = new Request.Builder()
                .url(apiUrl)
                .header("Authorization", "Bearer " + apiKey)
                .build();
        try {
            Response response = client.newCall(request).execute();
            return response.body().string();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
