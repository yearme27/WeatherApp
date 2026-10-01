# WeatherApp

A small Android app (Kotlin, MVVM) that shows the current weather for a searched city, or for the device's location on first launch. Data comes from the [OpenWeatherMap](https://openweathermap.org/api) API.

## Setup

1. Get a free API key from OpenWeatherMap.
2. Add it to `local.properties` in the project root (this file is git-ignored, so the key stays out of the repo):

   ```
   OPENWEATHER_API_KEY=your_key_here
   ```

   You can also set an `OPENWEATHER_API_KEY` environment variable instead.
3. Open the project in Android Studio, sync Gradle, and run it.

If the key is missing the app shows a message saying so instead of failing silently.

## How it works

- `SearchWeatherActivity` is the single screen and the launcher activity.
- `WeatherViewModel` looks up a city's coordinates, then fetches the weather and exposes it as LiveData, plus an error message LiveData.
- `WeatherRepository` wraps the two Retrofit calls in `WeatherApi`.
- The last searched city is saved in SharedPreferences and loaded on the next launch. With no saved city, the app asks for location permission (precise or approximate both work) and uses the device location.
