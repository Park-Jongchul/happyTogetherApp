package kr.happytogether.app

import android.app.Application
import android.os.Build
import android.webkit.WebView

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        // 멀티프로세스 환경에서 WebView 데이터 디렉터리 충돌 방지
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val process = getProcessName()
            if (packageName != process) WebView.setDataDirectorySuffix(process)
        }
    }
}
