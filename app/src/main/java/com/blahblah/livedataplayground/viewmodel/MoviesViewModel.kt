package com.blahblah.livedataplayground.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.room.Room
import com.blahblah.livedataplayground.model.MoviesDatabase
import com.blahblah.livedataplayground.model.OneMovieEntity
import com.blahblah.livedataplayground.model.TMDBApi
import com.blahblah.livedataplayground.utils.CoroutineWrapper
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine


/**
 * Description:
 * Created by shmuel on 27.2.19.
 */
class MoviesViewModel private constructor(application: Application) : AndroidViewModel(application) {
    private val coroutine = CoroutineWrapper(Executors.newSingleThreadExecutor().asCoroutineDispatcher())
    private val database: MoviesDatabase =
        Room.databaseBuilder(application, MoviesDatabase::class.java, "movies_property_db").build()
    private val tmdbDriver = TMDBApi(application)

    data class DataChunk(val firstPosition: Int, val data: List<OneMovieEntity>)


    val moviesListPage: MutableLiveData<DataChunk>

    init {
        val initialData = database.moviesListDao().getAllMovies(0, 10).value ?: listOf()
        moviesListPage = MutableLiveData(DataChunk(0, initialData))
        fetch()
    }

    var currentRequest: IntRange? = null
    fun fetch(positionRange: IntRange = 0 until PAGE_SIZE) {
        coroutine.launchUI {
            if (currentRequest?.let { positionRange.first in it && positionRange.last in it } == true) {
                return@launchUI
            }
            currentRequest = positionRange
            val dataFromDb = suspendCoroutine<List<OneMovieEntity>?> { continuation ->
                // Fetch a little bit before, and
                val allMovies = database.moviesListDao().getAllMovies(positionRange.first, PAGE_SIZE)
                allMovies.observeForever { t -> continuation.resume(t) }
            }

            val data = if (dataFromDb?.size ?: 0 < PAGE_SIZE) {
                val backendPage = dataFromDb?.lastOrNull()?.cameFromPage ?: 0
                coroutine.withContext {
                    val data = tmdbDriver.getList(backendPage + 1)
                    data.forEach { item -> database.moviesListDao().insert(item) }
                    data
                }
            } else {
                dataFromDb
            } ?: listOf()
            moviesListPage.postValue(DataChunk(positionRange.first, data))
            currentRequest = null
        }
    }

    companion object {
        fun init(application: Application): MoviesViewModel {
            return MoviesViewModel(application).also { instance = it }
        }

        var instance: MoviesViewModel? = null
        private const val PAGE_SIZE = 20
    }
}