#include <jni.h>

#include <cstdio>
#include <memory>
#include <mutex>
#include <new>
#include <string>

#include "PltAction.h"
#include "PltMediaRenderer.h"
#include "PltService.h"
#include "PltUPnP.h"

namespace {

std::string ToString(JNIEnv* env, jstring value) {
    if (value == nullptr) return std::string();
    const char* chars = env->GetStringUTFChars(value, nullptr);
    if (chars == nullptr) return std::string();
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

jstring ToJString(JNIEnv* env, const std::string& value) {
    return env->NewStringUTF(value.c_str());
}

jstring Success(JNIEnv*) {
    return nullptr;
}

jstring Error(JNIEnv* env, const std::string& message) {
    return ToJString(env, message);
}

std::string FormatUpnpDuration(jlong duration_ms) {
    const long long total_seconds = duration_ms > 0 ? duration_ms / 1000 : 0;
    const long long hours = total_seconds / 3600;
    const long long minutes = (total_seconds % 3600) / 60;
    const long long seconds = total_seconds % 60;
    char buffer[16];
    snprintf(buffer, sizeof(buffer), "%02lld:%02lld:%02lld", hours, minutes, seconds);
    return std::string(buffer);
}

class EnvScope {
public:
    explicit EnvScope(JavaVM* vm) : vm_(vm), env_(nullptr), attached_(false) {
        if (vm_ == nullptr) return;
        if (vm_->GetEnv(reinterpret_cast<void**>(&env_), JNI_VERSION_1_6) == JNI_OK) {
            return;
        }
        if (vm_->AttachCurrentThread(&env_, nullptr) == JNI_OK) {
            attached_ = true;
        } else {
            env_ = nullptr;
        }
    }

    ~EnvScope() {
        if (attached_ && vm_ != nullptr) {
            vm_->DetachCurrentThread();
        }
    }

    JNIEnv* env() const { return env_; }

private:
    JavaVM* vm_;
    JNIEnv* env_;
    bool attached_;
};

class LynUpnpRendererEngine final : public PLT_MediaRendererDelegate {
public:
    LynUpnpRendererEngine(
        JNIEnv* env,
        jobject callback,
        const std::string& friendly_name,
        const std::string& uuid) {
        env->GetJavaVM(&vm_);
        callback_ = env->NewGlobalRef(callback);
        jclass local_class = env->GetObjectClass(callback);
        callback_class_ = reinterpret_cast<jclass>(env->NewGlobalRef(local_class));
        env->DeleteLocalRef(local_class);

        on_set_media_ = env->GetMethodID(callback_class_, "handleSetMedia", "(Ljava/lang/String;Ljava/lang/String;)Z");
        on_play_ = env->GetMethodID(callback_class_, "handlePlay", "()Z");
        on_pause_ = env->GetMethodID(callback_class_, "handlePause", "()Z");
        on_stop_ = env->GetMethodID(callback_class_, "handleStop", "()Z");
        on_seek_ = env->GetMethodID(callback_class_, "handleSeek", "(Ljava/lang/String;Ljava/lang/String;)Z");
        on_set_volume_ = env->GetMethodID(callback_class_, "handleSetVolume", "(I)Z");
        on_set_mute_ = env->GetMethodID(callback_class_, "handleSetMute", "(Z)Z");

        renderer_raw_ = new (std::nothrow) PLT_MediaRenderer(
            friendly_name.c_str(),
            false,
            uuid.c_str(),
            0,
            true);
        if (renderer_raw_ != nullptr) {
            renderer_raw_->SetDelegate(this);
            renderer_ = PLT_DeviceHostReference(renderer_raw_);
        }
        upnp_ = std::make_unique<PLT_UPnP>();
    }

    ~LynUpnpRendererEngine() override {
        Release();
        EnvScope scope(vm_);
        JNIEnv* env = scope.env();
        if (env != nullptr) {
            if (callback_ != nullptr) {
                env->DeleteGlobalRef(callback_);
                callback_ = nullptr;
            }
            if (callback_class_ != nullptr) {
                env->DeleteGlobalRef(callback_class_);
                callback_class_ = nullptr;
            }
        }
    }

    std::string Start() {
        if (renderer_raw_ == nullptr || upnp_ == nullptr) {
            return "DLNA 接收端初始化失败。";
        }
        if (started_) return std::string();
        NPT_Result result = upnp_->AddDevice(renderer_);
        if (NPT_FAILED(result)) {
            return "注册 DLNA 接收设备失败。";
        }
        result = upnp_->Start();
        if (NPT_FAILED(result)) {
            return "启动 DLNA 接收端失败。";
        }
        started_ = true;
        UpdateTransportState("NO_MEDIA_PRESENT", 0, 0);
        return std::string();
    }

    void Release() {
        if (renderer_raw_ != nullptr) {
            renderer_raw_->SetDelegate(nullptr);
        }
        if (upnp_ != nullptr && started_) {
            upnp_->Stop();
            started_ = false;
        }
    }

    void UpdateTransportState(const std::string& state, jlong position_ms, jlong duration_ms) {
        std::lock_guard<std::mutex> lock(mutex_);
        SetAvTransportStateVariable("TransportState", state);
        SetAvTransportStateVariable("TransportStatus", "OK");
        SetAvTransportStateVariable("RelativeTimePosition", FormatUpnpDuration(position_ms));
        SetAvTransportStateVariable("AbsoluteTimePosition", FormatUpnpDuration(position_ms));
        SetAvTransportStateVariable("CurrentTrackDuration", FormatUpnpDuration(duration_ms));
        SetAvTransportStateVariable("CurrentMediaDuration", FormatUpnpDuration(duration_ms));
    }

    void UpdateVolume(jint volume_percent, jboolean muted) {
        std::lock_guard<std::mutex> lock(mutex_);
        const int volume = volume_percent < 0 ? 0 : (volume_percent > 100 ? 100 : volume_percent);
        volume_percent_ = volume;
        muted_ = muted == JNI_TRUE;
        SetRenderingStateVariable("Volume", std::to_string(volume_percent_));
        SetRenderingStateVariable("Mute", muted_ ? "1" : "0");
    }

    NPT_Result OnGetCurrentConnectionInfo(PLT_ActionReference& action) override {
        if (NPT_FAILED(action->VerifyArgumentValue("ConnectionID", "0"))) {
            action->SetError(706, "No Such Connection.");
            return NPT_FAILURE;
        }
        if (NPT_FAILED(action->SetArgumentValue("RcsID", "0")) ||
            NPT_FAILED(action->SetArgumentValue("AVTransportID", "0")) ||
            NPT_FAILED(action->SetArgumentValue("ProtocolInfo", "http-get:*:audio/*:*")) ||
            NPT_FAILED(action->SetArgumentValue("PeerConnectionManager", "/")) ||
            NPT_FAILED(action->SetArgumentValue("PeerConnectionID", "-1")) ||
            NPT_FAILED(action->SetArgumentValue("Direction", "Input")) ||
            NPT_FAILED(action->SetArgumentValue("Status", "OK"))) {
            return NPT_FAILURE;
        }
        return NPT_SUCCESS;
    }

    NPT_Result OnNext(PLT_ActionReference& action) override {
        action->SetError(701, "Next is not supported.");
        return NPT_FAILURE;
    }

    NPT_Result OnPause(PLT_ActionReference& action) override {
        if (!CallSimpleBoolean(on_pause_)) {
            action->SetError(501, "Pause failed.");
            return NPT_FAILURE;
        }
        UpdateTransportState("PAUSED_PLAYBACK", current_position_ms_, current_duration_ms_);
        return NPT_SUCCESS;
    }

    NPT_Result OnPlay(PLT_ActionReference& action) override {
        {
            std::lock_guard<std::mutex> lock(mutex_);
            if (current_uri_.empty()) {
                action->SetError(701, "No media present.");
                return NPT_FAILURE;
            }
        }
        if (!CallSimpleBoolean(on_play_)) {
            action->SetError(501, "Play failed.");
            return NPT_FAILURE;
        }
        UpdateTransportState("PLAYING", current_position_ms_, current_duration_ms_);
        return NPT_SUCCESS;
    }

    NPT_Result OnPrevious(PLT_ActionReference& action) override {
        action->SetError(701, "Previous is not supported.");
        return NPT_FAILURE;
    }

    NPT_Result OnSeek(PLT_ActionReference& action) override {
        NPT_String unit;
        NPT_String target;
        if (NPT_FAILED(action->GetArgumentValue("Unit", unit)) ||
            NPT_FAILED(action->GetArgumentValue("Target", target))) {
            action->SetError(402, "Invalid or Missing Args.");
            return NPT_FAILURE;
        }
        if (!CallSeek(unit.GetChars(), target.GetChars())) {
            action->SetError(710, "Seek mode not supported.");
            return NPT_FAILURE;
        }
        const jlong position_ms = ParseDurationMillis(target.GetChars());
        current_position_ms_ = position_ms;
        UpdateTransportState(CurrentTransportState(), current_position_ms_, current_duration_ms_);
        return NPT_SUCCESS;
    }

    NPT_Result OnStop(PLT_ActionReference& action) override {
        if (!CallSimpleBoolean(on_stop_)) {
            action->SetError(501, "Stop failed.");
            return NPT_FAILURE;
        }
        current_position_ms_ = 0;
        UpdateTransportState("STOPPED", current_position_ms_, current_duration_ms_);
        return NPT_SUCCESS;
    }

    NPT_Result OnSetAVTransportURI(PLT_ActionReference& action) override {
        NPT_String uri;
        NPT_String metadata;
        if (NPT_FAILED(action->GetArgumentValue("CurrentURI", uri)) ||
            NPT_FAILED(action->GetArgumentValue("CurrentURIMetaData", metadata))) {
            action->SetError(402, "Invalid or Missing Args.");
            return NPT_FAILURE;
        }
        if (!CallSetMedia(uri.GetChars(), metadata.GetChars())) {
            action->SetError(714, "Unsupported media.");
            return NPT_FAILURE;
        }

        {
            std::lock_guard<std::mutex> lock(mutex_);
            current_uri_ = uri.GetChars();
            current_metadata_ = metadata.GetChars();
            current_position_ms_ = 0;
            current_duration_ms_ = 0;
            SetAvTransportStateVariable("AVTransportURI", current_uri_);
            SetAvTransportStateVariable("AVTransportURIMetaData", current_metadata_);
            SetAvTransportStateVariable("AVTransportURIMetadata", current_metadata_);
            SetAvTransportStateVariable("CurrentTrackURI", current_uri_);
            SetAvTransportStateVariable("CurrentTrackMetaData", current_metadata_);
            SetAvTransportStateVariable("CurrentTrackMetadata", current_metadata_);
            SetAvTransportStateVariable("NumberOfTracks", "1");
            SetAvTransportStateVariable("CurrentTrack", "1");
            SetAvTransportStateVariable("CurrentTransportActions", "Play,Pause,Stop,Seek");
            SetAvTransportStateVariable("TransportState", "STOPPED");
            SetAvTransportStateVariable("TransportStatus", "OK");
        }
        return NPT_SUCCESS;
    }

    NPT_Result OnSetNextAVTransportURI(PLT_ActionReference& action) override {
        action->SetError(701, "Next transport URI is not supported.");
        return NPT_FAILURE;
    }

    NPT_Result OnSetPlayMode(PLT_ActionReference& action) override {
        NPT_String mode;
        if (NPT_FAILED(action->GetArgumentValue("NewPlayMode", mode))) {
            action->SetError(402, "Invalid or Missing Args.");
            return NPT_FAILURE;
        }
        if (mode.Compare("NORMAL", true) != 0) {
            action->SetError(712, "Play mode not supported.");
            return NPT_FAILURE;
        }
        SetAvTransportStateVariable("CurrentPlayMode", "NORMAL");
        return NPT_SUCCESS;
    }

    NPT_Result OnSetVolume(PLT_ActionReference& action) override {
        NPT_UInt32 volume = 0;
        if (NPT_FAILED(action->GetArgumentValue("DesiredVolume", volume))) {
            action->SetError(402, "Invalid or Missing Args.");
            return NPT_FAILURE;
        }
        const int clamped = volume > 100 ? 100 : static_cast<int>(volume);
        if (!CallSetVolume(clamped)) {
            action->SetError(501, "Set volume failed.");
            return NPT_FAILURE;
        }
        UpdateVolume(clamped, muted_ ? JNI_TRUE : JNI_FALSE);
        return NPT_SUCCESS;
    }

    NPT_Result OnSetVolumeDB(PLT_ActionReference& action) override {
        action->SetError(701, "VolumeDB is not supported.");
        return NPT_FAILURE;
    }

    NPT_Result OnGetVolumeDBRange(PLT_ActionReference& action) override {
        if (NPT_FAILED(action->SetArgumentValue("MinValue", "-10000")) ||
            NPT_FAILED(action->SetArgumentValue("MaxValue", "0"))) {
            return NPT_FAILURE;
        }
        return NPT_SUCCESS;
    }

    NPT_Result OnSetMute(PLT_ActionReference& action) override {
        bool muted = false;
        if (NPT_FAILED(action->GetArgumentValue("DesiredMute", muted))) {
            action->SetError(402, "Invalid or Missing Args.");
            return NPT_FAILURE;
        }
        if (!CallSetMute(muted)) {
            action->SetError(501, "Set mute failed.");
            return NPT_FAILURE;
        }
        UpdateVolume(volume_percent_, muted ? JNI_TRUE : JNI_FALSE);
        return NPT_SUCCESS;
    }

private:
    bool CallSetMedia(const std::string& uri, const std::string& metadata) {
        EnvScope scope(vm_);
        JNIEnv* env = scope.env();
        if (env == nullptr || callback_ == nullptr || on_set_media_ == nullptr) return false;
        jstring j_uri = env->NewStringUTF(uri.c_str());
        jstring j_metadata = env->NewStringUTF(metadata.c_str());
        const bool result = env->CallBooleanMethod(callback_, on_set_media_, j_uri, j_metadata) == JNI_TRUE;
        env->DeleteLocalRef(j_uri);
        env->DeleteLocalRef(j_metadata);
        return ClearException(env) && result;
    }

    bool CallSimpleBoolean(jmethodID method) {
        EnvScope scope(vm_);
        JNIEnv* env = scope.env();
        if (env == nullptr || callback_ == nullptr || method == nullptr) return false;
        const bool result = env->CallBooleanMethod(callback_, method) == JNI_TRUE;
        return ClearException(env) && result;
    }

    bool CallSeek(const std::string& unit, const std::string& target) {
        EnvScope scope(vm_);
        JNIEnv* env = scope.env();
        if (env == nullptr || callback_ == nullptr || on_seek_ == nullptr) return false;
        jstring j_unit = env->NewStringUTF(unit.c_str());
        jstring j_target = env->NewStringUTF(target.c_str());
        const bool result = env->CallBooleanMethod(callback_, on_seek_, j_unit, j_target) == JNI_TRUE;
        env->DeleteLocalRef(j_unit);
        env->DeleteLocalRef(j_target);
        return ClearException(env) && result;
    }

    bool CallSetVolume(int volume_percent) {
        EnvScope scope(vm_);
        JNIEnv* env = scope.env();
        if (env == nullptr || callback_ == nullptr || on_set_volume_ == nullptr) return false;
        const bool result = env->CallBooleanMethod(callback_, on_set_volume_, volume_percent) == JNI_TRUE;
        return ClearException(env) && result;
    }

    bool CallSetMute(bool muted) {
        EnvScope scope(vm_);
        JNIEnv* env = scope.env();
        if (env == nullptr || callback_ == nullptr || on_set_mute_ == nullptr) return false;
        const bool result = env->CallBooleanMethod(callback_, on_set_mute_, muted ? JNI_TRUE : JNI_FALSE) == JNI_TRUE;
        return ClearException(env) && result;
    }

    bool ClearException(JNIEnv* env) {
        if (env == nullptr || !env->ExceptionCheck()) return true;
        env->ExceptionClear();
        return false;
    }

    void SetAvTransportStateVariable(const char* name, const std::string& value) {
        PLT_Service* service = nullptr;
        if (renderer_raw_ == nullptr ||
            NPT_FAILED(renderer_raw_->FindServiceByType("urn:schemas-upnp-org:service:AVTransport:1", service)) ||
            service == nullptr) {
            return;
        }
        service->SetStateVariable(name, value.c_str());
    }

    void SetRenderingStateVariable(const char* name, const std::string& value) {
        PLT_Service* service = nullptr;
        if (renderer_raw_ == nullptr ||
            NPT_FAILED(renderer_raw_->FindServiceByType("urn:schemas-upnp-org:service:RenderingControl:1", service)) ||
            service == nullptr) {
            return;
        }
        service->SetStateVariable(name, value.c_str());
    }

    static jlong ParseDurationMillis(const std::string& value) {
        long long hours = 0;
        long long minutes = 0;
        double seconds = 0.0;
        if (sscanf(value.c_str(), "%lld:%lld:%lf", &hours, &minutes, &seconds) != 3) {
            return 0;
        }
        const double total_seconds = static_cast<double>(hours * 3600 + minutes * 60) + seconds;
        return static_cast<jlong>(total_seconds * 1000.0);
    }

    std::string CurrentTransportState() {
        PLT_Service* service = nullptr;
        if (renderer_raw_ == nullptr ||
            NPT_FAILED(renderer_raw_->FindServiceByType("urn:schemas-upnp-org:service:AVTransport:1", service)) ||
            service == nullptr) {
            return "STOPPED";
        }
        NPT_String state;
        if (NPT_SUCCEEDED(service->GetStateVariableValue("TransportState", state))) {
            return state.GetChars();
        }
        return "STOPPED";
    }

    std::mutex mutex_;
    JavaVM* vm_ = nullptr;
    jobject callback_ = nullptr;
    jclass callback_class_ = nullptr;
    jmethodID on_set_media_ = nullptr;
    jmethodID on_play_ = nullptr;
    jmethodID on_pause_ = nullptr;
    jmethodID on_stop_ = nullptr;
    jmethodID on_seek_ = nullptr;
    jmethodID on_set_volume_ = nullptr;
    jmethodID on_set_mute_ = nullptr;
    std::unique_ptr<PLT_UPnP> upnp_;
    PLT_DeviceHostReference renderer_;
    PLT_MediaRenderer* renderer_raw_ = nullptr;
    bool started_ = false;
    std::string current_uri_;
    std::string current_metadata_;
    jlong current_position_ms_ = 0;
    jlong current_duration_ms_ = 0;
    int volume_percent_ = 100;
    bool muted_ = false;
};

LynUpnpRendererEngine* RendererFromHandle(jlong handle) {
    return reinterpret_cast<LynUpnpRendererEngine*>(handle);
}

} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_top_iwesley_lyn_music_cast_upnp_android_AndroidUpnpMediaRenderer_nativeCreateRenderer(
    JNIEnv* env,
    jobject,
    jobject callback,
    jstring friendly_name,
    jstring uuid) {
    auto* engine = new (std::nothrow) LynUpnpRendererEngine(
        env,
        callback,
        ToString(env, friendly_name),
        ToString(env, uuid));
    return reinterpret_cast<jlong>(engine);
}

extern "C" JNIEXPORT jstring JNICALL
Java_top_iwesley_lyn_music_cast_upnp_android_AndroidUpnpMediaRenderer_nativeStartRenderer(
    JNIEnv* env,
    jobject,
    jlong handle) {
    auto* engine = RendererFromHandle(handle);
    if (engine == nullptr) return Error(env, "DLNA 接收端未初始化。");
    const std::string error = engine->Start();
    return error.empty() ? Success(env) : Error(env, error);
}

extern "C" JNIEXPORT void JNICALL
Java_top_iwesley_lyn_music_cast_upnp_android_AndroidUpnpMediaRenderer_nativeUpdateTransportState(
    JNIEnv* env,
    jobject,
    jlong handle,
    jstring state,
    jlong position_ms,
    jlong duration_ms) {
    auto* engine = RendererFromHandle(handle);
    if (engine == nullptr) return;
    engine->UpdateTransportState(ToString(env, state), position_ms, duration_ms);
}

extern "C" JNIEXPORT void JNICALL
Java_top_iwesley_lyn_music_cast_upnp_android_AndroidUpnpMediaRenderer_nativeUpdateVolume(
    JNIEnv*,
    jobject,
    jlong handle,
    jint volume_percent,
    jboolean muted) {
    auto* engine = RendererFromHandle(handle);
    if (engine == nullptr) return;
    engine->UpdateVolume(volume_percent, muted);
}

extern "C" JNIEXPORT void JNICALL
Java_top_iwesley_lyn_music_cast_upnp_android_AndroidUpnpMediaRenderer_nativeReleaseRenderer(
    JNIEnv*,
    jobject,
    jlong handle) {
    delete RendererFromHandle(handle);
}
