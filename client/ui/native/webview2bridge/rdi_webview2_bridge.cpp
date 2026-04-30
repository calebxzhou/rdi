#include <windows.h>
#include <commctrl.h>
#include <objbase.h>
#include <Unknwn.h>

#include <atomic>
#include <cstdio>
#include <mutex>
#include <string>
#include <utility>

static bool webview2_debug_enabled() {
    static const bool enabled = [] {
        wchar_t value[16] = {};
        const DWORD size = GetEnvironmentVariableW(L"RDI_WEBVIEW2_DEBUG", value, static_cast<DWORD>(std::size(value)));
        if (size == 0 || size >= std::size(value)) {
            return false;
        }
        return
            _wcsicmp(value, L"1") == 0 ||
            _wcsicmp(value, L"true") == 0 ||
            _wcsicmp(value, L"yes") == 0 ||
            _wcsicmp(value, L"on") == 0;
    }();
    return enabled;
}

static void dbg(const char* fmt, ...) {
    if (!webview2_debug_enabled()) {
        return;
    }
    FILE* f = fopen("C:\\Users\\calebxzhou\\rdi_webview2_debug.txt", "a");
    if (!f) return;
    va_list args;
    va_start(args, fmt);
    vfprintf(f, fmt, args);
    va_end(args);
    fprintf(f, "\n");
    fflush(f);
    fclose(f);
}

namespace {

DWORD window_thread_id(HWND hwnd) {
    if (hwnd == nullptr) {
        return 0;
    }
    return GetWindowThreadProcessId(hwnd, nullptr);
}

// {4E8A3389-C9D8-4BD2-B6B5-124FEE6CC14D}
static const IID IID_ICoreWebView2CreateCoreWebView2EnvironmentCompletedHandler =
    {0x4e8a3389, 0xc9d8, 0x4bd2, {0xb6, 0xb5, 0x12, 0x4f, 0xee, 0x6c, 0xc1, 0x4d}};

// {6C4819F3-C9B7-4260-8127-C9F5BDE7F68C}
static const IID IID_ICoreWebView2CreateCoreWebView2ControllerCompletedHandler =
    {0x6c4819f3, 0xc9b7, 0x4260, {0x81, 0x27, 0xc9, 0xf5, 0xbd, 0xe7, 0xf6, 0x8c}};

// {9E8F0CF8-E670-4B5E-B2BC-73E061E3184C}
static const IID IID_ICoreWebView2_2 =
    {0x9e8f0cf8, 0xe670, 0x4b5e, {0xb2, 0xbc, 0x73, 0xe0, 0x61, 0xe3, 0x18, 0x4c}};

// {D33A35BF-1C49-4F98-93AB-006E0533FE1C}
static const IID IID_ICoreWebView2NavigationCompletedEventHandler =
    {0xd33a35bf, 0x1c49, 0x4f98, {0x93, 0xab, 0x00, 0x6e, 0x05, 0x33, 0xfe, 0x1c}};

// {5A4F5069-5C15-47C3-8646-F4DE1C116670}
static const IID IID_ICoreWebView2GetCookiesCompletedHandler =
    {0x5a4f5069, 0x5c15, 0x47c3, {0x86, 0x46, 0xf4, 0xde, 0x1c, 0x11, 0x66, 0x70}};

enum RdiWebView2State : int {
    RDI_WEBVIEW2_STATE_IDLE = 0,
    RDI_WEBVIEW2_STATE_CREATING_ENVIRONMENT = 1,
    RDI_WEBVIEW2_STATE_CREATING_CONTROLLER = 2,
    RDI_WEBVIEW2_STATE_READY = 3,
    RDI_WEBVIEW2_STATE_FAILED = 4,
};

const char* state_name(int state) {
    switch (state) {
        case RDI_WEBVIEW2_STATE_IDLE:
            return "IDLE";
        case RDI_WEBVIEW2_STATE_CREATING_ENVIRONMENT:
            return "CREATING_ENVIRONMENT";
        case RDI_WEBVIEW2_STATE_CREATING_CONTROLLER:
            return "CREATING_CONTROLLER";
        case RDI_WEBVIEW2_STATE_READY:
            return "READY";
        case RDI_WEBVIEW2_STATE_FAILED:
            return "FAILED";
        default:
            return "UNKNOWN";
    }
}

constexpr UINT WM_RDI_WEBVIEW2_TASK = WM_APP + 0x352;

enum class RdiWebView2TaskType : int {
    Attach = 1,
    SetBounds = 2,
    SetVisible = 3,
    Navigate = 4,
    NotifyParentWindowPositionChanged = 5,
    Close = 6,
};

struct RdiWebView2Task {
    RdiWebView2TaskType type;
    HRESULT hr = S_OK;
    RECT bounds{0, 0, 0, 0};
    BOOL visible = TRUE;
    HANDLE completedEvent = nullptr;
    std::wstring loaderPath;
    std::wstring userDataDir;
    std::wstring url;
};

struct EventRegistrationToken {
    INT64 value;
};

struct ICoreWebView2;
struct ICoreWebView2_2;
struct ICoreWebView2Controller;
struct ICoreWebView2Environment;
struct ICoreWebView2Cookie;
struct ICoreWebView2CookieList;
struct ICoreWebView2CookieManager;

struct ICoreWebView2CreateCoreWebView2EnvironmentCompletedHandler;
struct ICoreWebView2CreateCoreWebView2ControllerCompletedHandler;
struct ICoreWebView2NavigationCompletedEventHandler;
struct ICoreWebView2GetCookiesCompletedHandler;

struct ICoreWebView2CreateCoreWebView2EnvironmentCompletedHandlerVtbl {
    HRESULT(STDMETHODCALLTYPE* QueryInterface)(
        ICoreWebView2CreateCoreWebView2EnvironmentCompletedHandler* self,
        REFIID riid,
        void** object
    );
    ULONG(STDMETHODCALLTYPE* AddRef)(
        ICoreWebView2CreateCoreWebView2EnvironmentCompletedHandler* self
    );
    ULONG(STDMETHODCALLTYPE* Release)(
        ICoreWebView2CreateCoreWebView2EnvironmentCompletedHandler* self
    );
    HRESULT(STDMETHODCALLTYPE* Invoke)(
        ICoreWebView2CreateCoreWebView2EnvironmentCompletedHandler* self,
        HRESULT errorCode,
        ICoreWebView2Environment* createdEnvironment
    );
};

struct ICoreWebView2CreateCoreWebView2EnvironmentCompletedHandler {
    const ICoreWebView2CreateCoreWebView2EnvironmentCompletedHandlerVtbl* lpVtbl;
};

struct ICoreWebView2CreateCoreWebView2ControllerCompletedHandlerVtbl {
    HRESULT(STDMETHODCALLTYPE* QueryInterface)(
        ICoreWebView2CreateCoreWebView2ControllerCompletedHandler* self,
        REFIID riid,
        void** object
    );
    ULONG(STDMETHODCALLTYPE* AddRef)(
        ICoreWebView2CreateCoreWebView2ControllerCompletedHandler* self
    );
    ULONG(STDMETHODCALLTYPE* Release)(
        ICoreWebView2CreateCoreWebView2ControllerCompletedHandler* self
    );
    HRESULT(STDMETHODCALLTYPE* Invoke)(
        ICoreWebView2CreateCoreWebView2ControllerCompletedHandler* self,
        HRESULT errorCode,
        ICoreWebView2Controller* createdController
    );
};

struct ICoreWebView2CreateCoreWebView2ControllerCompletedHandler {
    const ICoreWebView2CreateCoreWebView2ControllerCompletedHandlerVtbl* lpVtbl;
};

struct ICoreWebView2NavigationCompletedEventHandlerVtbl {
    HRESULT(STDMETHODCALLTYPE* QueryInterface)(
        ICoreWebView2NavigationCompletedEventHandler* self,
        REFIID riid,
        void** object
    );
    ULONG(STDMETHODCALLTYPE* AddRef)(
        ICoreWebView2NavigationCompletedEventHandler* self
    );
    ULONG(STDMETHODCALLTYPE* Release)(
        ICoreWebView2NavigationCompletedEventHandler* self
    );
    HRESULT(STDMETHODCALLTYPE* Invoke)(
        ICoreWebView2NavigationCompletedEventHandler* self,
        ICoreWebView2* sender,
        IUnknown* args
    );
};

struct ICoreWebView2NavigationCompletedEventHandler {
    const ICoreWebView2NavigationCompletedEventHandlerVtbl* lpVtbl;
};

struct ICoreWebView2GetCookiesCompletedHandlerVtbl {
    HRESULT(STDMETHODCALLTYPE* QueryInterface)(
        ICoreWebView2GetCookiesCompletedHandler* self,
        REFIID riid,
        void** object
    );
    ULONG(STDMETHODCALLTYPE* AddRef)(
        ICoreWebView2GetCookiesCompletedHandler* self
    );
    ULONG(STDMETHODCALLTYPE* Release)(
        ICoreWebView2GetCookiesCompletedHandler* self
    );
    HRESULT(STDMETHODCALLTYPE* Invoke)(
        ICoreWebView2GetCookiesCompletedHandler* self,
        HRESULT errorCode,
        ICoreWebView2CookieList* cookieList
    );
};

struct ICoreWebView2GetCookiesCompletedHandler {
    const ICoreWebView2GetCookiesCompletedHandlerVtbl* lpVtbl;
};

struct ICoreWebView2EnvironmentVtbl {
    HRESULT(STDMETHODCALLTYPE* QueryInterface)(ICoreWebView2Environment* self, REFIID riid, void** object);
    ULONG(STDMETHODCALLTYPE* AddRef)(ICoreWebView2Environment* self);
    ULONG(STDMETHODCALLTYPE* Release)(ICoreWebView2Environment* self);
    HRESULT(STDMETHODCALLTYPE* CreateCoreWebView2Controller)(ICoreWebView2Environment* self, HWND parentWindow, ICoreWebView2CreateCoreWebView2ControllerCompletedHandler* handler);
    HRESULT(STDMETHODCALLTYPE* CreateWebResourceResponse)(ICoreWebView2Environment* self, IStream* content, int statusCode, LPCWSTR reasonPhrase, LPCWSTR headers, IUnknown** response);
    HRESULT(STDMETHODCALLTYPE* get_BrowserVersionString)(ICoreWebView2Environment* self, LPWSTR* versionInfo);
    HRESULT(STDMETHODCALLTYPE* add_NewBrowserVersionAvailable)(ICoreWebView2Environment* self, IUnknown* eventHandler, EventRegistrationToken* token);
    HRESULT(STDMETHODCALLTYPE* remove_NewBrowserVersionAvailable)(ICoreWebView2Environment* self, EventRegistrationToken token);
};

struct ICoreWebView2Environment {
    const ICoreWebView2EnvironmentVtbl* lpVtbl;
};

struct ICoreWebView2ControllerVtbl {
    HRESULT(STDMETHODCALLTYPE* QueryInterface)(ICoreWebView2Controller* self, REFIID riid, void** object);
    ULONG(STDMETHODCALLTYPE* AddRef)(ICoreWebView2Controller* self);
    ULONG(STDMETHODCALLTYPE* Release)(ICoreWebView2Controller* self);
    HRESULT(STDMETHODCALLTYPE* get_IsVisible)(ICoreWebView2Controller* self, BOOL* isVisible);
    HRESULT(STDMETHODCALLTYPE* put_IsVisible)(ICoreWebView2Controller* self, BOOL isVisible);
    HRESULT(STDMETHODCALLTYPE* get_Bounds)(ICoreWebView2Controller* self, RECT* bounds);
    HRESULT(STDMETHODCALLTYPE* put_Bounds)(ICoreWebView2Controller* self, RECT bounds);
    HRESULT(STDMETHODCALLTYPE* get_ZoomFactor)(ICoreWebView2Controller* self, double* zoomFactor);
    HRESULT(STDMETHODCALLTYPE* put_ZoomFactor)(ICoreWebView2Controller* self, double zoomFactor);
    void* add_ZoomFactorChanged;
    HRESULT(STDMETHODCALLTYPE* remove_ZoomFactorChanged)(ICoreWebView2Controller* self, EventRegistrationToken token);
    HRESULT(STDMETHODCALLTYPE* SetBoundsAndZoomFactor)(ICoreWebView2Controller* self, RECT bounds, double zoomFactor);
    HRESULT(STDMETHODCALLTYPE* MoveFocus)(ICoreWebView2Controller* self, INT32 reason);
    void* add_MoveFocusRequested;
    void* remove_MoveFocusRequested;
    void* add_GotFocus;
    void* remove_GotFocus;
    void* add_LostFocus;
    void* remove_LostFocus;
    void* add_AcceleratorKeyPressed;
    void* remove_AcceleratorKeyPressed;
    HRESULT(STDMETHODCALLTYPE* get_ParentWindow)(ICoreWebView2Controller* self, HWND* parentWindow);
    HRESULT(STDMETHODCALLTYPE* put_ParentWindow)(ICoreWebView2Controller* self, HWND parentWindow);
    HRESULT(STDMETHODCALLTYPE* NotifyParentWindowPositionChanged)(ICoreWebView2Controller* self);
    HRESULT(STDMETHODCALLTYPE* Close)(ICoreWebView2Controller* self);
    HRESULT(STDMETHODCALLTYPE* get_CoreWebView2)(ICoreWebView2Controller* self, ICoreWebView2** coreWebView2);
};

struct ICoreWebView2Controller {
    const ICoreWebView2ControllerVtbl* lpVtbl;
};

struct ICoreWebView2Vtbl {
    HRESULT(STDMETHODCALLTYPE* QueryInterface)(ICoreWebView2* self, REFIID riid, void** object);
    ULONG(STDMETHODCALLTYPE* AddRef)(ICoreWebView2* self);
    ULONG(STDMETHODCALLTYPE* Release)(ICoreWebView2* self);
    HRESULT(STDMETHODCALLTYPE* get_Settings)(ICoreWebView2* self, IUnknown** settings);
    HRESULT(STDMETHODCALLTYPE* get_Source)(ICoreWebView2* self, LPWSTR* uri);
    HRESULT(STDMETHODCALLTYPE* Navigate)(ICoreWebView2* self, LPCWSTR uri);
    void* NavigateToString;
    void* add_NavigationStarting;
    void* remove_NavigationStarting;
    void* add_ContentLoading;
    void* remove_ContentLoading;
    void* add_SourceChanged;
    void* remove_SourceChanged;
    void* add_HistoryChanged;
    void* remove_HistoryChanged;
    void* add_NavigationCompleted;
    void* remove_NavigationCompleted;
    void* add_FrameNavigationStarting;
    void* remove_FrameNavigationStarting;
    void* add_FrameNavigationCompleted;
    void* remove_FrameNavigationCompleted;
    void* add_ScriptDialogOpening;
    void* remove_ScriptDialogOpening;
    void* add_PermissionRequested;
    void* remove_PermissionRequested;
    void* add_ProcessFailed;
    void* remove_ProcessFailed;
    void* AddScriptToExecuteOnDocumentCreated;
    void* remove_ScriptToExecuteOnDocumentCreated;
    void* ExecuteScript;
    void* CapturePreview;
    void* Reload;
    void* PostWebMessageAsJson;
    void* PostWebMessageAsString;
    void* add_WebMessageReceived;
    void* remove_WebMessageReceived;
    void* CallDevToolsProtocolMethod;
    void* get_BrowserProcessId;
    void* get_CanGoBack;
    void* get_CanGoForward;
    void* GoBack;
    void* GoForward;
    void* GetDevToolsProtocolEventReceiver;
    void* Stop;
    void* add_NewWindowRequested;
    void* remove_NewWindowRequested;
    void* add_DocumentTitleChanged;
    void* remove_DocumentTitleChanged;
    void* get_DocumentTitle;
    void* AddHostObjectToScript;
    void* RemoveHostObjectFromScript;
    void* OpenDevToolsWindow;
    void* add_ContainsFullScreenElementChanged;
    void* remove_ContainsFullScreenElementChanged;
    void* get_ContainsFullScreenElement;
    void* add_WebResourceRequested;
    void* remove_WebResourceRequested;
    void* AddWebResourceRequestedFilter;
    void* remove_WebResourceRequestedFilter;
    void* add_WindowCloseRequested;
    void* remove_WindowCloseRequested;
};

struct ICoreWebView2 {
    const ICoreWebView2Vtbl* lpVtbl;
};

struct ICoreWebView2_2Vtbl {
    ICoreWebView2Vtbl base;
    void* add_WebResourceResponseReceived;
    void* remove_WebResourceResponseReceived;
    HRESULT(STDMETHODCALLTYPE* NavigateWithWebResourceRequest)(ICoreWebView2_2* self, IUnknown* request);
    HRESULT(STDMETHODCALLTYPE* add_DOMContentLoaded)(ICoreWebView2_2* self, IUnknown* eventHandler, EventRegistrationToken* token);
    HRESULT(STDMETHODCALLTYPE* remove_DOMContentLoaded)(ICoreWebView2_2* self, EventRegistrationToken token);
    HRESULT(STDMETHODCALLTYPE* get_CookieManager)(ICoreWebView2_2* self, ICoreWebView2CookieManager** cookieManager);
};

struct ICoreWebView2_2 {
    const ICoreWebView2_2Vtbl* lpVtbl;
};

struct ICoreWebView2CookieVtbl {
    HRESULT(STDMETHODCALLTYPE* QueryInterface)(ICoreWebView2Cookie* self, REFIID riid, void** object);
    ULONG(STDMETHODCALLTYPE* AddRef)(ICoreWebView2Cookie* self);
    ULONG(STDMETHODCALLTYPE* Release)(ICoreWebView2Cookie* self);
    HRESULT(STDMETHODCALLTYPE* get_Name)(ICoreWebView2Cookie* self, LPWSTR* name);
    HRESULT(STDMETHODCALLTYPE* get_Value)(ICoreWebView2Cookie* self, LPWSTR* value);
    HRESULT(STDMETHODCALLTYPE* put_Value)(ICoreWebView2Cookie* self, LPCWSTR value);
    HRESULT(STDMETHODCALLTYPE* get_Domain)(ICoreWebView2Cookie* self, LPWSTR* domain);
    HRESULT(STDMETHODCALLTYPE* get_Path)(ICoreWebView2Cookie* self, LPWSTR* path);
    HRESULT(STDMETHODCALLTYPE* get_Expires)(ICoreWebView2Cookie* self, double* expires);
    HRESULT(STDMETHODCALLTYPE* put_Expires)(ICoreWebView2Cookie* self, double expires);
    HRESULT(STDMETHODCALLTYPE* get_IsHttpOnly)(ICoreWebView2Cookie* self, BOOL* isHttpOnly);
    HRESULT(STDMETHODCALLTYPE* put_IsHttpOnly)(ICoreWebView2Cookie* self, BOOL isHttpOnly);
    HRESULT(STDMETHODCALLTYPE* get_SameSite)(ICoreWebView2Cookie* self, INT32* sameSite);
    HRESULT(STDMETHODCALLTYPE* put_SameSite)(ICoreWebView2Cookie* self, INT32 sameSite);
    HRESULT(STDMETHODCALLTYPE* get_IsSecure)(ICoreWebView2Cookie* self, BOOL* isSecure);
    HRESULT(STDMETHODCALLTYPE* put_IsSecure)(ICoreWebView2Cookie* self, BOOL isSecure);
    HRESULT(STDMETHODCALLTYPE* get_IsSession)(ICoreWebView2Cookie* self, BOOL* isSession);
};

struct ICoreWebView2Cookie {
    const ICoreWebView2CookieVtbl* lpVtbl;
};

struct ICoreWebView2CookieListVtbl {
    HRESULT(STDMETHODCALLTYPE* QueryInterface)(ICoreWebView2CookieList* self, REFIID riid, void** object);
    ULONG(STDMETHODCALLTYPE* AddRef)(ICoreWebView2CookieList* self);
    ULONG(STDMETHODCALLTYPE* Release)(ICoreWebView2CookieList* self);
    HRESULT(STDMETHODCALLTYPE* get_Count)(ICoreWebView2CookieList* self, UINT* count);
    HRESULT(STDMETHODCALLTYPE* GetValueAtIndex)(ICoreWebView2CookieList* self, UINT index, ICoreWebView2Cookie** cookie);
};

struct ICoreWebView2CookieList {
    const ICoreWebView2CookieListVtbl* lpVtbl;
};

struct ICoreWebView2CookieManagerVtbl {
    HRESULT(STDMETHODCALLTYPE* QueryInterface)(ICoreWebView2CookieManager* self, REFIID riid, void** object);
    ULONG(STDMETHODCALLTYPE* AddRef)(ICoreWebView2CookieManager* self);
    ULONG(STDMETHODCALLTYPE* Release)(ICoreWebView2CookieManager* self);
    HRESULT(STDMETHODCALLTYPE* CreateCookie)(ICoreWebView2CookieManager* self, LPCWSTR name, LPCWSTR value, LPCWSTR domain, LPCWSTR path, ICoreWebView2Cookie** cookie);
    HRESULT(STDMETHODCALLTYPE* CopyCookie)(ICoreWebView2CookieManager* self, ICoreWebView2Cookie* cookieParam, ICoreWebView2Cookie** cookie);
    HRESULT(STDMETHODCALLTYPE* GetCookies)(ICoreWebView2CookieManager* self, LPCWSTR uri, ICoreWebView2GetCookiesCompletedHandler* handler);
    HRESULT(STDMETHODCALLTYPE* AddOrUpdateCookie)(ICoreWebView2CookieManager* self, ICoreWebView2Cookie* cookie);
    HRESULT(STDMETHODCALLTYPE* DeleteCookie)(ICoreWebView2CookieManager* self, ICoreWebView2Cookie* cookie);
    HRESULT(STDMETHODCALLTYPE* DeleteCookies)(ICoreWebView2CookieManager* self, LPCWSTR name, LPCWSTR uri);
    HRESULT(STDMETHODCALLTYPE* DeleteCookiesWithDomainAndPath)(ICoreWebView2CookieManager* self, LPCWSTR name, LPCWSTR domain, LPCWSTR path);
    HRESULT(STDMETHODCALLTYPE* DeleteAllCookies)(ICoreWebView2CookieManager* self);
};

struct ICoreWebView2CookieManager {
    const ICoreWebView2CookieManagerVtbl* lpVtbl;
};

using AddNavigationCompletedFn = HRESULT(STDMETHODCALLTYPE*)(
    ICoreWebView2* self,
    ICoreWebView2NavigationCompletedEventHandler* eventHandler,
    EventRegistrationToken* token
);
using RemoveNavigationCompletedFn = HRESULT(STDMETHODCALLTYPE*)(
    ICoreWebView2* self,
    EventRegistrationToken token
);
using ReloadFn = HRESULT(STDMETHODCALLTYPE*)(ICoreWebView2* self);

using CreateEnvironmentWithOptionsFn = HRESULT(STDAPICALLTYPE*)(
    PCWSTR browserExecutableFolder,
    PCWSTR userDataFolder,
    void* environmentOptions,
    ICoreWebView2CreateCoreWebView2EnvironmentCompletedHandler* environmentCreatedHandler
);

std::wstring trim_message(std::wstring message) {
    while (!message.empty() && (message.back() == L'\r' || message.back() == L'\n' || message.back() == L' ' || message.back() == L'\t')) {
        message.pop_back();
    }
    return message;
}

std::wstring format_hresult(HRESULT hr) {
    wchar_t* buffer = nullptr;
    const DWORD flags = FORMAT_MESSAGE_ALLOCATE_BUFFER | FORMAT_MESSAGE_FROM_SYSTEM | FORMAT_MESSAGE_IGNORE_INSERTS;
    const DWORD length = FormatMessageW(
        flags,
        nullptr,
        static_cast<DWORD>(hr),
        MAKELANGID(LANG_NEUTRAL, SUBLANG_DEFAULT),
        reinterpret_cast<LPWSTR>(&buffer),
        0,
        nullptr
    );
    std::wstring message;
    if (length != 0 && buffer != nullptr) {
        message = trim_message(buffer);
        LocalFree(buffer);
    }
    wchar_t hex[16] = {};
    wsprintfW(hex, L"0x%08X", static_cast<unsigned int>(hr));
    if (message.empty()) {
        return hex;
    }
    return message + L" (" + hex + L")";
}

bool should_bridge_mcmod_cookie(const std::wstring& name) {
    return
        name == L"MCMOD_SEED" ||
        name == L"yxd_token" ||
        name == L"method" ||
        name == L"redirect" ||
        name == L"ray" ||
        name == L"Example_auth" ||
        name == L"_uuid";
}

bool is_mcmod_url(const std::wstring& url) {
    return url.find(L"mcmod.cn") != std::wstring::npos;
}

std::wstring take_com_string(LPWSTR value) {
    if (value == nullptr) {
        return {};
    }
    std::wstring result(value);
    CoTaskMemFree(value);
    return result;
}

struct RdiWebView2Instance;

// =====================================================
// C-style COM handler for EnvironmentCreated callback
// Manual vtable to guarantee MSVC-compatible binary layout
// =====================================================
struct CEnvHandler {
    const ICoreWebView2CreateCoreWebView2EnvironmentCompletedHandlerVtbl* lpVtbl;
    LONG refCount;
    RdiWebView2Instance* owner;
};

static HRESULT STDMETHODCALLTYPE CEnvHandler_QueryInterface(ICoreWebView2CreateCoreWebView2EnvironmentCompletedHandler* self, REFIID riid, void** ppv);
static ULONG STDMETHODCALLTYPE CEnvHandler_AddRef(ICoreWebView2CreateCoreWebView2EnvironmentCompletedHandler* self);
static ULONG STDMETHODCALLTYPE CEnvHandler_Release(ICoreWebView2CreateCoreWebView2EnvironmentCompletedHandler* self);
static HRESULT STDMETHODCALLTYPE CEnvHandler_Invoke(ICoreWebView2CreateCoreWebView2EnvironmentCompletedHandler* self, HRESULT errorCode, ICoreWebView2Environment* env);

static const ICoreWebView2CreateCoreWebView2EnvironmentCompletedHandlerVtbl s_CEnvHandler_Vtbl = {
    CEnvHandler_QueryInterface,
    CEnvHandler_AddRef,
    CEnvHandler_Release,
    CEnvHandler_Invoke
};

// =====================================================
// C-style COM handler for ControllerCreated callback
// =====================================================
struct CCtrlHandler {
    const ICoreWebView2CreateCoreWebView2ControllerCompletedHandlerVtbl* lpVtbl;
    LONG refCount;
    RdiWebView2Instance* owner;
};

static HRESULT STDMETHODCALLTYPE CCtrlHandler_QueryInterface(ICoreWebView2CreateCoreWebView2ControllerCompletedHandler* self, REFIID riid, void** ppv);
static ULONG STDMETHODCALLTYPE CCtrlHandler_AddRef(ICoreWebView2CreateCoreWebView2ControllerCompletedHandler* self);
static ULONG STDMETHODCALLTYPE CCtrlHandler_Release(ICoreWebView2CreateCoreWebView2ControllerCompletedHandler* self);
static HRESULT STDMETHODCALLTYPE CCtrlHandler_Invoke(ICoreWebView2CreateCoreWebView2ControllerCompletedHandler* self, HRESULT errorCode, ICoreWebView2Controller* ctrl);

static const ICoreWebView2CreateCoreWebView2ControllerCompletedHandlerVtbl s_CCtrlHandler_Vtbl = {
    CCtrlHandler_QueryInterface,
    CCtrlHandler_AddRef,
    CCtrlHandler_Release,
    CCtrlHandler_Invoke
};

// =====================================================
// C-style COM handler for NavigationCompleted callback
// =====================================================
struct CNavigationCompletedHandler {
    const ICoreWebView2NavigationCompletedEventHandlerVtbl* lpVtbl;
    LONG refCount;
    RdiWebView2Instance* owner;
};

static HRESULT STDMETHODCALLTYPE CNavigationCompletedHandler_QueryInterface(ICoreWebView2NavigationCompletedEventHandler* self, REFIID riid, void** ppv);
static ULONG STDMETHODCALLTYPE CNavigationCompletedHandler_AddRef(ICoreWebView2NavigationCompletedEventHandler* self);
static ULONG STDMETHODCALLTYPE CNavigationCompletedHandler_Release(ICoreWebView2NavigationCompletedEventHandler* self);
static HRESULT STDMETHODCALLTYPE CNavigationCompletedHandler_Invoke(ICoreWebView2NavigationCompletedEventHandler* self, ICoreWebView2* sender, IUnknown* args);

static const ICoreWebView2NavigationCompletedEventHandlerVtbl s_CNavigationCompletedHandler_Vtbl = {
    CNavigationCompletedHandler_QueryInterface,
    CNavigationCompletedHandler_AddRef,
    CNavigationCompletedHandler_Release,
    CNavigationCompletedHandler_Invoke
};

// =====================================================
// C-style COM handler for GetCookies callback
// =====================================================
struct CGetCookiesHandler {
    const ICoreWebView2GetCookiesCompletedHandlerVtbl* lpVtbl;
    LONG refCount;
    RdiWebView2Instance* owner;
    ICoreWebView2CookieManager* cookieManager;
    ICoreWebView2* webView;
    BOOL mayReload;
};

static HRESULT STDMETHODCALLTYPE CGetCookiesHandler_QueryInterface(ICoreWebView2GetCookiesCompletedHandler* self, REFIID riid, void** ppv);
static ULONG STDMETHODCALLTYPE CGetCookiesHandler_AddRef(ICoreWebView2GetCookiesCompletedHandler* self);
static ULONG STDMETHODCALLTYPE CGetCookiesHandler_Release(ICoreWebView2GetCookiesCompletedHandler* self);
static HRESULT STDMETHODCALLTYPE CGetCookiesHandler_Invoke(ICoreWebView2GetCookiesCompletedHandler* self, HRESULT errorCode, ICoreWebView2CookieList* cookieList);

static const ICoreWebView2GetCookiesCompletedHandlerVtbl s_CGetCookiesHandler_Vtbl = {
    CGetCookiesHandler_QueryInterface,
    CGetCookiesHandler_AddRef,
    CGetCookiesHandler_Release,
    CGetCookiesHandler_Invoke
};

struct RdiWebView2Instance {
    std::atomic<ULONG> refCount{1};
    std::mutex mutex;
    HMODULE loaderModule = nullptr;
    CreateEnvironmentWithOptionsFn createEnvironmentWithOptions = nullptr;
    bool comInitialized = false;
    bool closed = false;
    HWND parentWindow = nullptr;
    HWND controllerHostWindow = nullptr;
    RECT bounds{0, 0, 1, 1};
    BOOL visible = TRUE;
    std::wstring pendingUrl;
    std::wstring lastError;
    HRESULT lastHresult = S_OK;
    int state = RDI_WEBVIEW2_STATE_IDLE;
    ICoreWebView2Environment* environment = nullptr;
    ICoreWebView2Controller* controller = nullptr;
    ICoreWebView2* webView = nullptr;
    CEnvHandler* pendingEnvironmentHandler = nullptr;
    CCtrlHandler* pendingControllerHandler = nullptr;
    CNavigationCompletedHandler* navigationCompletedHandler = nullptr;
    EventRegistrationToken navigationCompletedToken{0};
    bool navigationCompletedRegistered = false;
    bool mcmodCookieBridgeReloaded = false;
    bool mcmodCookieBridgeInProgress = false;
    HANDLE uiThreadHandle = nullptr;
    HANDLE uiThreadReadyEvent = nullptr;
    DWORD uiThreadId = 0;
    HRESULT uiThreadInitHr = E_FAIL;

    ULONG AddRef() {
        return ++refCount;
    }

    ULONG Release() {
        const ULONG remaining = --refCount;
        if (remaining == 0) {
            delete this;
        }
        return remaining;
    }

    ~RdiWebView2Instance() {
        close();
        if (uiThreadReadyEvent != nullptr) {
            CloseHandle(uiThreadReadyEvent);
            uiThreadReadyEvent = nullptr;
        }
    }

    static DWORD WINAPI ui_thread_entry(void* param) {
        return static_cast<RdiWebView2Instance*>(param)->ui_thread_main();
    }

    DWORD ui_thread_main() {
        const HRESULT initHr = CoInitializeEx(nullptr, COINIT_APARTMENTTHREADED);
        {
            std::lock_guard<std::mutex> lock(mutex);
            uiThreadId = GetCurrentThreadId();
            uiThreadInitHr = initHr;
            comInitialized = SUCCEEDED(initHr) || initHr == S_FALSE;
        }
        MSG msg{};
        PeekMessageW(&msg, nullptr, 0, 0, PM_NOREMOVE);
        if (uiThreadReadyEvent != nullptr) {
            SetEvent(uiThreadReadyEvent);
        }
        if (FAILED(initHr) && initHr != S_FALSE) {
            {
                std::lock_guard<std::mutex> lock(mutex);
                uiThreadId = 0;
                comInitialized = false;
            }
            Release();
            return 0;
        }
        while (GetMessageW(&msg, nullptr, 0, 0) > 0) {
            if (msg.message == WM_RDI_WEBVIEW2_TASK) {
                auto* task = reinterpret_cast<RdiWebView2Task*>(msg.lParam);
                if (task != nullptr) {
                    task->hr = execute_task_on_ui_thread(*task);
                    if (task->completedEvent != nullptr) {
                        SetEvent(task->completedEvent);
                    }
                }
                continue;
            }
            TranslateMessage(&msg);
            DispatchMessageW(&msg);
        }
        {
            std::lock_guard<std::mutex> lock(mutex);
            cleanup_locked();
            if (loaderModule != nullptr) {
                FreeLibrary(loaderModule);
                loaderModule = nullptr;
            }
            if (comInitialized) {
                CoUninitialize();
                comInitialized = false;
            }
            uiThreadId = 0;
        }
        Release();
        return 0;
    }

    HRESULT ensure_ui_thread() {
        HANDLE readyEvent = nullptr;
        HANDLE threadHandle = nullptr;
        {
            std::lock_guard<std::mutex> lock(mutex);
            if (closed) {
                return E_HANDLE;
            }
            if (uiThreadHandle != nullptr) {
                return uiThreadInitHr;
            }
            readyEvent = CreateEventW(nullptr, TRUE, FALSE, nullptr);
            if (readyEvent == nullptr) {
                return HRESULT_FROM_WIN32(GetLastError());
            }
            uiThreadReadyEvent = readyEvent;
            uiThreadInitHr = E_PENDING;
            AddRef();
            threadHandle = CreateThread(nullptr, 0, &RdiWebView2Instance::ui_thread_entry, this, 0, nullptr);
            if (threadHandle == nullptr) {
                const HRESULT hr = HRESULT_FROM_WIN32(GetLastError());
                uiThreadInitHr = hr;
                uiThreadReadyEvent = nullptr;
                CloseHandle(readyEvent);
                Release();
                return hr;
            }
            uiThreadHandle = threadHandle;
        }
        WaitForSingleObject(readyEvent, INFINITE);
        std::lock_guard<std::mutex> lock(mutex);
        return uiThreadInitHr;
    }

    HRESULT execute_task_on_ui_thread(RdiWebView2Task& task) {
        switch (task.type) {
            case RdiWebView2TaskType::Attach:
                return attach_on_ui_thread(
                    parentWindow,
                    task.loaderPath.empty() ? nullptr : task.loaderPath.c_str(),
                    task.userDataDir.empty() ? nullptr : task.userDataDir.c_str()
                );
            case RdiWebView2TaskType::SetBounds:
                return set_bounds_on_ui_thread(
                    task.bounds.left,
                    task.bounds.top,
                    task.bounds.right,
                    task.bounds.bottom
                );
            case RdiWebView2TaskType::SetVisible:
                return set_visible_on_ui_thread(task.visible);
            case RdiWebView2TaskType::Navigate:
                return navigate_on_ui_thread(task.url.empty() ? nullptr : task.url.c_str());
            case RdiWebView2TaskType::NotifyParentWindowPositionChanged:
                return notify_parent_window_position_changed_on_ui_thread();
            case RdiWebView2TaskType::Close:
                {
                    std::lock_guard<std::mutex> lock(mutex);
                    closed = true;
                    cleanup_locked();
                    return S_OK;
                }
        }
        return E_NOTIMPL;
    }

    HRESULT dispatch_task(RdiWebView2Task& task) {
        const HRESULT threadHr = ensure_ui_thread();
        if (FAILED(threadHr)) {
            return threadHr;
        }
        HANDLE completedEvent = CreateEventW(nullptr, TRUE, FALSE, nullptr);
        if (completedEvent == nullptr) {
            return HRESULT_FROM_WIN32(GetLastError());
        }
        task.completedEvent = completedEvent;
        DWORD threadId = 0;
        {
            std::lock_guard<std::mutex> lock(mutex);
            threadId = uiThreadId;
        }
        if (threadId == 0) {
            CloseHandle(completedEvent);
            task.completedEvent = nullptr;
            return E_HANDLE;
        }
        if (GetCurrentThreadId() == threadId) {
            task.hr = execute_task_on_ui_thread(task);
            CloseHandle(completedEvent);
            task.completedEvent = nullptr;
            return task.hr;
        }
        if (!PostThreadMessageW(threadId, WM_RDI_WEBVIEW2_TASK, 0, reinterpret_cast<LPARAM>(&task))) {
            const HRESULT hr = HRESULT_FROM_WIN32(GetLastError());
            CloseHandle(completedEvent);
            task.completedEvent = nullptr;
            return hr;
        }
        WaitForSingleObject(completedEvent, INFINITE);
        CloseHandle(completedEvent);
        task.completedEvent = nullptr;
        return task.hr;
    }

    HRESULT attach(HWND hwnd, const wchar_t* loaderPath, const wchar_t* userDataDir) {
        {
            std::lock_guard<std::mutex> lock(mutex);
            parentWindow = hwnd;
        }
        RdiWebView2Task task{
            .type = RdiWebView2TaskType::Attach,
            .loaderPath = loaderPath == nullptr ? L"" : loaderPath,
            .userDataDir = userDataDir == nullptr ? L"" : userDataDir
        };
        return dispatch_task(task);
    }

    HRESULT attach_on_ui_thread(HWND hwnd, const wchar_t* loaderPath, const wchar_t* userDataDir) {
        dbg(
            "attach: enter hwnd=%p loaderPath=%p userDataDir=%p thread=%lu hwndThread=%lu",
            hwnd,
            loaderPath,
            userDataDir,
            GetCurrentThreadId(),
            window_thread_id(hwnd)
        );
        if (hwnd == nullptr) {
            dbg("attach: hwnd is null");
            return E_INVALIDARG;
        }
        {
            dbg("attach: locking mutex");
            std::lock_guard<std::mutex> lock(mutex);
            dbg("attach: mutex locked");
            if (closed) {
                dbg("attach: closed");
                return E_HANDLE;
            }
            if (!comInitialized) {
                dbg("attach: calling CoInitializeEx");
                const HRESULT hrInit = CoInitializeEx(nullptr, COINIT_APARTMENTTHREADED);
                dbg("attach: CoInitializeEx returned 0x%08X", (unsigned)hrInit);
                if (FAILED(hrInit) && hrInit != S_FALSE) {
                    set_error_locked(L"COM初始化失败", hrInit, nullptr);
                    return hrInit;
                }
                comInitialized = true;
            }
            dbg("attach: calling ensure_loader_locked");
            const HRESULT loadHr = ensure_loader_locked(loaderPath);
            dbg("attach: ensure_loader_locked returned 0x%08X", (unsigned)loadHr);
            if (FAILED(loadHr)) {
                return loadHr;
            }
            parentWindow = hwnd;
            if (state == RDI_WEBVIEW2_STATE_READY || state == RDI_WEBVIEW2_STATE_CREATING_ENVIRONMENT || state == RDI_WEBVIEW2_STATE_CREATING_CONTROLLER) {
                dbg("attach: already in progress or ready, state=%d", state);
                return S_OK;
            }
            cleanup_locked();
            clear_error_locked();
            state = RDI_WEBVIEW2_STATE_CREATING_ENVIRONMENT;
        }

        const HRESULT hostHr = ensure_controller_host_window();
        dbg("attach: ensure_controller_host_window returned 0x%08X", static_cast<unsigned>(hostHr));
        if (FAILED(hostHr)) {
            std::lock_guard<std::mutex> lock(mutex);
            cleanup_locked();
            set_error_locked(L"创建WebView2宿主窗口失败", hostHr, nullptr);
            return hostHr;
        }

        // Create C-style COM handler (manual vtable, MSVC-compatible)
        auto* handler = new CEnvHandler();
        handler->lpVtbl = &s_CEnvHandler_Vtbl;
        handler->refCount = 1;
        handler->owner = this;
        {
            std::lock_guard<std::mutex> lock(mutex);
            pendingEnvironmentHandler = handler;
        }
        this->AddRef(); // owner ref

        dbg("attach: handler=%p vtbl=%p, calling createEnvironmentWithOptions", handler, handler->lpVtbl);
        const HRESULT hr = createEnvironmentWithOptions(
            nullptr,
            userDataDir,
            nullptr,
            reinterpret_cast<ICoreWebView2CreateCoreWebView2EnvironmentCompletedHandler*>(handler)
        );
        dbg("attach: createEnvironmentWithOptions returned 0x%08X", (unsigned)hr);
        if (FAILED(hr)) {
            std::lock_guard<std::mutex> lock(mutex);
            if (pendingEnvironmentHandler == handler) {
                pendingEnvironmentHandler = nullptr;
            }
            set_error_locked(L"请求创建WebView2环境失败", hr, nullptr);
            CEnvHandler_Release(reinterpret_cast<ICoreWebView2CreateCoreWebView2EnvironmentCompletedHandler*>(handler));
        }
        dbg("attach: exit hr=0x%08X", (unsigned)hr);
        return hr;
    }

    HRESULT set_bounds(int left, int top, int right, int bottom) {
        RdiWebView2Task task;
        task.type = RdiWebView2TaskType::SetBounds;
        task.bounds = RECT{left, top, right, bottom};
        return dispatch_task(task);
    }

    HRESULT set_bounds_on_ui_thread(int left, int top, int right, int bottom) {
        dbg(
            "set_bounds_on_ui_thread: left=%d top=%d right=%d bottom=%d thread=%lu",
            left,
            top,
            right,
            bottom,
            GetCurrentThreadId()
        );
        ICoreWebView2Controller* controllerRef = nullptr;
        RECT nextBounds{left, top, right, bottom};
        HWND hostWindow = nullptr;
        BOOL nextVisible = TRUE;
        {
            std::lock_guard<std::mutex> lock(mutex);
            if (closed) {
                return E_HANDLE;
            }
            bounds = nextBounds;
            hostWindow = controllerHostWindow;
            nextVisible = visible;
            controllerRef = controller;
            if (controllerRef != nullptr) {
                controllerRef->lpVtbl->AddRef(controllerRef);
            }
        }
        dbg(
            "set_bounds_on_ui_thread: host=%p controller=%p visible=%d child=%p",
            hostWindow,
            controllerRef,
            nextVisible ? 1 : 0,
            hostWindow != nullptr ? GetWindow(hostWindow, GW_CHILD) : nullptr
        );
        if (hostWindow != nullptr) {
            const int width = max(1, nextBounds.right - nextBounds.left);
            const int height = max(1, nextBounds.bottom - nextBounds.top);
            const BOOL moveHostOk = SetWindowPos(
                hostWindow,
                nullptr,
                nextBounds.left,
                nextBounds.top,
                width,
                height,
                SWP_NOZORDER | SWP_NOACTIVATE
            );
            dbg(
                "set_bounds_on_ui_thread: SetWindowPos ok=%d host=%p child=%p",
                moveHostOk ? 1 : 0,
                hostWindow,
                GetWindow(hostWindow, GW_CHILD)
            );
        }
        if (controllerRef == nullptr) {
            return S_OK;
        }
        const int width = max(1, nextBounds.right - nextBounds.left);
        const int height = max(1, nextBounds.bottom - nextBounds.top);
        const HWND controllerChildWindow = hostWindow != nullptr ? GetWindow(hostWindow, GW_CHILD) : nullptr;
        if (controllerChildWindow != nullptr) {
            const BOOL moveChildOk = MoveWindow(controllerChildWindow, 0, 0, width, height, TRUE);
            dbg(
                "set_bounds_on_ui_thread: MoveWindow child=%p ok=%d width=%d height=%d",
                controllerChildWindow,
                moveChildOk ? 1 : 0,
                width,
                height
            );
            if (nextVisible) {
                dbg("set_bounds_on_ui_thread: keep visibility during child move");
            }
            controllerRef->lpVtbl->Release(controllerRef);
            return moveChildOk ? S_OK : HRESULT_FROM_WIN32(GetLastError());
        }
        RECT controllerBounds{0, 0, width, height};
        const HRESULT hr = controllerRef->lpVtbl->put_Bounds(controllerRef, controllerBounds);
        dbg(
            "set_bounds_on_ui_thread: put_Bounds hr=0x%08X host=%p child=%p",
            static_cast<unsigned>(hr),
            hostWindow,
            controllerChildWindow
        );
        if (nextVisible) {
            dbg("set_bounds_on_ui_thread: keep visibility during put_Bounds");
        }
        controllerRef->lpVtbl->Release(controllerRef);
        return hr;
    }

    HRESULT set_visible(BOOL nextVisible) {
        RdiWebView2Task task;
        task.type = RdiWebView2TaskType::SetVisible;
        task.visible = nextVisible ? TRUE : FALSE;
        return dispatch_task(task);
    }

    HRESULT set_visible_on_ui_thread(BOOL nextVisible) {
        dbg(
            "set_visible_on_ui_thread: visible=%d thread=%lu",
            nextVisible ? 1 : 0,
            GetCurrentThreadId()
        );
        ICoreWebView2Controller* controllerRef = nullptr;
        HWND hostWindow = nullptr;
        {
            std::lock_guard<std::mutex> lock(mutex);
            if (closed) {
                return E_HANDLE;
            }
            visible = nextVisible ? TRUE : FALSE;
            hostWindow = controllerHostWindow;
            controllerRef = controller;
            if (controllerRef != nullptr) {
                controllerRef->lpVtbl->AddRef(controllerRef);
            }
        }
        if (hostWindow != nullptr) {
            ShowWindow(hostWindow, visible ? SW_SHOW : SW_HIDE);
        }
        if (controllerRef == nullptr) {
            return S_OK;
        }
        const HRESULT hr = controllerRef->lpVtbl->put_IsVisible(controllerRef, visible);
        dbg("set_visible_on_ui_thread: put_IsVisible hr=0x%08X host=%p", static_cast<unsigned>(hr), hostWindow);
        controllerRef->lpVtbl->Release(controllerRef);
        return hr;
    }

    HRESULT navigate(const wchar_t* url) {
        RdiWebView2Task task;
        task.type = RdiWebView2TaskType::Navigate;
        task.url = url == nullptr ? L"" : url;
        return dispatch_task(task);
    }

    HRESULT navigate_on_ui_thread(const wchar_t* url) {
        dbg(
            "navigate_on_ui_thread: url=%ls thread=%lu",
            url != nullptr ? url : L"(null)",
            GetCurrentThreadId()
        );
        ICoreWebView2* webViewRef = nullptr;
        std::wstring safeUrl = url == nullptr ? L"" : url;
        {
            std::lock_guard<std::mutex> lock(mutex);
            if (closed) {
                return E_HANDLE;
            }
            pendingUrl = safeUrl;
            webViewRef = webView;
            if (webViewRef != nullptr) {
                webViewRef->lpVtbl->AddRef(webViewRef);
            }
        }
        if (webViewRef == nullptr) {
            return S_OK;
        }
        const HRESULT hr = webViewRef->lpVtbl->Navigate(webViewRef, safeUrl.c_str());
        dbg("navigate_on_ui_thread: Navigate hr=0x%08X", static_cast<unsigned>(hr));
        webViewRef->lpVtbl->Release(webViewRef);
        return hr;
    }

    HRESULT install_mcmod_cookie_bridge(ICoreWebView2* webViewRef) {
        if (webViewRef == nullptr || navigationCompletedRegistered) {
            return S_OK;
        }
        auto addNavigationCompleted = reinterpret_cast<AddNavigationCompletedFn>(
            webViewRef->lpVtbl->add_NavigationCompleted
        );
        if (addNavigationCompleted == nullptr) {
            return E_NOTIMPL;
        }
        auto* handler = new CNavigationCompletedHandler();
        handler->lpVtbl = &s_CNavigationCompletedHandler_Vtbl;
        handler->refCount = 1;
        handler->owner = this;
        AddRef();
        EventRegistrationToken token{0};
        const HRESULT hr = addNavigationCompleted(
            webViewRef,
            reinterpret_cast<ICoreWebView2NavigationCompletedEventHandler*>(handler),
            &token
        );
        dbg("install_mcmod_cookie_bridge: add_NavigationCompleted hr=0x%08X", static_cast<unsigned>(hr));
        if (FAILED(hr)) {
            CNavigationCompletedHandler_Release(reinterpret_cast<ICoreWebView2NavigationCompletedEventHandler*>(handler));
            return hr;
        }
        navigationCompletedHandler = handler;
        navigationCompletedToken = token;
        navigationCompletedRegistered = true;
        return S_OK;
    }

    void on_navigation_completed(ICoreWebView2* sender) {
        if (sender == nullptr) {
            return;
        }
        LPWSTR sourceRaw = nullptr;
        std::wstring source;
        if (SUCCEEDED(sender->lpVtbl->get_Source(sender, &sourceRaw))) {
            source = take_com_string(sourceRaw);
        }
        if (!is_mcmod_url(source)) {
            return;
        }
        sync_mcmod_cookies(sender);
    }

    void sync_mcmod_cookies(ICoreWebView2* sender) {
        {
            std::lock_guard<std::mutex> lock(mutex);
            if (closed || mcmodCookieBridgeInProgress) {
                return;
            }
            mcmodCookieBridgeInProgress = true;
        }

        ICoreWebView2_2* webView2 = nullptr;
        const HRESULT qHr = sender->lpVtbl->QueryInterface(
            sender,
            IID_ICoreWebView2_2,
            reinterpret_cast<void**>(&webView2)
        );
        if (FAILED(qHr) || webView2 == nullptr) {
            dbg("sync_mcmod_cookies: QI ICoreWebView2_2 failed 0x%08X", static_cast<unsigned>(qHr));
            std::lock_guard<std::mutex> lock(mutex);
            mcmodCookieBridgeInProgress = false;
            return;
        }

        ICoreWebView2CookieManager* cookieManager = nullptr;
        const HRESULT managerHr = webView2->lpVtbl->get_CookieManager(webView2, &cookieManager);
        webView2->lpVtbl->base.Release(reinterpret_cast<ICoreWebView2*>(webView2));
        if (FAILED(managerHr) || cookieManager == nullptr) {
            dbg("sync_mcmod_cookies: get_CookieManager failed 0x%08X", static_cast<unsigned>(managerHr));
            std::lock_guard<std::mutex> lock(mutex);
            mcmodCookieBridgeInProgress = false;
            return;
        }

        auto* handler = new CGetCookiesHandler();
        handler->lpVtbl = &s_CGetCookiesHandler_Vtbl;
        handler->refCount = 1;
        handler->owner = this;
        handler->cookieManager = cookieManager;
        handler->webView = sender;
        {
            std::lock_guard<std::mutex> lock(mutex);
            handler->mayReload = !mcmodCookieBridgeReloaded ? TRUE : FALSE;
        }
        AddRef();
        cookieManager->lpVtbl->AddRef(cookieManager);
        sender->lpVtbl->AddRef(sender);

        const HRESULT cookiesHr = cookieManager->lpVtbl->GetCookies(
            cookieManager,
            L"https://play.mcmod.cn/",
            reinterpret_cast<ICoreWebView2GetCookiesCompletedHandler*>(handler)
        );
        dbg("sync_mcmod_cookies: GetCookies hr=0x%08X", static_cast<unsigned>(cookiesHr));
        cookieManager->lpVtbl->Release(cookieManager);
        if (FAILED(cookiesHr)) {
            CGetCookiesHandler_Release(reinterpret_cast<ICoreWebView2GetCookiesCompletedHandler*>(handler));
            std::lock_guard<std::mutex> lock(mutex);
            mcmodCookieBridgeInProgress = false;
        }
    }

    void on_mcmod_cookies_synced(bool copiedAny, bool mayReload, ICoreWebView2* targetWebView) {
        bool shouldReload = false;
        {
            std::lock_guard<std::mutex> lock(mutex);
            mcmodCookieBridgeInProgress = false;
            if (copiedAny && mayReload && !mcmodCookieBridgeReloaded) {
                mcmodCookieBridgeReloaded = true;
                shouldReload = true;
            }
        }
        if (shouldReload && targetWebView != nullptr) {
            auto reload = reinterpret_cast<ReloadFn>(targetWebView->lpVtbl->Reload);
            if (reload != nullptr) {
                const HRESULT reloadHr = reload(targetWebView);
                dbg("on_mcmod_cookies_synced: Reload hr=0x%08X", static_cast<unsigned>(reloadHr));
            }
        }
    }

    HRESULT notify_parent_window_position_changed() {
        RdiWebView2Task task;
        task.type = RdiWebView2TaskType::NotifyParentWindowPositionChanged;
        return dispatch_task(task);
    }

    HRESULT notify_parent_window_position_changed_on_ui_thread() {
        dbg(
            "notify_parent_window_position_changed_on_ui_thread: skipped for popup host thread=%lu",
            GetCurrentThreadId()
        );
        return S_OK;
    }

    void close() {
        dbg("close: thread=%lu", GetCurrentThreadId());
        HANDLE threadHandle = nullptr;
        DWORD threadId = 0;
        {
            std::lock_guard<std::mutex> lock(mutex);
            if (closed && uiThreadHandle == nullptr) {
                return;
            }
            threadHandle = uiThreadHandle;
            threadId = uiThreadId;
        }
        if (threadHandle == nullptr) {
            std::lock_guard<std::mutex> lock(mutex);
            closed = true;
            cleanup_locked();
            return;
        }
        if (GetCurrentThreadId() == threadId) {
            std::lock_guard<std::mutex> lock(mutex);
            closed = true;
            cleanup_locked();
            PostQuitMessage(0);
            return;
        }
        RdiWebView2Task task;
        task.type = RdiWebView2TaskType::Close;
        dispatch_task(task);
        PostThreadMessageW(threadId, WM_QUIT, 0, 0);
        WaitForSingleObject(threadHandle, INFINITE);
        CloseHandle(threadHandle);
        std::lock_guard<std::mutex> lock(mutex);
        uiThreadHandle = nullptr;
    }

    int get_state() {
        std::lock_guard<std::mutex> lock(mutex);
        dbg("get_state: state=%d(%s)", state, state_name(state));
        return state;
    }

    const wchar_t* get_last_error() {
        std::lock_guard<std::mutex> lock(mutex);
        return lastError.empty() ? nullptr : lastError.c_str();
    }

    HRESULT get_last_hresult() {
        std::lock_guard<std::mutex> lock(mutex);
        return lastHresult;
    }

    void on_environment_created(HRESULT errorCode, ICoreWebView2Environment* createdEnvironment) {
        dbg(
            "on_environment_created: errorCode=0x%08X env=%p thread=%lu",
            static_cast<unsigned>(errorCode),
            createdEnvironment,
            GetCurrentThreadId()
        );
        if (FAILED(errorCode) || createdEnvironment == nullptr) {
            std::lock_guard<std::mutex> lock(mutex);
            set_error_locked(L"创建WebView2环境失败", errorCode, nullptr);
            return;
        }

        HWND parent = nullptr;
        HWND hostWindow = nullptr;
        {
            std::lock_guard<std::mutex> lock(mutex);
            if (closed) {
                createdEnvironment->lpVtbl->Release(createdEnvironment);
                return;
            }
            createdEnvironment->lpVtbl->AddRef(createdEnvironment);
            environment = createdEnvironment;
            state = RDI_WEBVIEW2_STATE_CREATING_CONTROLLER;
            lastHresult = S_OK;
            lastError.clear();
            parent = parentWindow;
            hostWindow = controllerHostWindow;
        }
        dbg(
            "on_environment_created: parent=%p controllerHost=%p envVtbl=%p thread=%lu hwndThread=%lu creating controller",
            parent,
            hostWindow,
            createdEnvironment->lpVtbl,
            GetCurrentThreadId(),
            window_thread_id(hostWindow)
        );

        // Create C-style COM handler for controller callback
        auto* handler = new CCtrlHandler();
        handler->lpVtbl = &s_CCtrlHandler_Vtbl;
        handler->refCount = 1;
        handler->owner = this;
        {
            std::lock_guard<std::mutex> lock(mutex);
            pendingControllerHandler = handler;
        }
        this->AddRef();
        dbg(
            "on_environment_created: ctrlHandler=%p ctrlHandlerVtbl=%p",
            handler,
            handler->lpVtbl
        );

        const HRESULT hr = createdEnvironment->lpVtbl->CreateCoreWebView2Controller(
            createdEnvironment,
            hostWindow,
            reinterpret_cast<ICoreWebView2CreateCoreWebView2ControllerCompletedHandler*>(handler)
        );
        dbg(
            "on_environment_created: CreateCoreWebView2Controller returned 0x%08X",
            static_cast<unsigned>(hr)
        );
        if (FAILED(hr)) {
            std::lock_guard<std::mutex> lock(mutex);
            if (pendingControllerHandler == handler) {
                pendingControllerHandler = nullptr;
            }
            cleanup_locked();
            set_error_locked(L"请求创建WebView2控制器失败", hr, nullptr);
            CCtrlHandler_Release(reinterpret_cast<ICoreWebView2CreateCoreWebView2ControllerCompletedHandler*>(handler));
        }
    }

    void on_controller_created(HRESULT errorCode, ICoreWebView2Controller* createdController) {
        dbg(
            "on_controller_created: errorCode=0x%08X controller=%p thread=%lu",
            static_cast<unsigned>(errorCode),
            createdController,
            GetCurrentThreadId()
        );
        if (FAILED(errorCode) || createdController == nullptr) {
            std::lock_guard<std::mutex> lock(mutex);
            set_error_locked(L"创建WebView2控制器失败", errorCode, nullptr);
            return;
        }

        ICoreWebView2* createdWebView = nullptr;
        const HRESULT coreHr = createdController->lpVtbl->get_CoreWebView2(createdController, &createdWebView);
        dbg(
            "on_controller_created: get_CoreWebView2 returned 0x%08X webview=%p controllerVtbl=%p",
            static_cast<unsigned>(coreHr),
            createdWebView,
            createdController->lpVtbl
        );
        if (FAILED(coreHr) || createdWebView == nullptr) {
            createdController->lpVtbl->Close(createdController);
            createdController->lpVtbl->Release(createdController);
            if (createdWebView != nullptr) {
                createdWebView->lpVtbl->Release(createdWebView);
            }
            std::lock_guard<std::mutex> lock(mutex);
            set_error_locked(L"获取CoreWebView2失败", coreHr, nullptr);
            return;
        }

        RECT pendingBounds{};
        BOOL pendingVisible = TRUE;
        std::wstring pendingUrlCopy;
        HWND hostWindow = nullptr;
        {
            std::lock_guard<std::mutex> lock(mutex);
            if (closed) {
                createdWebView->lpVtbl->Release(createdWebView);
                createdController->lpVtbl->Close(createdController);
                createdController->lpVtbl->Release(createdController);
                return;
            }
            createdController->lpVtbl->AddRef(createdController);
            controller = createdController;
            webView = createdWebView;
            lastHresult = S_OK;
            lastError.clear();
            pendingBounds = bounds;
            pendingVisible = visible;
            pendingUrlCopy = pendingUrl;
            hostWindow = controllerHostWindow;
        }

        if (hostWindow != nullptr) {
            const int width = max(1, pendingBounds.right - pendingBounds.left);
            const int height = max(1, pendingBounds.bottom - pendingBounds.top);
            const BOOL moveHostOk = SetWindowPos(
                hostWindow,
                nullptr,
                pendingBounds.left,
                pendingBounds.top,
                width,
                height,
                SWP_NOZORDER | SWP_NOACTIVATE
            );
            dbg(
                "on_controller_created: SetWindowPos host=%p ok=%d width=%d height=%d child=%p",
                hostWindow,
                moveHostOk ? 1 : 0,
                width,
                height,
                GetWindow(hostWindow, GW_CHILD)
            );
        }
        RECT controllerBounds{0, 0, max(1, pendingBounds.right - pendingBounds.left), max(1, pendingBounds.bottom - pendingBounds.top)};
        const HRESULT boundsHr = createdController->lpVtbl->put_Bounds(createdController, controllerBounds);
        const HRESULT visibleHr = createdController->lpVtbl->put_IsVisible(createdController, pendingVisible);
        dbg(
            "on_controller_created: put_Bounds hr=0x%08X put_IsVisible hr=0x%08X pendingVisible=%d bounds=%ld,%ld,%ld,%ld host=%p",
            static_cast<unsigned>(boundsHr),
            static_cast<unsigned>(visibleHr),
            pendingVisible ? 1 : 0,
            controllerBounds.left,
            controllerBounds.top,
            controllerBounds.right,
            controllerBounds.bottom,
            hostWindow
        );
        const HWND controllerChildWindow = hostWindow != nullptr ? GetWindow(hostWindow, GW_CHILD) : nullptr;
        dbg(
            "on_controller_created: childWindow=%p hostClient=%ldx%ld",
            controllerChildWindow,
            controllerBounds.right - controllerBounds.left,
            controllerBounds.bottom - controllerBounds.top
        );
        if (FAILED(boundsHr) || FAILED(visibleHr)) {
            std::lock_guard<std::mutex> lock(mutex);
            set_error_locked(L"初始化WebView2控制器失败", FAILED(boundsHr) ? boundsHr : visibleHr, nullptr);
            return;
        }
        install_mcmod_cookie_bridge(createdWebView);
        if (!pendingUrlCopy.empty()) {
            const HRESULT navigateHr = createdWebView->lpVtbl->Navigate(createdWebView, pendingUrlCopy.c_str());
            dbg(
                "on_controller_created: Navigate returned 0x%08X urlLen=%zu webViewVtbl=%p",
                static_cast<unsigned>(navigateHr),
                pendingUrlCopy.size(),
                createdWebView->lpVtbl
            );
            if (FAILED(navigateHr)) {
                std::lock_guard<std::mutex> lock(mutex);
                lastHresult = navigateHr;
                lastError = L"WebView2初始导航失败: " + format_hresult(navigateHr);
            }
        }
        {
            std::lock_guard<std::mutex> lock(mutex);
            if (state != RDI_WEBVIEW2_STATE_FAILED) {
                state = RDI_WEBVIEW2_STATE_READY;
            }
        }
    }

private:
    HRESULT ensure_controller_host_window() {
        HWND parent = nullptr;
        HWND ownerWindow = nullptr;
        RECT nextBounds{};
        BOOL nextVisible = TRUE;
        HWND existingHost = nullptr;
        {
            std::lock_guard<std::mutex> lock(mutex);
            parent = parentWindow;
            nextBounds = bounds;
            nextVisible = visible;
            existingHost = controllerHostWindow;
        }
        if (parent == nullptr) {
            return E_INVALIDARG;
        }
        ownerWindow = GetAncestor(parent, GA_ROOT);
        if (ownerWindow == nullptr) {
            ownerWindow = parent;
        }
        if (existingHost != nullptr && IsWindow(existingHost)) {
            return S_OK;
        }
        const int width = max(1, nextBounds.right - nextBounds.left);
        const int height = max(1, nextBounds.bottom - nextBounds.top);
        HWND host = CreateWindowExW(
            WS_EX_TOOLWINDOW | WS_EX_NOACTIVATE,
            L"Static",
            L"",
            WS_POPUP | WS_CLIPCHILDREN | WS_CLIPSIBLINGS,
            nextBounds.left,
            nextBounds.top,
            width,
            height,
            ownerWindow,
            nullptr,
            GetModuleHandleW(nullptr),
            nullptr
        );
        if (host == nullptr) {
            return HRESULT_FROM_WIN32(GetLastError());
        }
        dbg(
            "ensure_controller_host_window: parent=%p parentThread=%lu owner=%p ownerThread=%lu host=%p thread=%lu hostThread=%lu ownerOfHost=%p",
            parent,
            window_thread_id(parent),
            ownerWindow,
            window_thread_id(ownerWindow),
            host,
            GetCurrentThreadId(),
            window_thread_id(host),
            GetWindow(host, GW_OWNER)
        );
        if (nextVisible) {
            ShowWindow(host, SW_SHOWNA);
        }
        std::lock_guard<std::mutex> lock(mutex);
        controllerHostWindow = host;
        return S_OK;
    }

    HRESULT ensure_loader_locked(const wchar_t* loaderPath) {
        if (createEnvironmentWithOptions != nullptr) {
            return S_OK;
        }
        const wchar_t* resolvedPath =
            (loaderPath != nullptr && loaderPath[0] != L'\0')
                ? loaderPath
                : L"WebView2Loader.dll";
        loaderModule = LoadLibraryW(resolvedPath);
        if (loaderModule == nullptr) {
            const HRESULT hr = HRESULT_FROM_WIN32(GetLastError());
            set_error_locked(L"加载WebView2Loader.dll失败", hr, resolvedPath);
            return hr;
        }
        auto* proc = reinterpret_cast<CreateEnvironmentWithOptionsFn>(
            GetProcAddress(loaderModule, "CreateCoreWebView2EnvironmentWithOptions")
        );
        if (proc == nullptr) {
            const HRESULT hr = HRESULT_FROM_WIN32(GetLastError());
            set_error_locked(L"查找CreateCoreWebView2EnvironmentWithOptions失败", hr, nullptr);
            return hr;
        }
        createEnvironmentWithOptions = proc;
        return S_OK;
    }

    void cleanup_locked() {
        if (webView != nullptr && navigationCompletedRegistered) {
            auto removeNavigationCompleted = reinterpret_cast<RemoveNavigationCompletedFn>(
                webView->lpVtbl->remove_NavigationCompleted
            );
            if (removeNavigationCompleted != nullptr) {
                removeNavigationCompleted(webView, navigationCompletedToken);
            }
            navigationCompletedRegistered = false;
            navigationCompletedToken = EventRegistrationToken{0};
        }
        if (navigationCompletedHandler != nullptr) {
            CNavigationCompletedHandler_Release(reinterpret_cast<ICoreWebView2NavigationCompletedEventHandler*>(navigationCompletedHandler));
            navigationCompletedHandler = nullptr;
        }
        mcmodCookieBridgeInProgress = false;
        mcmodCookieBridgeReloaded = false;
        if (webView != nullptr) {
            webView->lpVtbl->Release(webView);
            webView = nullptr;
        }
        if (controller != nullptr) {
            controller->lpVtbl->Close(controller);
            controller->lpVtbl->Release(controller);
            controller = nullptr;
        }
        if (environment != nullptr) {
            environment->lpVtbl->Release(environment);
            environment = nullptr;
        }
        if (controllerHostWindow != nullptr) {
            DestroyWindow(controllerHostWindow);
            controllerHostWindow = nullptr;
        }
        state = closed ? RDI_WEBVIEW2_STATE_IDLE : state;
    }

    void clear_error_locked() {
        dbg("clear_error_locked");
        lastHresult = S_OK;
        lastError.clear();
    }

    void set_error_locked(const wchar_t* stage, HRESULT hr, const wchar_t* detail) {
        dbg(
            "set_error_locked: stage=%ls hr=0x%08X detail=%ls",
            stage != nullptr ? stage : L"(null)",
            static_cast<unsigned>(hr),
            detail != nullptr ? detail : L"(null)"
        );
        lastHresult = hr;
        state = RDI_WEBVIEW2_STATE_FAILED;
        lastError = stage != nullptr ? stage : L"WebView2错误";
        lastError += L": ";
        lastError += format_hresult(hr);
        if (detail != nullptr && detail[0] != L'\0') {
            lastError += L"\n";
            lastError += detail;
        }
    }
};

// =====================================================
// CEnvHandler (EnvironmentCreated) COM method implementations
// =====================================================
static HRESULT STDMETHODCALLTYPE CEnvHandler_QueryInterface(ICoreWebView2CreateCoreWebView2EnvironmentCompletedHandler* self, REFIID riid, void** ppv) {
    auto* handler = reinterpret_cast<CEnvHandler*>(self);
    if (ppv == nullptr) return E_POINTER;
    if (IsEqualIID(riid, IID_IUnknown) || IsEqualIID(riid, IID_ICoreWebView2CreateCoreWebView2EnvironmentCompletedHandler)) {
        *ppv = self;
        CEnvHandler_AddRef(self);
        return S_OK;
    }
    *ppv = nullptr;
    return E_NOINTERFACE;
}

static ULONG STDMETHODCALLTYPE CEnvHandler_AddRef(ICoreWebView2CreateCoreWebView2EnvironmentCompletedHandler* self) {
    auto* handler = reinterpret_cast<CEnvHandler*>(self);
    const auto value = static_cast<ULONG>(InterlockedIncrement(reinterpret_cast<volatile LONG*>(&handler->refCount)));
    dbg("CEnvHandler_AddRef: self=%p ref=%lu", self, value);
    return value;
}

static ULONG STDMETHODCALLTYPE CEnvHandler_Release(ICoreWebView2CreateCoreWebView2EnvironmentCompletedHandler* self) {
    auto* handler = reinterpret_cast<CEnvHandler*>(self);
    const LONG remaining = InterlockedDecrement(reinterpret_cast<volatile LONG*>(&handler->refCount));
    dbg("CEnvHandler_Release: self=%p ref=%ld", self, remaining);
    if (remaining == 0) {
        if (handler->owner) {
            handler->owner->Release();
        }
        delete handler;
    }
    return remaining;
}

static HRESULT STDMETHODCALLTYPE CEnvHandler_Invoke(ICoreWebView2CreateCoreWebView2EnvironmentCompletedHandler* self, HRESULT errorCode, ICoreWebView2Environment* env) {
    auto* handler = reinterpret_cast<CEnvHandler*>(self);
        dbg(
            "CEnvHandler_Invoke: self=%p errorCode=0x%08X env=%p owner=%p thread=%lu",
            self,
            static_cast<unsigned>(errorCode),
            env,
            handler->owner,
            GetCurrentThreadId()
        );
    if (handler->owner) {
        {
            std::lock_guard<std::mutex> lock(handler->owner->mutex);
            if (handler->owner->pendingEnvironmentHandler == handler) {
                handler->owner->pendingEnvironmentHandler = nullptr;
            }
        }
        handler->owner->on_environment_created(errorCode, env);
    }
    CEnvHandler_Release(self);
    return S_OK;
}

// =====================================================
// CCtrlHandler (ControllerCreated) COM method implementations
// =====================================================
static HRESULT STDMETHODCALLTYPE CCtrlHandler_QueryInterface(ICoreWebView2CreateCoreWebView2ControllerCompletedHandler* self, REFIID riid, void** ppv) {
    auto* handler = reinterpret_cast<CCtrlHandler*>(self);
    if (ppv == nullptr) return E_POINTER;
    if (IsEqualIID(riid, IID_IUnknown) || IsEqualIID(riid, IID_ICoreWebView2CreateCoreWebView2ControllerCompletedHandler)) {
        *ppv = self;
        CCtrlHandler_AddRef(self);
        return S_OK;
    }
    *ppv = nullptr;
    return E_NOINTERFACE;
}

static ULONG STDMETHODCALLTYPE CCtrlHandler_AddRef(ICoreWebView2CreateCoreWebView2ControllerCompletedHandler* self) {
    auto* handler = reinterpret_cast<CCtrlHandler*>(self);
    const auto value = static_cast<ULONG>(InterlockedIncrement(reinterpret_cast<volatile LONG*>(&handler->refCount)));
    dbg("CCtrlHandler_AddRef: self=%p ref=%lu", self, value);
    return value;
}

static ULONG STDMETHODCALLTYPE CCtrlHandler_Release(ICoreWebView2CreateCoreWebView2ControllerCompletedHandler* self) {
    auto* handler = reinterpret_cast<CCtrlHandler*>(self);
    const LONG remaining = InterlockedDecrement(reinterpret_cast<volatile LONG*>(&handler->refCount));
    dbg("CCtrlHandler_Release: self=%p ref=%ld", self, remaining);
    if (remaining == 0) {
        if (handler->owner) {
            handler->owner->Release();
        }
        delete handler;
    }
    return remaining;
}

static HRESULT STDMETHODCALLTYPE CCtrlHandler_Invoke(ICoreWebView2CreateCoreWebView2ControllerCompletedHandler* self, HRESULT errorCode, ICoreWebView2Controller* ctrl) {
    auto* handler = reinterpret_cast<CCtrlHandler*>(self);
        dbg(
            "CCtrlHandler_Invoke: self=%p errorCode=0x%08X ctrl=%p owner=%p thread=%lu",
            self,
            static_cast<unsigned>(errorCode),
            ctrl,
            handler->owner,
            GetCurrentThreadId()
        );
    if (handler->owner) {
        {
            std::lock_guard<std::mutex> lock(handler->owner->mutex);
            if (handler->owner->pendingControllerHandler == handler) {
                handler->owner->pendingControllerHandler = nullptr;
            }
        }
        handler->owner->on_controller_created(errorCode, ctrl);
    }
    CCtrlHandler_Release(self);
    return S_OK;
}

// =====================================================
// CNavigationCompletedHandler COM method implementations
// =====================================================
static HRESULT STDMETHODCALLTYPE CNavigationCompletedHandler_QueryInterface(ICoreWebView2NavigationCompletedEventHandler* self, REFIID riid, void** ppv) {
    if (ppv == nullptr) return E_POINTER;
    if (IsEqualIID(riid, IID_IUnknown) || IsEqualIID(riid, IID_ICoreWebView2NavigationCompletedEventHandler)) {
        *ppv = self;
        CNavigationCompletedHandler_AddRef(self);
        return S_OK;
    }
    *ppv = nullptr;
    return E_NOINTERFACE;
}

static ULONG STDMETHODCALLTYPE CNavigationCompletedHandler_AddRef(ICoreWebView2NavigationCompletedEventHandler* self) {
    auto* handler = reinterpret_cast<CNavigationCompletedHandler*>(self);
    return static_cast<ULONG>(InterlockedIncrement(reinterpret_cast<volatile LONG*>(&handler->refCount)));
}

static ULONG STDMETHODCALLTYPE CNavigationCompletedHandler_Release(ICoreWebView2NavigationCompletedEventHandler* self) {
    auto* handler = reinterpret_cast<CNavigationCompletedHandler*>(self);
    const LONG remaining = InterlockedDecrement(reinterpret_cast<volatile LONG*>(&handler->refCount));
    if (remaining == 0) {
        if (handler->owner) {
            handler->owner->Release();
        }
        delete handler;
    }
    return remaining;
}

static HRESULT STDMETHODCALLTYPE CNavigationCompletedHandler_Invoke(ICoreWebView2NavigationCompletedEventHandler* self, ICoreWebView2* sender, IUnknown* args) {
    auto* handler = reinterpret_cast<CNavigationCompletedHandler*>(self);
    if (handler->owner) {
        handler->owner->on_navigation_completed(sender);
    }
    return S_OK;
}

// =====================================================
// CGetCookiesHandler COM method implementations
// =====================================================
static HRESULT STDMETHODCALLTYPE CGetCookiesHandler_QueryInterface(ICoreWebView2GetCookiesCompletedHandler* self, REFIID riid, void** ppv) {
    if (ppv == nullptr) return E_POINTER;
    if (IsEqualIID(riid, IID_IUnknown) || IsEqualIID(riid, IID_ICoreWebView2GetCookiesCompletedHandler)) {
        *ppv = self;
        CGetCookiesHandler_AddRef(self);
        return S_OK;
    }
    *ppv = nullptr;
    return E_NOINTERFACE;
}

static ULONG STDMETHODCALLTYPE CGetCookiesHandler_AddRef(ICoreWebView2GetCookiesCompletedHandler* self) {
    auto* handler = reinterpret_cast<CGetCookiesHandler*>(self);
    return static_cast<ULONG>(InterlockedIncrement(reinterpret_cast<volatile LONG*>(&handler->refCount)));
}

static ULONG STDMETHODCALLTYPE CGetCookiesHandler_Release(ICoreWebView2GetCookiesCompletedHandler* self) {
    auto* handler = reinterpret_cast<CGetCookiesHandler*>(self);
    const LONG remaining = InterlockedDecrement(reinterpret_cast<volatile LONG*>(&handler->refCount));
    if (remaining == 0) {
        if (handler->cookieManager) {
            handler->cookieManager->lpVtbl->Release(handler->cookieManager);
        }
        if (handler->webView) {
            handler->webView->lpVtbl->Release(handler->webView);
        }
        if (handler->owner) {
            handler->owner->Release();
        }
        delete handler;
    }
    return remaining;
}

static HRESULT STDMETHODCALLTYPE CGetCookiesHandler_Invoke(ICoreWebView2GetCookiesCompletedHandler* self, HRESULT errorCode, ICoreWebView2CookieList* cookieList) {
    auto* handler = reinterpret_cast<CGetCookiesHandler*>(self);
    bool copiedAny = false;
    if (SUCCEEDED(errorCode) && cookieList != nullptr && handler->cookieManager != nullptr) {
        UINT count = 0;
        if (SUCCEEDED(cookieList->lpVtbl->get_Count(cookieList, &count))) {
            for (UINT i = 0; i < count; ++i) {
                ICoreWebView2Cookie* sourceCookie = nullptr;
                if (FAILED(cookieList->lpVtbl->GetValueAtIndex(cookieList, i, &sourceCookie)) || sourceCookie == nullptr) {
                    continue;
                }
                LPWSTR nameRaw = nullptr;
                LPWSTR valueRaw = nullptr;
                const HRESULT nameHr = sourceCookie->lpVtbl->get_Name(sourceCookie, &nameRaw);
                const HRESULT valueHr = sourceCookie->lpVtbl->get_Value(sourceCookie, &valueRaw);
                std::wstring name = SUCCEEDED(nameHr) ? take_com_string(nameRaw) : std::wstring();
                std::wstring value = SUCCEEDED(valueHr) ? take_com_string(valueRaw) : std::wstring();
                if (should_bridge_mcmod_cookie(name) && !value.empty()) {
                    ICoreWebView2Cookie* bridgedCookie = nullptr;
                    const HRESULT createHr = handler->cookieManager->lpVtbl->CreateCookie(
                        handler->cookieManager,
                        name.c_str(),
                        value.c_str(),
                        L".mcmod.cn",
                        L"/",
                        &bridgedCookie
                    );
                    if (SUCCEEDED(createHr) && bridgedCookie != nullptr) {
                        BOOL isHttpOnly = FALSE;
                        if (SUCCEEDED(sourceCookie->lpVtbl->get_IsHttpOnly(sourceCookie, &isHttpOnly))) {
                            bridgedCookie->lpVtbl->put_IsHttpOnly(bridgedCookie, isHttpOnly);
                        }
                        bridgedCookie->lpVtbl->put_IsSecure(bridgedCookie, TRUE);
                        const HRESULT addHr = handler->cookieManager->lpVtbl->AddOrUpdateCookie(
                            handler->cookieManager,
                            bridgedCookie
                        );
                        dbg(
                            "CGetCookiesHandler_Invoke: bridge cookie %ls hr=0x%08X",
                            name.c_str(),
                            static_cast<unsigned>(addHr)
                        );
                        if (SUCCEEDED(addHr)) {
                            copiedAny = true;
                        }
                        bridgedCookie->lpVtbl->Release(bridgedCookie);
                    }
                }
                sourceCookie->lpVtbl->Release(sourceCookie);
            }
        }
    } else {
        dbg("CGetCookiesHandler_Invoke: GetCookies failed 0x%08X", static_cast<unsigned>(errorCode));
    }
    if (handler->owner) {
        handler->owner->on_mcmod_cookies_synced(copiedAny, handler->mayReload == TRUE, handler->webView);
    }
    CGetCookiesHandler_Release(self);
    return S_OK;
}

RdiWebView2Instance* to_instance(void* handle) {
    return static_cast<RdiWebView2Instance*>(handle);
}

} // namespace

extern "C" {

__declspec(dllexport) void* WINAPI rdi_webview2_create() {
    return new RdiWebView2Instance();
}

__declspec(dllexport) void WINAPI rdi_webview2_destroy(void* handle) {
    auto* instance = to_instance(handle);
    if (instance == nullptr) {
        return;
    }
    instance->close();
    instance->Release();
}

__declspec(dllexport) int WINAPI rdi_webview2_attach(
    void* handle,
    HWND hwnd,
    const wchar_t* loaderPath,
    const wchar_t* userDataDir
) {
    dbg("rdi_webview2_attach: handle=%p hwnd=%p loaderPath=%p userDataDir=%p", handle, hwnd, loaderPath, userDataDir);
    auto* instance = to_instance(handle);
    if (instance == nullptr) {
        dbg("rdi_webview2_attach: instance is null");
        return E_POINTER;
    }
    dbg("rdi_webview2_attach: calling instance->attach");
    return instance->attach(hwnd, loaderPath, userDataDir);
}

__declspec(dllexport) int WINAPI rdi_webview2_set_bounds(
    void* handle,
    int left,
    int top,
    int right,
    int bottom
) {
    auto* instance = to_instance(handle);
    if (instance == nullptr) {
        return E_POINTER;
    }
    return instance->set_bounds(left, top, right, bottom);
}

__declspec(dllexport) int WINAPI rdi_webview2_set_visible(void* handle, int visible) {
    auto* instance = to_instance(handle);
    if (instance == nullptr) {
        return E_POINTER;
    }
    return instance->set_visible(visible ? TRUE : FALSE);
}

__declspec(dllexport) int WINAPI rdi_webview2_navigate(void* handle, const wchar_t* url) {
    auto* instance = to_instance(handle);
    if (instance == nullptr) {
        return E_POINTER;
    }
    return instance->navigate(url);
}

__declspec(dllexport) int WINAPI rdi_webview2_notify_parent_window_position_changed(void* handle) {
    auto* instance = to_instance(handle);
    if (instance == nullptr) {
        return E_POINTER;
    }
    return instance->notify_parent_window_position_changed();
}

__declspec(dllexport) int WINAPI rdi_webview2_get_state(void* handle) {
    auto* instance = to_instance(handle);
    if (instance == nullptr) {
        dbg("rdi_webview2_get_state: instance is null");
        return RDI_WEBVIEW2_STATE_FAILED;
    }
    return instance->get_state();
}

__declspec(dllexport) const wchar_t* WINAPI rdi_webview2_get_last_error(void* handle) {
    auto* instance = to_instance(handle);
    if (instance == nullptr) {
        return L"WebView2桥(native bridge)句柄为空。";
    }
    return instance->get_last_error();
}

__declspec(dllexport) int WINAPI rdi_webview2_get_last_hresult(void* handle) {
    auto* instance = to_instance(handle);
    if (instance == nullptr) {
        return E_POINTER;
    }
    return instance->get_last_hresult();
}

} // extern "C"
