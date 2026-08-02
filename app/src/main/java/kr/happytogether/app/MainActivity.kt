package kr.happytogether.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.webkit.*
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewFeature
import androidx.webkit.WebSettingsCompat

/**
 * 행복하자 우리 (Happy Together) — WebView 셸
 *
 * 웹 자산은 APK 안에 번들되어 `https://appassets.androidplatform.net/` 로 서빙됩니다.
 * https 출처라서 localStorage · 카메라 · 마이크가 웹과 동일하게 동작하고, 오프라인에서도 열립니다.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var refresh: SwipeRefreshLayout
    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var pendingPermissionRequest: PermissionRequest? = null

    /** 파일 선택 (프로필 사진 · 증빙 첨부) */
    private val fileChooser = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uris = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
        filePathCallback?.onReceiveValue(uris)
        filePathCallback = null
    }

    /** 마이크 권한 (보이스룸) */
    private val micPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val req = pendingPermissionRequest
        pendingPermissionRequest = null
        if (granted && req != null) req.grant(req.resources) else req?.deny()
        if (!granted) toast("마이크 권한이 없어 보이스룸에서 발언할 수 없습니다.")
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        // 스플래시 테마 → 본 테마로 전환
        setTheme(R.style.Theme_HappyTogether)
        super.onCreate(savedInstanceState)

        // edge-to-edge: 웹의 env(safe-area-inset-*) 가 동작하도록
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webview)
        refresh = findViewById(R.id.refresh)

        setupWebView()
        setupBackPress()

        if (savedInstanceState == null) {
            webView.loadUrl(START_URL)
        } else {
            webView.restoreState(savedInstanceState)
        }
    }

    private fun setupWebView() {
        val assetLoader = WebViewAssetLoader.Builder()
            .setDomain(ASSET_DOMAIN)
            .addPathHandler("/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true          // localStorage (세션 유지)
            databaseEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true            // <meta viewport> 존중
            builtInZoomControls = false
            displayZoomControls = false
            setSupportZoom(false)
            mediaPlaybackRequiresUserGesture = false
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            textZoom = 100                    // 시스템 글꼴 크기에 레이아웃이 깨지지 않도록 고정
        }

        // 시스템 다크모드에 맞춘 강제 반전은 하지 않음 (브랜드 색 유지)
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(webView.settings, false)
        }

        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        webView.setBackgroundColor(ContextCompat.getColor(this, R.color.brand))
        webView.overScrollMode = View.OVER_SCROLL_NEVER

        webView.webViewClient = object : WebViewClient() {

            override fun shouldInterceptRequest(
                view: WebView, request: WebResourceRequest
            ): WebResourceResponse? = assetLoader.shouldInterceptRequest(request.url)

            override fun shouldOverrideUrlLoading(
                view: WebView, request: WebResourceRequest
            ): Boolean {
                val url = request.url
                // 앱 내부(번들 자산)는 WebView 가 그대로 처리
                if (url.host == ASSET_DOMAIN) return false
                // 그 외 외부 링크(카카오T · 지도 · 결제 등)는 시스템에 위임
                return openExternally(url)
            }

            override fun onPageFinished(view: WebView, url: String) {
                refresh.isRefreshing = false
            }

            override fun onReceivedError(
                view: WebView, request: WebResourceRequest, error: WebResourceError
            ) {
                if (request.isForMainFrame) {
                    refresh.isRefreshing = false
                    toast("페이지를 불러오지 못했습니다. 아래로 당겨 새로고침해 주세요.")
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {

            /** 보이스룸 마이크 권한 */
            override fun onPermissionRequest(request: PermissionRequest) {
                runOnUiThread {
                    if (request.resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)) {
                        val granted = ContextCompat.checkSelfPermission(
                            this@MainActivity, Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED
                        if (granted) {
                            request.grant(request.resources)
                        } else {
                            pendingPermissionRequest = request
                            micPermission.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    } else {
                        request.deny()
                    }
                }
            }

            /** 프로필 사진 · 증빙 파일 선택 */
            override fun onShowFileChooser(
                view: WebView,
                callback: ValueCallback<Array<Uri>>,
                params: FileChooserParams
            ): Boolean {
                filePathCallback?.onReceiveValue(null)
                filePathCallback = callback
                return try {
                    fileChooser.launch(params.createIntent())
                    true
                } catch (e: ActivityNotFoundException) {
                    filePathCallback = null
                    toast("파일을 선택할 수 있는 앱이 없습니다.")
                    false
                }
            }
        }

        refresh.setColorSchemeResources(R.color.brand)
        refresh.setOnRefreshListener { webView.reload() }
        // 최상단일 때만 당겨서 새로고침 (채팅·리스트 스크롤과 충돌 방지)
        refresh.setOnChildScrollUpCallback { _, _ -> webView.scrollY > 0 }
    }

    /** 외부 스킴/도메인은 브라우저·해당 앱으로 */
    private fun openExternally(url: Uri): Boolean = try {
        startActivity(Intent(Intent.ACTION_VIEW, url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        toast("연결할 수 있는 앱이 없습니다.")
        true
    }

    /** 하드웨어 뒤로가기 → 웹 히스토리(해시 라우터) 뒤로 */
    private fun setupBackPress() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            private var lastBackAt = 0L
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                    return
                }
                val now = System.currentTimeMillis()
                if (now - lastBackAt < 2000) {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                } else {
                    lastBackAt = now
                    toast("한 번 더 누르면 종료됩니다")
                }
            }
        })
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onPause() {
        super.onPause()
        webView.onPause()
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    companion object {
        private const val ASSET_DOMAIN = "appassets.androidplatform.net"

        /**
         * 번들된 웹앱을 띄웁니다. (오프라인 동작 · 즉시 로딩)
         * 서버의 최신본을 바로 반영하고 싶다면 아래 REMOTE_URL 로 바꾸세요.
         */
        private const val START_URL = "https://$ASSET_DOMAIN/assets/web/index.html"

        @Suppress("unused")
        const val REMOTE_URL = "https://park-jongchul.github.io/happyTogether/"
    }
}
