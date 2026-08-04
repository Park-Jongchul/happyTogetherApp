package kr.happytogether.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
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
        setupKeyboardInsets()
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
                // 우리 웹앱(GitHub Pages)도 WebView 안에서 처리
                if (url.host == REMOTE_HOST && (url.path ?: "").startsWith(REMOTE_PATH)) return false
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

    /**
     * 소프트 키보드가 떠도 WebView 아래쪽이 가려지지 않게 합니다.
     *
     * edge-to-edge(`setDecorFitsSystemWindows(false)`)에서는 매니페스트의
     * `adjustResize` 가 동작하지 않아 창이 줄어들지 않습니다. 그러면 가입 화면처럼
     * 입력칸이 많은 페이지에서 하단 버튼이 키보드 뒤에 깔리고 스크롤도 되지 않습니다.
     * 키보드 높이만큼 컨테이너에 아래 여백을 주어 WebView 뷰포트를 직접 줄입니다.
     */
    private fun setupKeyboardInsets() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            ViewCompat.setOnApplyWindowInsetsListener(refresh) { v, insets ->
                val ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
                val nav = insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom
                // 내비게이션 바 영역은 웹의 env(safe-area-inset-bottom) 이 이미 처리합니다.
                v.updatePadding(bottom = (ime - nav).coerceAtLeast(0))
                insets
            }
            return
        }

        // API 24~29: ime() 인셋을 받을 수 없어 보이는 창 높이로 키보드를 추정합니다.
        val root = window.decorView
        val visible = Rect()
        root.viewTreeObserver.addOnGlobalLayoutListener {
            root.getWindowVisibleDisplayFrame(visible)
            val hidden = root.height - visible.bottom
            // 내비게이션 바 정도의 작은 차이는 키보드로 보지 않습니다.
            val pad = if (hidden > root.height / 5) {
                val nav = ViewCompat.getRootWindowInsets(root)
                    ?.getInsets(WindowInsetsCompat.Type.navigationBars())?.bottom ?: 0
                (hidden - nav).coerceAtLeast(0)
            } else 0
            if (refresh.paddingBottom != pad) refresh.updatePadding(bottom = pad)
        }
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
        private const val REMOTE_HOST  = "park-jongchul.github.io"
        private const val REMOTE_PATH  = "/happyTogether"

        /**
         * GitHub Pages 최신본을 띄웁니다.
         * 웹만 배포해도 앱 업데이트 없이 바로 반영되지만, 오프라인에서는 열리지 않습니다.
         */
        private const val START_URL = "https://$REMOTE_HOST$REMOTE_PATH/"

        /**
         * 오프라인 동작이 필요하면 START_URL 대신 이 주소를 쓰세요.
         *
         * 경로 앞에 assets 를 또 붙이면 안 됩니다. AssetsPathHandler 가 "/" 에 등록돼 있어서
         * /web/index.html 이 곧 app/src/main/assets/web/index.html 입니다.
         */
        @Suppress("unused")
        const val BUNDLED_URL = "https://$ASSET_DOMAIN/web/index.html"
    }
}
