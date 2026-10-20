package sm.mar.diario

import android.content.Context

object BuildConfigInfo {
    fun version(ctx: Context): String = runCatching {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"
    }.getOrDefault("?")
}
