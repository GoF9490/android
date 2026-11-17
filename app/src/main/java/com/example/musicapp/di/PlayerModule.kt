package com.example.musicapp.di

import com.example.musicapp.player.MediaControllerPlayerRepository
import com.example.musicapp.player.PlayerRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PlayerModule {

    @Binds
    @Singleton
    abstract fun bindPlayerRepository(impl: MediaControllerPlayerRepository): PlayerRepository
}
