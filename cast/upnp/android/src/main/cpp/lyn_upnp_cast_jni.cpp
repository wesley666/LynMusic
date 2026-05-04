#include <jni.h>

#include <chrono>
#include <map>
#include <memory>
#include <mutex>
#include <new>
#include <string>
#include <thread>

#include "PltMediaController.h"
#include "PltUPnP.h"

namespace {

constexpr char kFieldSeparator = '\x1F';
constexpr char kRecordSeparator = '\x1E';
constexpr const char* kRendererSearchTarget = "urn:schemas-upnp-org:device:MediaRenderer:1";

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

std::string CleanField(const NPT_String& value) {
    std::string result(value.GetChars());
    for (char& c : result) {
        if (c == kFieldSeparator || c == kRecordSeparator || c == '\n' || c == '\r' || c == '\t') {
            c = ' ';
        }
    }
    return result;
}

struct DeviceRecord {
    PLT_DeviceDataReference device;
    std::string id;
    std::string name;
    std::string description;
    std::string location;
};

class LynUpnpCastEngine final : public PLT_MediaControllerDelegate {
public:
    LynUpnpCastEngine() {
        ctrl_point_ = PLT_CtrlPointReference(new PLT_CtrlPoint(kRendererSearchTarget));
        media_controller_ = PLT_MediaControllerReference(new PLT_MediaController(ctrl_point_, this));
        upnp_ = std::make_unique<PLT_UPnP>();
        upnp_->AddCtrlPoint(ctrl_point_);
        upnp_->Start();
    }

    ~LynUpnpCastEngine() override {
        if (media_controller_.AsPointer() != nullptr) {
            media_controller_->SetDelegate(nullptr);
        }
        if (upnp_) {
            upnp_->Stop();
        }
    }

    bool OnMRAdded(PLT_DeviceDataReference& device) override {
        std::lock_guard<std::mutex> lock(mutex_);
        const std::string uuid = CleanField(device->GetUUID());
        DeviceRecord record;
        record.device = device;
        record.id = uuid;
        record.name = CleanField(device->GetFriendlyName());
        record.description = CleanField(device->GetModelDescription());
        record.location = CleanField(device->GetDescriptionUrl());
        devices_[uuid] = record;
        return true;
    }

    void OnMRRemoved(PLT_DeviceDataReference& device) override {
        std::lock_guard<std::mutex> lock(mutex_);
        devices_.erase(CleanField(device->GetUUID()));
    }

    std::string StartDiscovery() {
        if (ctrl_point_.AsPointer() == nullptr) {
            return "UPnP 控制点未初始化。";
        }
        const NPT_Result result = ctrl_point_->Discover(
            NPT_HttpUrl("239.255.255.250", 1900, "*"),
            kRendererSearchTarget,
            2,
            NPT_TimeInterval(0.),
            NPT_TimeInterval(0.));
        return NPT_FAILED(result) ? "搜索投屏设备失败。" : std::string();
    }

    std::string ListDevices() {
        std::lock_guard<std::mutex> lock(mutex_);
        std::string payload;
        for (const auto& entry : devices_) {
            const DeviceRecord& device = entry.second;
            if (!payload.empty()) payload.push_back(kRecordSeparator);
            payload.append(device.id);
            payload.push_back(kFieldSeparator);
            payload.append(device.name);
            payload.push_back(kFieldSeparator);
            payload.append(device.description);
            payload.push_back(kFieldSeparator);
            payload.append(device.description);
            payload.push_back(kFieldSeparator);
            payload.push_back(kFieldSeparator);
            payload.append(device.location);
        }
        return payload;
    }

    std::string CastMedia(const std::string& device_id, const std::string& uri, const std::string& metadata) {
        PLT_DeviceDataReference device;
        const NPT_Result find_result = FindDevice(device_id, device);
        if (NPT_FAILED(find_result)) {
            return "未找到选中的投屏设备。";
        }

        media_controller_->Stop(device, 0, nullptr);
        std::this_thread::sleep_for(std::chrono::milliseconds(150));

        const NPT_Result uri_result = media_controller_->SetAVTransportURI(
            device,
            0,
            uri.c_str(),
            metadata.c_str(),
            nullptr);
        if (NPT_FAILED(uri_result)) {
            return "发送投屏地址失败。";
        }
        std::this_thread::sleep_for(std::chrono::milliseconds(150));

        const NPT_Result play_result = media_controller_->Play(device, 0, "1", nullptr);
        if (NPT_FAILED(play_result)) {
            return "启动投屏播放失败。";
        }
        return std::string();
    }

    std::string StopCast(const std::string& device_id) {
        PLT_DeviceDataReference device;
        const NPT_Result find_result = FindDevice(device_id, device);
        if (NPT_FAILED(find_result)) {
            return "未找到选中的投屏设备。";
        }
        const NPT_Result stop_result = media_controller_->Stop(device, 0, nullptr);
        return NPT_FAILED(stop_result) ? "停止投屏失败。" : std::string();
    }

private:
    NPT_Result FindDevice(const std::string& device_id, PLT_DeviceDataReference& device) {
        if (media_controller_.AsPointer() == nullptr) {
            return NPT_FAILURE;
        }
        NPT_Result result = media_controller_->FindRenderer(device_id.c_str(), device);
        if (NPT_SUCCEEDED(result)) {
            return result;
        }

        std::lock_guard<std::mutex> lock(mutex_);
        const auto iter = devices_.find(device_id);
        if (iter == devices_.end()) {
            return NPT_FAILURE;
        }
        device = iter->second.device;
        return NPT_SUCCESS;
    }

    std::mutex mutex_;
    std::map<std::string, DeviceRecord> devices_;
    std::unique_ptr<PLT_UPnP> upnp_;
    PLT_CtrlPointReference ctrl_point_;
    PLT_MediaControllerReference media_controller_;
};

LynUpnpCastEngine* FromHandle(jlong handle) {
    return reinterpret_cast<LynUpnpCastEngine*>(handle);
}

} // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_top_iwesley_lyn_music_cast_upnp_android_AndroidUpnpCastGateway_nativeCreate(
    JNIEnv*,
    jobject) {
    auto* engine = new (std::nothrow) LynUpnpCastEngine();
    return reinterpret_cast<jlong>(engine);
}

extern "C" JNIEXPORT jstring JNICALL
Java_top_iwesley_lyn_music_cast_upnp_android_AndroidUpnpCastGateway_nativeStartDiscovery(
    JNIEnv* env,
    jobject,
    jlong handle) {
    auto* engine = FromHandle(handle);
    if (engine == nullptr) return Error(env, "UPnP 控制点未初始化。");
    const std::string error = engine->StartDiscovery();
    return error.empty() ? Success(env) : Error(env, error);
}

extern "C" JNIEXPORT jstring JNICALL
Java_top_iwesley_lyn_music_cast_upnp_android_AndroidUpnpCastGateway_nativeListDevices(
    JNIEnv* env,
    jobject,
    jlong handle) {
    auto* engine = FromHandle(handle);
    if (engine == nullptr) return ToJString(env, "");
    return ToJString(env, engine->ListDevices());
}

extern "C" JNIEXPORT jstring JNICALL
Java_top_iwesley_lyn_music_cast_upnp_android_AndroidUpnpCastGateway_nativeCastMedia(
    JNIEnv* env,
    jobject,
    jlong handle,
    jstring device_id,
    jstring uri,
    jstring metadata) {
    auto* engine = FromHandle(handle);
    if (engine == nullptr) return Error(env, "UPnP 控制点未初始化。");
    const std::string error = engine->CastMedia(
        ToString(env, device_id),
        ToString(env, uri),
        ToString(env, metadata));
    return error.empty() ? Success(env) : Error(env, error);
}

extern "C" JNIEXPORT jstring JNICALL
Java_top_iwesley_lyn_music_cast_upnp_android_AndroidUpnpCastGateway_nativeStopCast(
    JNIEnv* env,
    jobject,
    jlong handle,
    jstring device_id) {
    auto* engine = FromHandle(handle);
    if (engine == nullptr) return Error(env, "UPnP 控制点未初始化。");
    const std::string error = engine->StopCast(ToString(env, device_id));
    return error.empty() ? Success(env) : Error(env, error);
}

extern "C" JNIEXPORT void JNICALL
Java_top_iwesley_lyn_music_cast_upnp_android_AndroidUpnpCastGateway_nativeRelease(
    JNIEnv*,
    jobject,
    jlong handle) {
    delete FromHandle(handle);
}
