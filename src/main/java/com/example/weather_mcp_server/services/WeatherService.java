package com.example.weather_mcp_server.services;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Service
public class WeatherService {

    private final RestClient restClient = RestClient.create();
    private final ObjectMapper mapper = new ObjectMapper();

    @Tool(description = "Get the current weather for a given city")
    public String getWeather(String city) {
        try {
            // Step 1: Geocode city to lat/lon
            String geoUrl = "https://geocoding-api.open-meteo.com/v1/search?name="
                    + city + "&count=1&language=en&format=json";

            String geoResponse = restClient.get()
                    .uri(geoUrl)
                    .retrieve()
                    .body(String.class);

            JsonNode geoJson = mapper.readTree(geoResponse);
            JsonNode results = geoJson.get("results");

            if (results == null || results.isEmpty()) {
                return "City not found: " + city;
            }

            double lat = results.get(0).get("latitude").asDouble();
            double lon = results.get(0).get("longitude").asDouble();
            String timezone = results.get(0).get("timezone").asText();

            // Step 2: Get current weather
            String weatherUrl = "https://api.open-meteo.com/v1/forecast"
                    + "?latitude=" + lat
                    + "&longitude=" + lon
                    + "&current=temperature_2m,relative_humidity_2m,wind_speed_10m,weather_code"
                    + "&timezone=" + timezone;

            String weatherResponse = restClient.get()
                    .uri(weatherUrl)
                    .retrieve()
                    .body(String.class);

            JsonNode weatherJson = mapper.readTree(weatherResponse);
            JsonNode current = weatherJson.get("current");

            double temp = current.get("temperature_2m").asDouble();
            int humidity = current.get("relative_humidity_2m").asInt();
            double windSpeed = current.get("wind_speed_10m").asDouble();
            int weatherCode = current.get("weather_code").asInt();
            String units = weatherJson.get("current_units").get("temperature_2m").asText();

            return String.format(
                    "Weather in %s: %s, Temperature: %.1f%s, Humidity: %d%%, Wind Speed: %.1f km/h",
                    city, describeWeatherCode(weatherCode), temp, units, humidity, windSpeed
            );

        } catch (Exception e) {
            return "Failed to fetch weather for " + city + ": " + e.getMessage();
        }
    }

    @Tool(description = "Get a 5-day forecast for a city")
    public String getForecast(String city, int days) {
        try {
            // Geocode
            String geoUrl = "https://geocoding-api.open-meteo.com/v1/search?name="
                    + city + "&count=1&language=en&format=json";

            String geoResponse = restClient.get()
                    .uri(geoUrl)
                    .retrieve()
                    .body(String.class);

            JsonNode geoJson = mapper.readTree(geoResponse);
            JsonNode results = geoJson.get("results");

            if (results == null || results.isEmpty()) {
                return "City not found: " + city;
            }

            double lat = results.get(0).get("latitude").asDouble();
            double lon = results.get(0).get("longitude").asDouble();
            String timezone = results.get(0).get("timezone").asText();

            // Get forecast
            String weatherUrl = "https://api.open-meteo.com/v1/forecast"
                    + "?latitude=" + lat
                    + "&longitude=" + lon
                    + "&daily=temperature_2m_max,temperature_2m_min,weather_code"
                    + "&timezone=" + timezone
                    + "&forecast_days=" + Math.min(days, 7);

            String weatherResponse = restClient.get()
                    .uri(weatherUrl)
                    .retrieve()
                    .body(String.class);

            JsonNode weatherJson = mapper.readTree(weatherResponse);
            JsonNode daily = weatherJson.get("daily");

            JsonNode dates = daily.get("time");
            JsonNode maxTemps = daily.get("temperature_2m_max");
            JsonNode minTemps = daily.get("temperature_2m_min");
            JsonNode codes = daily.get("weather_code");
            String units = weatherJson.get("daily_units").get("temperature_2m_max").asText();

            StringBuilder sb = new StringBuilder("Forecast for " + city + ":\n");
            for (int i = 0; i < dates.size(); i++) {
                sb.append(String.format("  %s: %s, High: %.1f%s, Low: %.1f%s%n",
                        dates.get(i).asText(),
                        describeWeatherCode(codes.get(i).asInt()),
                        maxTemps.get(i).asDouble(), units,
                        minTemps.get(i).asDouble(), units));
            }

            return sb.toString();

        } catch (Exception e) {
            return "Failed to fetch forecast for " + city + ": " + e.getMessage();
        }
    }

//    @Tool(description = "Get the current weather for a given city")
//    public String getWeather(String city) {
//        // Your actual logic here
//        return "Sunny, 72°F in " + city;
//    }

//    @Tool(description = "Get a 5-day forecast for a city")
//    public String getForecast(String city, int days) {
//        return "Forecast for " + city + " over " + days + " days: ...";
//    }

    private String describeWeatherCode(int code) {
        return switch (code) {
            case 0 -> "Clear sky";
            case 1 -> "Mainly clear";
            case 2 -> "Partly cloudy";
            case 3 -> "Overcast";
            case 45 -> "Foggy";
            case 48 -> "Icy fog";
            case 51 -> "Light drizzle";
            case 53 -> "Moderate drizzle";
            case 55 -> "Dense drizzle";
            case 56 -> "Light freezing drizzle";
            case 57 -> "Heavy freezing drizzle";
            case 61 -> "Slight rain";
            case 63 -> "Moderate rain";
            case 65 -> "Heavy rain";
            case 66 -> "Light freezing rain";
            case 67 -> "Heavy freezing rain";
            case 71 -> "Slight snowfall";
            case 73 -> "Moderate snowfall";
            case 75 -> "Heavy snowfall";
            case 77 -> "Snow grains";
            case 80 -> "Slight rain showers";
            case 81 -> "Moderate rain showers";
            case 82 -> "Violent rain showers";
            case 85 -> "Slight snow showers";
            case 86 -> "Heavy snow showers";
            case 95 -> "Thunderstorm";
            case 96 -> "Thunderstorm with slight hail";
            case 99 -> "Thunderstorm with heavy hail";
            default -> "Unknown (code " + code + ")";
        };
    }
}