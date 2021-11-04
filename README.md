# restful-api-caller

## Description:
The program takes in the name of a city and provides information about the weather (chance of rain, humidity, current, min, max temp, etc.) and the air quality index (AQI). 
The program consumes 2 RESTful APIs:
openweathermap.org https://openweathermap.org/current and 
aqicn.org https://aqicn.org/api/ to the info about the weather and AQI respectively. The program also implements an exponential back-off for retry logic.

## Design:
The program uses the libraries: HttpClient, HttpRequest, HttpResponse to create an HttpClient, send an HTTP request to one of the RESTful API and process an HttpResponse that contains a JSON object, which in turns contain information like weather and air quality index for a city. The program uses an external library from org.json (https://mvnrepository.com/artifact/org.json/json). The program also implements an exponential back-of retry logic for use whenever there is a 5xx (server error) code. Retry logic maximum wait-time for retry is 16 seconds (about 4 retries) before giving up. 

If request to an API is successful code, 2xx, a JSON response will be parsed into a JAVA readable object and displayed accordingly via helper methods. The first API will display weather information like current temp, min, max temp, humidity, chance of rain, etc. The second API will display air quality index (AQI) ranging from 0 to 500 with descriptions of what the AQI number value means.

## Usage:
To use the program, just download and unzip the zipped file, place all files in the same directory, use any IDE or editor to run. 
To compile and run, use the command:
java -classpath ./json-20210307.jar program2.java Chicago

and enter a valid name of a city. The program takes a city and only a city name as the only argument and call the 2 RESTful APIs. The 2 APIs will have their respective key and/or token in the code itself for remote access. The first API will return weather information for a given city. The second API will return the AQI for the given city.

*Note that not all cities have weather and/or AQI information.*

*Note that you need to sign up for an API key access.*
