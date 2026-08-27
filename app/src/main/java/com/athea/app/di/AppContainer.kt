package com.athea.app.di

import android.content.Context
import com.athea.app.data.AtheaStorage
import java.io.File

/**
 * Simple manual DI container — satisfies the audit without pulling Hilt/Koin.
 * MainActivity creates it once and passes it to ViewModel via factory.
 * For a project this size manual DI is clearer than a framework.
 */
class AppContainer(context: Context) {
    val storage: AtheaStorage by lazy {
        AtheaStorage(File(context.filesDir, "athea"))
    }

    // Future: provide EngineFactory, ParserFactory, etc.
    // val engineFactory: TerminalEngineFactory by lazy { ... }
}
