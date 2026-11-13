package top.iwesley.lyn.music.platform

import android.content.Context
import androidx.room.Room
import top.iwesley.lyn.music.data.db.LynMusicDatabase
import top.iwesley.lyn.music.data.db.openLynMusicDatabase

internal class AndroidRuntimeDatabaseProvider(
    private val openDatabase: (Context) -> LynMusicDatabase = ::createAndroidRuntimeDatabase,
) {
    private val lock = Any()
    private var database: LynMusicDatabase? = null

    fun getOrCreate(context: Context): LynMusicDatabase = synchronized(lock) {
        database ?: openDatabase(context.applicationContext).also { database = it }
    }
}

private fun createAndroidRuntimeDatabase(appContext: Context): LynMusicDatabase {
    return openLynMusicDatabase(
        Room.databaseBuilder<LynMusicDatabase>(
            context = appContext,
            name = appContext.getDatabasePath("lynmusic.db").absolutePath,
        ),
    ).getOrThrow()
}
