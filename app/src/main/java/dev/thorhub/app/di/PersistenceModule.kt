package dev.thorhub.app.di

import android.content.Context
import androidx.room.Room
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.thorhub.app.platform.session.RoomSessionTracker
import dev.thorhub.core.database.MIGRATION_1_2
import dev.thorhub.core.database.MIGRATION_2_3
import dev.thorhub.core.database.MIGRATION_3_4
import dev.thorhub.core.database.MIGRATION_4_5
import dev.thorhub.core.database.ThorHubDatabase
import dev.thorhub.core.model.SessionTracker
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PersistenceProviderModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ThorHubDatabase =
        Room.databaseBuilder(context, ThorHubDatabase::class.java, "thor-game-hub.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
            .build()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class PersistenceBindingModule {
    @Binds
    @Singleton
    abstract fun bindSessionTracker(implementation: RoomSessionTracker): SessionTracker
}
