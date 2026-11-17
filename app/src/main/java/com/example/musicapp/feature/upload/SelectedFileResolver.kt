package com.example.musicapp.feature.upload

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 선택한 URI의 표시 이름·크기·MIME을 조회한다. ViewModel이 `Context`를 갖지 않도록 분리한 경계. */
interface SelectedFileResolver {
    suspend fun resolve(uri: Uri): SelectedFile
}

class ContentResolverSelectedFileResolver @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : SelectedFileResolver {
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO

    override suspend fun resolve(uri: Uri): SelectedFile = withContext(ioDispatcher) {
        context.contentResolver.querySelectedFile(uri)
    }
}
