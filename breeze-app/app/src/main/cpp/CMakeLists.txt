# Add Crashlytics NDK library
add_library(crashlytics SHARED IMPORTED)
set_target_properties(crashlytics PROPERTIES IMPORTED_LOCATION
    ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libcrashlytics.so)

# Link against Crashlytics
target_link_libraries(${PROJECT_NAME}
    # ... existing libraries ...
    crashlytics
)

# Enable GWP-ASan
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -fsanitize=address -fno-omit-frame-pointer")
set(CMAKE_C_FLAGS "${CMAKE_C_FLAGS} -fsanitize=address -fno-omit-frame-pointer")

# ... rest of existing configuration ... 