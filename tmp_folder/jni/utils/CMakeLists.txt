add_library(utils STATIC
        NPUWareUtilsLib.cpp
        utils.cpp
)

target_include_directories(utils PUBLIC ${CMAKE_CURRENT_SOURCE_DIR})