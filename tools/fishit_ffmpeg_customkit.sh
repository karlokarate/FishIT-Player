#!/usr/bin/env bash
set -euo pipefail

# FishIT-Player FFmpegKit Custom Kit
# ----------------------------------
# This script is intended to be executed INSIDE the ffmpeg-kit repo root.
# It configures a slim FFmpeg build with:
# - only the demuxers we care about
# - only audio decoders we care about
# - mp4/fMP4 muxing
# - no video SW decoders, no encoders, no filters, minimal protocols.

: "${FFMPEGKIT_API_LEVEL:=24}"

echo "=== FishIT FFmpegKit custom kit ==="
echo "ANDROID_SDK_ROOT=${ANDROID_SDK_ROOT:-<not set>}"
echo "ANDROID_NDK_ROOT=${ANDROID_NDK_ROOT:-<not set>}"
echo "FFMPEGKIT_API_LEVEL=${FFMPEGKIT_API_LEVEL}"

# ----- android.sh flags (coarse control) -----
ANDROID_SH_FLAGS=()

# Architectures: arm64-v8a + armeabi-v7a, no x86
# If you ever want to drop 32-bit, comment out the two lines below and enable --disable-arm-v7a + --disable-arm-v7a-neon.
# ANDROID_SH_FLAGS+=(--disable-arm-v7a)
# ANDROID_SH_FLAGS+=(--disable-arm-v7a-neon)
ANDROID_SH_FLAGS+=(--disable-x86)
ANDROID_SH_FLAGS+=(--disable-x86-64)

# API level
ANDROID_SH_FLAGS+=(--api-level="${FFMPEGKIT_API_LEVEL}")

# Built-in Android libs
ANDROID_SH_FLAGS+=(--enable-android-zlib)
ANDROID_SH_FLAGS+=(--enable-android-media-codec)

# No GPL encoders (x264/x265/…)
# → DO NOT add --enable-gpl here.

# No external libs for now; we rely on FFmpeg core.
# If you want Opus/Vorbis via external libs later, you can also add e.g.:
# ANDROID_SH_FLAGS+=(--enable-opus)
# ANDROID_SH_FLAGS+=(--enable-libvorbis)

# Optimize for speed a bit (optional, remove if you want minimum size)
ANDROID_SH_FLAGS+=(--speed)

# ----- Fine-grained FFmpeg configure flags -----
# ffmpeg-kit build scripts respect FFMPEG_EXTRA_CONFIGURE_FLAGS and pass it down to ffmpeg's ./configure.
# We use this to slim down demuxers/decoders/muxers/protocols.

FFMPEG_EXTRA_CONFIGURE_FLAGS=""
FFMPEG_EXTRA_CONFIGURE_FLAGS+=" --disable-everything"

# Demuxers
FFMPEG_EXTRA_CONFIGURE_FLAGS+=" --enable-demuxer=mov"
FFMPEG_EXTRA_CONFIGURE_FLAGS+=" --enable-demuxer=mp4"
FFMPEG_EXTRA_CONFIGURE_FLAGS+=" --enable-demuxer=ism"
FFMPEG_EXTRA_CONFIGURE_FLAGS+=" --enable-demuxer=matroska"
FFMPEG_EXTRA_CONFIGURE_FLAGS+=" --enable-demuxer=webm"
FFMPEG_EXTRA_CONFIGURE_FLAGS+=" --enable-demuxer=avi"
FFMPEG_EXTRA_CONFIGURE_FLAGS+=" --enable-demuxer=mpegts"
FFMPEG_EXTRA_CONFIGURE_FLAGS+=" --enable-demuxer=flv"
FFMPEG_EXTRA_CONFIGURE_FLAGS+=" --enable-demuxer=ogg"
FFMPEG_EXTRA_CONFIGURE_FLAGS+=" --enable-demuxer=wav"

# Muxers (mp4 + fragmented mp4)
FFMPEG_EXTRA_CONFIGURE_FLAGS+=" --enable-muxer=mp4"
FFMPEG_EXTRA_CONFIGURE_FLAGS+=" --enable-muxer=ismv"

# Audio decoders
FFMPEG_EXTRA_CONFIGURE_FLAGS+=" --enable-decoder=aac"
FFMPEG_EXTRA_CONFIGURE_FLAGS+=" --enable-decoder=mp3"
FFMPEG_EXTRA_CONFIGURE_FLAGS+=" --enable-decoder=vorbis"
FFMPEG_EXTRA_CONFIGURE_FLAGS+=" --enable-decoder=opus"
FFMPEG_EXTRA_CONFIGURE_FLAGS+=" --enable-decoder=flac"
FFMPEG_EXTRA_CONFIGURE_FLAGS+=" --enable-decoder=alac"
FFMPEG_EXTRA_CONFIGURE_FLAGS+=" --enable-decoder=ape"
FFMPEG_EXTRA_CONFIGURE_FLAGS+=" --enable-decoder=wmav1"
FFMPEG_EXTRA_CONFIGURE_FLAGS+=" --enable-decoder=wmav2"

# Parsers
FFMPEG_EXTRA_CONFIGURE_FLAGS+=" --enable-parser=aac"
FFMPEG_EXTRA_CONFIGURE_FLAGS+=" --enable-parser=aac_latm"
FFMPEG_EXTRA_CONFIGURE_FLAGS+=" --enable-parser=mpegaudio"
FFMPEG_EXTRA_CONFIGURE_FLAGS+=" --enable-parser=vorbis"
FFMPEG_EXTRA_CONFIGURE_FLAGS+=" --enable-parser=opus"
FFMPEG_EXTRA_CONFIGURE_FLAGS+=" --enable-parser=flac"

# Protocols: only local file + pipe
FFMPEG_EXTRA_CONFIGURE_FLAGS+=" --enable-protocol=file"
FFMPEG_EXTRA_CONFIGURE_FLAGS+=" --enable-protocol=pipe"

# No encoders, no filters, no video SW decoders, no network protocols.

export FFMPEG_EXTRA_CONFIGURE_FLAGS

echo "=== android.sh flags ==="
printf '  %s\n' "${ANDROID_SH_FLAGS[@]}"
echo "=== FFMPEG_EXTRA_CONFIGURE_FLAGS ==="
echo "  ${FFMPEG_EXTRA_CONFIGURE_FLAGS}"

# ----- Run android.sh with our custom settings -----
./android.sh "${ANDROID_SH_FLAGS[@]}"

echo "=== FishIT FFmpegKit custom kit build finished ==="
echo "Check prebuilt/bundle-android-aar/ffmpeg-kit/ for the resulting AAR."