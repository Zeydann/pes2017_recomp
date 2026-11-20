cmake_minimum_required(VERSION 3.22.1)
project(savagewind_host LANGUAGES C CXX)

# Host shell only.
#
# The Savage Wind runtime lives in ../../../.. (runtime/, runtime/nvn/**) and is
# built by the upstream Makefile. It is added to this target one directory at a
# time as the Android platform layer lands, alongside SDL3, shaderc, VMA, ImGui
# and the FidelityFX sources. See PORT-HANDOFF.md for the order.

add_library(savagewind_host SHARED
    jni_bridge.cpp
)

target_compile_features(savagewind_host PRIVATE cxx_std_17)

find_library(log_lib log)
target_link_libraries(savagewind_host PRIVATE ${log_lib} android)
