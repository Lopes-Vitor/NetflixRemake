package co.tiagoaguiar.netflixremake.util

import android.os.Handler
import android.os.Looper
import android.util.Log
import co.tiagoaguiar.netflixremake.model.Category
import co.tiagoaguiar.netflixremake.model.Movie
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import javax.net.ssl.HttpsURLConnection

    // CategoryTask serve para fazer a requisição HTTP das categorias e filmes
    // fora da UI thread para que a tela n fique travada para o usuario

class CategoryTask(private val callback: Callback) {

    private val handler = Handler(Looper.getMainLooper())

    interface Callback {
        fun onPreExecute()
        fun onResult(categories: List<Category>)
        fun onFailure(message: String)
    }

    fun execute(url: String) {
        callback.onPreExecute()
        val executor = Executors.newSingleThreadExecutor()

        executor.execute {
            var urlConnection: HttpsURLConnection? = null
            var stream: InputStream? = null
            try {
                val requestUrl = URL(url) // abrir a url
                urlConnection = requestUrl.openConnection() as HttpsURLConnection // abrir a conexão
                urlConnection.readTimeout = 2000 // tempo de leitura (2sec)
                urlConnection.connectTimeout = 2000 // tempo de conhexão (2sec)

                val statusCode: Int = urlConnection.responseCode
                if (statusCode > 400) {
                    throw IOException()
                }

                stream = urlConnection.inputStream // sequencia de bytes
                val jsonAsString = stream.bufferedReader().use { it.readText() } // bytes -> string
                val categories = toCategories(jsonAsString)

                // Aqui roda dentro da UI thread novamente
                handler.post {
                    callback.onResult(categories)
                }

            } catch (e: IOException) {
                val message = e.message ?: "Erro desconhecido"

                // Aqui roda dentro da UI thread novamente
                handler.post {
                    callback.onFailure(message)
                }
            } finally {
                urlConnection?.disconnect()
                stream?.close()
            }
        }
    }

    private fun toCategories(jsonAsString: String): List<Category> {
        val categories = mutableListOf<Category>()

        val jsonRoot = JSONObject(jsonAsString)
        val jsonCategories = jsonRoot.getJSONArray("category")
        for (i in 0 until jsonCategories.length()) {
            val jsonCategory = jsonCategories.getJSONObject(i)

            val title = jsonCategory.getString("title")
            val jsonMovies = jsonCategory.getJSONArray("movie")

            val movies = mutableListOf<Movie>()
            for (j in 0 until jsonMovies.length()) {
                val jsonMovie = jsonMovies.getJSONObject(j)
                val id = jsonMovie.getInt("id")
                val coverUrl = jsonMovie.getString("cover_url")

                movies.add(Movie(id, coverUrl))
            }
            categories.add(Category(title, movies))
        }


        return categories
    }

}