#include <jni.h>

#include <chrono>
#include <condition_variable>
#include <cstdio>
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

struct TransportInfoQuery {
    std::mutex mutex;
    std::condition_variable cv;
    bool done = false;
    bool success = false;
    std::string state;
};

struct PositionInfoQuery {
    std::mutex mutex;
    std::condition_variable cv;
    bool done = false;
    bool success = false;
    NPT_Int64 position_ms = 0;
    NPT_Int64 duration_ms = 0;
};

std::string FormatSeekTarget(NPT_Int64 position_ms) {
    if (position_ms < 0) position_ms = 0;
    const NPT_Int64 total_seconds = position_ms / 1000;
    const NPT_Int64 hours = total_seconds / 3600;
    const NPT_Int64 minutes = (total_seconds % 3600) / 60;
    const NPT_Int64 seconds = total_seconds % 60;
    char buffer[16];
    std::snprintf(buffer, sizeof(buffer), "%02lld:%02lld:%02lld",
                  static_cast<long long>(hours),
                  static_cast<long long>(minutes),
                  static_cast<long long>(seconds));
    return std::string(buffer);
}

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

    void OnGetTransportInfoResult(
        NPT_Result res,
        PLT_DeviceDataReference&,
        PLT_TransportInfo* info,
        void* userdata) override {
        auto* query = static_cast<TransportInfoQuery*>(userdata);
        if (query == nullptr) return;
        {
            std::lock_guard<std::mutex> lock(query->mutex);
            query->success = NPT_SUCCEEDED(res) && info != nullptr;
            if (query->success) {
                query->state = CleanField(info->cur_transport_state);
            }
            query->done = true;
        }
        query->cv.notify_all();
        std::lock_guard<std::mutex> lock(query_mutex_);
        transport_queries_.erase(query);
    }

    void OnGetPositionInfoResult(
        NPT_Result res,
        PLT_DeviceDataReference&,
        PLT_PositionInfo* info,
        void* userdata) override {
        auto* query = static_cast<PositionInfoQuery*>(userdata);
        if (query == nullptr) return;
        {
            std::lock_guard<std::mutex> lock(query->mutex);
            query->success = NPT_SUCCEEDED(res) && info != nullptr;
            if (query->success) {
                query->position_ms = info->rel_time.ToMillis();
                query->duration_ms = info->track_duration.ToMillis();
                if (query->position_ms < 0) query->position_ms = 0;
                if (query->duration_ms < 0) query->duration_ms = 0;
            }
            query->done = true;
        }
        query->cv.notify_all();
        std::lock_guard<std::mutex> lock(query_mutex_);
        position_queries_.erase(query);
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

    std::string PlayCast(const std::string& device_id) {
        PLT_DeviceDataReference device;
        const NPT_Result find_result = FindDevice(device_id, device);
        if (NPT_FAILED(find_result)) {
            return "未找到选中的投屏设备。";
        }
        const NPT_Result play_result = media_controller_->Play(device, 0, "1", nullptr);
        return NPT_FAILED(play_result) ? "恢复投屏播放失败。" : std::string();
    }

    std::string PauseCast(const std::string& device_id) {
        PLT_DeviceDataReference device;
        const NPT_Result find_result = FindDevice(device_id, device);
        if (NPT_FAILED(find_result)) {
            return "未找到选中的投屏设备。";
        }
        const NPT_Result pause_result = media_controller_->Pause(device, 0, nullptr);
        return NPT_FAILED(pause_result) ? "暂停投屏失败。" : std::string();
    }

    std::string SeekCast(const std::string& device_id, NPT_Int64 position_ms) {
        PLT_DeviceDataReference device;
        const NPT_Result find_result = FindDevice(device_id, device);
        if (NPT_FAILED(find_result)) {
            return "未找到选中的投屏设备。";
        }
        const std::string target = FormatSeekTarget(position_ms);
        const NPT_Result seek_result = media_controller_->Seek(
            device,
            0,
            "REL_TIME",
            target.c_str(),
            nullptr);
        return NPT_FAILED(seek_result) ? "调整投屏进度失败。" : std::string();
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

    std::string QueryPlaybackState(const std::string& device_id) {
        PLT_DeviceDataReference device;
        const NPT_Result find_result = FindDevice(device_id, device);
        if (NPT_FAILED(find_result)) {
            return std::string();
        }

        auto transport_query = std::make_shared<TransportInfoQuery>();
        {
            std::lock_guard<std::mutex> lock(query_mutex_);
            transport_queries_[transport_query.get()] = transport_query;
        }
        const NPT_Result transport_result = media_controller_->GetTransportInfo(
            device,
            0,
            transport_query.get());
        if (NPT_FAILED(transport_result)) {
            std::lock_guard<std::mutex> lock(query_mutex_);
            transport_queries_.erase(transport_query.get());
            return std::string();
        }
        {
            std::unique_lock<std::mutex> lock(transport_query->mutex);
            transport_query->cv.wait_for(
                lock,
                std::chrono::milliseconds(1000),
                [&transport_query] { return transport_query->done; });
        }
        if (!transport_query->done || !transport_query->success) {
            return std::string();
        }

        NPT_Int64 position_ms = 0;
        NPT_Int64 duration_ms = 0;
        auto position_query = std::make_shared<PositionInfoQuery>();
        {
            std::lock_guard<std::mutex> lock(query_mutex_);
            position_queries_[position_query.get()] = position_query;
        }
        const NPT_Result position_result = media_controller_->GetPositionInfo(
            device,
            0,
            position_query.get());
        if (NPT_FAILED(position_result)) {
            std::lock_guard<std::mutex> lock(query_mutex_);
            position_queries_.erase(position_query.get());
        } else {
            std::unique_lock<std::mutex> lock(position_query->mutex);
            position_query->cv.wait_for(
                lock,
                std::chrono::milliseconds(1000),
                [&position_query] { return position_query->done; });
            if (position_query->done && position_query->success) {
                position_ms = position_query->position_ms;
                duration_ms = position_query->duration_ms;
            }
        }

        std::string payload = transport_query->state.empty() ? "UNKNOWN" : transport_query->state;
        payload.push_back(kFieldSeparator);
        payload.append(std::to_string(position_ms));
        payload.push_back(kFieldSeparator);
        payload.append(std::to_string(duration_ms));
        return payload;
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
    std::mutex query_mutex_;
    std::map<void*, std::shared_ptr<TransportInfoQuery>> transport_queries_;
    std::map<void*, std::shared_ptr<PositionInfoQuery>> position_queries_;
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
Java_top_iwesley_lyn_music_cast_upnp_android_AndroidUpnpCastGateway_nativePlayCast(
    JNIEnv* env,
    jobject,
    jlong handle,
    jstring device_id) {
    auto* engine = FromHandle(handle);
    if (engine == nullptr) return Error(env, "UPnP 控制点未初始化。");
    const std::string error = engine->PlayCast(ToString(env, device_id));
    return error.empty() ? Success(env) : Error(env, error);
}

extern "C" JNIEXPORT jstring JNICALL
Java_top_iwesley_lyn_music_cast_upnp_android_AndroidUpnpCastGateway_nativePauseCast(
    JNIEnv* env,
    jobject,
    jlong handle,
    jstring device_id) {
    auto* engine = FromHandle(handle);
    if (engine == nullptr) return Error(env, "UPnP 控制点未初始化。");
    const std::string error = engine->PauseCast(ToString(env, device_id));
    return error.empty() ? Success(env) : Error(env, error);
}

extern "C" JNIEXPORT jstring JNICALL
Java_top_iwesley_lyn_music_cast_upnp_android_AndroidUpnpCastGateway_nativeSeekCast(
    JNIEnv* env,
    jobject,
    jlong handle,
    jstring device_id,
    jlong position_ms) {
    auto* engine = FromHandle(handle);
    if (engine == nullptr) return Error(env, "UPnP 控制点未初始化。");
    const std::string error = engine->SeekCast(
        ToString(env, device_id),
        static_cast<NPT_Int64>(position_ms));
    return error.empty() ? Success(env) : Error(env, error);
}

extern "C" JNIEXPORT jstring JNICALL
Java_top_iwesley_lyn_music_cast_upnp_android_AndroidUpnpCastGateway_nativeQueryCastPlayback(
    JNIEnv* env,
    jobject,
    jlong handle,
    jstring device_id) {
    auto* engine = FromHandle(handle);
    if (engine == nullptr) return ToJString(env, "");
    return ToJString(env, engine->QueryPlaybackState(ToString(env, device_id)));
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
