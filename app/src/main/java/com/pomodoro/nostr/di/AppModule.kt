package com.pomodoro.nostr.di

import android.content.Context
import com.pomodoro.nostr.nostr.BlossomClient
import com.pomodoro.nostr.nostr.ContactsManager
import com.pomodoro.nostr.nostr.KeyManager
import com.pomodoro.nostr.nostr.MetadataCache
import com.pomodoro.nostr.nostr.NostrClient
import com.pomodoro.nostr.nostr.RankingService
import com.pomodoro.nostr.nostr.SearchService
import com.pomodoro.nostr.nostr.SessionPublisher
import com.pomodoro.nostr.timer.SessionHistory
import com.pomodoro.nostr.timer.TimerPreferences
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideKeyManager(
        @ApplicationContext context: Context
    ): KeyManager {
        return KeyManager(context)
    }

    @Provides
    @Singleton
    fun provideNostrClient(): NostrClient {
        return NostrClient()
    }

    @Provides
    @Singleton
    fun provideTimerPreferences(
        @ApplicationContext context: Context
    ): TimerPreferences {
        return TimerPreferences(context)
    }

    @Provides
    @Singleton
    fun provideSessionHistory(
        @ApplicationContext context: Context
    ): SessionHistory {
        return SessionHistory(context)
    }

    @Provides
    @Singleton
    fun provideMetadataCache(): MetadataCache {
        return MetadataCache()
    }

    @Provides
    @Singleton
    fun provideSearchService(
        metadataCache: MetadataCache
    ): SearchService {
        return SearchService(metadataCache)
    }

    @Provides
    @Singleton
    fun provideContactsManager(
        @ApplicationContext context: Context
    ): ContactsManager {
        return ContactsManager(context)
    }

    @Provides
    @Singleton
    fun provideSessionPublisher(
        keyManager: KeyManager,
        nostrClient: NostrClient
    ): SessionPublisher {
        return SessionPublisher(keyManager, nostrClient)
    }

    @Provides
    @Singleton
    fun provideRankingService(): RankingService {
        return RankingService()
    }

    @Provides
    @Singleton
    fun provideBlossomClient(
        keyManager: KeyManager
    ): BlossomClient {
        return BlossomClient(keyManager)
    }
}
