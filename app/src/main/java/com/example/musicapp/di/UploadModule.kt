package com.example.musicapp.di

import com.example.musicapp.feature.upload.AudioTagReader
import com.example.musicapp.feature.upload.ContentResolverSelectedFileResolver
import com.example.musicapp.feature.upload.MediaAudioTagReader
import com.example.musicapp.feature.upload.SelectedFileResolver
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class UploadModule {

    @Binds
    abstract fun bindAudioTagReader(impl: MediaAudioTagReader): AudioTagReader

    @Binds
    abstract fun bindSelectedFileResolver(impl: ContentResolverSelectedFileResolver): SelectedFileResolver
}
