package co.tiagoaguiar.netflixremake.util

import android.os.Handler
import android.os.Looper
import android.util.Log
import co.tiagoaguiar.netflixremake.model.Category
import co.tiagoaguiar.netflixremake.model.Movie
import co.tiagoaguiar.netflixremake.model.MovieDetail
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import javax.net.ssl.HttpsURLConnection

class MovieTask(private val callback: Callback) {

    private val handler = Handler(Looper.getMainLooper())

    interface Callback {
        fun onPreExecute()
        fun onResult(movieDetail: MovieDetail)
        fun onFailure(message: String)
    }

    fun execute(url: String) {
        callback.onPreExecute()
        //UI thread (1)
        val executor = Executors.newSingleThreadExecutor()

        executor.execute {
            // nova thread (2)
            var urlConnection: HttpsURLConnection? = null
            var stream: InputStream? = null
            try {
                val requestUrl = URL(url) // abrir a url
                urlConnection = requestUrl.openConnection() as HttpsURLConnection // abrir a conexão
                urlConnection.readTimeout = 2000 // tempo de leitura (2sec)
                urlConnection.connectTimeout = 2000 // tempo de conhexão (2sec)

                val statusCode: Int = urlConnection.responseCode
                if (statusCode == 400){
                    stream = urlConnection.errorStream
                    val jsonAsString = stream.bufferedReader().use { it.readText() }

                    val json = JSONObject(jsonAsString)
                    val message = json.getString("message")
                    throw IOException(message)
                } else if (statusCode > 400) {
                    throw IOException("Erro na comunicação com o servidor!")
                }

                stream = urlConnection.inputStream // sequencia de bytes
                val jsonAsString = stream.bufferedReader().use { it.readText() } // bytes -> string
                val movieDetail = toMovieDetail(jsonAsString)

                handler.post {
                    callback.onResult(movieDetail)
                }

            } catch (e: IOException) {
                val message = e.message ?: "Erro desconhecido"
                handler.post {
                    callback.onFailure(message)
                }
            } finally {
                urlConnection?.disconnect()
                stream?.close()
            }
        }
    }

    private fun toMovieDetail(jsonAsString: String): MovieDetail{
        val json = JSONObject(jsonAsString)

        val id = json.getInt("id")
        val title = json.getString("title")
        val desc = json.getString("desc")
        val cast = json.getString("cast")
        val coverUrl = json.getString("cover_url")

        val jsonMovies = json.getJSONArray("movie")

        val similars = mutableListOf<Movie>()
        for (i in 0 until jsonMovies.length()){
            val jsonMovie = jsonMovies.getJSONObject(i)

            val similarId = jsonMovie.getInt("id")
            val similarCoverUrl = jsonMovie.getString("cover_url")

            val m = Movie(similarId, similarCoverUrl)
            similars.add(m)
        }
        val movie = Movie(id, coverUrl, title, desc, cast)
        return MovieDetail(movie, similars)
    }

}