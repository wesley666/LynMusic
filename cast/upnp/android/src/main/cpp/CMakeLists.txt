cmake_minimum_required(VERSION 3.22.1)

project(lyn_upnp_cast)

set(UPNP_ROOT ${CMAKE_CURRENT_SOURCE_DIR}/../../../../../../third_party/libUPnP)

set(UPNP_SOURCES
    ${UPNP_ROOT}/Platinum/Source/Core/PltAction.cpp
    ${UPNP_ROOT}/Platinum/Source/Core/PltArgument.cpp
    ${UPNP_ROOT}/Platinum/Source/Core/PltConstants.cpp
    ${UPNP_ROOT}/Platinum/Source/Core/PltCtrlPoint.cpp
    ${UPNP_ROOT}/Platinum/Source/Core/PltCtrlPointTask.cpp
    ${UPNP_ROOT}/Platinum/Source/Core/PltDatagramStream.cpp
    ${UPNP_ROOT}/Platinum/Source/Core/PltDeviceData.cpp
    ${UPNP_ROOT}/Platinum/Source/Core/PltDeviceHost.cpp
    ${UPNP_ROOT}/Platinum/Source/Core/PltEvent.cpp
    ${UPNP_ROOT}/Platinum/Source/Core/PltHttp.cpp
    ${UPNP_ROOT}/Platinum/Source/Core/PltHttpClientTask.cpp
    ${UPNP_ROOT}/Platinum/Source/Core/PltHttpServer.cpp
    ${UPNP_ROOT}/Platinum/Source/Core/PltHttpServerTask.cpp
    ${UPNP_ROOT}/Platinum/Source/Core/PltIconsData.cpp
    ${UPNP_ROOT}/Platinum/Source/Core/PltMimeType.cpp
    ${UPNP_ROOT}/Platinum/Source/Core/PltProtocolInfo.cpp
    ${UPNP_ROOT}/Platinum/Source/Core/PltService.cpp
    ${UPNP_ROOT}/Platinum/Source/Core/PltSsdp.cpp
    ${UPNP_ROOT}/Platinum/Source/Core/PltStateVariable.cpp
    ${UPNP_ROOT}/Platinum/Source/Core/PltTaskManager.cpp
    ${UPNP_ROOT}/Platinum/Source/Core/PltThreadTask.cpp
    ${UPNP_ROOT}/Platinum/Source/Core/PltUPnP.cpp
    ${UPNP_ROOT}/Platinum/Source/Devices/MediaRenderer/AVTransportSCPD.cpp
    ${UPNP_ROOT}/Platinum/Source/Devices/MediaRenderer/PltMediaController.cpp
    ${UPNP_ROOT}/Platinum/Source/Devices/MediaRenderer/PltMediaRenderer.cpp
    ${UPNP_ROOT}/Platinum/Source/Devices/MediaRenderer/RdrConnectionManagerSCPD.cpp
    ${UPNP_ROOT}/Platinum/Source/Devices/MediaRenderer/RenderingControlSCPD.cpp
    ${UPNP_ROOT}/Platinum/Source/Devices/MediaServer/ConnectionManagerSCPD.cpp
    ${UPNP_ROOT}/Platinum/Source/Devices/MediaServer/ContentDirectorySCPD.cpp
    ${UPNP_ROOT}/Platinum/Source/Devices/MediaServer/ContentDirectorywSearchSCPD.cpp
    ${UPNP_ROOT}/Platinum/Source/Devices/MediaServer/PltDidl.cpp
    ${UPNP_ROOT}/Platinum/Source/Devices/MediaServer/PltFileMediaServer.cpp
    ${UPNP_ROOT}/Platinum/Source/Devices/MediaServer/PltMediaBrowser.cpp
    ${UPNP_ROOT}/Platinum/Source/Devices/MediaServer/PltMediaCache.cpp
    ${UPNP_ROOT}/Platinum/Source/Devices/MediaServer/PltMediaItem.cpp
    ${UPNP_ROOT}/Platinum/Source/Devices/MediaServer/PltMediaServer.cpp
    ${UPNP_ROOT}/Platinum/Source/Devices/MediaServer/PltSyncMediaBrowser.cpp
    ${UPNP_ROOT}/Platinum/Source/Devices/MediaConnect/PltMediaConnect.cpp
    ${UPNP_ROOT}/Platinum/Source/Devices/MediaConnect/X_MS_MediaReceiverRegistrarSCPD.cpp
    ${UPNP_ROOT}/Neptune/Source/Core/Neptune.cpp
    ${UPNP_ROOT}/Neptune/Source/Core/NptAutomaticCleaner.cpp
    ${UPNP_ROOT}/Neptune/Source/Core/NptBase64.cpp
    ${UPNP_ROOT}/Neptune/Source/Core/NptBufferedStreams.cpp
    ${UPNP_ROOT}/Neptune/Source/Core/NptCommon.cpp
    ${UPNP_ROOT}/Neptune/Source/Core/NptDataBuffer.cpp
    ${UPNP_ROOT}/Neptune/Source/Core/NptDebug.cpp
    ${UPNP_ROOT}/Neptune/Source/Core/NptFile.cpp
    ${UPNP_ROOT}/Neptune/Source/Core/NptHash.cpp
    ${UPNP_ROOT}/Neptune/Source/Core/NptHttp.cpp
    ${UPNP_ROOT}/Neptune/Source/Core/NptList.cpp
    ${UPNP_ROOT}/Neptune/Source/Core/NptLogging.cpp
    ${UPNP_ROOT}/Neptune/Source/Core/NptMessaging.cpp
    ${UPNP_ROOT}/Neptune/Source/Core/NptNetwork.cpp
    ${UPNP_ROOT}/Neptune/Source/Core/NptQueue.cpp
    ${UPNP_ROOT}/Neptune/Source/Core/NptResults.cpp
    ${UPNP_ROOT}/Neptune/Source/Core/NptRingBuffer.cpp
    ${UPNP_ROOT}/Neptune/Source/Core/NptSimpleMessageQueue.cpp
    ${UPNP_ROOT}/Neptune/Source/Core/NptSockets.cpp
    ${UPNP_ROOT}/Neptune/Source/Core/NptStreams.cpp
    ${UPNP_ROOT}/Neptune/Source/Core/NptStrings.cpp
    ${UPNP_ROOT}/Neptune/Source/Core/NptSystem.cpp
    ${UPNP_ROOT}/Neptune/Source/Core/NptThreads.cpp
    ${UPNP_ROOT}/Neptune/Source/Core/NptTime.cpp
    ${UPNP_ROOT}/Neptune/Source/Core/NptTls.cpp
    ${UPNP_ROOT}/Neptune/Source/Core/NptUri.cpp
    ${UPNP_ROOT}/Neptune/Source/Core/NptUtils.cpp
    ${UPNP_ROOT}/Neptune/Source/Core/NptXml.cpp
    ${UPNP_ROOT}/Neptune/Source/System/Bsd/NptBsdNetwork.cpp
    ${UPNP_ROOT}/Neptune/Source/System/Bsd/NptBsdResolver.cpp
    ${UPNP_ROOT}/Neptune/Source/System/Bsd/NptBsdSockets.cpp
    ${UPNP_ROOT}/Neptune/Source/System/Null/NptNullAutoreleasePool.cpp
    ${UPNP_ROOT}/Neptune/Source/System/StdC/NptStdcConsole.cpp
    ${UPNP_ROOT}/Neptune/Source/System/StdC/NptStdcDebug.cpp
    ${UPNP_ROOT}/Neptune/Source/System/StdC/NptStdcEnvironment.cpp
    ${UPNP_ROOT}/Neptune/Source/System/StdC/NptStdcFile.cpp
    ${UPNP_ROOT}/Neptune/Source/System/Posix/NptPosixEnvironment.cpp
    ${UPNP_ROOT}/Neptune/Source/System/Posix/NptPosixFile.cpp
    ${UPNP_ROOT}/Neptune/Source/System/Posix/NptPosixQueue.cpp
    ${UPNP_ROOT}/Neptune/Source/System/Posix/NptPosixSystem.cpp
    ${UPNP_ROOT}/Neptune/Source/System/Posix/NptPosixThreads.cpp
    ${UPNP_ROOT}/Neptune/Source/System/Posix/NptPosixTime.cpp
    ${UPNP_ROOT}/Neptune/Source/System/Posix/NptSelectableMessageQueue.cpp
)

add_library(lyn_upnp_cast SHARED
    lyn_upnp_cast_jni.cpp
    lyn_upnp_renderer_jni.cpp
    ${UPNP_SOURCES}
)

target_include_directories(lyn_upnp_cast PRIVATE
    ${UPNP_ROOT}
    ${UPNP_ROOT}/Platinum/Source/Core
    ${UPNP_ROOT}/Platinum/Source/Platinum
    ${UPNP_ROOT}/Platinum/Source/Devices/MediaConnect
    ${UPNP_ROOT}/Platinum/Source/Devices/MediaRenderer
    ${UPNP_ROOT}/Platinum/Source/Devices/MediaServer
    ${UPNP_ROOT}/Neptune/Source/Core
    ${UPNP_ROOT}/Neptune/Source/System/Bsd
    ${UPNP_ROOT}/Neptune/Source/System/Posix
)

target_compile_features(lyn_upnp_cast PRIVATE cxx_std_17)
target_compile_definitions(lyn_upnp_cast PRIVATE
    ANDROID
    NPT_CONFIG_ENABLE_LOGGING
    NPT_fseek=fseek
    NPT_ftell=ftell
)
target_compile_options(lyn_upnp_cast PRIVATE
    -frtti
    -fexceptions
    -Wno-deprecated-declarations
    -Wno-unused-parameter
    -Wno-unused-variable
    -Wno-format-security
)

find_library(log-lib log)
target_link_libraries(lyn_upnp_cast ${log-lib})
