APP_ABI := arm64-v8a
APP_STL := c++_shared
APP_PLATFORM := android-31
APP_OPTIM := debug
APP_CPPFLAGS := -std=c++17 \
				-Wall \
				-Wno-int-to-void-pointer-cast \
				-Wno-reorder-ctor \
				-frtti \
				-fexceptions \
				$(EXTRA_GLOBAL_CPPFLAGS) \
				-fvisibility=hidden #Hide all symbols unless specifically enabled in code
