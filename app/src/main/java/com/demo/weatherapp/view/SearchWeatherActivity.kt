package com.demo.weatherapp.view

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Looper
import android.text.Editable
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.lifecycle.ViewModelProvider
import com.bumptech.glide.Glide
import com.demo.weatherapp.R
import com.demo.weatherapp.model.WeatherApi
import com.demo.weatherapp.model.WeatherRepository
import com.demo.weatherapp.model.WeatherViewModelFactory
import com.demo.weatherapp.viewmodel.WeatherViewModel
import com.google.android.gms.location.*
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class SearchWeatherActivity : AppCompatActivity() {
    private lateinit var viewModel: WeatherViewModel

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var cityEditText: EditText
    private lateinit var searchButton: Button
    private lateinit var weatherDetailsLayout: LinearLayout
    private lateinit var currentTempTextView: TextView
    private lateinit var descriptionView: LinearLayout
    private lateinit var highTempTextView: TextView
    private lateinit var lowTempTextView: TextView
    private lateinit var descriptionTextView: TextView
    private lateinit var displaySearchBox: TextView
    private lateinit var weatherIconImageView: ImageView

    // Single place where the last searched city is stored and read.
    private val sharedPref by lazy {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_search_weather)

        cityEditText = findViewById(R.id.cityEditText)
        searchButton = findViewById(R.id.searchButton)
        weatherDetailsLayout = findViewById(R.id.weatherDetailsLayout)
        currentTempTextView = findViewById(R.id.currentTempTextView)
        highTempTextView = findViewById(R.id.highTempTextView)
        lowTempTextView = findViewById(R.id.lowTempTextView)
        descriptionView = findViewById(R.id.desc)
        descriptionTextView = findViewById(R.id.descriptionTextView)
        displaySearchBox = findViewById(R.id.displaySearchBox)
        weatherIconImageView = findViewById(R.id.weatherIconImageView)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val savedCity = getLastSearchedCity()
        cityEditText.text = Editable.Factory.getInstance().newEditable(savedCity)

        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.openweathermap.org/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val weatherApiService = retrofit.create(WeatherApi::class.java)
        val repository = WeatherRepository(weatherApiService)
        val factory = WeatherViewModelFactory(repository)
        viewModel = ViewModelProvider(this, factory)[WeatherViewModel::class.java]

        viewModel.weatherData.observe(this) { data ->
            if (data == null) return@observe

            // The API returns Kelvin by default.
            val tempInFahrenheit = (data.main.temp - 273.15) * 9 / 5 + 32
            val tempMaxInFahrenheit = (data.main.temp_max - 273.15) * 9 / 5 + 32
            val tempMinInFahrenheit = (data.main.temp_min - 273.15) * 9 / 5 + 32

            currentTempTextView.text = String.format("%.1f°F", tempInFahrenheit)
            highTempTextView.text = String.format("%.1f°F", tempMaxInFahrenheit)
            lowTempTextView.text = String.format("%.1f°F", tempMinInFahrenheit)
            weatherDetailsLayout.visibility = View.VISIBLE

            val condition = data.weather.firstOrNull()
            if (condition != null) {
                descriptionTextView.text = condition.description
                descriptionView.visibility = View.VISIBLE

                Glide.with(this)
                    .load("https://openweathermap.org/img/wn/${condition.icon}@2x.png")
                    .into(weatherIconImageView)
                weatherIconImageView.visibility = View.VISIBLE
            } else {
                descriptionView.visibility = View.GONE
                weatherIconImageView.visibility = View.GONE
            }
        }

        viewModel.errorMessage.observe(this) { error ->
            Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
        }

        searchButton.setOnClickListener {
            val city = cityEditText.text.toString().trim()
            if (city.isNotEmpty()) {
                displaySearchBox.text = city
                displaySearchBox.visibility = View.VISIBLE
                saveLastSearchedCity(city)
                viewModel.fetchWeather(city)
            }
        }

        if (savedCity.isNotEmpty()) {
            displaySearchBox.text = savedCity
            displaySearchBox.visibility = View.VISIBLE
            viewModel.fetchWeather(savedCity)
        } else {
            fetchWeatherUsingDeviceLocation()
        }
    }

    override fun onPause() {
        super.onPause()
        val city = cityEditText.text.toString().trim()
        if (city.isNotEmpty()) {
            saveLastSearchedCity(city)
        }
    }

    private fun getLastSearchedCity(): String {
        return sharedPref.getString(KEY_LAST_CITY, "") ?: ""
    }

    private fun saveLastSearchedCity(city: String) {
        sharedPref.edit().putString(KEY_LAST_CITY, city).apply()
    }

    // Either precise or approximate location is enough for a weather lookup.
    private fun hasLocationPermission(): Boolean {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    private fun fetchWeatherUsingDeviceLocation() {
        if (!hasLocationPermission()) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ),
                LOCATION_PERMISSION_REQUEST
            )
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            location?.let {
                viewModel.fetchWeatherDevice(it.latitude, it.longitude)
            } ?: run {
                Log.d("LOCATION_DEBUG", "Location is null, requesting new location data.")
                requestNewLocationData()
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            // Granting either precise or approximate location is fine.
            if (grantResults.any { it == PackageManager.PERMISSION_GRANTED }) {
                fetchWeatherUsingDeviceLocation()
            } else {
                Toast.makeText(this, "Location permission denied. Search for a city instead.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun requestNewLocationData() {
        if (!hasLocationPermission()) return

        val locationRequest = LocationRequest().apply {
            priority = LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY
            interval = 0
            fastestInterval = 0
            numUpdates = 1
        }

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult?) {
                    val lastLocation = locationResult?.lastLocation ?: return
                    viewModel.fetchWeatherDevice(lastLocation.latitude, lastLocation.longitude)
                }
            },
            Looper.getMainLooper()
        )
    }

    companion object {
        private const val PREFS_NAME = "weather_app_prefs"
        private const val KEY_LAST_CITY = "LAST_SEARCHED_CITY"
        private const val LOCATION_PERMISSION_REQUEST = 1
    }
}
