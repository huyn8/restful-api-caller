import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.json.*;

/**
 * @Author Huy Nguyen
 * @REST
 * @Description The program takes in the name of a city and provides information 
 * about the weather (chance of rain, humidity, current, min, max temp, etc.) and
 * the air quality index (AQI). The program consumes 2 RESTful API: openweathermap.org
 * and aqicn.org to the info about the weather and AQI respectively. The program also
 * implements a exponential back-off for retry logic.
 */
public class program2 {
	public static String city = "";//name of the city to be used by 2 APIs
	public static boolean retry_first_api = true;//used for first API for retry logic
	public static boolean retry_second_api = true;//used for second API for retry logic
	public static long retry_threshold = 16000;//wait time in (miliseconds) threshold for retry logic
	public static int retry_count_1 = 0;//count retry time(s) for first API
	public static int retry_count_2 = 0;//count retry time(s) for second API
	
	/**
	 * Main function to run the program by parsing in the name of a city, create a HTTP client
	 * create an HTTP request with the name of the city, send request to a RESTful API, and evaluate
	 * the response to determine if exponential back-off retry logic is needed.
	 * @param args: String - the name of a city
	 */
	public static void main(String[] args) {
		//invalid number of argument(s)
		if(args.length < 1) {
			System.out.println("Please enter a city");
			return;
		}
		
		//parse cities with more than 1 word
		for(String i : args) {
			city += i +" ";
		}
		city = city.stripTrailing();//get rid of last white space
		city = city.replaceAll("\\s", "%20");//replace all white spaces with valid http url format
	
		
		//create http client
		HttpClient client = (HttpClient) HttpClient.newBuilder()
				.version(HttpClient.Version.HTTP_1_1)
				.connectTimeout(Duration.ofSeconds(5))
				.build();
		HttpRequest request;//http request to be used by client
		
		while(true) {//loop for re-trying logic
			if(retry_first_api) {
				long wait_time = (long) (Math.pow(2, retry_count_1) * 1000);
				if(wait_time >= retry_threshold) {
					retry_first_api = false;//retry threshold hit, no need to retry this API anymore
					System.out.println("First API re-try threshold reached, terminating retrying");
				}
				else {
					try {
						TimeUnit.MILLISECONDS.sleep(wait_time);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					//create first http request for the first API
					request = (HttpRequest) HttpRequest.newBuilder()
							.uri(URI.create("https://api.openweathermap.org/data/2.5/weather?q="+city+"&appid=[yourkey]"))
							.timeout(Duration.ofSeconds(8))
							.header("accept", "application/json")
							.build();
					//use client to send request to the first API
				    client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
				    .thenApply(response -> { 
				    	System.out.print(reTry(response.statusCode(), 1));
			        	return response;})
				    .thenApply(HttpResponse::body)
				    .thenApply(program2::parseWeatherJSON)
				    .join();
				}
			}
			
			if(retry_second_api) {
				long wait_time = (long) (Math.pow(2, retry_count_2) * 1000);
				if(wait_time >= retry_threshold) {
					retry_second_api = false;//retry threshold hit, no need to retry this API anymore
					System.out.println("Second API re-try threshold reached, terminating retrying");
				}
				else {
					try {
						TimeUnit.MILLISECONDS.sleep(wait_time);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					//create second http request for the second API
					request = (HttpRequest) HttpRequest.newBuilder()
							.uri(URI.create("https://api.waqi.info/feed/"+city+"/?token=[yourkey]"))
							.timeout(Duration.ofSeconds(8))
							.header("accept", "application/json")
							.build();
					//use client to send request to the second API
				    client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
				    .thenApply(response -> { 
				    	System.out.print(reTry(response.statusCode(), 2));
			        	return response;})
				    .thenApply(HttpResponse::body)
				    .thenApply(program2::parseCityDataJSON)
				    .join(); 
				}
			}
			if(!retry_first_api && !retry_second_api) {//if no retries, terminate loop
				break;
			}
		}
	}

	/**
	 * Function to convert ms to mph for first API call and parsing
	 * @param ms: double - speed value in meter/second
	 * @return an imperial double value mile/hour for the wind speed
	 */
	public static double convertMsToMph(double ms) {
		return ms * 2.237;
	}
	
	/**
	 * Function to convert Kelvin to Fahrenheit for first API call and parsing
	 * @param kelvin: double - temperature value in Kelvin
	 * @return a temperature double value in Fahrenheit
	 */
	public static double convertKtoF(double kelvin) {
		return (kelvin - 273.15) * 9 / 5 + 32;
	}
	
	/**
	 * Function to implement re-try logic for code 5xx (server error)
	 * @param response_code: int - response code to trigger retry logic
	 * @param api_num: int - 1 for first API, 2 for second API to trigger appropriate retry for the correct API
	 * @return fail message if any
	 */
	public static String reTry(int response_code, int api_num) {
		if(response_code <= 399) {//success code 2xx or 3xx redirect but still success
			if(api_num == 1) {
				retry_first_api = false;
			}
			if(api_num == 2) {
				retry_second_api = false;
			}
		}
		String value = "";
		if(response_code >= 500) {//code 5xx server error (needs retry)
			if(api_num == 1) {
				value = "\nFirst api call failed, re-trying...\n";
				retry_count_1++;
			}
			else {
				value = "\nSecond api call failed, re-trying...\n";
				retry_count_2++;
			}
		}
		else {//code 4xx client error (invalid city in this case, no need to retry)
			if(api_num == 1) {
				retry_first_api = false;
			}
			else {
				retry_second_api = false;
			}
		}
		return value;
	}
	
	/**
	 * Function to parse a JSON object and display weather info for a given city (if any)
	 * @param response_string: String - A JSON string to be parsed into a Java readable object
	 * @return null
	 */
	public static String parseWeatherJSON(String response_string) {
		JSONObject obj = new JSONObject(response_string);
		String cleaned_city = city.replaceAll("%20", " ");//replace all %20 with white spaces for displaying purposes

		try {				
			System.out.println("FIRST RESTFUL API: ");
			//parse useful information about the weather for a given city
			String city = obj.getString("name");
			String country = obj.getJSONObject("sys").getString("country");
			String description = obj.getJSONArray("weather").getJSONObject(0).getString("description");
			int humidity = obj.getJSONObject("main").getInt("humidity");
			double current_temp = obj.getJSONObject("main").getDouble("temp");
			double min_temp = obj.getJSONObject("main").getDouble("temp_min");
			double max_temp = obj.getJSONObject("main").getDouble("temp_max");
			double wind_speed = obj.getJSONObject("wind").getDouble("speed");
			
			//display useful weather information for a given city
	        System.out.println("City: " + city + ", Country: " + country + "\n" 
	        		+ "Description: " + description + "\n" 
	        		+ "Humidity(chance of rain): " + humidity + "%");
	        System.out.printf("Current temp: %.1fF\n", convertKtoF(current_temp));
	        System.out.printf("Min temp: %.1fF\n", convertKtoF(min_temp));
	        System.out.printf("Max temp: %.1fF\n", convertKtoF(max_temp));
	        System.out.printf("Wind speed: %.1fmph\n\n", convertMsToMph(wind_speed));
		}
		catch(JSONException e){
			System.out.println("Weather data is not available for: " + cleaned_city + "\n");
		}        
        return null;
	}
	
	/**
	 * Function to parse AQI from a given city (if any)
	 * @param response_string : String - A JSON string to be parsed into a Java readable object
	 * @return null
	 */
	public static String parseCityDataJSON(String response_string) {
		JSONObject obj = new JSONObject(response_string);
		String cleaned_city = city.replaceAll("%20", " ");//replace all %20 with white spaces for displaying purposes
		
		try {		
			System.out.println("SECOND RESTFUL API: ");
			//display useful air quality index information for a given city
			int aqi = obj.getJSONObject("data").getInt("aqi");//parse air quality index for a given city

			if(aqi >= 0 && aqi <= 50) {
				System.out.println("Air Quality Index for " + cleaned_city + " is Good: AQI = " + aqi 
						+ "\n->Air quality is considered satisfactory, and air pollution poses little or no risk.");
			}
			else if(aqi >= 51 && aqi <= 100) {
				System.out.println("Air Quality Index for " + cleaned_city + " is Moderate: AQI = " + aqi 
						+ "\n->Air quality is acceptable; however, for some pollutants,\n"
						+ "there may be a moderate health concern for a very small number of\n"
						+ "people who are unusually sensitive to air pollution.");
			}
			else if(aqi >= 101 && aqi <= 150) {
				System.out.println("Air Quality Index for " + cleaned_city + " is Unhealthy for Sensitive Groups: AQI = " + aqi 
						+ "\n->Members of sensitive groups may experience health effects.\n"
						+ "The general public is not likely to be affected.");
			}
			else if(aqi >= 151 && aqi <= 200) {
				System.out.println("Air Quality Index for " + cleaned_city + " is Unhealthy: AQI = " + aqi 
						+ "\n->Everyone may begin to experience health effects;\n"
						+ "members of sensitive groups may experience more serious health effects.");
			}
			else if(aqi >= 201 && aqi <= 300) {
				System.out.println("Air Quality Index for " + cleaned_city + " is Very Unhealthy: AQI = " + aqi 
						+ "\n->Health warnings of emergency conditions.\n"
						+ "The entire population is more likely to be affected.");
			}
			else if(aqi > 300) {
				System.out.println("Air Quality Index for " + cleaned_city + " is Hazardous: AQI = " + aqi 
						+ "\n->Health alert: everyone may experience more serious health effects.");
			}
			else {
				System.out.println("Air Quality Index data is unavailable for the specified city.");
			}
		}
		catch (JSONException e){
			System.out.println("Air Quality Index data is not available for: " + cleaned_city + "\n");
		}
		return null;
	}
}
