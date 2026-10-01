package com.demo.weatherapp.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.demo.weatherapp.BuildConfig
import com.demo.weatherapp.model.WeatherData
import com.demo.weatherapp.model.WeatherRepository
import kotlinx.coroutines.launch

class WeatherViewModel(private val repository: WeatherRepository) : ViewModel() {

    private val _weatherData = MutableLiveData<WeatherData?>()
    val weatherData: LiveData<WeatherData?> get() = _weatherData

    private val _errorMessage = MutableLiveData<String>()
    val errorMessage: LiveData<String> get() = _errorMessage

    // Looks up the city's coordinates first, then fetches the weather for them.
    fun fetchWeather(city: String) {
        if (!hasApiKey()) return

        viewModelScope.launch {
            try {
                val geoLocation = repository.getGeoLocationForCity(city)
                if (geoLocation == null) {
                    _errorMessage.value = "Couldn't find a location for \"$city\""
                    return@launch
                }
                publishWeather(repository.getWeatherByCoordinates(geoLocation.lat, geoLocation.lon))
            } catch (e: Exception) {
                Log.e("WeatherViewModel", "Error fetching weather for city: $city. Error: ${e.message}", e)
                _errorMessage.value = e.message ?: "Something went wrong fetching the weather"
            }
        }
    }

    // Used when the weather is requested for the device's current location.
    fun fetchWeatherDevice(lat: Double, lon: Double) {
        if (!hasApiKey()) return

        viewModelScope.launch {
            try {
                publishWeather(repository.getWeatherByCoordinates(lat, lon))
            } catch (e: Exception) {
                Log.e("WeatherViewModel", "Error fetching weather for coordinates: Lat: $lat, Lon: $lon. Error: ${e.message}", e)
                _errorMessage.value = e.message ?: "Something went wrong fetching the weather"
            }
        }
    }

    private fun publishWeather(data: WeatherData?) {
        if (data != null) {
            _weatherData.value = data
        } else {
            _errorMessage.value = "Couldn't load the weather. Check your connection and try again."
        }
    }

    private fun hasApiKey(): Boolean {
        if (BuildConfig.OPENWEATHER_API_KEY.isBlank()) {
            _errorMessage.value = "Missing OpenWeather API key. Add OPENWEATHER_API_KEY to local.properties."
            return false
        }
        return true
    }
}
